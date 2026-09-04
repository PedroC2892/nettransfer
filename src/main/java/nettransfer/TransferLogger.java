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
        sb.append(header("ENVIO", transferId));
        sb.append(row("Para", peerName + " (" + peerIp + ")"));
        sb.append(row("Total", formatSize(totalSize) + "  ·  " + files.size() + " ficheiro(s)"));
        for (TransferMessage.FileEntry f : files) {
            String tag = f.isDirectory ? "  [pasta]" : "  [fich] ";
            sb.append(row(tag + f.relativePath, f.isDirectory ? "" : formatSize(f.size)));
        }
        write(sb.toString());
    }

    public static void logSendDone(String transferId, String peerName, long totalSize, long elapsedMs) {
        write(header("ENVIO CONCLUÍDO", transferId)
                + row("Para", peerName)
                + row("Total", formatSize(totalSize))
                + row("Tempo", elapsedMs / 1000 + "s")
                + row("Velocidade", elapsedMs > 0 ? formatSize((long)(totalSize * 1000.0 / elapsedMs)) + "/s" : "—"));
    }

    public static void logSendRejected(String transferId, String peerName) {
        write(header("ENVIO RECUSADO", transferId) + row("Por", peerName));
    }

    public static void logSendError(String transferId, String peerName) {
        write(header("ERRO NO ENVIO", transferId) + row("Para", peerName));
    }

    public static void logReceiveRequest(String transferId, String senderName, String senderIp,
                                          List<TransferMessage.FileEntry> files, long totalSize) {
        StringBuilder sb = new StringBuilder();
        sb.append(header("PEDIDO RECEBIDO", transferId));
        sb.append(row("De", senderName + " (" + senderIp + ")"));
        sb.append(row("Total", formatSize(totalSize) + "  ·  " + files.size() + " ficheiro(s)"));
        for (TransferMessage.FileEntry f : files) {
            String tag = f.isDirectory ? "  [pasta]" : "  [fich] ";
            sb.append(row(tag + f.relativePath, f.isDirectory ? "" : formatSize(f.size)));
        }
        write(sb.toString());
    }

    public static void logReceiveAccepted(String transferId, String destDir) {
        write(header("RECEÇÃO ACEITE", transferId) + row("Pasta", destDir));
    }

    public static void logReceiveRejected(String transferId) {
        write(header("RECEÇÃO RECUSADA", transferId));
    }

    public static void logReceiveDone(String transferId, String senderName, long totalSize, long elapsedMs) {
        write(header("RECEÇÃO CONCLUÍDA", transferId)
                + row("De", senderName)
                + row("Total", formatSize(totalSize))
                + row("Tempo", elapsedMs / 1000 + "s")
                + row("Velocidade", elapsedMs > 0 ? formatSize((long)(totalSize * 1000.0 / elapsedMs)) + "/s" : "—"));
    }

    public static void logReceiveError(String transferId) {
        write(header("ERRO NA RECEÇÃO", transferId));
    }

    public static void logPeerDiscovered(String name, String ip, int port) {
        write(header("DISPOSITIVO DESCOBERTO", null)
                + row("Nome", name)
                + row("IP", ip)
                + row("Porta", String.valueOf(port)));
    }

    public static void logPeerLost(String name, String ip) {
        write(header("DISPOSITIVO PERDIDO", null)
                + row("Nome", name)
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
