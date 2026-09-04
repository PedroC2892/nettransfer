package nettransfer;

import com.google.gson.Gson;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.function.Consumer;

public class DiscoveryService {
    public static final int DISCOVERY_PORT = 54321;
    public static final long BROADCAST_INTERVAL_MS = 5000;

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
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setBroadcast(true);
            InetAddress broadcastAddr = InetAddress.getByName("255.255.255.255");
            DatagramPacket packet = new DatagramPacket(data, data.length, broadcastAddr, port);

            while (true) {
                socket.send(packet);
                try {
                    Thread.sleep(intervalMillis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    public void broadcastReceiver(int port, Consumer<Peer> onPeerDiscovered) throws IOException {
        try (DatagramSocket socket = new DatagramSocket(port)) {
            byte[] buffer = new byte[1024];
            while (true) {
                DatagramPacket receivedPacket = new DatagramPacket(buffer, buffer.length);
                socket.receive(receivedPacket);

                String json = new String(receivedPacket.getData(), 0, receivedPacket.getLength(), StandardCharsets.UTF_8);
                DiscoveryMessage received = gson.fromJson(json, DiscoveryMessage.class);

                if (received.id.equals(myId)) {
                    continue;
                }

                Peer peer = new Peer(received.id, received.userName, received.hostName,
                        receivedPacket.getAddress().getHostAddress(), received.tcpPort);
                onPeerDiscovered.accept(peer);
            }
        }
    }
}
