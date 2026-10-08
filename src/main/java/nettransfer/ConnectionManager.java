package nettransfer;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ConnectionManager {
    private static final Logger LOG = Logger.getLogger(ConnectionManager.class.getName());
    private static final int MAX_CONNECTIONS = 8;

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
                    socket.close();
                    continue;
                }
                try {
                    socket.setSoTimeout(FileTransferService.SOCKET_TIMEOUT_MS);
                } catch (IOException e) {
                    connectionSlots.release();
                    socket.close();
                    throw e;
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
                    LOG.log(Level.WARNING, "Failed to accept connection", e);
                }
            }
        }
    }
}
