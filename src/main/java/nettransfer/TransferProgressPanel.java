package nettransfer;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;

public class TransferProgressPanel extends JPanel {
    private final JProgressBar progressBar = new JProgressBar(0, 100);
    private final JLabel detailLabel = new JLabel(" ");
    private final JLabel statusLabel = new JLabel("A aguardar");
    private final JButton openFolderButton;
    private String receiveDir;

    public TransferProgressPanel(String peerName, String receiveDir) {
        this.receiveDir = receiveDir;
        setLayout(new BorderLayout(8, 2));
        setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        JLabel titleLabel = new JLabel(peerName);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD));
        progressBar.setStringPainted(true);

        openFolderButton = new JButton("Abrir pasta");
        openFolderButton.setVisible(false);
        openFolderButton.addActionListener(e -> {
            if (this.receiveDir != null) MainFrame.openFolder(this.receiveDir);
        });

        JPanel top = new JPanel(new BorderLayout(8, 0));
        top.add(titleLabel, BorderLayout.WEST);
        top.add(progressBar, BorderLayout.CENTER);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        right.add(statusLabel);
        right.add(openFolderButton);
        top.add(right, BorderLayout.EAST);

        add(top, BorderLayout.NORTH);
        add(detailLabel, BorderLayout.SOUTH);
        setMaximumSize(new Dimension(Integer.MAX_VALUE, 60));
    }

    public void setReceiveDir(String dir) {
        this.receiveDir = dir;
    }

    public void updateProgress(long transferred, long total, double speedBps) {
        int pct = total > 0 ? (int) (transferred * 100 / total) : 0;
        progressBar.setValue(pct);
        progressBar.setString(pct + "%");
        double speedMBs = speedBps / (1024.0 * 1024.0);
        String eta = "--";
        if (speedBps > 0 && total > transferred) {
            long remainingSec = (long) ((total - transferred) / speedBps);
            eta = remainingSec + "s";
        }
        detailLabel.setText(String.format("%s / %s  -  %.2f MB/s  -  ETA %s",
                MainFrame.formatSize(transferred), MainFrame.formatSize(total), speedMBs, eta));
    }

    public void setStatus(String status) {
        statusLabel.setText(status);
        // Show "Abrir pasta" button when transfer is done and we know where files landed
        boolean done = "Concluido".equals(status);
        openFolderButton.setVisible(done && receiveDir != null);
    }
}
