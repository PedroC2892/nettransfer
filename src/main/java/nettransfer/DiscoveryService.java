package nettransfer;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

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
    public static final int DISCOVERY_PORT = 54321;
    public static final long BROADCAST_INTERVAL_MS = 5000;

    private static final long RATE_WINDOW_MS = 10_000;
    private static final int RATE_MAX_PACKETS = 10;
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_BITS = 128;

    private static final SecretKeySpec DISCOVERY_KEY = deriveDiscoveryKey();

    private final Gson gson = new Gson();
    private final String myId = AppSettings.load().getOrCreateDeviceId();
    private final SecureRandom random = new SecureRandom();
    private final byte[] plaintext;
    private final byte[] goodbyePlaintext;

    public DiscoveryService(int tcpPort) {
        DiscoveryMessage msg = new DiscoveryMessage("DISCOVER", myId, getUserName(), getHostname(), tcpPort);
        plaintext = gson.toJson(msg).getBytes(StandardCharsets.UTF_8);
        DiscoveryMessage bye = new DiscoveryMessage("GOODBYE", myId, getUserName(), getHostname(), tcpPort);
        goodbyePlaintext = gson.toJson(bye).getBytes(StandardCharsets.UTF_8);
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

    private byte[] encryptPacket(byte[] plain) throws GeneralSecurityException {
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
            return "desconhecido";
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
            } catch (Exception ignored) {
                // best-effort: the stale timeout is the fallback if this doesn't get through
            }
        }
    }

    public void broadcastReceiver(int port, Consumer<Peer> onPeerDiscovered, Consumer<String> onPeerGone) throws IOException {
        Map<String, Deque<Long>> recentPackets = new HashMap<>();
        try (DatagramSocket socket = new DatagramSocket(port)) {
            byte[] buffer = new byte[1024];
            while (true) {
                DatagramPacket receivedPacket = new DatagramPacket(buffer, buffer.length);
                socket.receive(receivedPacket);

                String ip = receivedPacket.getAddress().getHostAddress();
                if (isRateLimited(recentPackets, ip)) continue;

                byte[] plain;
                try {
                    plain = decryptPacket(receivedPacket.getData(), receivedPacket.getLength());
                } catch (GeneralSecurityException e) {
                    continue;
                }

                String json = new String(plain, StandardCharsets.UTF_8);
                DiscoveryMessage received;
                try {
                    received = gson.fromJson(json, DiscoveryMessage.class);
                } catch (JsonSyntaxException e) {
                    continue;
                }
                if (!isValid(received) || received.id.equals(myId)) {
                    continue;
                }

                if ("GOODBYE".equals(received.type)) {
                    onPeerGone.accept(received.id);
                    continue;
                }

                Peer peer = new Peer(received.id, received.userName, received.hostName, ip, received.tcpPort);
                onPeerDiscovered.accept(peer);
            }
        }
    }

    private static boolean isRateLimited(Map<String, Deque<Long>> recentPackets, String ip) {
        long now = System.currentTimeMillis();
        Deque<Long> times = recentPackets.computeIfAbsent(ip, k -> new ArrayDeque<>());
        while (!times.isEmpty() && now - times.peekFirst() > RATE_WINDOW_MS) times.pollFirst();
        if (times.size() >= RATE_MAX_PACKETS) return true;
        times.addLast(now);
        return false;
    }

    private static boolean isValid(DiscoveryMessage m) {
        if (m == null) return false;
        if (!isSaneString(m.id) || !isSaneString(m.userName) || !isSaneString(m.hostName)) return false;
        return m.tcpPort >= 1024 && m.tcpPort <= 65535;
    }

    private static boolean isSaneString(String s) {
        return s != null && !s.isEmpty() && s.length() <= 256;
    }
}
