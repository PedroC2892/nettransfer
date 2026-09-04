package nettransfer;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.util.List;

public class TransferRequestDialog {

    private final Stage owner;
    private final String senderName;
    private final List<TransferMessage.FileEntry> files;
    private final long totalSize;
    private final int totalFiles;
    private boolean accepted = false;

    private final String senderIp;

    public TransferRequestDialog(Stage owner, String senderName, String senderIp,
                                  List<TransferMessage.FileEntry> files, long totalSize, int totalFiles) {
        this.owner = owner;
        this.senderName = senderName;
        this.senderIp = senderIp;
        this.files = files;
        this.totalSize = totalSize;
        this.totalFiles = totalFiles;
    }

    public boolean showAndWait() {
        Stage dialog = new Stage();
        dialog.initOwner(owner);
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initStyle(StageStyle.UNDECORATED);
        dialog.setTitle("Pedido de transferência");

        VBox root = new VBox(0);
        root.getStyleClass().add("root");
        root.setStyle("-fx-background-color:#1a1a2e; -fx-background-radius:12; -fx-border-color:#0f3460; -fx-border-radius:12; -fx-border-width:1;");

        // Header
        VBox header = new VBox(4);
        header.getStyleClass().add("dialog-header");
        header.setStyle("-fx-background-radius: 12 12 0 0;");
        Label title = new Label("Pedido de transferência");
        title.getStyleClass().add("dialog-title");
        Label sender = new Label("De: " + senderName + "  ·  " + senderIp);
        sender.getStyleClass().add("dialog-sender");
        header.getChildren().addAll(title, sender);

        // File list
        VBox fileList = new VBox(0);
        fileList.setPadding(new Insets(8, 24, 8, 24));

        int shown = Math.min(files.size(), 8);
        for (int i = 0; i < shown; i++) {
            TransferMessage.FileEntry entry = files.get(i);
            HBox row = new HBox(8);
            row.getStyleClass().add("file-entry");
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(6, 0, 6, 0));
            String icon = entry.isDirectory ? "📁" : fileIcon(entry.name);
            Label ico = new Label(icon);
            ico.setStyle("-fx-font-size:14px;");
            Label name = new Label(entry.name);
            name.getStyleClass().add("file-entry-name");
            HBox sp = new HBox();
            HBox.setHgrow(sp, Priority.ALWAYS);
            Label size = new Label(entry.isDirectory ? "pasta" : MainController.formatSize(entry.size));
            size.getStyleClass().add("file-entry-size");
            row.getChildren().addAll(ico, name, sp, size);
            fileList.getChildren().add(row);
        }
        if (files.size() > 8) {
            Label more = new Label("... e mais " + (files.size() - 8) + " ficheiro(s)");
            more.setStyle("-fx-text-fill:#6c7a96; -fx-font-size:12px; -fx-padding: 6 0 0 0;");
            fileList.getChildren().add(more);
        }

        ScrollPane scroll = new ScrollPane(fileList);
        scroll.setFitToWidth(true);
        scroll.setMaxHeight(240);
        scroll.getStyleClass().add("scroll-pane");
        scroll.setStyle("-fx-border-width:0; -fx-background-color:transparent;");

        // Summary
        VBox summary = new VBox(4);
        summary.getStyleClass().add("summary-box");
        summary.setStyle("-fx-margin: 12 24 12 24; -fx-background-color:#0f1a30; -fx-background-radius:8;");
        VBox.setMargin(summary, new Insets(0, 24, 0, 24));
        Label summaryLabel = new Label(totalFiles + " ficheiro(s)  ·  " + MainController.formatSize(totalSize) + " no total");
        summaryLabel.getStyleClass().add("summary-text");
        summary.getChildren().add(summaryLabel);

        // Buttons
        Button acceptBtn = new Button("Aceitar");
        acceptBtn.getStyleClass().add("btn-primary");
        acceptBtn.setPrefWidth(110);
        acceptBtn.setOnAction(e -> { accepted = true; dialog.close(); });

        Button rejectBtn = new Button("Recusar");
        rejectBtn.getStyleClass().add("btn-secondary");
        rejectBtn.setPrefWidth(110);
        rejectBtn.setOnAction(e -> { accepted = false; dialog.close(); });

        HBox btnRow = new HBox(10, rejectBtn, acceptBtn);
        btnRow.setAlignment(Pos.CENTER_RIGHT);
        btnRow.setPadding(new Insets(16, 24, 20, 24));

        root.getChildren().addAll(header, scroll, summary, btnRow);

        Scene scene = new Scene(root);
        scene.getStylesheets().add(getClass().getResource("app.css").toExternalForm());
        scene.setFill(javafx.scene.paint.Color.TRANSPARENT);
        dialog.setScene(scene);
        dialog.sizeToScene();
        dialog.centerOnScreen();
        dialog.showAndWait();

        return accepted;
    }

    private String fileIcon(String name) {
        if (name == null) return "📄";
        String lc = name.toLowerCase();
        if (lc.endsWith(".jpg") || lc.endsWith(".jpeg") || lc.endsWith(".png") || lc.endsWith(".gif") || lc.endsWith(".webp")) return "🖼";
        if (lc.endsWith(".mp4") || lc.endsWith(".mkv") || lc.endsWith(".avi") || lc.endsWith(".mov")) return "🎬";
        if (lc.endsWith(".mp3") || lc.endsWith(".flac") || lc.endsWith(".wav") || lc.endsWith(".ogg")) return "🎵";
        if (lc.endsWith(".zip") || lc.endsWith(".tar") || lc.endsWith(".gz") || lc.endsWith(".rar") || lc.endsWith(".7z")) return "📦";
        if (lc.endsWith(".pdf")) return "📕";
        if (lc.endsWith(".java") || lc.endsWith(".py") || lc.endsWith(".js") || lc.endsWith(".ts") || lc.endsWith(".c") || lc.endsWith(".cpp")) return "💻";
        return "📄";
    }
}
