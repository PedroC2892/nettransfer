package nettransfer;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Discovery packets carry the username and hostname of every peer on the LAN.
 * Sent as plain UDP broadcast, they would be readable by anyone sniffing the
 * network, so each packet is encrypted with AES-256-GCM under a static,
 * app-wide pre-shared key: [12-byte random IV][ciphertext + 16-byte tag].
 * This is not peer-authenticated (no per-connection handshake is possible for
 * a one-to-many broadcast) — it only shields the payload from passive sniffing
 * by anyone who doesn't have the app.
 */
public class DiscoveryService {
    private static final Logger LOG = Logger.getLogger(DiscoveryService.class.getName());
    public static final int DISCOVERY_PORT = 54321;
    public static final long BROADCAST_INTERVAL_MS = 5000;

    private static final long RATE_WINDOW_MS = 10_000;
    private static final int RATE_MAX_PACKETS = 10;
    private static final int MAX_TRACKED_SOURCES = 4096;
    private static final Gson GSON = new Gson();
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_BITS = 128;

    private static final SecretKeySpec DISCOVERY_KEY = deriveDiscoveryKey();

    private final String myId = AppSettings.load().getOrCreateDeviceId();
    private final SecureRandom random = new SecureRandom();
    private final byte[] plaintext;
    private final byte[] goodbyePlaintext;

    public DiscoveryService(int tcpPort) {
        DiscoveryMessage msg = new DiscoveryMessage("DISCOVER", myId, getUserName(), getHostname(), tcpPort);
        plaintext = GSON.toJson(msg).getBytes(StandardCharsets.UTF_8);
        DiscoveryMessage bye = new DiscoveryMessage("GOODBYE", myId, getUserName(), getHostname(), tcpPort);
        goodbyePlaintext = GSON.toJson(bye).getBytes(StandardCharsets.UTF_8);
    }

