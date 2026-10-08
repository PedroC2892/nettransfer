package nettransfer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileTransferServiceTest {

    @TempDir
    Path tmp;

    /** The receiver used to need the JavaFX toolkit; it must now run headless. */
    @Test
    void transfersFileOverLoopbackWithoutJavaFx() throws Exception {
        byte[] content = new byte[300_000];
        new Random(1).nextBytes(content);
        Path src = Files.write(tmp.resolve("data.bin"), content);

        TestSupport.RecordingListener receiver = new TestSupport.RecordingListener(true);
        ServerSocket server = TestSupport.startReceiver(receiver);
        TestSupport.RecordingListener sender = new TestSupport.RecordingListener(true);
        FileTransferService.sendFiles(TestSupport.loopbackPeer(server), List.of(src.toFile()),
                UUID.randomUUID().toString(), "tester", sender);

        assertEquals(TransferStatus.DONE, sender.await());
        assertEquals(TransferStatus.DONE, receiver.await());
        assertArrayEquals(content, Files.readAllBytes(Path.of(receiver.receiveDir, "data.bin")));
    }

    @Test
    void rejectedRequestIsReportedToSender() throws Exception {
        Path src = Files.writeString(tmp.resolve("a.txt"), "hello");
        TestSupport.RecordingListener receiver = new TestSupport.RecordingListener(false);
        ServerSocket server = TestSupport.startReceiver(receiver);
        TestSupport.RecordingListener sender = new TestSupport.RecordingListener(true);
        FileTransferService.sendFiles(TestSupport.loopbackPeer(server), List.of(new File(src.toString())),
                UUID.randomUUID().toString(), "tester", sender);

        assertEquals(TransferStatus.REJECTED, sender.await());
        assertEquals(TransferStatus.REJECTED, receiver.await());
        assertTrue(receiver.asked);
    }
}
