package nettransfer;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class ConnectionManager {
    private final ServerSocket serverSocket;
    private final TransferListener listener;

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
                Thread handler = new Thread(() -> FileTransferService.handleIncoming(socket, listener));
                handler.setDaemon(true);
                handler.start();
            } catch (IOException e) {
                if (!serverSocket.isClosed()) {
                    e.printStackTrace();
                }
            }
        }
    }
}
