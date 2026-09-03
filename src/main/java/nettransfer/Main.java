package nettransfer;

import javax.swing.SwingUtilities;

public class Main {
    public static void main(String[] args) throws Exception {
        DiscoveryService discoveryService = new DiscoveryService();
        PeerListFrame peerListFrame = new PeerListFrame();

        SwingUtilities.invokeLater(() -> peerListFrame.setVisible(true));

        Thread receiverThread = new Thread(() -> {
            try {
                discoveryService.broadcastReceiver(DiscoveryService.DISCOVERY_PORT, peerListFrame::addOrUpdatePeer);
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