package nettransfer;

import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;
import java.util.LinkedHashMap;
import java.util.Map;

public class PeerListFrame extends JFrame {
    private final DefaultTableModel tableModel;
    private final Map<String, Integer> rowByPeerId = new LinkedHashMap<>();

    public PeerListFrame() {
        super("nettransfer - Peers descobertos");

        tableModel = new DefaultTableModel(new Object[]{"Nome", "Hostname", "IP", "Porta TCP"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        JTable table = new JTable(tableModel);
        add(new JScrollPane(table));

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(500, 300);
        setLocationRelativeTo(null);
    }

    public void addOrUpdatePeer(Peer peer) {
        SwingUtilities.invokeLater(() -> {
            Object[] rowData = {peer.name, peer.hostName, peer.ipAddress, peer.tcpPort};
            Integer row = rowByPeerId.get(peer.id);
            if (row != null) {
                for (int col = 0; col < rowData.length; col++) {
                    tableModel.setValueAt(rowData[col], row, col);
                }
            } else {
                tableModel.addRow(rowData);
                rowByPeerId.put(peer.id, tableModel.getRowCount() - 1);
            }
        });
    }
}
