package nettransfer;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ConnectionManager {
    private static final Logger LOG = Logger.getLogger(ConnectionManager.class.getName());
    private static final int MAX_CONNECTIONS = 8;
    private static final int MAX_PER_HOST = 2;

    private final ServerSocket serverSocket;
    private final TransferListener listener;
    private final Semaphore connectionSlots = new Semaphore(MAX_CONNECTIONS);
    private final ExecutorService pool = Executors.newFixedThreadPool(MAX_CONNECTIONS);
    private final Map<InetAddress, Integer> perHost = new ConcurrentHashMap<>();

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

    public void stop() {
        try {
            serverSocket.close();
        } catch (IOException e) {
            LOG.log(Level.FINE, "Close failed", e);
        }
        pool.shutdownNow();
    }

    private void acceptLoop() {
        while (!serverSocket.isClosed()) {
            try {
                Socket socket = serverSocket.accept();
                InetAddress ip = socket.getInetAddress();
                // one host must not be able to take every slot and lock everyone else out
                if (perHost.merge(ip, 1, Integer::sum) > MAX_PER_HOST || !connectionSlots.tryAcquire()) {
                    perHost.computeIfPresent(ip, (k, n) -> n > 1 ? n - 1 : null);
                    TransferLogger.logConnectionRejected(ip.getHostAddress());
                    socket.close();
                    continue;
                }
                Runnable release = () -> {
                    connectionSlots.release();
                    perHost.computeIfPresent(ip, (k, n) -> n > 1 ? n - 1 : null);
                };
                try {
                    socket.setSoTimeout(FileTransferService.SOCKET_TIMEOUT_MS);
                } catch (IOException e) {
                    release.run();
                    socket.close();
                    throw e;
                }
                pool.submit(() -> {
                    try {
                        FileTransferService.handleIncoming(socket, listener);
                    } finally {
                        release.run();
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