    private static SecretKeySpec deriveDiscoveryKey() {
        try {
            byte[] keyBytes = MessageDigest.getInstance("SHA-256")
                    .digest("NetTransfer UDP discovery v1".getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(keyBytes, "AES");
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    byte[] encryptPacket(byte[] plain) throws GeneralSecurityException {
        byte[] iv = new byte[GCM_IV_LENGTH];
        random.nextBytes(iv);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, DISCOVERY_KEY, new GCMParameterSpec(GCM_TAG_BITS, iv));
        byte[] ciphertext = cipher.doFinal(plain);
        byte[] packet = new byte[GCM_IV_LENGTH + ciphertext.length];
        System.arraycopy(iv, 0, packet, 0, GCM_IV_LENGTH);
        System.arraycopy(ciphertext, 0, packet, GCM_IV_LENGTH, ciphertext.length);
        return packet;
    }

    private static byte[] decryptPacket(byte[] packet, int length) throws GeneralSecurityException {
        if (length <= GCM_IV_LENGTH) {
            throw new AEADBadTagException("Packet too short");
        }
        byte[] iv = new byte[GCM_IV_LENGTH];
        System.arraycopy(packet, 0, iv, 0, GCM_IV_LENGTH);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, DISCOVERY_KEY, new GCMParameterSpec(GCM_TAG_BITS, iv));
        return cipher.doFinal(packet, GCM_IV_LENGTH, length - GCM_IV_LENGTH);
    }

    private static String getHostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            return "unknown";
        }
    }

    public static String getUserName() {
        try {
            String user = System.getProperty("user.name");
            return user != null ? user : "unknown";
        } catch (SecurityException e) {
            return "unknown";
        }
    }

    public void broadcastDiscovery(int port, long intervalMillis) throws IOException {
        Set<String> lastInterfaceNames = null;
        while (true) {
            List<NetworkInterfaceInfo> ifaces = NetworkInterfaceInfo.enumerate();
            Set<String> names = new LinkedHashSet<>();
            for (NetworkInterfaceInfo i : ifaces) names.add(i.name);
            if (!names.equals(lastInterfaceNames)) {
                TransferLogger.logInterfaces(ifaces);
                lastInterfaceNames = names;
            }

            AppSettings settings = AppSettings.load();
            for (NetworkInterfaceInfo info : ifaces) {
                if (!info.supportsBroadcast || !settings.isInterfaceEnabled(info.name)) continue;
                try (DatagramSocket socket = new DatagramSocket(
                        new InetSocketAddress(InetAddress.getByName(info.ipAddress), 0))) {
                    socket.setBroadcast(true);
                    InetAddress bcast = InetAddress.getByName(info.broadcastAddress);
                    byte[] packet = encryptPacket(plaintext);
                    socket.send(new DatagramPacket(packet, packet.length, bcast, port));
                } catch (IOException e) {
                    // interface may have changed state between enumeration and send — skip it this cycle
                    LOG.log(Level.FINE, "Broadcast on " + info.name + " failed", e);
                } catch (GeneralSecurityException e) {
                    TransferLogger.logSecurityEvent("Failed to encrypt discovery packet: " + e.getMessage(), info.ipAddress);
                }
            }

            try {
                Thread.sleep(intervalMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    /** Best-effort single-shot broadcast telling peers this device is going offline now, instead of making them wait for the stale timeout. */
    public void announceGoodbye(int port) {
        for (NetworkInterfaceInfo info : NetworkInterfaceInfo.enumerate()) {
            if (!info.supportsBroadcast) continue;
            try (DatagramSocket socket = new DatagramSocket(
                    new InetSocketAddress(InetAddress.getByName(info.ipAddress), 0))) {
                socket.setBroadcast(true);
                InetAddress bcast = InetAddress.getByName(info.broadcastAddress);
                byte[] packet = encryptPacket(goodbyePlaintext);
                socket.send(new DatagramPacket(packet, packet.length, bcast, port));
            } catch (IOException | GeneralSecurityException e) {
                // best-effort: the stale timeout is the fallback if this doesn't get through
                LOG.log(Level.FINE, "Goodbye on " + info.name + " failed", e);
            }
        }
    }

    public void broadcastReceiver(int port, Consumer<Peer> onPeerDiscovered, Consumer<String> onPeerGone) throws IOException {
        Map<String, Deque<Long>> recentPackets = new HashMap<>();
        try (DatagramSocket socket = new DatagramSocket(port)) {
            byte[] buffer = new byte[2048]; // 3 x 256 UTF-8 chars plus JSON and GCM overhead must fit
            while (true) {
                DatagramPacket receivedPacket = new DatagramPacket(buffer, buffer.length);
                socket.receive(receivedPacket);

                String ip = receivedPacket.getAddress().getHostAddress();
                if (isRateLimited(recentPackets, ip)) continue;

                DiscoveryMessage received = parse(receivedPacket.getData(), receivedPacket.getLength());
                if (received == null || received.id.equals(myId)) continue;

                if ("GOODBYE".equals(received.type)) {
                    onPeerGone.accept(received.id);
                    continue;
                }

                Peer peer = new Peer(received.id, received.userName, received.hostName, ip, received.tcpPort);
                onPeerDiscovered.accept(peer);
            }
        }
    }

    /** Decrypts and validates one packet; returns null for anything malformed, forged or unknown. */
    static DiscoveryMessage parse(byte[] packet, int length) {
        try {
            byte[] plain = decryptPacket(packet, length);
            DiscoveryMessage m = GSON.fromJson(new String(plain, StandardCharsets.UTF_8), DiscoveryMessage.class);
            return isValid(m) ? m : null;
        } catch (GeneralSecurityException | JsonParseException e) {
            return null; // expected for foreign or corrupted traffic on the port
        }
    }

    static boolean isRateLimited(Map<String, Deque<Long>> recentPackets, String ip) {
        long now = System.currentTimeMillis();
        if (recentPackets.size() > MAX_TRACKED_SOURCES) {
            // spoofed source addresses would otherwise grow the map without bound
            recentPackets.values().removeIf(t -> t.isEmpty() || now - t.peekLast() > RATE_WINDOW_MS);
            if (recentPackets.size() > MAX_TRACKED_SOURCES) recentPackets.clear();
        }
        Deque<Long> times = recentPackets.computeIfAbsent(ip, k -> new ArrayDeque<>());
        while (!times.isEmpty() && now - times.peekFirst() > RATE_WINDOW_MS) times.pollFirst();
        if (times.size() >= RATE_MAX_PACKETS) return true;
        times.addLast(now);
        return false;
    }

    private static boolean isValid(DiscoveryMessage m) {
        if (m == null || !("DISCOVER".equals(m.type) || "GOODBYE".equals(m.type))) return false;
        if (!isSaneString(m.id) || !isSaneString(m.userName) || !isSaneString(m.hostName)) return false;
        return m.tcpPort >= 1024 && m.tcpPort <= 65535;
    }

    private static boolean isSaneString(String s) {
        return s != null && !s.isEmpty() && s.length() <= 256;
    }
}
