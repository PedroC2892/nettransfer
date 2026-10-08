package nettransfer;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/** Loopback helpers shared by the protocol tests. */
final class TestSupport {
    private TestSupport() {}

    /** Records the callbacks of one transfer and lets tests await its final status. */
    static class RecordingListener implements TransferListener {
        final boolean accept;
        final CompletableFuture<TransferStatus> finalStatus = new CompletableFuture<>();
        volatile String receiveDir;
        volatile boolean asked;

        RecordingListener(boolean accept) { this.accept = accept; }

        @Override
        public boolean onIncomingRequest(String transferId, String senderName, String senderIp,
                                         List<TransferMessage.FileEntry> files, long totalSize, int totalFiles,
                                         long usableSpace, boolean enoughSpace, String verificationCode) {
            asked = true;
            return accept;
        }

        @Override public void onProgress(String transferId, long transferred, long total, double speed) {}

        @Override
        public void onStatusChange(String transferId, TransferStatus status) {
            if (status != TransferStatus.TRANSFERRING) finalStatus.complete(status);
        }

        @Override public void onReceiveDir(String transferId, String dirPath) { receiveDir = dirPath; }

        TransferStatus await() throws Exception {
            return finalStatus.get(20, TimeUnit.SECONDS);
        }
    }

    /** Accepts one connection on loopback and runs the receiver on it in the background. */
    static ServerSocket startReceiver(TransferListener listener) throws IOException {
        ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
        Thread t = new Thread(() -> {
            try (server) {
                Socket s = server.accept();
                s.setSoTimeout(10_000);
                FileTransferService.handleIncoming(s, listener);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
        t.setDaemon(true);
        t.start();
        return server;
    }

    static Peer loopbackPeer(ServerSocket server) {
        return new Peer("test-peer", "tester", "localhost", "127.0.0.1", server.getLocalPort());
    }

    /** Speaks the protocol by hand so tests can send what an honest sender never would. */
    static final class RogueSender implements AutoCloseable {
        private final Socket socket;
        private final java.io.DataOutputStream out;
        private final java.io.DataInputStream in;
        private final com.google.gson.Gson gson = new com.google.gson.Gson();

        RogueSender(ServerSocket server) throws Exception {
            socket = new Socket(InetAddress.getLoopbackAddress(), server.getLocalPort());
            socket.setSoTimeout(10_000);
            Handshake hs = Handshake.forSender(socket.getInputStream(), socket.getOutputStream());
            out = new java.io.DataOutputStream(new java.io.BufferedOutputStream(
                    new EncryptedOutputStream(socket.getOutputStream(), hs)));
            in = new java.io.DataInputStream(new EncryptedInputStream(socket.getInputStream(), hs));
        }

        void send(TransferMessage m) throws IOException { m.writeTo(out, gson); }

        void sendRaw(byte[] data) throws IOException { out.write(data); out.flush(); }

        TransferMessage read() throws IOException { return TransferMessage.readFrom(in, gson); }

        @Override public void close() throws IOException { socket.close(); }
    }

    static TransferMessage request(List<TransferMessage.FileEntry> files) {
        long total = files.stream().filter(f -> !f.isDirectory).mapToLong(f -> f.size).sum();
        int count = (int) files.stream().filter(f -> !f.isDirectory).count();
        return TransferMessage.transferRequest(java.util.UUID.randomUUID().toString(), "rogue", files, total, count);
    }

    static TransferMessage.FileEntry file(String path, long size) {
        return new TransferMessage.FileEntry(path, path, size, false);
    }
}
