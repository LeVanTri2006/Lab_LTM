package com.vku.rmi.monitor;

import com.vku.rmi.model.DataRecord;
import com.vku.rmi.model.ServerInfo;
import com.vku.rmi.service.IClientService;
import com.vku.rmi.ui.Theme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.rmi.registry.LocateRegistry;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class ClusterMonitorGUI extends JFrame {
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss");
    private final ServerCard first;
    private final ServerCard second;
    private final JLabel summary = Theme.label("Đang đọc dữ liệu từ hai máy chủ…", Color.WHITE, 13, true);
    private final DefaultTableModel model = new DefaultTableModel(
            new String[]{"Mã SV", "Họ và tên", "Server 1", "Server 2", "Đối chiếu"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) { return false; }
    };
    private final JTextArea log = new JTextArea(4, 40);
    private List<Row> displayedRows = List.of();
    private String previousSummary;
    private boolean polling;

    public ClusterMonitorGUI(String host1, int port1, String host2, int port2) {
        super("Giám sát đồng bộ · Lab 5 RMI");
        first = new ServerCard("SERVER_1", host1, port1);
        second = new ServerCard("SERVER_2", host2, port2);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1080, 740);
        setMinimumSize(new Dimension(800, 590));
        setLocationRelativeTo(null);

        JPanel heading = Theme.header("Giám sát đồng bộ", "Đối chiếu dữ liệu sinh viên giữa hai máy chủ RMI");
        summary.setBorder(new EmptyBorder(0, 0, 0, 5));
        heading.add(summary, BorderLayout.EAST);
        add(heading, BorderLayout.NORTH);

        JPanel body = Theme.frameBody();
        JPanel nodes = new JPanel(new GridLayout(1, 2, 16, 0));
        nodes.setOpaque(false);
        nodes.add(first.panel);
        nodes.add(second.panel);
        body.add(nodes, BorderLayout.NORTH);
        body.add(dataPanel(), BorderLayout.CENTER);
        body.add(logPanel(), BorderLayout.SOUTH);
        add(body);

        poll();
        new Timer(2500, event -> poll()).start();
    }

    private JPanel dataPanel() {
        JPanel card = Theme.card(new BorderLayout(0, 12));
        card.add(Theme.label("Đối chiếu hồ sơ", Theme.INK, 18, true), BorderLayout.NORTH);
        JTable table = new JTable(model);
        JScrollPane scroll = Theme.table(table);
        Theme.widths(table, 90, 200, 110, 110, 150);
        card.add(scroll, BorderLayout.CENTER);
        return card;
    }

    private JPanel logPanel() {
        JPanel card = Theme.card(new BorderLayout(0, 8));
        card.add(Theme.label("Nhật ký theo dõi", Theme.INK, 15, true), BorderLayout.NORTH);
        log.setEditable(false);
        log.setFont(Theme.font(Font.PLAIN, 13));
        log.setBackground(Theme.BACKGROUND);
        log.setForeground(Theme.INK);
        log.setBorder(new EmptyBorder(4, 8, 4, 8));
        JScrollPane scroll = new JScrollPane(log);
        scroll.setBorder(BorderFactory.createLineBorder(Theme.LINE));
        card.add(scroll, BorderLayout.CENTER);
        return card;
    }

    private void poll() {
        if (polling) return;
        polling = true;
        new SwingWorker<Comparison, Void>() {
            @Override
            protected Comparison doInBackground() {
                return new Comparison(first.read(), second.read());
            }

            @Override
            protected void done() {
                try {
                    Comparison result = get();
                    first.display(result.first());
                    second.display(result.second());
                    displayComparison(result);
                } catch (Exception error) {
                    summary.setText("Không thể cập nhật kết quả");
                } finally {
                    polling = false;
                }
            }
        }.execute();
    }

    private void displayComparison(Comparison comparison) {
        Map<String, Pair> records = new TreeMap<>();
        NodeSnapshot left = comparison.first();
        NodeSnapshot right = comparison.second();
        if (left != null) {
            for (DataRecord record : left.records()) records.put(record.getId(), new Pair(record, null));
        }
        if (right != null) {
            for (DataRecord record : right.records()) {
                records.merge(record.getId(), new Pair(null, record),
                        (existing, added) -> new Pair(existing.left(), added.right()));
            }
        }
        boolean canCompare = left != null && right != null;
        int matching = 0;
        var rows = new java.util.ArrayList<Row>();
        for (var entry : records.entrySet()) {
            DataRecord a = entry.getValue().left();
            DataRecord b = entry.getValue().right();
            boolean equal = canCompare && a != null && a.sameState(b);
            if (equal) matching++;
            String status;
            if (!canCompare) status = "Chưa xác minh";
            else if (equal) status = "Khớp";
            else if (a == null) status = "Thiếu ở Server 1";
            else if (b == null) status = "Thiếu ở Server 2";
            else status = "Khác dữ liệu";
            rows.add(new Row(entry.getKey(), a == null ? b.getName() : a.getName(),
                    version(a, left), version(b, right), status));
        }
        if (!displayedRows.equals(rows)) {
            displayedRows = rows;
            model.setRowCount(0);
            for (Row row : rows) model.addRow(new Object[]{row.id(), row.name(), row.first(), row.second(), row.status()});
        }

        String text = canCompare
                ? matching + "/" + records.size() + " hồ sơ khớp · " + (records.size() - matching) + " chênh lệch"
                : "Cần hai máy chủ để đối chiếu";
        summary.setText(text);
        if (!text.equals(previousSummary)) {
            appendLog(text);
            previousSummary = text;
        }
    }

    private String version(DataRecord record, NodeSnapshot snapshot) {
        if (snapshot == null) return "Không truy cập";
        return record == null ? "—" : "v" + record.getVersion();
    }

    private void appendLog(String message) {
        log.append(LocalTime.now().format(CLOCK) + "   " + message + "\n");
        if (log.getLineCount() > 300) {
            try {
                log.getDocument().remove(0, log.getLineEndOffset(0));
            } catch (javax.swing.text.BadLocationException ignored) {}
        }
        log.setCaretPosition(log.getDocument().getLength());
    }

    private final class ServerCard {
        private final String id;
        private final String host;
        private final int port;
        private final JPanel panel = Theme.card(new BorderLayout(8, 6));
        private final JLabel state = Theme.label("Đang kết nối…", Theme.MUTED, 13, true);
        private final JLabel storage = Theme.label("Lưu trữ · —", Theme.MUTED, 13, false);
        private final JLabel details = Theme.label(" ", Theme.MUTED, 13, false);
        private String previousState;

        ServerCard(String id, String host, int port) {
            this.id = id;
            this.host = host;
            this.port = port;
            JPanel header = new JPanel(new BorderLayout());
            header.setOpaque(false);
            state.setBorder(new EmptyBorder(0, 0, 0, 5));
            header.add(Theme.label(id.replace("SERVER_", "Máy chủ "), Theme.INK, 18, true), BorderLayout.WEST);
            header.add(state, BorderLayout.EAST);
            panel.add(header, BorderLayout.NORTH);
            JPanel metadata = new JPanel(new GridLayout(0, 1, 0, 3));
            metadata.setOpaque(false);
            metadata.add(Theme.label(host + ":" + port, Theme.MUTED, 13, false));
            metadata.add(storage);
            metadata.add(details);
            panel.add(metadata);
        }

        NodeSnapshot read() {
            try {
                var registry = LocateRegistry.getRegistry(host, port);
                var service = (IClientService) registry.lookup("ClientService");
                ServerInfo info = service.getServerInfo();
                if (!id.equals(info.getServerId())) return null;
                return new NodeSnapshot(info, service.getAllRecords());
            } catch (Exception error) {
                return null;
            }
        }

        void display(NodeSnapshot snapshot) {
            String nextState;
            if (snapshot == null) {
                nextState = "Ngoại tuyến";
                state.setForeground(Theme.ERROR);
                storage.setText("Lưu trữ · —");
                details.setText("Chưa đọc được dữ liệu");
            } else {
                ServerInfo info = snapshot.info();
                boolean linked = info.isPartnerConnected();
                nextState = linked ? "Đã kết nối đối tác" : "Đang chạy độc lập";
                state.setForeground(linked ? Theme.ACCENT : Theme.WARNING);
                storage.setText("Lưu trữ · " + info.getStorage());
                details.setText(snapshot.records().size() + " hồ sơ · Hoạt động " + info.getUptime() + " giây");
            }
            state.setText(nextState);
            if (!nextState.equals(previousState)) {
                appendLog(id + " · " + nextState);
                previousState = nextState;
            }
        }
    }

    private record NodeSnapshot(ServerInfo info, List<DataRecord> records) {}
    private record Comparison(NodeSnapshot first, NodeSnapshot second) {}
    private record Pair(DataRecord left, DataRecord right) {}
    private record Row(String id, String name, String first, String second, String status) {}

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            Theme.install();
            String host1 = args.length >= 2 ? args[0] : "127.0.0.1";
            int port1 = args.length >= 2 ? Integer.parseInt(args[1]) : 1099;
            String host2 = args.length >= 4 ? args[2] : "127.0.0.1";
            int port2 = args.length >= 4 ? Integer.parseInt(args[3]) : 1100;
            new ClusterMonitorGUI(host1, port1, host2, port2).setVisible(true);
        });
    }
}
