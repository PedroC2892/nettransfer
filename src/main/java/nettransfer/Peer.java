package nettransfer;

public class Peer {
    public String id;
    public String name;
    public int tcpPort;

    public Peer(String id, String name, int tcpPort) {
        this.id = id;
        this.name = name;
        this.tcpPort = tcpPort;
    }
}
