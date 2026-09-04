package nettransfer;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

public class DiscoveryService {
    public static final int DISCOVERY_PORT = 54321;
    public static final long BROADCAST_INTERVAL_MS = 5000;

    private static final long RATE_WINDOW_MS = 10_000;
    private static final int RATE_MAX_PACKETS = 10;

    private final Gson gson = new Gson();
    private final String myId = UUID.randomUUID().toString();
    private final byte[] data;

    public DiscoveryService(int tcpPort) {
        DiscoveryMessage msg = new DiscoveryMessage("DISCOVER", myId, getUserName(), getHostname(), tcpPort);
        data = gson.toJson(msg).getBytes(StandardCharsets.UTF_8);
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
                    socket.send(new DatagramPacket(data, data.length, bcast, port));
                } catch (IOException e) {
                    // interface may have changed state between enumeration and send — skip it this cycle
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

    public void broadcastReceiver(int port, Consumer<Peer> onPeerDiscovered) throws IOException {
        Map<String, Deque<Long>> recentPackets = new HashMap<>();
        try (DatagramSocket socket = new DatagramSocket(port)) {
            byte[] buffer = new byte[1024];
            while (true) {
                DatagramPacket receivedPacket = new DatagramPacket(buffer, buffer.length);
                socket.receive(receivedPacket);

                String ip = receivedPacket.getAddress().getHostAddress();
                if (isRateLimited(recentPackets, ip)) continue;

                String json = new String(receivedPacket.getData(), 0, receivedPacket.getLength(), StandardCharsets.UTF_8);
                DiscoveryMessage received;
                try {
                    received = gson.fromJson(json, DiscoveryMessage.class);
                } catch (JsonSyntaxException e) {
                    continue;
                }
                if (!isValid(received) || received.id.equals(myId)) {
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
