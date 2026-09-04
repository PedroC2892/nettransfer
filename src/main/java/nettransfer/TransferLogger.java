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
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    static {
        try {
            Files.createDirectories(FileTransferService.DOWNLOAD_BASE);
        } catch (IOException ignored) {}
    }

    public static void logSendStart(String transferId, String peerName, String peerIp, List<TransferMessage.FileEntry> files, long totalSize) {
        StringBuilder sb = new StringBuilder();
        sb.append(line("ENVIO INICIADO"));
        sb.append(field("ID", shortId(transferId)));
        sb.append(field("Destino", peerName + " (" + peerIp + ")"));
        sb.append(field("Ficheiros", files.size() + ""));
        sb.append(field("Tamanho total", formatSize(totalSize)));
        for (TransferMessage.FileEntry f : files) {
            String icon = f.isDirectory ? "[pasta]" : "[fich] ";
            sb.append(field("  " + icon + " " + f.relativePath, f.isDirectory ? "" : formatSize(f.size)));
        }
        write(sb.toString());
    }

    public static void logSendDone(String transferId, String peerName, long totalSize, long elapsedMs) {
        String speed = elapsedMs > 0 ? formatSize((long)(totalSize * 1000.0 / elapsedMs)) + "/s" : "—";
        write(line("ENVIO CONCLUÍDO") + field("ID", shortId(transferId)) + field("Destino", peerName) + field("Total", formatSize(totalSize)) + field("Duração", elapsedMs / 1000 + "s") + field("Velocidade média", speed));
    }

    public static void logSendRejected(String transferId, String peerName) {
        write(line("ENVIO RECUSADO") + field("ID", shortId(transferId)) + field("Destino", peerName));
    }

    public static void logSendError(String transferId, String peerName) {
        write(line("ERRO NO ENVIO") + field("ID", shortId(transferId)) + field("Destino", peerName));
    }

    public static void logReceiveRequest(String transferId, String senderName, String senderIp, List<TransferMessage.FileEntry> files, long totalSize) {
        StringBuilder sb = new StringBuilder();
        sb.append(line("PEDIDO RECEBIDO"));
        sb.append(field("ID", shortId(transferId)));
        sb.append(field("Remetente", senderName + " (" + senderIp + ")"));
        sb.append(field("Ficheiros", files.size() + ""));
        sb.append(field("Tamanho total", formatSize(totalSize)));
        for (TransferMessage.FileEntry f : files) {
            String icon = f.isDirectory ? "[pasta]" : "[fich] ";
            sb.append(field("  " + icon + " " + f.relativePath, f.isDirectory ? "" : formatSize(f.size)));
        }
        write(sb.toString());
    }

    public static void logReceiveAccepted(String transferId, String destDir) {
        write(line("TRANSFERÊNCIA ACEITE") + field("ID", shortId(transferId)) + field("Pasta de destino", destDir));
    }

    public static void logReceiveRejected(String transferId) {
        write(line("TRANSFERÊNCIA RECUSADA") + field("ID", shortId(transferId)));
    }

    public static void logReceiveDone(String transferId, String senderName, long totalSize, long elapsedMs) {
        String speed = elapsedMs > 0 ? formatSize((long)(totalSize * 1000.0 / elapsedMs)) + "/s" : "—";
        write(line("RECEÇÃO CONCLUÍDA") + field("ID", shortId(transferId)) + field("Remetente", senderName) + field("Total", formatSize(totalSize)) + field("Duração", elapsedMs / 1000 + "s") + field("Velocidade média", speed));
    }

    public static void logReceiveError(String transferId) {
        write(line("ERRO NA RECEÇÃO") + field("ID", shortId(transferId)));
    }

    public static void logPeerDiscovered(String name, String ip, int port) {
        write(line("DISPOSITIVO DESCOBERTO") + field("Nome", name) + field("IP", ip) + field("Porta", port + ""));
    }

    public static void logPeerLost(String name, String ip) {
        write(line("DISPOSITIVO PERDIDO") + field("Nome", name) + field("IP", ip));
    }

    // ──────────────────────────────────────────

    private static String timestamp() {
        return LocalDateTime.now().format(FMT);
    }

    private static String line(String event) {
        return "\n[" + timestamp() + "] ── " + event + " ──\n";
    }

    private static String field(String key, String value) {
        if (value == null || value.isEmpty()) return "  " + key + "\n";
        return String.format("  %-28s %s%n", key, value);
    }

    private static String shortId(String id) {
        return id.length() > 8 ? id.substring(0, 8) : id;
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
            System.err.println("[TransferLogger] Erro ao escrever log: " + e.getMessage());
        }
    }
}
