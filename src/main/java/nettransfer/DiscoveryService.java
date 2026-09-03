package nettransfer;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import com.google.gson.Gson;

public class DiscoveryService {
    private final Gson gson = new Gson();
    
    // Hostname extraction
    private static String getHostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            return "desconhecido";
        }
    }
    
    // Username extraction
    public static String getUserName() {
        try {
            String user = System.getProperty("user.name");
            return user != null ? user : "unknown";
        } catch (SecurityException e) {
            return "unknown";
        }
    }
    
    // txt -> JSON conversion
    String hostname = getHostname();
    String username = getUserName();
    String myId = UUID.randomUUID().toString();
    // Mesage creation 
    DiscoveryMessage msg = new DiscoveryMessage("DISCOVER", myId, username, hostname, 0);
    byte[] data = gson.toJson(msg).getBytes(StandardCharsets.UTF_8);


    // Broadcast sending
    // port = 54321
    public void broadcastDiscovery(int port) throws IOException {        
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setBroadcast(true);
            InetAddress broadcastAddr = InetAddress.getByName("255.255.255.255");
            DatagramPacket packet = new DatagramPacket(data, data.length, broadcastAddr, port);
            socket.send(packet);
        }
    }

    public void broadcastReceiver(int port) throws IOException {
        try (DatagramSocket socket = new DatagramSocket(port)) {
            byte[] buffer = new byte[1024];
            while (true) {
                DatagramPacket receivedPacket = new DatagramPacket(buffer, buffer.length);
                socket.receive(receivedPacket);

                String json = new String(receivedPacket.getData(), 0, receivedPacket.getLength(), StandardCharsets.UTF_8);
                DiscoveryMessage received = gson.fromJson(json, DiscoveryMessage.class);

                System.out.println("Recebido de " + receivedPacket.getAddress() + ": " + received.userName);
                
            }
        }
    }


}