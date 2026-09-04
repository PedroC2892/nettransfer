package nettransfer;

import javax.swing.SwingUtilities;

public class Main {
    public static void main(String[] args) throws Exception {
        MainFrame mainFrame = new MainFrame();

        ConnectionManager connectionManager = new ConnectionManager(mainFrame);
        connectionManager.start();

        DiscoveryService discoveryService = new DiscoveryService(connectionManager.getPort());

        SwingUtilities.invokeLater(() -> mainFrame.setVisible(true));

        Thread receiverThread = new Thread(() -> {
            try {
                discoveryService.broadcastReceiver(DiscoveryService.DISCOVERY_PORT, mainFrame::onPeerDiscovered);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        receiverThread.setDaemon(true);
        receiverThread.start();

        Thread broadcastThread = new Thread(() -> {
            try {
                discoveryService.broadcastDiscovery(DiscoveryService.DISCOVERY_PORT, DiscoveryService.BROADCAST_INTERVAL_MS);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        broadcastThread.setDaemon(true);
        broadcastThread.start();
    }
}
