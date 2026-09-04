package nettransfer;

import com.google.gson.Gson;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javafx.application.Platform;

public class FileTransferService {
    private static final long PROGRESS_INTERVAL_MS = 150;

    static final Path DOWNLOAD_BASE = Paths.get(System.getProperty("user.home"), "Downloads", "NetTransfer");
    private static final DateTimeFormatter FOLDER_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    public static String getDownloadBaseDir() {
        return DOWNLOAD_BASE.toString();
    }

    public static void sendFiles(Peer peer, List<File> selectedFiles, String transferId, String senderName, TransferListener listener) {
        Thread t = new Thread(() -> doSend(peer, selectedFiles, transferId, senderName, listener));
        t.setDaemon(true);
        t.start();
    }

    private static void doSend(Peer peer, List<File> selectedFiles, String transferId, String senderName, TransferListener listener) {
        Gson gson = new Gson();
        try {
            List<PreparedFile> entries = expand(selectedFiles);
            long totalSize = entries.stream().filter(e -> !e.isDirectory).mapToLong(e -> e.size).sum();
            int totalFiles = (int) entries.stream().filter(e -> !e.isDirectory).count();
            List<TransferMessage.FileEntry> fileEntries = entries.stream()
                    .map(e -> new TransferMessage.FileEntry(baseName(e.relativePath), e.relativePath, e.size, e.isDirectory))
                    .collect(Collectors.toList());

            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(peer.ipAddress, peer.tcpPort), 5000);

                // ECDH handshake — sender goes first
                Handshake hs = Handshake.forSender(socket.getInputStream(), socket.getOutputStream());

                // All further I/O is encrypted
                OutputStream encOut = new EncryptedOutputStream(socket.getOutputStream(), hs);
                InputStream encIn = new EncryptedInputStream(socket.getInputStream(), hs);
                DataOutputStream out = new DataOutputStream(new BufferedOutputStream(encOut));
                DataInputStream in = new DataInputStream(new BufferedInputStream(encIn));

                TransferMessage.transferRequest(transferId, senderName, fileEntries, totalSize, totalFiles).writeTo(out, gson);

                TransferMessage response = TransferMessage.readFrom(in, gson);
                if (!response.accepted) {
                    listener.onStatusChange(transferId, TransferStatus.REJECTED);
                    return;
                }

                listener.onStatusChange(transferId, TransferStatus.TRANSFERRING);
                long transferred = 0;
                long startTime = System.currentTimeMillis();
                long lastCallback = 0;
                byte[] buffer = new byte[64 * 1024];

                for (PreparedFile entry : entries) {
                    if (entry.isDirectory) {
                        TransferMessage.fileStart(transferId, entry.relativePath, 0, true).writeTo(out, gson);
                        continue;
                    }
                    TransferMessage.fileStart(transferId, entry.relativePath, entry.size, false).writeTo(out, gson);
                    try (FileInputStream fis = new FileInputStream(entry.file)) {
                        int read;
                        while ((read = fis.read(buffer)) != -1) {
                            out.write(buffer, 0, read);
                            transferred += read;
                            long now = System.currentTimeMillis();
                            if (now - lastCallback > PROGRESS_INTERVAL_MS || transferred == totalSize) {
                                double speed = transferred / Math.max(0.001, (now - startTime) / 1000.0);
                                listener.onProgress(transferId, transferred, totalSize, speed);
                                lastCallback = now;
                            }
                        }
                    }
                    out.flush();
                    TransferMessage.fileEnd(transferId, entry.relativePath).writeTo(out, gson);
                }

                TransferMessage.transferComplete(transferId).writeTo(out, gson);
                listener.onStatusChange(transferId, TransferStatus.DONE);
            }
        } catch (Exception e) {
            listener.onStatusChange(transferId, TransferStatus.ERROR);
        }
    }

    public static void handleIncoming(Socket socket, TransferListener listener) {
        Gson gson = new Gson();
        TransferMessage request = null;
        try {
            // ECDH handshake — receiver goes second
            Handshake hs = Handshake.forReceiver(socket.getInputStream(), socket.getOutputStream());

            InputStream encIn = new EncryptedInputStream(socket.getInputStream(), hs);
            OutputStream encOut = new EncryptedOutputStream(socket.getOutputStream(), hs);
            DataInputStream in = new DataInputStream(new BufferedInputStream(encIn));
            DataOutputStream out = new DataOutputStream(new BufferedOutputStream(encOut));

            request = TransferMessage.readFrom(in, gson);
            final TransferMessage finalRequest = request;
            boolean accepted = waitForUiResponse(listener, finalRequest);

            TransferMessage.transferResponse(request.transferId, accepted).writeTo(out, gson);
            if (!accepted) {
                listener.onStatusChange(request.transferId, TransferStatus.REJECTED);
                return;
            }

            listener.onStatusChange(request.transferId, TransferStatus.TRANSFERRING);

            String timestamp = LocalDateTime.now().format(FOLDER_FMT);
            Path destBase = DOWNLOAD_BASE.resolve(timestamp).toAbsolutePath().normalize();
            Files.createDirectories(destBase);
            listener.onReceiveDir(request.transferId, destBase.toString());

            long transferred = 0;
            long startTime = System.currentTimeMillis();
            long lastCallback = 0;
            byte[] buffer = new byte[64 * 1024];

            while (true) {
                TransferMessage msg = TransferMessage.readFrom(in, gson);
                if ("TRANSFER_COMPLETE".equals(msg.type)) {
                    listener.onStatusChange(request.transferId, TransferStatus.DONE);
                    break;
                }
                if (!"FILE_START".equals(msg.type)) continue;

                Path target = resolveSafePath(destBase, msg.relativePath);
                if (msg.isDirectory) {
                    Files.createDirectories(target);
                    continue;
                }
                Files.createDirectories(target.getParent());
                target = uniquePath(target);

                try (FileOutputStream fos = new FileOutputStream(target.toFile())) {
                    long remaining = msg.size;
                    while (remaining > 0) {
                        int toRead = (int) Math.min(buffer.length, remaining);
                        int read = in.read(buffer, 0, toRead);
                        if (read == -1) throw new EOFException("Ligacao fechada durante a transferencia");
                        fos.write(buffer, 0, read);
                        remaining -= read;
                        transferred += read;
                        long now = System.currentTimeMillis();
                        if (now - lastCallback > PROGRESS_INTERVAL_MS || remaining == 0) {
                            double speed = transferred / Math.max(0.001, (now - startTime) / 1000.0);
                            listener.onProgress(request.transferId, transferred, request.totalSize, speed);
                            lastCallback = now;
                        }
                    }
                }
                TransferMessage.readFrom(in, gson); // FILE_END
            }
        } catch (Exception e) {
            String id = request != null ? request.transferId : "desconhecido";
            listener.onStatusChange(id, TransferStatus.ERROR);
        } finally {
            try { socket.close(); } catch (IOException ignored) {}
        }
    }

    private static boolean waitForUiResponse(TransferListener listener, TransferMessage request) {
        // The listener.onIncomingRequest blocks via a modal JavaFX dialog on the FX thread,
        // but we're on a background thread. We use a CountDownLatch pattern via the holder array.
        boolean[] result = {false};
        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        Platform.runLater(() -> {
            result[0] = listener.onIncomingRequest(
                    request.transferId, request.senderName, request.files,
                    request.totalSize, request.totalFiles);
            latch.countDown();
        });
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return result[0];
    }

    private static Path resolveSafePath(Path base, String relativePath) throws IOException {
        Path resolved = base.resolve(relativePath).normalize();
        if (!resolved.startsWith(base)) throw new IOException("Path traversal detetado: " + relativePath);
        return resolved;
    }

    private static Path uniquePath(Path target) {
        if (!Files.exists(target)) return target;
        String name = target.getFileName().toString();
        String base = name, ext = "";
        int dot = name.lastIndexOf('.');
        if (dot > 0) { base = name.substring(0, dot); ext = name.substring(dot); }
        Path parent = target.getParent();
        int i = 2;
        Path candidate;
        do { candidate = parent.resolve(base + "_" + i + ext); i++; }
        while (Files.exists(candidate));
        return candidate;
    }

    private static String baseName(String relativePath) {
        int idx = relativePath.lastIndexOf('/');
        return idx >= 0 ? relativePath.substring(idx + 1) : relativePath;
    }

    private static List<PreparedFile> expand(List<File> selectedFiles) throws IOException {
        List<PreparedFile> entries = new ArrayList<>();
        for (File f : selectedFiles) {
            if (f.isFile()) {
                entries.add(new PreparedFile(f, f.getName(), f.length(), false));
            } else if (f.isDirectory()) {
                Path rootPath = f.toPath();
                try (Stream<Path> stream = Files.walk(rootPath)) {
                    for (Path p : (Iterable<Path>) stream.sorted()::iterator) {
                        String rel = f.getName();
                        if (!p.equals(rootPath)) {
                            rel += "/" + rootPath.relativize(p).toString().replace(File.separatorChar, '/');
                        }
                        if (Files.isDirectory(p)) {
                            entries.add(new PreparedFile(null, rel, 0, true));
                        } else {
                            entries.add(new PreparedFile(p.toFile(), rel, Files.size(p), false));
                        }
                    }
                }
            }
        }
        return entries;
    }

    private static class PreparedFile {
        final File file;
        final String relativePath;
        final long size;
        final boolean isDirectory;

        PreparedFile(File file, String relativePath, long size, boolean isDirectory) {
            this.file = file;
            this.relativePath = relativePath;
            this.size = size;
            this.isDirectory = isDirectory;
        }
    }
}
