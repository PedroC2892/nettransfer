package nettransfer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class TransferLogger {

    public static final Path LOG_FILE = FileTransferService.DOWNLOAD_BASE.resolve("nettransfer.log");
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    static {
        try { Files.createDirectories(FileTransferService.DOWNLOAD_BASE); } catch (IOException ignored) {}
    }

    public static void logSendStart(String transferId, String peerName, String peerIp,
                                     List<TransferMessage.FileEntry> files, long totalSize) {
        StringBuilder sb = new StringBuilder();
        sb.append(header("SEND", transferId));
        sb.append(row("To", peerName + " (" + peerIp + ")"));
        sb.append(row("Total", formatSize(totalSize) + "  ·  " + files.size() + " ficheiro(s)"));
        for (TransferMessage.FileEntry f : files) {
            String tag = f.isDirectory ? "  [dir]  " : "  [file] ";
            sb.append(row(tag + f.relativePath, f.isDirectory ? "" : formatSize(f.size)));
        }
        write(sb.toString());
    }

    public static void logSendDone(String transferId, String peerName, long totalSize, long elapsedMs) {
        write(header("SEND DONE", transferId)
                + row("To", peerName)
                + row("Total", formatSize(totalSize))
                + row("Duration", elapsedMs / 1000 + "s")
                + row("Speed", elapsedMs > 0 ? formatSize((long)(totalSize * 1000.0 / elapsedMs)) + "/s" : "—"));
    }

    public static void logSendRejected(String transferId, String peerName) {
        write(header("SEND REJECTED", transferId) + row("By", peerName));
    }

    public static void logSendError(String transferId, String peerName, String peerIp,
                                     long transferred, long totalSize, String reason) {
        write(header("SEND ERROR", transferId)
                + row("To", peerName + (peerIp != null ? " (" + peerIp + ")" : ""))
                + row("Reason", reason != null ? reason : "unknown")
                + row("Progress", formatSize(transferred) + " of " + formatSize(totalSize)));
    }

    public static void logReceiveRequest(String transferId, String senderName, String senderIp,
                                          List<TransferMessage.FileEntry> files, long totalSize) {
        StringBuilder sb = new StringBuilder();
        sb.append(header("INCOMING REQUEST", transferId));
        sb.append(row("From", senderName + " (" + senderIp + ")"));
        sb.append(row("Total", formatSize(totalSize) + "  ·  " + files.size() + " ficheiro(s)"));
        for (TransferMessage.FileEntry f : files) {
            String tag = f.isDirectory ? "  [dir]  " : "  [file] ";
            sb.append(row(tag + f.relativePath, f.isDirectory ? "" : formatSize(f.size)));
        }
        write(sb.toString());
    }

    public static void logReceiveAccepted(String transferId, String destDir) {
        write(header("TRANSFER ACCEPTED", transferId) + row("Folder", destDir));
    }

    public static void logReceiveRejected(String transferId) {
        write(header("TRANSFER REJECTED", transferId));
    }

    public static void logReceiveDone(String transferId, String senderName, long totalSize, long elapsedMs) {
        write(header("RECEIVE DONE", transferId)
                + row("From", senderName)
                + row("Total", formatSize(totalSize))
                + row("Duration", elapsedMs / 1000 + "s")
                + row("Speed", elapsedMs > 0 ? formatSize((long)(totalSize * 1000.0 / elapsedMs)) + "/s" : "—"));
    }

    public static void logReceiveError(String transferId, String senderName, String senderIp,
                                        long transferred, long totalSize, String reason) {
        write(header("RECEIVE ERROR", transferId)
                + row("From", (senderName != null ? senderName : "unknown")
                        + (senderIp != null ? " (" + senderIp + ")" : ""))
                + row("Reason", reason != null ? reason : "unknown")
                + row("Progress", formatSize(transferred) + " of " + formatSize(totalSize)));
    }

    public static void logPeerDiscovered(String name, String ip, int port) {
        write(header("DEVICE FOUND", null)
                + row("Name", name)
                + row("IP", ip)
                + row("Port", String.valueOf(port)));
    }

    public static void logPeerLost(String name, String ip) {
        write(header("DEVICE LOST", null)
                + row("Name", name)
                + row("IP", ip));
    }

    // ── Formatting ────────────────────────────────────────────────────────────

    private static String header(String event, String id) {
        String ts = LocalDateTime.now().format(FMT);
        String idPart = id != null ? "  #" + id.substring(0, 8) : "";
        return "\n── " + event + idPart + "\n"
             + "   " + ts + "\n";
    }

    private static String row(String key, String value) {
        if (value == null || value.isBlank()) return String.format("   %-22s%n", key);
        return String.format("   %-22s %s%n", key, value);
    }

    static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        double kb = bytes / 1024.0;
        if (kb < 1024) return String.format("%.1f KB", kb);
        double mb = kb / 1024.0;
        if (mb < 1024) return String.format("%.1f MB", mb);
        return String.format("%.2f GB", mb / 1024.0);
    }

    private static synchronized void write(String text) {
        try {
            Files.createDirectories(LOG_FILE.getParent());
            Files.writeString(LOG_FILE, text, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.err.println("[TransferLogger] " + e.getMessage());
        }
    }
}
