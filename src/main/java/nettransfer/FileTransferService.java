package nettransfer;

import com.google.gson.Gson;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class FileTransferService {
    private static final long PROGRESS_INTERVAL_MS = 150;
    private static final long SPACE_SAFETY_MARGIN = 100L * 1024 * 1024;
    /** How long the receiver's user has to accept or reject an incoming transfer. */
    public static final int REQUEST_TIMEOUT_SECONDS = 60;
    static final int SOCKET_TIMEOUT_MS = 30_000;

    private static final int MAX_ENTRIES = 100_000;
    private static final Pattern UUID_PATTERN =
            Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    private static final String FORBIDDEN_CHARS = "\\:*?\"<>|";
    private static final Pattern WINDOWS_RESERVED =
            Pattern.compile("(?i)(CON|PRN|AUX|NUL|COM[0-9]|LPT[0-9])(\\..*)?");

    static final Path DOWNLOAD_BASE = Paths.get(System.getProperty("user.home"), "Downloads", "NetTransfer");
    private static final DateTimeFormatter FOLDER_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    public static String getDownloadBaseDir() {
        return DOWNLOAD_BASE.toString();
    }

    public static void sendFiles(Peer peer, List<File> selectedFiles, String transferId,
                                  String senderName, TransferListener listener) {
        Thread t = new Thread(() -> doSend(peer, selectedFiles, transferId, senderName, listener));
        t.setDaemon(true);
        t.start();
    }

    private static void doSend(Peer peer, List<File> selectedFiles, String transferId,
                                String senderName, TransferListener listener) {
        Gson gson = new Gson();
        long startTime = System.currentTimeMillis();
        List<TransferMessage.FileEntry> fileEntries = null;
        long totalSize = 0;
        long sentBytes = 0;
        try {
            List<PreparedFile> entries = expand(selectedFiles);

            for (PreparedFile entry : entries) {
                if (entry.isDirectory) continue;
                if (!entry.file.exists() || !entry.file.canRead()) {
                    TransferLogger.logSendError(transferId, peer.name, peer.ipAddress, 0, 0,
                            "File no longer exists or is unreadable: " + entry.relativePath);
                    listener.onStatusChange(transferId, TransferStatus.ERROR);
                    return;
                }
            }

            totalSize = entries.stream().filter(e -> !e.isDirectory).mapToLong(e -> e.size).sum();
            int totalFiles = (int) entries.stream().filter(e -> !e.isDirectory).count();
            fileEntries = entries.stream()
                    .map(e -> new TransferMessage.FileEntry(baseName(e.relativePath), e.relativePath, e.size, e.isDirectory))
                    .collect(Collectors.toList());

            // Log and notify UI before connecting
            TransferLogger.logSendStart(transferId, peer.name, peer.ipAddress, fileEntries, totalSize);
            listener.onSendStart(transferId, peer.name, peer.ipAddress, fileEntries, totalSize);

            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(peer.ipAddress, peer.tcpPort), 5000);
                socket.setSoTimeout(SOCKET_TIMEOUT_MS);

                Handshake hs = Handshake.forSender(socket.getInputStream(), socket.getOutputStream());
                listener.onVerificationCode(transferId, hs.getVerificationCode());
                OutputStream encOut = new EncryptedOutputStream(socket.getOutputStream(), hs);
                InputStream encIn = new EncryptedInputStream(socket.getInputStream(), hs);
                DataOutputStream out = new DataOutputStream(new BufferedOutputStream(encOut));
                DataInputStream in = new DataInputStream(new BufferedInputStream(encIn));

                TransferMessage.transferRequest(transferId, senderName, fileEntries, totalSize, totalFiles).writeTo(out, gson);

                // the receiver's user may take up to REQUEST_TIMEOUT_SECONDS to answer
                socket.setSoTimeout(REQUEST_TIMEOUT_SECONDS * 1000 + SOCKET_TIMEOUT_MS);
                TransferMessage response = TransferMessage.readFrom(in, gson);
                socket.setSoTimeout(SOCKET_TIMEOUT_MS);
                if (!"TRANSFER_RESPONSE".equals(response.type)) throw new IOException("Unexpected message: " + response.type);
                if (!response.accepted) {
                    TransferLogger.logSendRejected(transferId, peer.name);
                    listener.onStatusChange(transferId, TransferStatus.REJECTED);
                    return;
                }

                listener.onStatusChange(transferId, TransferStatus.TRANSFERRING);
                long transferred = 0;
                long lastCallback = 0;
                byte[] buffer = new byte[64 * 1024];

                for (PreparedFile entry : entries) {
                    if (entry.isDirectory) {
                        TransferMessage.fileStart(transferId, entry.relativePath, 0, true).writeTo(out, gson);
                        continue;
                    }
                    TransferMessage.fileStart(transferId, entry.relativePath, entry.size, false).writeTo(out, gson);
                    MessageDigest digest = newSha256();
                    try (FileInputStream fis = new FileInputStream(entry.file)) {
                        // send exactly the announced size: a file that grew or shrank would desync the stream
                        long remaining = entry.size;
                        while (remaining > 0) {
                            int read = fis.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                            if (read == -1) throw new IOException("File shrank during transfer: " + entry.relativePath);
                            remaining -= read;
                            out.write(buffer, 0, read);
                            digest.update(buffer, 0, read);
                            transferred += read;
                            sentBytes = transferred;
                            long now = System.currentTimeMillis();
                            if (now - lastCallback > PROGRESS_INTERVAL_MS || transferred == totalSize) {
                                double speed = transferred / Math.max(0.001, (now - startTime) / 1000.0);
                                listener.onProgress(transferId, transferred, totalSize, speed);
                                lastCallback = now;
                            }
                        }
                    }
                    out.flush();
                    String hash = HexFormat.of().formatHex(digest.digest());
                    TransferMessage.fileEnd(transferId, entry.relativePath, hash).writeTo(out, gson);
                }

                TransferMessage.transferComplete(transferId).writeTo(out, gson);
                long elapsed = System.currentTimeMillis() - startTime;
                TransferLogger.logSendDone(transferId, peer.name, totalSize, elapsed);
                listener.onStatusChange(transferId, TransferStatus.DONE);
            }
        } catch (Exception e) {
            String reason = e.getClass().getSimpleName()
                    + (e.getMessage() != null ? ": " + e.getMessage() : "");
            TransferLogger.logSendError(transferId, peer.name, peer.ipAddress, sentBytes, totalSize, reason);
            listener.onStatusChange(transferId, TransferStatus.ERROR);
        }
    }

    public static void handleIncoming(Socket socket, TransferListener listener) {
        Gson gson = new Gson();
        TransferMessage request = null;
        String senderIp = socket.getInetAddress().getHostAddress();
        long startTime = System.currentTimeMillis();
        long recvBytes = 0;
        Path destBase = null;
        List<Path> writtenFiles = new ArrayList<>();
        try {
            Handshake hs = Handshake.forReceiver(socket.getInputStream(), socket.getOutputStream());
            InputStream encIn = new EncryptedInputStream(socket.getInputStream(), hs);
            OutputStream encOut = new EncryptedOutputStream(socket.getOutputStream(), hs);
            DataInputStream in = new DataInputStream(new BufferedInputStream(encIn));
            DataOutputStream out = new DataOutputStream(new BufferedOutputStream(encOut));

            request = TransferMessage.readFrom(in, gson);
            validateRequest(request);

            // Log the incoming request
            TransferLogger.logReceiveRequest(request.transferId, request.senderName, senderIp,
                    request.files, request.totalSize);

            long usableSpace = Files.getFileStore(DOWNLOAD_BASE).getUsableSpace();
            boolean enoughSpace = usableSpace >= request.totalSize + SPACE_SAFETY_MARGIN;

            // The listener blocks until the user answers; it must give up after REQUEST_TIMEOUT_SECONDS.
            boolean accepted = listener.onIncomingRequest(request.transferId, request.senderName, senderIp,
                    request.files, request.totalSize, request.totalFiles,
                    usableSpace, enoughSpace, hs.getVerificationCode());

            TransferMessage.transferResponse(request.transferId, accepted).writeTo(out, gson);
            if (!accepted) {
                TransferLogger.logReceiveRejected(request.transferId);
                listener.onStatusChange(request.transferId, TransferStatus.REJECTED);
                return;
            }

            listener.onStatusChange(request.transferId, TransferStatus.TRANSFERRING);

            Map<String, TransferMessage.FileEntry> pendingEntries = new HashMap<>();
            if (request.files != null) {
                for (TransferMessage.FileEntry fe : request.files) pendingEntries.put(fe.relativePath, fe);
            }

            String timestamp = LocalDateTime.now().format(FOLDER_FMT);
            destBase = DOWNLOAD_BASE.resolve(timestamp).toAbsolutePath().normalize();
            Files.createDirectories(destBase);

            TransferLogger.logReceiveAccepted(request.transferId, destBase.toString());
            listener.onReceiveDir(request.transferId, destBase.toString());

            long transferred = 0;
            long lastCallback = 0;
            byte[] buffer = new byte[64 * 1024];

            while (true) {
                TransferMessage msg = TransferMessage.readFrom(in, gson);
                if ("TRANSFER_COMPLETE".equals(msg.type)) {
                    if (!pendingEntries.isEmpty()) {
                        throw new IOException("Sender finished with " + pendingEntries.size() + " accepted entries missing");
                    }
                    long elapsed = System.currentTimeMillis() - startTime;
                    TransferLogger.logReceiveDone(request.transferId, request.senderName,
                            request.totalSize, elapsed);
                    listener.onStatusChange(request.transferId, TransferStatus.DONE);
                    break;
                }
                if (!"FILE_START".equals(msg.type)) throw new IOException("Unexpected message: " + msg.type);

                TransferMessage.FileEntry approved = pendingEntries.remove(msg.relativePath);
                if (approved == null || approved.isDirectory != msg.isDirectory
                        || (!msg.isDirectory && approved.size != msg.size)) {
                    TransferLogger.logSecurityEvent(
                            "Receiver aborted: file not in accepted transfer manifest: " + msg.relativePath, senderIp);
                    throw new IOException("File not present in accepted transfer: " + msg.relativePath);
                }

                Path target = resolveSafePath(destBase, msg.relativePath);
                if (msg.isDirectory) {
                    Files.createDirectories(target);
                    continue;
                }
                Files.createDirectories(target.getParent());
                target = createUnique(target);
                writtenFiles.add(target);

                MessageDigest digest = newSha256();
                try (OutputStream fos = Files.newOutputStream(target)) {
                    long remaining = msg.size;
                    while (remaining > 0) {
                        int toRead = (int) Math.min(buffer.length, remaining);
                        int read = in.read(buffer, 0, toRead);
                        if (read == -1) throw new EOFException("Connection closed during transfer");
                        fos.write(buffer, 0, read);
                        digest.update(buffer, 0, read);
                        remaining -= read;
                        transferred += read;
                        recvBytes = transferred;
                        long now = System.currentTimeMillis();
                        if (now - lastCallback > PROGRESS_INTERVAL_MS || remaining == 0) {
                            double speed = transferred / Math.max(0.001, (now - startTime) / 1000.0);
                            listener.onProgress(request.transferId, transferred, request.totalSize, speed);
                            lastCallback = now;
                        }
                    }
                }
                TransferMessage fileEnd = TransferMessage.readFrom(in, gson);
                if (!"FILE_END".equals(fileEnd.type) || !msg.relativePath.equals(fileEnd.relativePath)) {
                    throw new IOException("Expected FILE_END for " + msg.relativePath);
                }
                String actualHash = HexFormat.of().formatHex(digest.digest());
                // the hash is mandatory: a sender that omits it must not skip verification
                if (!actualHash.equalsIgnoreCase(fileEnd.fileHash)) {
                    Files.deleteIfExists(target);
                    TransferLogger.logIntegrityError(request.transferId, msg.relativePath, fileEnd.fileHash, actualHash);
                    throw new IOException("File integrity check failed: " + msg.relativePath);
                }
            }
        } catch (Exception e) {
            String id = request != null ? request.transferId : "unknown";
            String name = request != null ? request.senderName : null;
            long total = request != null ? request.totalSize : 0;
            String reason = e.getClass().getSimpleName()
                    + (e.getMessage() != null ? ": " + e.getMessage() : "");
            TransferLogger.logReceiveError(id, name, senderIp, recvBytes, total, reason);
            cleanupPartial(id, destBase, writtenFiles);
            listener.onStatusChange(id, TransferStatus.ERROR);
        } finally {
            try { socket.close(); } catch (IOException ignored) {}
        }
    }

    private static void cleanupPartial(String transferId, Path destBase, List<Path> writtenFiles) {
        int removed = 0;
        long freed = 0;
        for (Path p : writtenFiles) {
            try {
                long size = Files.exists(p) ? Files.size(p) : 0;
                if (Files.deleteIfExists(p)) {
                    removed++;
                    freed += size;
                }
            } catch (IOException ignored) {}
        }
        if (destBase != null) {
            try (Stream<Path> stream = Files.exists(destBase) ? Files.list(destBase) : null) {
                if (stream != null && stream.findAny().isEmpty()) Files.deleteIfExists(destBase);
            } catch (IOException ignored) {}
        }
        if (removed > 0) TransferLogger.logPartialCleanup(transferId, removed, freed);
    }

    private static MessageDigest newSha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Rejects any request whose fields could crash, mislead or exhaust the receiver. */
    static void validateRequest(TransferMessage r) throws IOException {
        if (!"TRANSFER_REQUEST".equals(r.type)) throw new IOException("Expected TRANSFER_REQUEST, got " + r.type);
        if (r.transferId == null || !UUID_PATTERN.matcher(r.transferId).matches()) {
            r.transferId = null; // never echo an unchecked id into the log
            throw new IOException("Invalid transfer id");
        }
        if (!isPrintable(r.senderName, 256)) throw new IOException("Invalid sender name");
        if (r.files == null || r.files.size() > MAX_ENTRIES) throw new IOException("Invalid file list");
        Set<String> seen = new HashSet<>();
        long sum = 0;
        int count = 0;
        for (TransferMessage.FileEntry fe : r.files) {
            if (fe == null) throw new IOException("Null file entry");
            checkRelativePath(fe.relativePath);
            if (!seen.add(fe.relativePath)) throw new IOException("Duplicate entry: " + fe.relativePath);
            if (fe.size < 0 || (fe.isDirectory && fe.size != 0)) throw new IOException("Invalid size: " + fe.relativePath);
            fe.name = baseName(fe.relativePath); // display name is derived locally, never trusted
            if (!fe.isDirectory) {
                sum = Math.addExact(sum, fe.size);
                count++;
            }
        }
        if (sum != r.totalSize || count != r.totalFiles) throw new IOException("Totals do not match the file list");
    }

    /**
     * A remote path must be relative, '/'-separated, and every component must be a
     * plain file name that is valid on Windows, macOS and Linux alike.
     */
    static void checkRelativePath(String path) throws IOException {
        if (path == null || path.isEmpty() || path.length() > 4096) throw new IOException("Invalid path length");
        for (String part : path.split("/", -1)) {
            if (part.equals(".") || part.equals("..") || !isPrintable(part, 255) || part.chars().anyMatch(c -> FORBIDDEN_CHARS.indexOf(c) >= 0)
                    || part.endsWith(".") || part.endsWith(" ")
                    || WINDOWS_RESERVED.matcher(part).matches()) {
                throw new IOException("Unsafe file name: " + path.replaceAll("\\p{Cntrl}", "?"));
            }
        }
    }

    private static boolean isPrintable(String s, int maxLen) {
        return s != null && !s.isEmpty() && s.length() <= maxLen && s.chars().noneMatch(Character::isISOControl);
    }

    private static Path resolveSafePath(Path base, String relativePath) throws IOException {
        checkRelativePath(relativePath);
        Path resolved = base.resolve(relativePath).normalize();
        if (!resolved.startsWith(base)) throw new IOException("Path traversal detected: " + relativePath);
        return resolved;
    }

    /** Atomically creates a new empty file at target, or at name_2.ext, name_3.ext... if taken. Never overwrites. */
    static Path createUnique(Path target) throws IOException {
        String name = target.getFileName().toString();
        String base = name, ext = "";
        int dot = name.lastIndexOf('.');
        if (dot > 0) { base = name.substring(0, dot); ext = name.substring(dot); }
        for (int i = 1; i <= 10_000; i++) {
            Path candidate = i == 1 ? target : target.resolveSibling(base + "_" + i + ext);
            try {
                return Files.createFile(candidate);
            } catch (FileAlreadyExistsException e) {
                // taken (possibly by a concurrent transfer) — try the next suffix
            }
        }
        throw new IOException("Too many files named " + name);
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
