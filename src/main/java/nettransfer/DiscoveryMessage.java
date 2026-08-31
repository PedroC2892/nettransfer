package nettransfer;

public class DiscoveryMessage {
    // Classe que da o formato a mensagem 
    // de discoberta
    public String type;      // "DISCOVER" ou "RESPONSE"
    public String id;
    public String userName;
    public String hostName;
    public int tcpPort;

    public DiscoveryMessage(String type, String id, String userName, String hostName, int tcpPort) {
        this.type = type;
        this.id = id;
        this.userName = userName;
        this.hostName = hostName;
        this.tcpPort = tcpPort;
    }
}
