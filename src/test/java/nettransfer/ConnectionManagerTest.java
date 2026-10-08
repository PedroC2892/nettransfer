package nettransfer;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.Socket;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConnectionManagerTest {

    @Test
    void oneHostCannotTakeAllSlots() throws Exception {
        ConnectionManager cm = new ConnectionManager(new TestSupport.RecordingListener(false));
        cm.start();
        InetAddress lo = InetAddress.getLoopbackAddress();
        try (Socket a = new Socket(lo, cm.getPort()); Socket b = new Socket(lo, cm.getPort());
             Socket c = new Socket(lo, cm.getPort())) {
            c.setSoTimeout(5_000);
            assertEquals(-1, c.getInputStream().read(), "third idle connection from the same host must be closed");
        } finally {
            cm.stop();
        }
    }
}
