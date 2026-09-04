package nettransfer;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.awt.Desktop;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MainController implements TransferListener {

    private static final long PEER_TIMEOUT_MS = 15000;

    private final Stage stage;

    private final Map<String, Peer> peers = new LinkedHashMap<>();
    private final Map<String, Long> lastSeen = new HashMap<>();
    private final Map<String, DeviceCard> cards = new LinkedHashMap<>();
    private final Set<String> selectedPeerIds = new LinkedHashSet<>();
    private final List<File> selectedFiles = new ArrayList<>();
    private final Map<String, TransferProgressPanel> progressPanels = new ConcurrentHashMap<>();

    private FlowPane cardsPane;
    private Label emptyLabel;
    private Label filesLabel;
    private Button sendButton;
    private Button chooseButton;
    private VBox progressContainer;

    public MainController(Stage stage) {
        this.stage = stage;
    }

    public void show() {
        BorderPane root = new BorderPane();
        root.getStyleClass().add("root");

        root.setTop(buildTopBar());
        root.setCenter(buildCenter());
        root.setBottom(buildBottom());

        Scene scene = new Scene(root, 860, 620);
        scene.getStylesheets().add(getClass().getResource("app.css").toExternalForm());
        stage.setTitle("NetTransfer");
        stage.setScene(scene);
        stage.setMinWidth(600);
        stage.setMinHeight(450);
        stage.show();

        // Global keyboard shortcuts
        scene.addEventFilter(KeyEvent.KEY_PRESSED, this::handleGlobalKey);

        Timeline peerCleanup = new Timeline(new KeyFrame(Duration.seconds(5), e -> removeStalePeers()));
        peerCleanup.setCycleCount(Animation.INDEFINITE);
        peerCleanup.play();
    }

    private void handleGlobalKey(KeyEvent e) {
        switch (e.getCode()) {
            case F -> { chooseFiles(); e.consume(); }
            case ENTER -> {
                if (!sendButton.isDisabled()) { sendToSelected(); e.consume(); }
            }
            case ESCAPE -> { selectedFiles.clear(); filesLabel.setText("Nenhum ficheiro selecionado"); updateSendButton(); e.consume(); }
            case LEFT, RIGHT, UP, DOWN -> navigateCards(e.getCode());
            case A -> {
                if (e.isControlDown()) { selectAllCards(); e.consume(); }
            }
            default -> {}
        }
    }

    private void navigateCards(KeyCode dir) {
        List<DeviceCard> cardList = new ArrayList<>(cards.values());
        if (cardList.isEmpty()) return;
        int current = -1;
        for (int i = 0; i < cardList.size(); i++) {
            if (cardList.get(i).isFocused()) { current = i; break; }
        }
        int next;
        if (current == -1) {
            next = 0;
        } else if (dir == KeyCode.RIGHT || dir == KeyCode.DOWN) {
            next = Math.min(current + 1, cardList.size() - 1);
        } else {
            next = Math.max(current - 1, 0);
        }
        cardList.get(next).focus();
    }

    private void selectAllCards() {
        for (DeviceCard card : cards.values()) card.setSelected(true);
        selectedPeerIds.addAll(cards.keySet());
        updateSendButton();
    }

    private HBox buildTopBar() {
        HBox bar = new HBox();
        bar.getStyleClass().add("top-bar");
        bar.setAlignment(Pos.CENTER_LEFT);

        Label title = new Label("NetTransfer");
        title.getStyleClass().add("app-title");

        bar.getChildren().add(title);
        return bar;
    }

    private Node buildCenter() {
        VBox center = new VBox(0);
        center.setPadding(new Insets(0, 20, 0, 20));

        Label sectionLabel = new Label("DISPOSITIVOS");
        sectionLabel.getStyleClass().add("section-label");

        cardsPane = new FlowPane();
        cardsPane.setHgap(10);
        cardsPane.setVgap(10);
        cardsPane.setPadding(new Insets(0, 0, 16, 0));

        emptyLabel = new Label("À procura de dispositivos na rede...");
        emptyLabel.getStyleClass().add("empty-label");
        emptyLabel.setMaxWidth(Double.MAX_VALUE);
        emptyLabel.setAlignment(Pos.CENTER);
        emptyLabel.setPadding(new Insets(40, 0, 40, 0));

        StackPane cardsArea = new StackPane(cardsPane, emptyLabel);
        StackPane.setAlignment(emptyLabel, Pos.CENTER);
        cardsPane.setVisible(false);

        ScrollPane scroll = new ScrollPane(cardsArea);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("scroll-pane");
        scroll.setStyle("-fx-border-width:0;");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        center.getChildren().addAll(sectionLabel, scroll);
        return center;
    }

    private Node buildBottom() {
        VBox bottom = new VBox(0);

        // Buttons
        chooseButton = new Button("Selecionar ficheiros  [F]");
        chooseButton.getStyleClass().add("btn-secondary");
        chooseButton.setOnAction(e -> chooseFiles());

        sendButton = new Button("Enviar  [Enter]");
        sendButton.getStyleClass().add("btn-primary");
        sendButton.setDisable(true);
        sendButton.setOnAction(e -> sendToSelected());
        sendButton.setDefaultButton(true);

        Button openDownloadsBtn = new Button("Pasta de downloads");
        openDownloadsBtn.getStyleClass().add("btn-secondary");
        openDownloadsBtn.setOnAction(e -> openFolder(FileTransferService.DOWNLOAD_BASE.toString()));

        filesLabel = new Label("Nenhum ficheiro selecionado  ·  [Esc] limpar");
        filesLabel.getStyleClass().add("files-label");

        HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox btnRow = new HBox(10, chooseButton, sendButton, spacer, openDownloadsBtn);
        btnRow.setAlignment(Pos.CENTER_LEFT);

        HBox bottomBar = new HBox(12);
        bottomBar.getStyleClass().add("bottom-bar");
        bottomBar.setAlignment(Pos.CENTER_LEFT);

        VBox leftBar = new VBox(6, btnRow, filesLabel);
        leftBar.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(leftBar, Priority.ALWAYS);
        bottomBar.getChildren().add(leftBar);

        // Progress area
        Label progressLabel = new Label("TRANSFERÊNCIAS");
        progressLabel.getStyleClass().add("section-label");
        VBox.setMargin(progressLabel, new Insets(0, 20, 0, 20));

        progressContainer = new VBox(8);
        progressContainer.setPadding(new Insets(0, 20, 12, 20));
        progressContainer.getStyleClass().add("progress-area");

        ScrollPane progressScroll = new ScrollPane(progressContainer);
        progressScroll.setFitToWidth(true);
        progressScroll.setPrefHeight(170);
        progressScroll.getStyleClass().add("scroll-pane");
        progressScroll.setStyle("-fx-border-width:0;");

        bottom.getChildren().addAll(bottomBar, progressLabel, progressScroll);
        VBox.setMargin(bottomBar, new Insets(8, 0, 0, 0));
        return bottom;
    }

    private void chooseFiles() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Selecionar ficheiros");
        List<File> files = chooser.showOpenMultipleDialog(stage);
        if (files != null && !files.isEmpty()) addSelectedFiles(files);
    }

    private void addSelectedFiles(List<File> files) {
        selectedFiles.addAll(files);
        long total = selectedFiles.stream().mapToLong(this::sizeOf).sum();
        filesLabel.setText(selectedFiles.size() + " ficheiro(s) selecionado(s)  ·  " + formatSize(total) + "  ·  [Esc] limpar");
        updateSendButton();
    }

    private long sizeOf(File f) {
        if (f.isFile()) return f.length();
        File[] ch = f.listFiles();
        if (ch == null) return 0;
        long s = 0;
        for (File c : ch) s += sizeOf(c);
        return s;
    }

    private void updateSendButton() {
        sendButton.setDisable(selectedPeerIds.isEmpty() || selectedFiles.isEmpty());
    }

    private void sendToSelected() {
        String senderName = DiscoveryService.getUserName();
        List<File> toSend = new ArrayList<>(selectedFiles);
        for (String peerId : new ArrayList<>(selectedPeerIds)) {
            Peer peer = peers.get(peerId);
            if (peer == null) continue;
            String tid = UUID.randomUUID().toString();
            TransferProgressPanel panel = new TransferProgressPanel(peer.name, null, peer.ipAddress, "ENVIAR", null, 0);
            progressPanels.put(tid, panel);
            Platform.runLater(() -> progressContainer.getChildren().add(0, panel.node()));
            FileTransferService.sendFiles(peer, toSend, tid, senderName, this);
        }
        selectedFiles.clear();
        filesLabel.setText("Nenhum ficheiro selecionado  ·  [Esc] limpar");
        for (String id : new ArrayList<>(selectedPeerIds)) {
            DeviceCard card = cards.get(id);
            if (card != null) card.setSelected(false);
        }
        selectedPeerIds.clear();
        updateSendButton();
    }

    @Override
    public void onSendStart(String transferId, String peerName, String peerIp,
                             List<TransferMessage.FileEntry> files, long totalSize) {
        Platform.runLater(() -> {
            TransferProgressPanel panel = progressPanels.get(transferId);
            if (panel != null) panel.setFileDetails(files, totalSize, peerIp);
        });
    }

    public void onPeerDiscovered(Peer peer) {
        Platform.runLater(() -> {
            boolean isNew = !peers.containsKey(peer.id);
            peers.put(peer.id, peer);
            lastSeen.put(peer.id, System.currentTimeMillis());
            if (isNew) TransferLogger.logPeerDiscovered(peer.name, peer.ipAddress, peer.tcpPort);
            DeviceCard card = cards.get(peer.id);
            if (card == null) {
                card = new DeviceCard(peer);
                cards.put(peer.id, card);
                cardsPane.getChildren().add(card.node());
                // Focus first card automatically
                if (cards.size() == 1) card.focus();
            } else {
                card.updatePeer(peer);
            }
            cardsPane.setVisible(true);
            emptyLabel.setVisible(false);
        });
    }

    private void removeStalePeers() {
        long now = System.currentTimeMillis();
        List<String> stale = new ArrayList<>();
        lastSeen.forEach((id, ts) -> { if (now - ts > PEER_TIMEOUT_MS) stale.add(id); });
        for (String id : stale) {
            Peer lost = peers.remove(id);
            lastSeen.remove(id);
            selectedPeerIds.remove(id);
            DeviceCard card = cards.remove(id);
            if (card != null) cardsPane.getChildren().remove(card.node());
            if (lost != null) TransferLogger.logPeerLost(lost.name, lost.ipAddress);
        }
        boolean empty = cards.isEmpty();
        cardsPane.setVisible(!empty);
        emptyLabel.setVisible(empty);
        updateSendButton();
    }

    static void openFolder(String path) {
        new Thread(() -> {
            try { new ProcessBuilder("xdg-open", path).start(); }
            catch (Exception e) {
                try { Desktop.getDesktop().open(new File(path)); } catch (Exception ignored) {}
            }
        }).start();
    }

    static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        double kb = bytes / 1024.0;
        if (kb < 1024) return String.format("%.1f KB", kb);
        double mb = kb / 1024.0;
        if (mb < 1024) return String.format("%.1f MB", mb);
        return String.format("%.2f GB", mb / 1024.0);
    }

    // ── TransferListener ──

    @Override
    public boolean onIncomingRequest(String transferId, String senderName, String senderIp,
                                     List<TransferMessage.FileEntry> files,
                                     long totalSize, int totalFiles) {
        TransferProgressPanel panel = new TransferProgressPanel(
                senderName, null, senderIp, "RECEBER", files, totalSize);
        progressPanels.put(transferId, panel);
        progressContainer.getChildren().add(0, panel.node());

        TransferRequestDialog dlg = new TransferRequestDialog(stage, senderName, senderIp, files, totalSize, totalFiles);
        boolean accepted = dlg.showAndWait();
        panel.setStatus(accepted ? "A receber..." : "Recusado", accepted ? "normal" : "rejected");
        return accepted;
    }

    @Override
    public void onReceiveDir(String transferId, String dirPath) {
        Platform.runLater(() -> {
            TransferProgressPanel panel = progressPanels.get(transferId);
            if (panel != null) panel.setReceiveDir(dirPath);
        });
    }

    @Override
    public void onProgress(String transferId, long transferred, long total, double speedBps) {
        Platform.runLater(() -> {
            TransferProgressPanel panel = progressPanels.get(transferId);
            if (panel != null) panel.updateProgress(transferred, total, speedBps);
        });
    }

    @Override
    public void onStatusChange(String transferId, TransferStatus status) {
        Platform.runLater(() -> {
            TransferProgressPanel panel = progressPanels.get(transferId);
            if (panel != null) {
                String text = switch (status) {
                    case WAITING -> "A aguardar";
                    case TRANSFERRING -> "A transferir...";
                    case DONE -> "Concluído";
                    case REJECTED -> "Recusado";
                    case ERROR -> "Erro";
                };
                String style = switch (status) {
                    case DONE -> "done";
                    case ERROR -> "error";
                    case REJECTED -> "rejected";
                    default -> "normal";
                };
                panel.setStatus(text, style);
            }
        });
    }

    // ── DeviceCard ──

    private class DeviceCard {
        private final String peerId;
        private boolean selected = false;
        private final VBox box;
        private final Label nameLabel;
        private final Label hostLabel;
        private final Label ipLabel;
        private final Label checkLabel;

        DeviceCard(Peer peer) {
            this.peerId = peer.id;
            box = new VBox(3);
            box.getStyleClass().add("device-card");
            box.setAlignment(Pos.TOP_LEFT);
            box.setFocusTraversable(true);

            HBox topRow = new HBox();
            topRow.setAlignment(Pos.CENTER_LEFT);
            Label icon = new Label("○");
            icon.getStyleClass().add("card-icon");
            HBox iconSpacer = new HBox();
            HBox.setHgrow(iconSpacer, Priority.ALWAYS);
            checkLabel = new Label("●");
            checkLabel.getStyleClass().add("card-selected-check");
            checkLabel.setVisible(false);
            topRow.getChildren().addAll(icon, iconSpacer, checkLabel);

            nameLabel = new Label(peer.name);
            nameLabel.getStyleClass().add("card-name");
            hostLabel = new Label(peer.hostName);
            hostLabel.getStyleClass().add("card-host");
            ipLabel = new Label(peer.ipAddress);
            ipLabel.getStyleClass().add("card-ip");

            box.getChildren().addAll(topRow, nameLabel, hostLabel, ipLabel);

            // Mouse
            box.setOnMouseClicked(e -> toggle());

            // Keyboard: Space or Enter toggles selection
            box.setOnKeyPressed(e -> {
                if (e.getCode() == KeyCode.SPACE || e.getCode() == KeyCode.ENTER) {
                    toggle();
                    e.consume();
                }
            });
        }

        void toggle() {
            setSelected(!selected);
            if (selected) selectedPeerIds.add(peerId);
            else selectedPeerIds.remove(peerId);
            updateSendButton();
        }

        void setSelected(boolean value) {
            selected = value;
            if (value) box.getStyleClass().add("selected");
            else box.getStyleClass().remove("selected");
            checkLabel.setVisible(value);
        }

        boolean isFocused() { return box.isFocused(); }
        void focus() { box.requestFocus(); }

        void updatePeer(Peer peer) {
            nameLabel.setText(peer.name);
            hostLabel.setText(peer.hostName);
            ipLabel.setText(peer.ipAddress);
        }

        Node node() { return box; }
    }
}
