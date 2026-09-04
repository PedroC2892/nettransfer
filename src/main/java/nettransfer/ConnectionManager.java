package nettransfer;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

public class ConnectionManager {
    private static final int MAX_CONNECTIONS = 8;
    private static final int SOCKET_READ_TIMEOUT_MS = 30_000;

    private final ServerSocket serverSocket;
    private final TransferListener listener;
    private final Semaphore connectionSlots = new Semaphore(MAX_CONNECTIONS);
    private final ExecutorService pool = Executors.newFixedThreadPool(MAX_CONNECTIONS);

    public ConnectionManager(TransferListener listener) throws IOException {
        this.listener = listener;
        this.serverSocket = new ServerSocket(0);
    }

    public int getPort() {
        return serverSocket.getLocalPort();
    }

    public void start() {
        Thread acceptThread = new Thread(this::acceptLoop);
        acceptThread.setDaemon(true);
        acceptThread.start();
    }

    private void acceptLoop() {
        while (!serverSocket.isClosed()) {
            try {
                Socket socket = serverSocket.accept();
                if (!connectionSlots.tryAcquire()) {
                    TransferLogger.logConnectionRejected(socket.getInetAddress().getHostAddress());
                    try { socket.close(); } catch (IOException ignored) {}
                    continue;
                }
                try {
                    socket.setSoTimeout(SOCKET_READ_TIMEOUT_MS);
                } catch (IOException e) {
                    connectionSlots.release();
                    try { socket.close(); } catch (IOException ignored) {}
                    continue;
                }
                pool.submit(() -> {
                    try {
                        FileTransferService.handleIncoming(socket, listener);
                    } finally {
                        connectionSlots.release();
                    }
                });
            } catch (IOException e) {
                if (!serverSocket.isClosed()) {
                    e.printStackTrace();
                }
            }
        }
    }
}
