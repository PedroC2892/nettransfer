package nettransfer;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;

public class TransferProgressPanel extends JPanel {
    private final JProgressBar progressBar = new JProgressBar(0, 100);
    private final JLabel detailLabel = new JLabel(" ");
    private final JLabel statusLabel = new JLabel("A aguardar");

    public TransferProgressPanel(String peerName) {
        setLayout(new BorderLayout(8, 2));
        setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        JLabel titleLabel = new JLabel(peerName);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD));
        progressBar.setStringPainted(true);

        JPanel top = new JPanel(new BorderLayout(8, 0));
        top.add(titleLabel, BorderLayout.WEST);
        top.add(progressBar, BorderLayout.CENTER);
        top.add(statusLabel, BorderLayout.EAST);

        add(top, BorderLayout.NORTH);
        add(detailLabel, BorderLayout.SOUTH);
        setMaximumSize(new Dimension(Integer.MAX_VALUE, 60));
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
    }
}
