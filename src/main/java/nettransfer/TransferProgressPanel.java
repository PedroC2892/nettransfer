package nettransfer;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class TransferProgressPanel {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final VBox box;
    private final ProgressBar progressBar;
    private final Label pctLabel;
    private final Label statusLabel;
    private final Label detailLabel;
    private final Button openBtn;
    private final Button detailsBtn;
    private final VBox detailsBox;
    private boolean detailsVisible = false;
    private String receiveDir;

    public TransferProgressPanel(String peerName, String receiveDir,
                                  String peerIp, String direction,
                                  List<TransferMessage.FileEntry> files,
                                  long totalSize) {
        this.receiveDir = receiveDir;

        box = new VBox(6);
        box.getStyleClass().add("transfer-card");

        // ── Top row ──
        Label dirIcon = new Label("ENVIAR".equals(direction) ? "↑" : "↓");
        dirIcon.setStyle("-fx-font-size:12px; -fx-font-weight:bold; -fx-text-fill:" +
                ("ENVIAR".equals(direction) ? "#e94560" : "#48bb78") + ";");

        Label nameLabel = new Label(peerName);
        nameLabel.getStyleClass().add("transfer-peer-name");

        Label timeLabel = new Label(LocalDateTime.now().format(TIME_FMT));
        timeLabel.setStyle("-fx-font-size:11px; -fx-text-fill:#4a5568;");

        statusLabel = new Label("Waiting");
        statusLabel.getStyleClass().addAll("transfer-status");

        detailsBtn = new Button("▸ details");
        detailsBtn.setStyle("-fx-background-color:transparent; -fx-font-size:11px; -fx-text-fill:#4a5568; -fx-cursor:hand; -fx-padding:0 4 0 4;");
        detailsBtn.setOnAction(e -> toggleDetails());

        openBtn = new Button("📂 Open");
        openBtn.getStyleClass().add("btn-secondary");
        openBtn.setStyle("-fx-font-size:11px; -fx-padding: 3 10 3 10;");
        openBtn.setVisible(false);
        openBtn.setOnAction(e -> { if (this.receiveDir != null) MainController.openFolder(this.receiveDir); });

        HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox topRow = new HBox(8, dirIcon, nameLabel, timeLabel, spacer, detailsBtn, statusLabel, openBtn);
        topRow.setAlignment(Pos.CENTER_LEFT);

        // ── Progress row ──
        progressBar = new ProgressBar(0);
        progressBar.setMaxWidth(Double.MAX_VALUE);
        progressBar.setPrefHeight(6);
        progressBar.getStyleClass().add("progress-bar");

        pctLabel = new Label("0%");
        pctLabel.setStyle("-fx-font-size:12px; -fx-text-fill:#6c7a96;");

        HBox progressRow = new HBox(10, progressBar, pctLabel);
        progressRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(progressBar, Priority.ALWAYS);

        detailLabel = new Label("");
        detailLabel.getStyleClass().add("transfer-detail");

        // ── Details panel (collapsed by default) ──
        detailsBox = new VBox(4);
        detailsBox.setStyle("-fx-background-color:#0d1525; -fx-background-radius:6; -fx-padding:10 12 10 12;");
        detailsBox.setVisible(false);
        detailsBox.setManaged(false);

        // Header info
        VBox infoBlock = new VBox(3);
        addDetailRow(infoBlock, "IP", peerIp != null ? peerIp : "—");
        addDetailRow(infoBlock, "Direction", direction);
        addDetailRow(infoBlock, "Total", MainController.formatSize(totalSize));
        if (files != null) addDetailRow(infoBlock, "Files", files.size() + "");
        detailsBox.getChildren().add(infoBlock);

        // File list
        if (files != null && !files.isEmpty()) {
            Label filesHdr = new Label("Files:");
            filesHdr.setStyle("-fx-font-size:11px; -fx-text-fill:#4a5568; -fx-padding: 6 0 2 0;");
            detailsBox.getChildren().add(filesHdr);
            for (TransferMessage.FileEntry f : files) {
                HBox row = new HBox(6);
                row.setAlignment(Pos.CENTER_LEFT);
                String ico = f.isDirectory ? "📁" : fileIcon(f.name);
                Label icon = new Label(ico);
                icon.setStyle("-fx-font-size:12px;");
                Label fname = new Label(f.relativePath);
                fname.setStyle("-fx-font-size:12px; -fx-text-fill:#8a9bb5;");
                HBox sp2 = new HBox(); HBox.setHgrow(sp2, Priority.ALWAYS);
                Label fsize = new Label(f.isDirectory ? "folder" : MainController.formatSize(f.size));
                fsize.setStyle("-fx-font-size:11px; -fx-text-fill:#4a5568;");
                row.getChildren().addAll(icon, fname, sp2, fsize);
                detailsBox.getChildren().add(row);
            }
        }

        box.getChildren().addAll(topRow, progressRow, detailLabel, detailsBox);
    }

    private void addDetailRow(VBox parent, String key, String value) {
        HBox row = new HBox(8);
        Label k = new Label(key + ":");
        k.setStyle("-fx-font-size:11px; -fx-text-fill:#4a5568; -fx-min-width:70;");
        Label v = new Label(value);
        v.setStyle("-fx-font-size:11px; -fx-text-fill:#8a9bb5;");
        row.getChildren().addAll(k, v);
        parent.getChildren().add(row);
    }

    private void toggleDetails() {
        detailsVisible = !detailsVisible;
        detailsBox.setVisible(detailsVisible);
        detailsBox.setManaged(detailsVisible);
        detailsBtn.setText(detailsVisible ? "▾ details" : "▸ details");
    }

    public Node node() { return box; }

    public void setReceiveDir(String dir) {
        this.receiveDir = dir;
    }

    public void setFileDetails(List<TransferMessage.FileEntry> files, long totalSize, String peerIp) {
        // Update details box with file info after construction (used for send panels created before files are known)
        detailsBox.getChildren().clear();
        VBox infoBlock = new VBox(3);
        addDetailRow(infoBlock, "IP", peerIp != null ? peerIp : "—");
        addDetailRow(infoBlock, "Total", MainController.formatSize(totalSize));
        if (files != null) addDetailRow(infoBlock, "Files", files.size() + "");
        detailsBox.getChildren().add(infoBlock);
        if (files != null && !files.isEmpty()) {
            Label filesHdr = new Label("Files:");
            filesHdr.setStyle("-fx-font-size:11px; -fx-text-fill:#4a5568; -fx-padding: 6 0 2 0;");
            detailsBox.getChildren().add(filesHdr);
            for (TransferMessage.FileEntry f : files) {
                HBox row = new HBox(6);
                row.setAlignment(Pos.CENTER_LEFT);
                Label icon = new Label(f.isDirectory ? "📁" : fileIcon(f.name));
                icon.setStyle("-fx-font-size:12px;");
                Label fname = new Label(f.relativePath);
                fname.setStyle("-fx-font-size:12px; -fx-text-fill:#8a9bb5;");
                HBox sp2 = new HBox(); HBox.setHgrow(sp2, Priority.ALWAYS);
                Label fsize = new Label(f.isDirectory ? "folder" : MainController.formatSize(f.size));
                fsize.setStyle("-fx-font-size:11px; -fx-text-fill:#4a5568;");
                row.getChildren().addAll(icon, fname, sp2, fsize);
                detailsBox.getChildren().add(row);
            }
        }
    }

    public void updateProgress(long transferred, long total, double speedBps) {
        double pct = total > 0 ? (double) transferred / total : 0;
        progressBar.setProgress(pct);
        pctLabel.setText(String.format("%.0f%%", pct * 100));

        double speedMB = speedBps / (1024.0 * 1024.0);
        String eta = "—";
        if (speedBps > 0 && total > transferred) {
            long secs = (long) ((total - transferred) / speedBps);
            eta = secs < 60 ? secs + "s" : (secs / 60) + "m " + (secs % 60) + "s";
        }
        detailLabel.setText(String.format("%s / %s  ·  %.1f MB/s  ·  ~%s restantes",
                MainController.formatSize(transferred),
                MainController.formatSize(total),
                speedMB, eta));
    }

    public void setStatus(String text, String styleClass) {
        statusLabel.setText(text);
        statusLabel.getStyleClass().removeAll("done", "error", "rejected", "normal");
        statusLabel.getStyleClass().add(styleClass);
        boolean done = "done".equals(styleClass);
        openBtn.setVisible(done && receiveDir != null);
        if (done) progressBar.setProgress(1.0);
    }

    private String fileIcon(String name) {
        if (name == null) return "📄";
        String lc = name.toLowerCase();
        if (lc.matches(".*\\.(jpg|jpeg|png|gif|webp|svg)$")) return "🖼";
        if (lc.matches(".*\\.(mp4|mkv|avi|mov|webm)$")) return "🎬";
        if (lc.matches(".*\\.(mp3|flac|wav|ogg|aac)$")) return "🎵";
        if (lc.matches(".*\\.(zip|tar|gz|rar|7z|bz2)$")) return "📦";
        if (lc.endsWith(".pdf")) return "📕";
        if (lc.matches(".*\\.(java|py|js|ts|c|cpp|rs|go|sh)$")) return "💻";
        if (lc.matches(".*\\.(doc|docx|odt|txt|md)$")) return "📝";
        return "📄";
    }
}
