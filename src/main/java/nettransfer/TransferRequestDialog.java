package nettransfer;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.KeyCode;
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
    private final String senderIp;
    private final List<TransferMessage.FileEntry> files;
    private final long totalSize;
    private final int totalFiles;
    private final long usableSpace;
    private final boolean enoughSpace;
    private final String verificationCode;
    private boolean accepted = false;

    public TransferRequestDialog(Stage owner, String senderName, String senderIp,
                                  List<TransferMessage.FileEntry> files, long totalSize, int totalFiles,
                                  long usableSpace, boolean enoughSpace, String verificationCode) {
        this.owner = owner;
        this.senderName = senderName;
        this.senderIp = senderIp;
        this.files = files;
        this.totalSize = totalSize;
        this.totalFiles = totalFiles;
        this.usableSpace = usableSpace;
        this.enoughSpace = enoughSpace;
        this.verificationCode = verificationCode;
    }

    public boolean showAndWait() {
        Stage dialog = new Stage();
        dialog.initOwner(owner);
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initStyle(StageStyle.UNDECORATED);

        VBox root = new VBox(0);
        root.setStyle("-fx-background-color:#111111; -fx-border-color:#2a2a2a; -fx-border-width:1;");

        // Header
        VBox header = new VBox(4);
        header.getStyleClass().add("dialog-header");
        Label title = new Label("Incoming transfer");
        title.getStyleClass().add("dialog-title");
        Label sender = new Label(senderName + "  ·  " + senderIp);
        sender.getStyleClass().add("dialog-sender");
        header.getChildren().addAll(title, sender);

        // Verification code (MITM defence)
        Label verifyHint = new Label("Verification code — must match on both devices");
        verifyHint.getStyleClass().add("verify-label");
        Label verifyCode = new Label(verificationCode != null ? verificationCode : "——————");
        verifyCode.getStyleClass().add("verify-code");
        VBox verifyBox = new VBox(4, verifyHint, verifyCode);
        verifyBox.getStyleClass().add("verify-box");
        VBox.setMargin(verifyBox, new Insets(14, 22, 0, 22));

        // File list
        VBox fileList = new VBox(0);
        fileList.setPadding(new Insets(6, 22, 6, 22));

        int shown = Math.min(files.size(), 8);
        for (int i = 0; i < shown; i++) {
            TransferMessage.FileEntry entry = files.get(i);
            HBox row = new HBox(8);
            row.getStyleClass().add("file-entry");
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(5, 0, 5, 0));
            String icon = entry.isDirectory ? "▶" : "—";
            Label ico = new Label(icon);
            ico.setStyle("-fx-font-size:11px; -fx-text-fill:#555555;");
            Label name = new Label(entry.name);
            name.getStyleClass().add("file-entry-name");
            HBox sp = new HBox();
            HBox.setHgrow(sp, Priority.ALWAYS);
            Label size = new Label(entry.isDirectory ? "folder" : MainController.formatSize(entry.size));
            size.getStyleClass().add("file-entry-size");
            row.getChildren().addAll(ico, name, sp, size);
            fileList.getChildren().add(row);
        }
        if (files.size() > 8) {
            Label more = new Label("+ " + (files.size() - 8) + " more file(s)");
            more.setStyle("-fx-text-fill:#444444; -fx-font-size:12px; -fx-padding: 4 0 0 0;");
            fileList.getChildren().add(more);
        }

        ScrollPane scroll = new ScrollPane(fileList);
        scroll.setFitToWidth(true);
        scroll.setMaxHeight(220);
        scroll.getStyleClass().add("scroll-pane");
        scroll.setStyle("-fx-border-width:0; -fx-background-color:transparent;");

        // Summary
        VBox summary = new VBox(2);
        summary.getStyleClass().add("summary-box");
        VBox.setMargin(summary, new Insets(0, 22, 0, 22));
        Label summaryLabel = new Label(totalFiles + " file(s)  ·  " + MainController.formatSize(totalSize));
        summaryLabel.getStyleClass().add("summary-text");
        summary.getChildren().add(summaryLabel);

        Label spaceWarning = new Label("Not enough space — needs " + MainController.formatSize(totalSize)
                + ", " + MainController.formatSize(usableSpace) + " available");
        spaceWarning.getStyleClass().add("verify-warning");
        spaceWarning.setVisible(!enoughSpace);
        spaceWarning.setManaged(!enoughSpace);
        VBox.setMargin(spaceWarning, new Insets(8, 22, 0, 22));

        // Buttons — Enter accepts, Esc rejects
        Button acceptBtn = new Button("Accept  [Enter]");
        acceptBtn.getStyleClass().add("btn-primary");
        acceptBtn.setPrefWidth(130);
        acceptBtn.setDefaultButton(true);
        acceptBtn.setDisable(!enoughSpace);
        acceptBtn.setOnAction(e -> { accepted = true; dialog.close(); });

        Button rejectBtn = new Button("Recusar  [Esc]");
        rejectBtn.getStyleClass().add("btn-secondary");
        rejectBtn.setPrefWidth(130);
        rejectBtn.setCancelButton(true);
        rejectBtn.setOnAction(e -> { accepted = false; dialog.close(); });

        HBox btnRow = new HBox(10, rejectBtn, acceptBtn);
        btnRow.setAlignment(Pos.CENTER_RIGHT);
        btnRow.setPadding(new Insets(14, 22, 18, 22));

        root.getChildren().addAll(header, verifyBox, scroll, summary, spaceWarning, btnRow);

        Scene scene = new Scene(root);
        scene.getStylesheets().add(getClass().getResource("app.css").toExternalForm());

        // Global keyboard for dialog
        scene.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ENTER) { if (enoughSpace) { accepted = true; dialog.close(); } }
            else if (e.getCode() == KeyCode.ESCAPE) { accepted = false; dialog.close(); }
        });

        dialog.setScene(scene);
        dialog.sizeToScene();
        dialog.centerOnScreen();
        dialog.showAndWait();

        return accepted;
    }
}
