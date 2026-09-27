package nettransfer;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

public class App extends Application {

    public static final String VERSION = "1.1";

    @Override
    public void start(Stage stage) throws Exception {
        MainController controller = new MainController(stage);

        ConnectionManager connectionManager = new ConnectionManager(controller);
        connectionManager.start();

        DiscoveryService discoveryService = new DiscoveryService(connectionManager.getPort());

        Thread receiverThread = new Thread(() -> {
            try {
                discoveryService.broadcastReceiver(DiscoveryService.DISCOVERY_PORT, controller::onPeerDiscovered, controller::onPeerGone);
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

        Runtime.getRuntime().addShutdownHook(new Thread(() ->
                discoveryService.announceGoodbye(DiscoveryService.DISCOVERY_PORT)));

        controller.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
