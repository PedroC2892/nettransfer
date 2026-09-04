package nettransfer;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.TransferHandler;
import javax.swing.border.Border;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MainFrame extends JFrame implements TransferListener {
    private static final long PEER_TIMEOUT_MS = 15000;

    private final Map<String, Peer> peers = new LinkedHashMap<>();
    private final Map<String, Long> lastSeen = new HashMap<>();
    private final Map<String, DeviceCard> cards = new HashMap<>();
    private final Set<String> selectedPeerIds = new LinkedHashSet<>();
    private final List<File> selectedFiles = new ArrayList<>();
    private final Map<String, TransferProgressPanel> progressPanels = new ConcurrentHashMap<>();

    private final JPanel cardsPanel;
    private final JLabel emptyLabel;
    private final JLabel filesLabel;
    private final JButton sendButton;
    private final JPanel progressContainer;

    public MainFrame() {
        super("NetTransfer");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(900, 650);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        cardsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 12));
        emptyLabel = new JLabel("À procura de dispositivos...", SwingConstants.CENTER);
        emptyLabel.setFont(emptyLabel.getFont().deriveFont(16f));

        JPanel cardsWrapper = new JPanel(new BorderLayout());
        cardsWrapper.add(cardsPanel, BorderLayout.NORTH);
        cardsWrapper.add(emptyLabel, BorderLayout.CENTER);
        JScrollPane cardsScroll = new JScrollPane(cardsWrapper);
        cardsScroll.setBorder(BorderFactory.createTitledBorder("Dispositivos"));

        filesLabel = new JLabel("Nenhum ficheiro selecionado");
        sendButton = new JButton("Enviar");
        sendButton.setEnabled(false);
        sendButton.addActionListener(e -> sendToSelectedPeers());

        JButton chooseButton = new JButton("Selecionar ficheiros");
        chooseButton.addActionListener(e -> chooseFiles());

        JPanel dropZone = new JPanel(new BorderLayout());
        dropZone.setBorder(BorderFactory.createDashedBorder(Color.GRAY));
        dropZone.setPreferredSize(new Dimension(0, 60));
        JLabel dropLabel = new JLabel("Arraste ficheiros ou pastas para aqui", SwingConstants.CENTER);
        dropZone.add(dropLabel, BorderLayout.CENTER);
        dropZone.setTransferHandler(new FileDropHandler());

        JPanel controlPanel = new JPanel();
        controlPanel.setLayout(new BoxLayout(controlPanel, BoxLayout.Y_AXIS));
        JPanel buttonsRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttonsRow.add(chooseButton);
        buttonsRow.add(sendButton);
        controlPanel.add(dropZone);
        controlPanel.add(buttonsRow);
        controlPanel.add(filesLabel);

        progressContainer = new JPanel();
        progressContainer.setLayout(new BoxLayout(progressContainer, BoxLayout.Y_AXIS));
        JScrollPane progressScroll = new JScrollPane(progressContainer);
        progressScroll.setBorder(BorderFactory.createTitledBorder("Transferencias"));
        progressScroll.setPreferredSize(new Dimension(0, 200));

        JPanel southPanel = new JPanel(new BorderLayout());
        southPanel.add(controlPanel, BorderLayout.NORTH);
        southPanel.add(progressScroll, BorderLayout.CENTER);

        add(cardsScroll, BorderLayout.CENTER);
        add(southPanel, BorderLayout.SOUTH);

        Timer timeoutTimer = new Timer(5000, e -> removeStalePeers());
        timeoutTimer.start();
    }

    public void onPeerDiscovered(Peer peer) {
        SwingUtilities.invokeLater(() -> {
            peers.put(peer.id, peer);
            lastSeen.put(peer.id, System.currentTimeMillis());
            DeviceCard card = cards.get(peer.id);
            if (card == null) {
                card = new DeviceCard(peer);
                cards.put(peer.id, card);
                cardsPanel.add(card);
            } else {
                card.updatePeer(peer);
            }
            emptyLabel.setVisible(false);
            cardsPanel.revalidate();
            cardsPanel.repaint();
        });
    }

    private void removeStalePeers() {
        long now = System.currentTimeMillis();
        List<String> stale = new ArrayList<>();
        for (Map.Entry<String, Long> e : lastSeen.entrySet()) {
            if (now - e.getValue() > PEER_TIMEOUT_MS) {
                stale.add(e.getKey());
            }
        }
        if (stale.isEmpty()) {
            return;
        }
        for (String id : stale) {
            peers.remove(id);
            lastSeen.remove(id);
            selectedPeerIds.remove(id);
            DeviceCard card = cards.remove(id);
            if (card != null) {
                cardsPanel.remove(card);
            }
        }
        cardsPanel.revalidate();
        cardsPanel.repaint();
        emptyLabel.setVisible(cards.isEmpty());
        updateSendButtonState();
    }

    private void chooseFiles() {
        JFileChooser chooser = new JFileChooser();
        chooser.setMultiSelectionEnabled(true);
        chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            addSelectedFiles(Arrays.asList(chooser.getSelectedFiles()));
        }
    }

    private void addSelectedFiles(List<File> files) {
        selectedFiles.addAll(files);
        long totalSize = selectedFiles.stream().mapToLong(this::sizeOf).sum();
        filesLabel.setText(selectedFiles.size() + " item(s) selecionado(s) - " + formatSize(totalSize));
        updateSendButtonState();
    }

    private long sizeOf(File f) {
        if (f.isFile()) {
            return f.length();
        }
        File[] children = f.listFiles();
        if (children == null) {
            return 0;
        }
        long total = 0;
        for (File c : children) {
            total += sizeOf(c);
        }
        return total;
    }

    static String formatSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        double kb = bytes / 1024.0;
        if (kb < 1024) {
            return String.format("%.1f KB", kb);
        }
        double mb = kb / 1024.0;
        if (mb < 1024) {
            return String.format("%.1f MB", mb);
        }
        return String.format("%.2f GB", mb / 1024.0);
    }

    private void updateSendButtonState() {
        sendButton.setEnabled(!selectedPeerIds.isEmpty() && !selectedFiles.isEmpty());
    }

    private void sendToSelectedPeers() {
        String senderName = DiscoveryService.getUserName();
        List<File> filesToSend = new ArrayList<>(selectedFiles);
        for (String peerId : new ArrayList<>(selectedPeerIds)) {
            Peer peer = peers.get(peerId);
            if (peer == null) {
                continue;
            }
            String transferId = UUID.randomUUID().toString();
            TransferProgressPanel panel = new TransferProgressPanel(peer.name);
            progressPanels.put(transferId, panel);
            progressContainer.add(panel);
            progressContainer.revalidate();
            FileTransferService.sendFiles(peer, filesToSend, transferId, senderName, this);
        }

        selectedFiles.clear();
        filesLabel.setText("Nenhum ficheiro selecionado");
        for (String peerId : new ArrayList<>(selectedPeerIds)) {
            DeviceCard card = cards.get(peerId);
            if (card != null) {
                card.setSelected(false);
            }
        }
        selectedPeerIds.clear();
        updateSendButtonState();
    }

    @Override
    public boolean onIncomingRequest(String transferId, String senderName, List<TransferMessage.FileEntry> files, long totalSize, int totalFiles) {
        TransferProgressPanel panel = new TransferProgressPanel(senderName);
        progressPanels.put(transferId, panel);
        progressContainer.add(panel);
        progressContainer.revalidate();

        TransferRequestDialog dialog = new TransferRequestDialog(this, senderName, files, totalSize, totalFiles);
        dialog.setVisible(true);
        boolean accepted = dialog.isAccepted();
        panel.setStatus(accepted ? "A receber" : "Recusado");
        return accepted;
    }

    @Override
    public void onProgress(String transferId, long transferred, long total, double speedBps) {
        SwingUtilities.invokeLater(() -> {
            TransferProgressPanel panel = progressPanels.get(transferId);
            if (panel != null) {
                panel.updateProgress(transferred, total, speedBps);
            }
        });
    }

    @Override
    public void onStatusChange(String transferId, TransferStatus status) {
        SwingUtilities.invokeLater(() -> {
            TransferProgressPanel panel = progressPanels.get(transferId);
            if (panel != null) {
                panel.setStatus(statusText(status));
            }
        });
    }

    private String statusText(TransferStatus status) {
        switch (status) {
            case WAITING:
                return "A aguardar";
            case TRANSFERRING:
                return "A transferir";
            case DONE:
                return "Concluido";
            case REJECTED:
                return "Recusado";
            case ERROR:
                return "Erro";
            default:
                return status.toString();
        }
    }

    private class FileDropHandler extends TransferHandler {
        @Override
        public boolean canImport(TransferHandler.TransferSupport support) {
            return support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
        }

        @Override
        @SuppressWarnings("unchecked")
        public boolean importData(TransferHandler.TransferSupport support) {
            if (!canImport(support)) {
                return false;
            }
            try {
                List<File> dropped = (List<File>) support.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                addSelectedFiles(dropped);
                return true;
            } catch (Exception e) {
                return false;
            }
        }
    }

    private class DeviceCard extends JPanel {
        private final String peerId;
        private boolean selected = false;
        private final JLabel nameLabel;
        private final JLabel hostLabel;
        private final JLabel ipLabel;

        DeviceCard(Peer peer) {
            this.peerId = peer.id;
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            setPreferredSize(new Dimension(180, 90));
            setBackground(Color.WHITE);

            nameLabel = new JLabel(peer.name);
            nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD));
            hostLabel = new JLabel(peer.hostName);
            ipLabel = new JLabel(peer.ipAddress);
            for (JLabel l : new JLabel[]{nameLabel, hostLabel, ipLabel}) {
                l.setAlignmentX(Component.CENTER_ALIGNMENT);
                add(l);
            }
            setSelected(false);

            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    toggleSelected();
                }
            });
        }

        void updatePeer(Peer peer) {
            nameLabel.setText(peer.name);
            hostLabel.setText(peer.hostName);
            ipLabel.setText(peer.ipAddress);
        }

        void toggleSelected() {
            setSelected(!selected);
            if (selected) {
                selectedPeerIds.add(peerId);
            } else {
                selectedPeerIds.remove(peerId);
            }
            updateSendButtonState();
        }

        void setSelected(boolean value) {
            selected = value;
            setBackground(selected ? new Color(200, 225, 255) : Color.WHITE);
            setBorder(new RoundedBorder(selected ? new Color(70, 130, 220) : Color.GRAY, 12));
        }
    }

    private static class RoundedBorder implements Border {
        private final Color color;
        private final int radius;

        RoundedBorder(Color color, int radius) {
            this.color = color;
            this.radius = radius;
        }

        @Override
        public Insets getBorderInsets(Component c) {
            return new Insets(radius / 2, radius, radius / 2, radius);
        }

        @Override
        public boolean isBorderOpaque() {
            return false;
        }

        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);
            g2.draw(new RoundRectangle2D.Double(x, y, width - 1, height - 1, radius, radius));
            g2.dispose();
        }
    }
}
