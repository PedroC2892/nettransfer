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
import javafx.scene.control.TextArea;
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
import java.io.IOException;
import java.nio.file.Files;
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

    private int focusedCardIndex = -1;

    private FlowPane cardsPane;
    private Label emptyLabel;
    private Label filesLabel;
    private Button sendButton;
    private VBox progressContainer;

    // Tab strip
    private Button tabTransfers;
    private Button tabLogs;
    private StackPane tabContent;
    private ScrollPane transfersScroll;
    private TextArea logArea;

    // Which tab is active: 0 = transfers, 1 = logs
    private int activeTab = 0;

    public MainController(Stage stage) {
        this.stage = stage;
    }

    public void show() {
        BorderPane root = new BorderPane();
        root.getStyleClass().add("root");

        root.setTop(buildTopBar());
        root.setCenter(buildCenter());
        root.setBottom(buildBottom());

        Scene scene = new Scene(root, 920, 680);
        scene.getStylesheets().add(getClass().getResource("app.css").toExternalForm());
        stage.setTitle("NetTransfer");
        stage.setScene(scene);
        stage.setMinWidth(620);
        stage.setMinHeight(500);
        stage.show();

        scene.addEventFilter(KeyEvent.KEY_PRESSED, this::handleGlobalKey);

        // Refresh logs every 2s when log tab is active
        Timeline logRefresh = new Timeline(new KeyFrame(Duration.seconds(2), e -> {
            if (activeTab == 1) refreshLog();
        }));
        logRefresh.setCycleCount(Animation.INDEFINITE);
        logRefresh.play();

        Timeline peerCleanup = new Timeline(new KeyFrame(Duration.seconds(5), e -> removeStalePeers()));
        peerCleanup.setCycleCount(Animation.INDEFINITE);
        peerCleanup.play();
    }

    // ── Keyboard ─────────────────────────────────────────────────────────────

    private void handleGlobalKey(KeyEvent e) {
        // Tab switching: Ctrl+1 / Ctrl+2
        if (e.isControlDown() && e.getCode() == KeyCode.DIGIT1) { switchTab(0); e.consume(); return; }
        if (e.isControlDown() && e.getCode() == KeyCode.DIGIT2) { switchTab(1); e.consume(); return; }

        // Don't intercept arrows when log area has focus (let it scroll)
        if (logArea.isFocused()) return;

        switch (e.getCode()) {
            case F      -> { chooseFiles(); e.consume(); }
            case ENTER  -> { if (!sendButton.isDisabled()) { sendToSelected(); e.consume(); } }
            case ESCAPE -> { selectedFiles.clear(); filesLabel.setText("Nenhum ficheiro selecionado"); updateSendButton(); e.consume(); }
            case LEFT, UP   -> { navigateCards(-1, e.isShiftDown()); e.consume(); }
            case RIGHT, DOWN -> { navigateCards(+1, e.isShiftDown()); e.consume(); }
            case A -> { if (e.isControlDown()) { selectAll(); e.consume(); } }
            default -> {}
        }
    }

    private void navigateCards(int delta, boolean extend) {
        List<DeviceCard> list = new ArrayList<>(cards.values());
        if (list.isEmpty()) return;
        if (focusedCardIndex < 0) focusedCardIndex = 0;
        else focusedCardIndex = Math.max(0, Math.min(list.size() - 1, focusedCardIndex + delta));

        DeviceCard target = list.get(focusedCardIndex);
        if (!extend) {
            for (DeviceCard c : list) c.setSelected(false);
            selectedPeerIds.clear();
        }
        target.setSelected(true);
        target.focus();
        selectedPeerIds.add(target.peerId);
        updateSendButton();
    }

    private void selectAll() {
        for (DeviceCard c : cards.values()) c.setSelected(true);
        selectedPeerIds.addAll(cards.keySet());
        updateSendButton();
    }

    // ── Layout ────────────────────────────────────────────────────────────────

    private HBox buildTopBar() {
        HBox bar = new HBox();
        bar.getStyleClass().add("top-bar");
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setSpacing(12);

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
        cardsPane.setHgap(12);
        cardsPane.setVgap(12);
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

        // ── Action bar ──
        Button chooseBtn = new Button("Selecionar ficheiros  [F]");
        chooseBtn.getStyleClass().add("btn-secondary");
        chooseBtn.setOnAction(e -> chooseFiles());

        sendButton = new Button("Enviar  [Enter]");
        sendButton.getStyleClass().add("btn-primary");
        sendButton.setDisable(true);
        sendButton.setDefaultButton(true);
        sendButton.setOnAction(e -> sendToSelected());

        Button openBtn = new Button("📂 Downloads");
        openBtn.getStyleClass().add("btn-secondary");
        openBtn.setOnAction(e -> openFolder(FileTransferService.DOWNLOAD_BASE.toString()));

        filesLabel = new Label("Nenhum ficheiro selecionado");
        filesLabel.getStyleClass().add("files-label");

        HBox spacer = new HBox();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox btnRow = new HBox(10, chooseBtn, sendButton, spacer, openBtn);
        btnRow.setAlignment(Pos.CENTER_LEFT);

        VBox actionBar = new VBox(6, btnRow, filesLabel);
        actionBar.getStyleClass().add("bottom-bar");

        // ── Tab strip ──
        tabTransfers = new Button("Transferências  [Ctrl+1]");
        tabTransfers.getStyleClass().addAll("tab-btn", "active");
        tabTransfers.setOnAction(e -> switchTab(0));

        tabLogs = new Button("Registos  [Ctrl+2]");
        tabLogs.getStyleClass().add("tab-btn");
        tabLogs.setOnAction(e -> switchTab(1));

        HBox tabStrip = new HBox(tabTransfers, tabLogs);
        tabStrip.getStyleClass().add("tab-strip");

        // ── Transfers panel ──
        progressContainer = new VBox(8);
        progressContainer.setPadding(new Insets(8, 20, 12, 20));
        progressContainer.getStyleClass().add("progress-area");

        transfersScroll = new ScrollPane(progressContainer);
        transfersScroll.setFitToWidth(true);
        transfersScroll.setPrefHeight(220);
        transfersScroll.getStyleClass().add("scroll-pane");
        transfersScroll.setStyle("-fx-border-width:0;");

        // ── Log panel ──
        logArea = new TextArea();
        logArea.getStyleClass().add("log-area");
        logArea.setEditable(false);
        logArea.setWrapText(false);
        logArea.setPrefHeight(220);

        // ── Tab content switcher ──
        tabContent = new StackPane(transfersScroll, logArea);
        logArea.setVisible(false);

        bottom.getChildren().addAll(actionBar, tabStrip, tabContent);
        return bottom;
    }

    private void switchTab(int tab) {
        activeTab = tab;
        tabTransfers.getStyleClass().remove("active");
        tabLogs.getStyleClass().remove("active");
        if (tab == 0) {
            tabTransfers.getStyleClass().add("active");
            transfersScroll.setVisible(true);
            logArea.setVisible(false);
        } else {
            tabLogs.getStyleClass().add("active");
            transfersScroll.setVisible(false);
            logArea.setVisible(true);
            refreshLog();
            logArea.requestFocus();
        }
    }

    private void refreshLog() {
        try {
            String text = Files.exists(TransferLogger.LOG_FILE)
                    ? Files.readString(TransferLogger.LOG_FILE)
                    : "(sem registos ainda)";
            logArea.setText(text);
            logArea.setScrollTop(Double.MAX_VALUE);
        } catch (IOException e) {
            logArea.setText("Erro ao ler o ficheiro de log.");
        }
    }

    // ── Files ─────────────────────────────────────────────────────────────────

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
        long s = 0; for (File c : ch) s += sizeOf(c); return s;
    }

    private void updateSendButton() {
        sendButton.setDisable(selectedPeerIds.isEmpty() || selectedFiles.isEmpty());
    }

    // ── Send ──────────────────────────────────────────────────────────────────

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
            DeviceCard c = cards.get(id); if (c != null) c.setSelected(false);
        }
        selectedPeerIds.clear();
        updateSendButton();
        // Switch to transfers tab automatically
        switchTab(0);
    }

    // ── Peers ─────────────────────────────────────────────────────────────────

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
                cardsPane.setVisible(true);
                if (cards.size() == 1) { focusedCardIndex = 0; card.focus(); }
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
        boolean empty = cards.isEmpty();
        cardsPane.setVisible(!empty);
        emptyLabel.setVisible(empty);
        if (empty) focusedCardIndex = -1;
        else focusedCardIndex = Math.min(focusedCardIndex, cards.size() - 1);
        updateSendButton();
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    static void openFolder(String path) {
        new Thread(() -> {
            try { new ProcessBuilder("xdg-open", path).start(); }
            catch (Exception e) { try { Desktop.getDesktop().open(new File(path)); } catch (Exception ignored) {} }
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
            TransferProgressPanel p = progressPanels.get(transferId);
            if (p != null) p.setFileDetails(files, totalSize, peerIp);
        });
    }

    @Override
    public boolean onIncomingRequest(String transferId, String senderName, String senderIp,
                                     List<TransferMessage.FileEntry> files, long totalSize, int totalFiles) {
        TransferProgressPanel panel = new TransferProgressPanel(senderName, null, senderIp, "RECEBER", files, totalSize);
        progressPanels.put(transferId, panel);
        progressContainer.getChildren().add(0, panel.node());
        switchTab(0);

        TransferRequestDialog dlg = new TransferRequestDialog(stage, senderName, senderIp, files, totalSize, totalFiles);
        boolean accepted = dlg.showAndWait();
        panel.setStatus(accepted ? "A receber..." : "Recusado", accepted ? "normal" : "rejected");
        return accepted;
    }

    @Override
    public void onReceiveDir(String transferId, String dirPath) {
        Platform.runLater(() -> {
            TransferProgressPanel p = progressPanels.get(transferId);
            if (p != null) p.setReceiveDir(dirPath);
        });
    }

    @Override
    public void onProgress(String transferId, long transferred, long total, double speedBps) {
        Platform.runLater(() -> {
            TransferProgressPanel p = progressPanels.get(transferId);
            if (p != null) p.updateProgress(transferred, total, speedBps);
        });
    }

    @Override
    public void onStatusChange(String transferId, TransferStatus status) {
        Platform.runLater(() -> {
            TransferProgressPanel p = progressPanels.get(transferId);
            if (p == null) return;
            String text = switch (status) {
                case WAITING -> "A aguardar";
                case TRANSFERRING -> "A transferir...";
                case DONE -> "Concluído";
                case REJECTED -> "Recusado";
                case ERROR -> "Erro";
            };
            String style = switch (status) {
                case DONE -> "done"; case ERROR -> "error"; case REJECTED -> "rejected"; default -> "normal";
            };
            p.setStatus(text, style);
        });
    }

    // ── DeviceCard ────────────────────────────────────────────────────────────

    private class DeviceCard {
        final String peerId;
        private boolean selected = false;
        private final VBox box;
        private final Label nameLabel, hostLabel, ipLabel, checkLabel;

        DeviceCard(Peer peer) {
            this.peerId = peer.id;
            box = new VBox(4);
            box.getStyleClass().add("device-card");
            box.setAlignment(Pos.TOP_LEFT);
            box.setFocusTraversable(true);

            HBox topRow = new HBox();
            topRow.setAlignment(Pos.CENTER_LEFT);
            Label icon = new Label("💻");
            icon.getStyleClass().add("card-icon");
            HBox sp = new HBox(); HBox.setHgrow(sp, Priority.ALWAYS);
            checkLabel = new Label("✓");
            checkLabel.getStyleClass().add("card-selected-check");
            checkLabel.setVisible(false);
            topRow.getChildren().addAll(icon, sp, checkLabel);

            nameLabel = new Label(peer.name); nameLabel.getStyleClass().add("card-name");
            hostLabel = new Label(peer.hostName); hostLabel.getStyleClass().add("card-host");
            ipLabel   = new Label(peer.ipAddress); ipLabel.getStyleClass().add("card-ip");

            box.getChildren().addAll(topRow, nameLabel, hostLabel, ipLabel);

            box.setOnMouseClicked(e -> {
                List<String> ids = new ArrayList<>(cards.keySet());
                focusedCardIndex = ids.indexOf(peerId);
                toggle();
            });

            box.setOnKeyPressed(e -> {
                if (e.getCode() == KeyCode.SPACE) { toggle(); e.consume(); }
            });
        }

        void toggle() {
            setSelected(!selected);
            if (selected) selectedPeerIds.add(peerId); else selectedPeerIds.remove(peerId);
            updateSendButton();
        }

        void setSelected(boolean v) {
            selected = v;
            if (v) box.getStyleClass().add("selected"); else box.getStyleClass().remove("selected");
            checkLabel.setVisible(v);
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
