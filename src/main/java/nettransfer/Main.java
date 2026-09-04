package nettransfer;

import javax.swing.SwingUtilities;
import java.util.List;

public class Main {
    public static void main(String[] args) throws Exception {
        PeerListFrame peerListFrame = new PeerListFrame();

        TransferListener listener = new TransferListener() {
            @Override
            public boolean onIncomingRequest(String transferId, String senderName, List<TransferMessage.FileEntry> files, long totalSize, int totalFiles) {
                System.out.println("Pedido de transferencia de " + senderName + " (" + totalFiles + " ficheiros, " + totalSize + " bytes)");
                return true;
            }

            @Override
            public void onProgress(String transferId, long transferred, long total, double speedBps) {
            }

            @Override
            public void onStatusChange(String transferId, TransferStatus status) {
                System.out.println("Transferencia " + transferId + ": " + status);
            }
        };

        ConnectionManager connectionManager = new ConnectionManager(listener);
        connectionManager.start();

        DiscoveryService discoveryService = new DiscoveryService(connectionManager.getPort());

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
