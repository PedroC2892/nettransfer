package nettransfer;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.util.List;

public class TransferRequestDialog extends JDialog {
    private boolean accepted = false;

    public TransferRequestDialog(Frame owner, String senderName, List<TransferMessage.FileEntry> files, long totalSize, int totalFiles) {
        super(owner, "Pedido de transferencia", true);
        setLayout(new BorderLayout(10, 10));
        setSize(420, 380);
        setLocationRelativeTo(owner);

        JLabel header = new JLabel(senderName + " quer enviar " + totalFiles + " ficheiro(s) - " + MainFrame.formatSize(totalSize));
        header.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        DefaultListModel<String> model = new DefaultListModel<>();
        for (TransferMessage.FileEntry f : files) {
            if (f.isDirectory) {
                continue;
            }
            String type = f.name.contains(".") ? f.name.substring(f.name.lastIndexOf('.') + 1) : "ficheiro";
            model.addElement(f.relativePath + "  (" + type + ", " + MainFrame.formatSize(f.size) + ")");
        }
        JList<String> fileList = new JList<>(model);
        JScrollPane scroll = new JScrollPane(fileList);
        scroll.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));

        JButton acceptButton = new JButton("Aceitar");
        JButton rejectButton = new JButton("Recusar");
        acceptButton.addActionListener(e -> {
            accepted = true;
            dispose();
        });
        rejectButton.addActionListener(e -> {
            accepted = false;
            dispose();
        });

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER));
        buttons.add(acceptButton);
        buttons.add(rejectButton);

        add(header, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
    }

    public boolean isAccepted() {
        return accepted;
    }
}
