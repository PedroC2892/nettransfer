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

public class TransferProgressPanel {

    private final VBox box;
    private final ProgressBar progressBar;
    private final Label pctLabel;
    private final Label statusLabel;
    private final Label detailLabel;
    private final Button openBtn;
    private String receiveDir;

    public TransferProgressPanel(String peerName, String receiveDir) {
        this.receiveDir = receiveDir;

        box = new VBox(6);
        box.getStyleClass().add("transfer-card");

        // Top row: peer name + status
        Label nameLabel = new Label(peerName);
        nameLabel.getStyleClass().add("transfer-peer-name");

        statusLabel = new Label("A aguardar");
        statusLabel.getStyleClass().addAll("transfer-status");

        HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        openBtn = new Button("📂 Abrir");
        openBtn.getStyleClass().add("btn-secondary");
        openBtn.setStyle("-fx-font-size:11px; -fx-padding: 3 10 3 10;");
        openBtn.setVisible(false);
        openBtn.setOnAction(e -> { if (this.receiveDir != null) MainController.openFolder(this.receiveDir); });

        HBox topRow = new HBox(8, nameLabel, spacer, statusLabel, openBtn);
        topRow.setAlignment(Pos.CENTER_LEFT);

        // Progress bar row
        progressBar = new ProgressBar(0);
        progressBar.setMaxWidth(Double.MAX_VALUE);
        progressBar.setPrefHeight(6);
        progressBar.getStyleClass().add("progress-bar");

        pctLabel = new Label("0%");
        pctLabel.setStyle("-fx-font-size:12px; -fx-text-fill:#6c7a96;");

        HBox progressRow = new HBox(10, progressBar, pctLabel);
        progressRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(progressBar, Priority.ALWAYS);

        // Detail row
        detailLabel = new Label("");
        detailLabel.getStyleClass().add("transfer-detail");

        box.getChildren().addAll(topRow, progressRow, detailLabel);
    }

    public Node node() { return box; }

    public void setReceiveDir(String dir) {
        this.receiveDir = dir;
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
}
