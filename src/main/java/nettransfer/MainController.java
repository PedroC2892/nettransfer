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
import javafx.scene.control.ProgressBar;
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
    private final Map<String, ActiveTransfer> activeTransfers = new ConcurrentHashMap<>();

    private int focusedCardIndex = -1;
    private int activeTab = 0; // 0=devices, 1=logs

    // UI refs
    private FlowPane cardsPane;
    private Label emptyLabel;
    private Label filesLabel;
    private Button sendButton;
    private Button navDevices, navLogs;
    private StackPane centerStack;
    private VBox devicesView;
    private TextArea logArea;
    private VBox overlayView;
    private VBox overlayCardContainer;
    private Button overlayDismissBtn;
    private Label overlayHint;

    public MainController(Stage stage) {
        this.stage = stage;
    }

    // ── Startup ──────────────────────────────────────────────────────────────

    public void show() {
        BorderPane root = new BorderPane();
        root.getStyleClass().add("root");
        root.setTop(buildTopBar());
        root.setCenter(buildCenter());

        Scene scene = new Scene(root, 860, 600);
        scene.getStylesheets().add(getClass().getResource("app.css").toExternalForm());
        stage.setTitle("NetTransfer");
        stage.setScene(scene);
        stage.setMinWidth(580);
        stage.setMinHeight(440);
        stage.show();

        scene.addEventFilter(KeyEvent.KEY_PRESSED, this::handleKey);

        Timeline logRefresh = new Timeline(new KeyFrame(Duration.seconds(2),
                e -> { if (activeTab == 1) refreshLog(); }));
        logRefresh.setCycleCount(Animation.INDEFINITE);
        logRefresh.play();

        Timeline peerCleanup = new Timeline(new KeyFrame(Duration.seconds(5), e -> removeStalePeers()));
        peerCleanup.setCycleCount(Animation.INDEFINITE);
        peerCleanup.play();
    }

    // ── Layout ────────────────────────────────────────────────────────────────

    private HBox buildTopBar() {
        Label title = new Label("NetTransfer");
        title.getStyleClass().add("app-title");

        HBox spacer = new HBox(); HBox.setHgrow(spacer, Priority.ALWAYS);

        navDevices = new Button("Dispositivos  [Ctrl+1]");
        navDevices.getStyleClass().addAll("nav-tab", "active");
        navDevices.setOnAction(e -> switchTab(0));

        navLogs = new Button("Registos  [Ctrl+2]");
        navLogs.getStyleClass().add("nav-tab");
        navLogs.setOnAction(e -> switchTab(1));

        HBox bar = new HBox(0, title, spacer, navDevices, navLogs);
        bar.getStyleClass().add("top-bar");
        bar.setAlignment(Pos.CENTER_LEFT);
        return bar;
    }

    private Node buildCenter() {
        // Devices view
        devicesView = buildDevicesView();

        // Log view
        logArea = new TextArea();
        logArea.getStyleClass().add("log-area");
        logArea.setEditable(false);
        logArea.setWrapText(false);
        logArea.setText("(sem registos ainda)");

        VBox logsView = new VBox(logArea);
        logsView.setFillWidth(true);
        VBox.setVgrow(logArea, Priority.ALWAYS);
        logsView.setVisible(false);

        // Overlay (transfer in progress)
        overlayView = buildOverlay();
        overlayView.setVisible(false);

        centerStack = new StackPane(devicesView, logsView, overlayView);
        // Store reference to logs view so we can toggle it
        centerStack.getChildren().get(1).setUserData("logs");

        return centerStack;
    }

    private VBox buildDevicesView() {
        // Devices section
        Label devLabel = new Label("DISPOSITIVOS");
        devLabel.getStyleClass().add("section-label");

        cardsPane = new FlowPane();
        cardsPane.setHgap(10);
        cardsPane.setVgap(10);
        cardsPane.setPadding(new Insets(0, 0, 16, 0));
        cardsPane.setVisible(false);

        emptyLabel = new Label("À procura de dispositivos na rede...");
        emptyLabel.getStyleClass().add("empty-label");
        emptyLabel.setMaxWidth(Double.MAX_VALUE);
        emptyLabel.setAlignment(Pos.CENTER);
        emptyLabel.setPadding(new Insets(50, 0, 50, 0));

        StackPane cardsArea = new StackPane(cardsPane, emptyLabel);
        StackPane.setAlignment(emptyLabel, Pos.CENTER);

        ScrollPane cardsScroll = new ScrollPane(cardsArea);
        cardsScroll.setFitToWidth(true);
        cardsScroll.getStyleClass().add("scroll-pane");
        cardsScroll.setStyle("-fx-border-width:0;");
        VBox.setVgrow(cardsScroll, Priority.ALWAYS);

        VBox top = new VBox(0, devLabel, cardsScroll);
        top.setPadding(new Insets(0, 20, 0, 20));
        VBox.setVgrow(top, Priority.ALWAYS);

        // Action bar
        Node actionBar = buildActionBar();

        VBox view = new VBox(0, top, actionBar);
        VBox.setVgrow(top, Priority.ALWAYS);
        return view;
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

        Button openBtn = new Button("Downloads");
        openBtn.getStyleClass().add("btn-secondary");
        openBtn.setOnAction(e -> openFolder(FileTransferService.DOWNLOAD_BASE.toString()));

        filesLabel = new Label("Nenhum ficheiro selecionado");
        filesLabel.getStyleClass().add("files-label");

        HBox sp = new HBox(); HBox.setHgrow(sp, Priority.ALWAYS);
        HBox btnRow = new HBox(10, chooseBtn, sendButton, sp, openBtn);
        btnRow.setAlignment(Pos.CENTER_LEFT);

        VBox bar = new VBox(6, btnRow, filesLabel);
        bar.getStyleClass().add("action-bar");
        return bar;
    }

    private VBox buildOverlay() {
        // Header
        Label titleLabel = new Label("TRANSFERÊNCIA");
        titleLabel.getStyleClass().add("overlay-title");
        overlayHint = new Label("");
        overlayHint.getStyleClass().add("overlay-counter");
        HBox hSp = new HBox(); HBox.setHgrow(hSp, Priority.ALWAYS);
        HBox header = new HBox(titleLabel, hSp, overlayHint);
        header.getStyleClass().add("overlay-header");
        header.setAlignment(Pos.CENTER_LEFT);

        // Cards scroll
        overlayCardContainer = new VBox(12);
        overlayCardContainer.setPadding(new Insets(20, 20, 20, 20));
        ScrollPane scroll = new ScrollPane(overlayCardContainer);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("scroll-pane");
        scroll.setStyle("-fx-border-width:0;");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        // Footer
        overlayDismissBtn = new Button("Fechar  [Esc]");
        overlayDismissBtn.getStyleClass().add("btn-primary");
        overlayDismissBtn.setDisable(true);
        overlayDismissBtn.setOnAction(e -> dismissOverlay());

        HBox fSp = new HBox(); HBox.setHgrow(fSp, Priority.ALWAYS);
        HBox footer = new HBox(12, fSp, overlayDismissBtn);
        footer.getStyleClass().add("overlay-footer");
        footer.setAlignment(Pos.CENTER_RIGHT);

        VBox overlay = new VBox(0, header, scroll, footer);
        overlay.getStyleClass().add("overlay");
        VBox.setVgrow(scroll, Priority.ALWAYS);
        overlay.setFillWidth(true);
        return overlay;
    }

    // ── Tab / overlay switching ───────────────────────────────────────────────

    private void switchTab(int tab) {
        if (overlayView.isVisible()) return; // overlay blocks tab switching
        activeTab = tab;
        navDevices.getStyleClass().remove("active");
        navLogs.getStyleClass().remove("active");
        devicesView.setVisible(tab == 0);
        centerStack.getChildren().get(1).setVisible(tab == 1); // logsView
        if (tab == 0) {
            navDevices.getStyleClass().add("active");
        } else {
            navLogs.getStyleClass().add("active");
            refreshLog();
            logArea.requestFocus();
        }
    }

    private void showOverlay() {
        overlayView.setVisible(true);
        overlayDismissBtn.setDisable(true);
        overlayHint.setText("Em curso...");
        overlayDismissBtn.requestFocus();
    }

    private void dismissOverlay() {
        overlayView.setVisible(false);
        activeTransfers.clear();
        overlayCardContainer.getChildren().clear();
        // Return focus to cards
        if (!cards.isEmpty()) {
            new ArrayList<>(cards.values()).get(Math.max(0, focusedCardIndex)).focus();
        }
    }

    private void checkDismissable() {
        boolean allDone = activeTransfers.values().stream().allMatch(ActiveTransfer::isDone);
        overlayDismissBtn.setDisable(!allDone);
        long done = activeTransfers.values().stream().filter(ActiveTransfer::isDone).count();
        long total = activeTransfers.size();
        if (allDone) {
            overlayHint.setText("Concluído — " + done + "/" + total);
        } else {
            overlayHint.setText(done + "/" + total + " concluídas");
        }
    }

    private void addToOverlay(String transferId, String peerName, String peerIp, String direction,
                               List<TransferMessage.FileEntry> files, long totalSize) {
        ActiveTransfer at = new ActiveTransfer(transferId, peerName, peerIp, direction, files, totalSize);
        activeTransfers.put(transferId, at);
        overlayCardContainer.getChildren().add(at.card);
        if (!overlayView.isVisible()) showOverlay();
    }

    // ── Keyboard ─────────────────────────────────────────────────────────────

    private void handleKey(KeyEvent e) {
        if (e.isControlDown() && e.getCode() == KeyCode.DIGIT1) { switchTab(0); e.consume(); return; }
        if (e.isControlDown() && e.getCode() == KeyCode.DIGIT2) { switchTab(1); e.consume(); return; }

        // Overlay is showing
        if (overlayView.isVisible()) {
            if ((e.getCode() == KeyCode.ESCAPE || e.getCode() == KeyCode.ENTER)
                    && !overlayDismissBtn.isDisabled()) {
                dismissOverlay();
                e.consume();
            }
            return; // block all other keys when overlay active
        }

        // Log view has its own scroll — don't intercept arrows
        if (activeTab == 1 && logArea.isFocused()) return;

        switch (e.getCode()) {
            case F      -> { chooseFiles(); e.consume(); }
            case ENTER  -> { if (!sendButton.isDisabled()) { sendToSelected(); e.consume(); } }
            case ESCAPE -> { selectedFiles.clear(); filesLabel.setText("Nenhum ficheiro selecionado"); updateSendButton(); e.consume(); }
            case LEFT,  UP   -> { navigateCards(-1, e.isShiftDown()); e.consume(); }
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

    // ── Send ─────────────────────────────────────────────────────────────────

    private void sendToSelected() {
        String senderName = DiscoveryService.getUserName();
        List<File> toSend = new ArrayList<>(selectedFiles);
        for (String peerId : new ArrayList<>(selectedPeerIds)) {
            Peer peer = peers.get(peerId);
            if (peer == null) continue;
            String tid = UUID.randomUUID().toString();
            // Overlay entry created immediately (file list filled in onSendStart)
            addToOverlay(tid, peer.name, peer.ipAddress, "ENVIAR", null, 0);
            FileTransferService.sendFiles(peer, toSend, tid, senderName, this);
        }
        selectedFiles.clear();
        filesLabel.setText("Nenhum ficheiro selecionado");
        for (String id : new ArrayList<>(selectedPeerIds)) {
            DeviceCard c = cards.get(id); if (c != null) c.setSelected(false);
        }
        selectedPeerIds.clear();
        updateSendButton();
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
                cardsPane.setVisible(true);
                emptyLabel.setVisible(false);
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
            Peer lost = peers.remove(id); lastSeen.remove(id); selectedPeerIds.remove(id);
            DeviceCard card = cards.remove(id);
            if (card != null) cardsPane.getChildren().remove(card.node());
            if (lost != null) TransferLogger.logPeerLost(lost.name, lost.ipAddress);
        }
        boolean empty = cards.isEmpty();
        cardsPane.setVisible(!empty); emptyLabel.setVisible(empty);
        if (empty) focusedCardIndex = -1; else focusedCardIndex = Math.min(focusedCardIndex, cards.size()-1);
        updateSendButton();
    }

    // ── Logs ─────────────────────────────────────────────────────────────────

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
            ActiveTransfer at = activeTransfers.get(transferId);
            if (at != null) at.setFiles(files, totalSize, peerIp);
        });
    }

    @Override
    public boolean onIncomingRequest(String transferId, String senderName, String senderIp,
                                     List<TransferMessage.FileEntry> files, long totalSize, int totalFiles) {
        // Show the transfer entry immediately (shows overlay if not shown)
        addToOverlay(transferId, senderName, senderIp, "RECEBER", files, totalSize);

        TransferRequestDialog dlg = new TransferRequestDialog(stage, senderName, senderIp, files, totalSize, totalFiles);
        boolean accepted = dlg.showAndWait();

        ActiveTransfer at = activeTransfers.get(transferId);
        if (at != null) at.setStatus(accepted ? TransferStatus.TRANSFERRING : TransferStatus.REJECTED);
        return accepted;
    }

    @Override
    public void onReceiveDir(String transferId, String dirPath) {
        Platform.runLater(() -> {
            ActiveTransfer at = activeTransfers.get(transferId);
            if (at != null) at.receiveDir = dirPath;
        });
    }

    @Override
    public void onProgress(String transferId, long transferred, long total, double speedBps) {
        Platform.runLater(() -> {
            ActiveTransfer at = activeTransfers.get(transferId);
            if (at != null) at.updateProgress(transferred, total, speedBps);
        });
    }

    @Override
    public void onStatusChange(String transferId, TransferStatus status) {
        Platform.runLater(() -> {
            ActiveTransfer at = activeTransfers.get(transferId);
            if (at != null) at.setStatus(status);
            checkDismissable();
        });
    }

    // ── ActiveTransfer ────────────────────────────────────────────────────────

    private class ActiveTransfer {
        final String transferId;
        String peerName, peerIp, direction;
        long totalSize;
        TransferStatus status = TransferStatus.WAITING;
        String receiveDir;

        final VBox card;
        private final ProgressBar bar;
        private final Label pctLabel, detailLabel, statusLabel;
        private final Button openBtn;
        private final VBox fileListBox;
        private final Label peerNameLabel, peerIpLabel;

        ActiveTransfer(String id, String peerName, String peerIp, String direction,
                       List<TransferMessage.FileEntry> files, long totalSize) {
            this.transferId = id;
            this.peerName = peerName;
            this.peerIp = peerIp != null ? peerIp : "";
            this.direction = direction;
            this.totalSize = totalSize;

            card = new VBox(12);
            card.getStyleClass().add("overlay-card");

            // Direction + peer name
            String dirStr = "ENVIAR".equals(direction) ? "↑" : "↓";
            Label dirLabel = new Label(dirStr);
            dirLabel.getStyleClass().add("overlay-direction");

            peerNameLabel = new Label(peerName);
            peerNameLabel.getStyleClass().add("overlay-peer-name");

            peerIpLabel = new Label(this.peerIp);
            peerIpLabel.getStyleClass().add("overlay-peer-ip");

            HBox nameRow = new HBox(8, dirLabel, peerNameLabel);
            nameRow.setAlignment(Pos.CENTER_LEFT);
            VBox.setMargin(peerIpLabel, new Insets(0, 0, 0, 24));

            VBox headerBox = new VBox(3, nameRow, peerIpLabel);

            // File list
            fileListBox = new VBox(3);
            if (files != null) populateFiles(files);

            // Progress
            bar = new ProgressBar(0);
            bar.setMaxWidth(Double.MAX_VALUE);
            bar.getStyleClass().add("progress-bar");

            pctLabel = new Label("—");
            pctLabel.getStyleClass().add("overlay-pct");
            pctLabel.setMinWidth(36);

            HBox progressRow = new HBox(10, bar, pctLabel);
            progressRow.setAlignment(Pos.CENTER_LEFT);
            HBox.setHgrow(bar, Priority.ALWAYS);

            detailLabel = new Label("");
            detailLabel.getStyleClass().add("overlay-detail");

            // Status + open button
            statusLabel = new Label("A aguardar...");
            statusLabel.getStyleClass().add("overlay-status");

            openBtn = new Button("Abrir pasta");
            openBtn.getStyleClass().add("btn-secondary");
            openBtn.setStyle("-fx-font-size:11px; -fx-padding: 4 12 4 12;");
            openBtn.setVisible(false);
            openBtn.setOnAction(e -> { if (receiveDir != null) openFolder(receiveDir); });

            HBox sp = new HBox(); HBox.setHgrow(sp, Priority.ALWAYS);
            HBox statusRow = new HBox(8, statusLabel, sp, openBtn);
            statusRow.setAlignment(Pos.CENTER_LEFT);

            card.getChildren().addAll(headerBox, fileListBox, progressRow, detailLabel, statusRow);
        }

        private void populateFiles(List<TransferMessage.FileEntry> files) {
            fileListBox.getChildren().clear();
            int shown = Math.min(files.size(), 8);
            for (int i = 0; i < shown; i++) {
                TransferMessage.FileEntry f = files.get(i);
                HBox row = new HBox();
                row.setAlignment(Pos.CENTER_LEFT);
                Label fname = new Label(f.isDirectory ? "▶  " + f.name : f.name);
                fname.getStyleClass().add("overlay-file-name");
                HBox sp = new HBox(); HBox.setHgrow(sp, Priority.ALWAYS);
                Label fsize = new Label(f.isDirectory ? "pasta" : formatSize(f.size));
                fsize.getStyleClass().add("overlay-file-size");
                row.getChildren().addAll(fname, sp, fsize);
                fileListBox.getChildren().add(row);
            }
            if (files.size() > 8) {
                Label more = new Label("+ " + (files.size() - 8) + " ficheiro(s)");
                more.getStyleClass().add("overlay-file-size");
                fileListBox.getChildren().add(more);
            }
        }

        void setFiles(List<TransferMessage.FileEntry> files, long total, String ip) {
            this.totalSize = total;
            if (ip != null) { this.peerIp = ip; peerIpLabel.setText(ip); }
            if (files != null) populateFiles(files);
        }

        void updateProgress(long transferred, long total, double speedBps) {
            double pct = total > 0 ? (double) transferred / total : 0;
            bar.setProgress(pct);
            pctLabel.setText(String.format("%.0f%%", pct * 100));
            double speedMB = speedBps / (1024.0 * 1024.0);
            String eta = "—";
            if (speedBps > 0 && total > transferred) {
                long secs = (long) ((total - transferred) / speedBps);
                eta = secs < 60 ? secs + "s" : (secs / 60) + "m " + (secs % 60) + "s";
            }
            detailLabel.setText(String.format("%s / %s  ·  %.1f MB/s  ·  ~%s restante",
                    formatSize(transferred), formatSize(total), speedMB, eta));
        }

        void setStatus(TransferStatus s) {
            this.status = s;
            statusLabel.getStyleClass().removeAll("done", "error", "rejected");
            String text = switch (s) {
                case WAITING -> "A aguardar...";
                case TRANSFERRING -> "A transferir...";
                case DONE -> "✓  Concluído";
                case REJECTED -> "—  Recusado";
                case ERROR -> "✗  Erro";
            };
            statusLabel.setText(text);
            if (s == TransferStatus.DONE) {
                statusLabel.getStyleClass().add("done");
                bar.setProgress(1.0);
                pctLabel.setText("100%");
                openBtn.setVisible(receiveDir != null);
            } else if (s == TransferStatus.ERROR) {
                statusLabel.getStyleClass().add("error");
            } else if (s == TransferStatus.REJECTED) {
                statusLabel.getStyleClass().add("rejected");
            }
            checkDismissable();
        }

        boolean isDone() {
            return status == TransferStatus.DONE
                    || status == TransferStatus.ERROR
                    || status == TransferStatus.REJECTED;
        }
    }

    // ── DeviceCard ────────────────────────────────────────────────────────────

    private class DeviceCard {
        final String peerId;
        private boolean selected = false;
        private final VBox box;
        private final Label nameLabel, hostLabel, ipLabel, checkLabel;

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
            HBox sp = new HBox(); HBox.setHgrow(sp, Priority.ALWAYS);
            checkLabel = new Label("●");
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
