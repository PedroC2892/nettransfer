package nettransfer;

public class Peer {
    public String id;
    public String name;
    public String hostName;
    public String ipAddress;
    public int tcpPort;

    public Peer(String id, String name, String hostName, String ipAddress, int tcpPort) {
        this.id = id;
        this.name = name;
        this.hostName = hostName;
        this.ipAddress = ipAddress;
        this.tcpPort = tcpPort;
    }
}
