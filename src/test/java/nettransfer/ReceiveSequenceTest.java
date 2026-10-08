package nettransfer;

import org.junit.jupiter.api.Test;

import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static nettransfer.TestSupport.file;
import static nettransfer.TestSupport.request;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReceiveSequenceTest {

    private TestSupport.RecordingListener receiver;

    private TestSupport.RogueSender acceptedRogue(TransferMessage req) throws Exception {
        receiver = new TestSupport.RecordingListener(true);
        ServerSocket server = TestSupport.startReceiver(receiver);
        TestSupport.RogueSender rogue = new TestSupport.RogueSender(server);
        rogue.send(req);
        assertTrue(rogue.read().accepted);
        return rogue;
    }

    @Test
    void missingHashFailsAndRemovesFile() throws Exception {
        TransferMessage req = request(List.of(file("a.txt", 5)));
        try (TestSupport.RogueSender rogue = acceptedRogue(req)) {
            rogue.send(TransferMessage.fileStart(req.transferId, "a.txt", 5, false));
            rogue.sendRaw("hello".getBytes(StandardCharsets.UTF_8));
            rogue.send(TransferMessage.fileEnd(req.transferId, "a.txt", null));
            rogue.send(TransferMessage.transferComplete(req.transferId));
            assertEquals(TransferStatus.ERROR, receiver.await());
        }
        assertFalse(Files.exists(Path.of(receiver.receiveDir, "a.txt")));
    }

    @Test
    void wrongHashFails() throws Exception {
        TransferMessage req = request(List.of(file("a.txt", 5)));
        try (TestSupport.RogueSender rogue = acceptedRogue(req)) {
            rogue.send(TransferMessage.fileStart(req.transferId, "a.txt", 5, false));
            rogue.sendRaw("hello".getBytes(StandardCharsets.UTF_8));
            rogue.send(TransferMessage.fileEnd(req.transferId, "a.txt", "00".repeat(32)));
            assertEquals(TransferStatus.ERROR, receiver.await());
        }
    }

    @Test
    void earlyCompleteFails() throws Exception {
        TransferMessage req = request(List.of(file("a.txt", 5), file("b.txt", 5)));
        try (TestSupport.RogueSender rogue = acceptedRogue(req)) {
            rogue.send(TransferMessage.transferComplete(req.transferId));
            assertEquals(TransferStatus.ERROR, receiver.await());
        }
    }

    @Test
    void unknownMessageFails() throws Exception {
        TransferMessage req = request(List.of(file("a.txt", 5)));
        try (TestSupport.RogueSender rogue = acceptedRogue(req)) {
            TransferMessage odd = TransferMessage.transferComplete(req.transferId);
            odd.type = "SURPRISE";
            rogue.send(odd);
            rogue.send(TransferMessage.transferComplete(req.transferId));
            assertEquals(TransferStatus.ERROR, receiver.await());
        }
    }
}
