package nettransfer;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.util.logging.Level;
import java.util.logging.Logger;

public class App extends Application {
    private static final Logger LOG = Logger.getLogger(App.class.getName());

    public static final String VERSION = "1.2.0";

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
                // usually the UDP port is taken by another running instance
                LOG.log(Level.SEVERE, "Discovery receiver stopped; this device will not see peers", e);
            }
        });
        receiverThread.setDaemon(true);
        receiverThread.start();

        Thread broadcastThread = new Thread(() -> {
            try {
                discoveryService.broadcastDiscovery(DiscoveryService.DISCOVERY_PORT, DiscoveryService.BROADCAST_INTERVAL_MS);
            } catch (Exception e) {
                LOG.log(Level.SEVERE, "Discovery broadcaster stopped; peers will not see this device", e);
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
