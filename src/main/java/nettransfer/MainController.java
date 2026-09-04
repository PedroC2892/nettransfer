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
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
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

    // Tracks which card has keyboard focus (index into cards values)
    private int focusedCardIndex = -1;

    private FlowPane cardsPane;
    private Label emptyLabel;
    private Label filesLabel;
    private Button sendButton;
    private VBox progressContainer;

    public MainController(Stage stage) {
        this.stage = stage;
    }

    public void show() {
        // Single VBox with everything — flows naturally, scrolls as one unit
        VBox content = new VBox(0);
        content.getStyleClass().add("root");

        // Top bar
        content.getChildren().add(buildTopBar());

        // Devices section
        Label devicesLabel = new Label("DISPOSITIVOS");
        devicesLabel.getStyleClass().add("section-label");
        VBox.setMargin(devicesLabel, new Insets(0, 20, 0, 20));

        cardsPane = new FlowPane();
        cardsPane.setHgap(10);
        cardsPane.setVgap(10);
        cardsPane.setPadding(new Insets(0, 20, 0, 20));

        emptyLabel = new Label("À procura de dispositivos na rede...");
        emptyLabel.getStyleClass().add("empty-label");
        emptyLabel.setPadding(new Insets(30, 20, 30, 20));
        emptyLabel.setMaxWidth(Double.MAX_VALUE);

        content.getChildren().addAll(devicesLabel, cardsPane, emptyLabel);

        // Action bar — right below cards, always
        content.getChildren().add(buildActionBar());

        // Transfers section
        Label transfersLabel = new Label("TRANSFERÊNCIAS");
        transfersLabel.getStyleClass().add("section-label");
        VBox.setMargin(transfersLabel, new Insets(0, 20, 0, 20));

        progressContainer = new VBox(8);
        progressContainer.setPadding(new Insets(0, 20, 20, 20));

        content.getChildren().addAll(transfersLabel, progressContainer);

        // Wrap everything in a single scroll pane
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("scroll-pane");
        scroll.setStyle("-fx-border-width:0;");
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

        Scene scene = new Scene(scroll, 860, 620);
        scene.getStylesheets().add(getClass().getResource("app.css").toExternalForm());
        stage.setTitle("NetTransfer");
        stage.setScene(scene);
        stage.setMinWidth(580);
        stage.setMinHeight(420);
        stage.show();

        scene.addEventFilter(KeyEvent.KEY_PRESSED, this::handleGlobalKey);

        Timeline cleanup = new Timeline(new KeyFrame(Duration.seconds(5), e -> removeStalePeers()));
        cleanup.setCycleCount(Animation.INDEFINITE);
        cleanup.play();
    }

    // ── Global keyboard handler ──────────────────────────────────────────────

    private void handleGlobalKey(KeyEvent e) {
        switch (e.getCode()) {
            case F -> { chooseFiles(); e.consume(); }
            case ENTER -> {
                // Enter sends if possible, otherwise falls through to button default
                if (!sendButton.isDisabled() && !anyButtonFocused()) {
                    sendToSelected();
                    e.consume();
                }
            }
            case ESCAPE -> {
                selectedFiles.clear();
                filesLabel.setText("Nenhum ficheiro selecionado");
                updateSendButton();
                e.consume();
            }
            case LEFT, UP -> { navigateAndSelect(-1, e.isShiftDown()); e.consume(); }
            case RIGHT, DOWN -> { navigateAndSelect(+1, e.isShiftDown()); e.consume(); }
            case A -> {
                if (e.isControlDown()) { selectAllCards(); e.consume(); }
            }
            default -> {}
        }
    }

    private boolean anyButtonFocused() {
        return sendButton.isFocused();
    }

    // move = -1 or +1; extend = Shift held (adds to selection instead of replacing)
    private void navigateAndSelect(int move, boolean extend) {
        List<DeviceCard> cardList = new ArrayList<>(cards.values());
        if (cardList.isEmpty()) return;

        if (focusedCardIndex < 0) focusedCardIndex = 0;
        else focusedCardIndex = Math.max(0, Math.min(cardList.size() - 1, focusedCardIndex + move));

        DeviceCard target = cardList.get(focusedCardIndex);

        if (!extend) {
            // Single select: deselect all, select this one
            for (DeviceCard c : cardList) c.setSelected(false);
            selectedPeerIds.clear();
        }

        target.selectAndFocus();
        selectedPeerIds.add(target.peerId);
        updateSendButton();
    }

    private void selectAllCards() {
        for (DeviceCard card : cards.values()) card.setSelected(true);
        selectedPeerIds.addAll(cards.keySet());
        updateSendButton();
    }

    // ── UI builders ──────────────────────────────────────────────────────────

    private HBox buildTopBar() {
        HBox bar = new HBox();
        bar.getStyleClass().add("top-bar");
        bar.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label("NetTransfer");
        title.getStyleClass().add("app-title");
        bar.getChildren().add(title);
        return bar;
    }

    private Node buildActionBar() {
        Button chooseBtn = new Button("Selecionar ficheiros  [F]");
        chooseBtn.getStyleClass().add("btn-secondary");
        chooseBtn.setOnAction(e -> chooseFiles());

        sendButton = new Button("Enviar  [Enter]");
        sendButton.getStyleClass().add("btn-primary");
        sendButton.setDisable(true);
        sendButton.setDefaultButton(true);
        sendButton.setOnAction(e -> sendToSelected());

        Button openBtn = new Button("Pasta de downloads");
        openBtn.getStyleClass().add("btn-secondary");
        openBtn.setOnAction(e -> openFolder(FileTransferService.DOWNLOAD_BASE.toString()));

        filesLabel = new Label("Nenhum ficheiro selecionado");
        filesLabel.getStyleClass().add("files-label");

        HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox btnRow = new HBox(10, chooseBtn, sendButton, spacer, openBtn);
        btnRow.setAlignment(Pos.CENTER_LEFT);

        VBox bar = new VBox(6, btnRow, filesLabel);
        bar.getStyleClass().add("bottom-bar");
        bar.setPadding(new Insets(12, 20, 12, 20));
        VBox.setMargin(bar, new Insets(10, 0, 0, 0));
        return bar;
    }

    // ── File selection ───────────────────────────────────────────────────────

    private void chooseFiles() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Selecionar ficheiros");
        List<File> files = chooser.showOpenMultipleDialog(stage);
        if (files != null && !files.isEmpty()) addSelectedFiles(files);
    }

    private void addSelectedFiles(List<File> files) {
        selectedFiles.addAll(files);
        long total = selectedFiles.stream().mapToLong(this::sizeOf).sum();
        filesLabel.setText(selectedFiles.size() + " ficheiro(s)  ·  " + formatSize(total) + "  ·  [Esc] limpar");
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

    // ── Send ─────────────────────────────────────────────────────────────────

    private void sendToSelected() {
        String senderName = DiscoveryService.getUserName();
        List<File> toSend = new ArrayList<>(selectedFiles);
        for (String peerId : new ArrayList<>(selectedPeerIds)) {
            Peer peer = peers.get(peerId);
            if (peer == null) continue;
            String tid = UUID.randomUUID().toString();
            TransferProgressPanel panel = new TransferProgressPanel(peer.name, null, peer.ipAddress, "ENVIAR", null, 0);
            progressPanels.put(tid, panel);
            progressContainer.getChildren().add(0, panel.node());
            FileTransferService.sendFiles(peer, toSend, tid, senderName, this);
        }
        selectedFiles.clear();
        filesLabel.setText("Nenhum ficheiro selecionado");
        for (String id : new ArrayList<>(selectedPeerIds)) {
            DeviceCard card = cards.get(id);
            if (card != null) card.setSelected(false);
        }
        selectedPeerIds.clear();
        updateSendButton();
    }

    // ── Peer discovery ────────────────────────────────────────────────────────

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
                emptyLabel.setVisible(false);
                emptyLabel.setManaged(false);
                // Auto-focus first card
                if (cards.size() == 1) {
                    focusedCardIndex = 0;
                    card.focus();
                }
            } else {
                card.updatePeer(peer);
            }
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
        if (cards.isEmpty()) {
            emptyLabel.setVisible(true);
            emptyLabel.setManaged(true);
            focusedCardIndex = -1;
        } else {
            focusedCardIndex = Math.min(focusedCardIndex, cards.size() - 1);
        }
        updateSendButton();
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

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

    // ── TransferListener ──────────────────────────────────────────────────────

    @Override
    public void onSendStart(String transferId, String peerName, String peerIp,
                             List<TransferMessage.FileEntry> files, long totalSize) {
        Platform.runLater(() -> {
            TransferProgressPanel panel = progressPanels.get(transferId);
            if (panel != null) panel.setFileDetails(files, totalSize, peerIp);
        });
    }

    @Override
    public boolean onIncomingRequest(String transferId, String senderName, String senderIp,
                                     List<TransferMessage.FileEntry> files,
                                     long totalSize, int totalFiles) {
        TransferProgressPanel panel = new TransferProgressPanel(senderName, null, senderIp, "RECEBER", files, totalSize);
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

    // ── DeviceCard ────────────────────────────────────────────────────────────

    private class DeviceCard {
        final String peerId;
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
            HBox sp = new HBox();
            HBox.setHgrow(sp, Priority.ALWAYS);
            checkLabel = new Label("●");
            checkLabel.getStyleClass().add("card-selected-check");
            checkLabel.setVisible(false);
            topRow.getChildren().addAll(icon, sp, checkLabel);

            nameLabel = new Label(peer.name);
            nameLabel.getStyleClass().add("card-name");
            hostLabel = new Label(peer.hostName);
            hostLabel.getStyleClass().add("card-host");
            ipLabel = new Label(peer.ipAddress);
            ipLabel.getStyleClass().add("card-ip");

            box.getChildren().addAll(topRow, nameLabel, hostLabel, ipLabel);

            // Mouse click: toggle this card (multi-select)
            box.setOnMouseClicked(e -> {
                // Update focusedCardIndex
                List<String> ids = new ArrayList<>(cards.keySet());
                focusedCardIndex = ids.indexOf(peerId);
                toggle();
            });

            // Space on focused card toggles (multi-select)
            box.setOnKeyPressed(e -> {
                if (e.getCode() == KeyCode.SPACE) {
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

        void selectAndFocus() {
            setSelected(true);
            box.requestFocus();
        }

        void focus() { box.requestFocus(); }

        void updatePeer(Peer peer) {
            nameLabel.setText(peer.name);
            hostLabel.setText(peer.hostName);
            ipLabel.setText(peer.ipAddress);
        }

        Node node() { return box; }
    }
}
