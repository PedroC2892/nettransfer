package nettransfer;

public class Main {
    public static void main(String[] args) throws Exception {
        DiscoveryService discoveryService = new DiscoveryService();

        Thread receiverThread = new Thread(() -> {
            try {
                discoveryService.broadcastReceiver(DiscoveryService.DISCOVERY_PORT);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        receiverThread.setDaemon(true);
        receiverThread.start();

        discoveryService.broadcastDiscovery(DiscoveryService.DISCOVERY_PORT);
    }
}