package nettransfer;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
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
    
}