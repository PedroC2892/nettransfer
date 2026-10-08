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

    @Test
    void createUniqueNeverOverwrites() throws Exception {
        Path existing = Files.writeString(tmp.resolve("report.pdf"), "keep me");
        Path created = FileTransferService.createUnique(existing);
        assertEquals("report_2.pdf", created.getFileName().toString());
        assertEquals("keep me", Files.readString(existing));
        assertEquals("report_3.pdf", FileTransferService.createUnique(existing).getFileName().toString());
    }

    /** Runs a send where the source file is modified while the receiver's user is deciding. */
    private TransferStatus[] sendWhileModifying(Path src, Runnable modify) throws Exception {
        TestSupport.RecordingListener receiver = new TestSupport.RecordingListener(true) {
            @Override
            public boolean onIncomingRequest(String transferId, String senderName, String senderIp,
                                             List<TransferMessage.FileEntry> files, long totalSize, int totalFiles,
                                             long usableSpace, boolean enoughSpace, String verificationCode) {
                modify.run();
                return true;
            }
        };
        ServerSocket server = TestSupport.startReceiver(receiver);
        TestSupport.RecordingListener sender = new TestSupport.RecordingListener(true);
        FileTransferService.sendFiles(TestSupport.loopbackPeer(server), List.of(src.toFile()),
                UUID.randomUUID().toString(), "tester", sender);
        return new TransferStatus[]{sender.await(), receiver.await()};
    }

    @Test
    void fileGrowingDuringTransferSendsAnnouncedSize() throws Exception {
        Path src = Files.writeString(tmp.resolve("log.txt"), "12345");
        TransferStatus[] result = sendWhileModifying(src, () -> {
            try { Files.writeString(src, "67890", java.nio.file.StandardOpenOption.APPEND); }
            catch (java.io.IOException e) { throw new RuntimeException(e); }
        });
        assertEquals(TransferStatus.DONE, result[0]);
        assertEquals(TransferStatus.DONE, result[1]);
    }

    @Test
    void fileShrinkingDuringTransferFailsCleanly() throws Exception {
        Path src = Files.writeString(tmp.resolve("log.txt"), "1234567890");
        TransferStatus[] result = sendWhileModifying(src, () -> {
            try { Files.writeString(src, "1"); }
            catch (java.io.IOException e) { throw new RuntimeException(e); }
        });
        assertEquals(TransferStatus.ERROR, result[0]);
        assertEquals(TransferStatus.ERROR, result[1]);
    }

    /** A receiver answering with anything but TRANSFER_RESPONSE must not be read as a rejection or acceptance. */
    @Test
    void senderRejectsUnexpectedResponse() throws Exception {
        Path src = Files.writeString(tmp.resolve("a.txt"), "hello");
        try (ServerSocket server = new ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress())) {
            TestSupport.RecordingListener sender = new TestSupport.RecordingListener(true);
            FileTransferService.sendFiles(TestSupport.loopbackPeer(server), List.of(src.toFile()),
                    UUID.randomUUID().toString(), "tester", sender);
            try (java.net.Socket s = server.accept()) {
                Handshake hs = Handshake.forReceiver(s.getInputStream(), s.getOutputStream());
                com.google.gson.Gson gson = new com.google.gson.Gson();
                java.io.DataInputStream in = new java.io.DataInputStream(new EncryptedInputStream(s.getInputStream(), hs));
                java.io.DataOutputStream out = new java.io.DataOutputStream(new EncryptedOutputStream(s.getOutputStream(), hs));
                TransferMessage.readFrom(in, gson);
                TransferMessage bogus = TransferMessage.transferResponse("x", true);
                bogus.type = "FILE_END";
                bogus.writeTo(out, gson);
                assertEquals(TransferStatus.ERROR, sender.await());
            }
        }
    }
}
