package com.vku.rmi.client;

import com.vku.rmi.model.DataRecord;
import com.vku.rmi.service.IClientService;
import com.vku.rmi.ui.Theme;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;
import java.util.Locale;

public class RmiClientGUI extends JFrame {
    private final FailoverManager failoverManager;
    private final JLabel connection = Theme.label("Đang tìm máy chủ…", Color.WHITE, 13, true);
    private final JLabel feedback = Theme.label(" ", Theme.MUTED, 13, false);
    private final JLabel recordCount = Theme.label("0 hồ sơ", Theme.MUTED, 13, false);
    private final JTextField id = Theme.field(16);
    private final JTextField name = Theme.field(16);
    private final JTextField department = Theme.field(16);
    private final JTextField score = Theme.field(16);
    private final JTextField search = Theme.field(12);
    private final JButton add = Theme.button("Thêm hồ sơ", Theme.ACCENT, Color.WHITE);
    private final JButton update = Theme.button("Lưu chỉnh sửa", Color.WHITE, Theme.INK);
    private final JButton delete = Theme.button("Xóa hồ sơ", Color.WHITE, Theme.ERROR);
    private final JButton refresh = Theme.button("Làm mới", Color.WHITE, Theme.INK);
    private final JButton switchServer = Theme.button("Chuyển máy chủ", Color.WHITE, Theme.INK);
    private final DefaultTableModel model = new DefaultTableModel(
            new String[]{"Mã SV", "Họ và tên", "Khoa / Ngành", "GPA", "Bản", "Cập nhật bởi"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) { return false; }
    };
    private final JTable table = new JTable(model);
    private List<DataRecord> records = List.of();
    private boolean busy;

    public RmiClientGUI() {
        this("127.0.0.1", 1099, "127.0.0.1", 1100);
    }

    public RmiClientGUI(String host1, int port1, String host2, int port2) {
        super("Quản lý hồ sơ sinh viên · Lab 5 RMI");
        failoverManager = new FailoverManager(host1, port1, host2, port2);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1160, 710);
        setMinimumSize(new Dimension(940, 670));
        setLocationRelativeTo(null);

        JPanel heading = Theme.header("Hồ sơ sinh viên", "Quản lý dữ liệu trên hai máy chủ RMI");
        JPanel endpoint = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 7));
        endpoint.setOpaque(false);
        connection.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 5));
        endpoint.add(connection);
        endpoint.add(switchServer);
        heading.add(endpoint, BorderLayout.EAST);
        add(heading, BorderLayout.NORTH);

        JPanel body = Theme.frameBody();
        body.add(editor(), BorderLayout.WEST);
        body.add(directory(), BorderLayout.CENTER);
        body.add(feedback, BorderLayout.SOUTH);
        add(body, BorderLayout.CENTER);

        refresh.addActionListener(event -> run(null, false));
        switchServer.addActionListener(event -> run(null, true));
        add.addActionListener(event -> save(true));
        update.addActionListener(event -> save(false));
        delete.addActionListener(event -> deleteRecord());
        run(null, false);
        new Timer(4000, event -> run(null, false)).start();
    }

    private JPanel editor() {
        JPanel card = Theme.card(new BorderLayout(0, 14));
        card.setPreferredSize(new Dimension(275, 0));

        JPanel fields = new JPanel(new GridLayout(0, 1, 0, 7));
        fields.setOpaque(false);
        fields.add(input("Mã sinh viên", id));
        fields.add(input("Họ và tên", name));
        fields.add(input("Khoa / Ngành", department));
        fields.add(input("GPA (0–4)", score));

        JButton clear = Theme.button("Nhập hồ sơ mới", Color.WHITE, Theme.MUTED);
        clear.addActionListener(event -> {
            table.clearSelection();
            for (JTextField field : List.of(id, name, department, score)) field.setText("");
            id.requestFocusInWindow();
        });
        JPanel actions = new JPanel(new GridLayout(0, 1, 0, 5));
        actions.setOpaque(false);
        for (JButton button : List.of(add, update, delete, clear)) actions.add(button);

        JPanel form = new JPanel(new BorderLayout(0, 9));
        form.setOpaque(false);
        form.add(fields, BorderLayout.NORTH);
        form.add(actions, BorderLayout.CENTER);
        JPanel titleAndForm = new JPanel(new BorderLayout(0, 10));
        titleAndForm.setOpaque(false);
        titleAndForm.add(Theme.label("Chi tiết hồ sơ", Theme.INK, 18, true), BorderLayout.NORTH);
        titleAndForm.add(form, BorderLayout.CENTER);
        card.add(titleAndForm, BorderLayout.NORTH);
        return card;
    }

    private JPanel input(String text, JTextField field) {
        JPanel input = new JPanel(new BorderLayout(0, 4));
        input.setOpaque(false);
        JLabel label = Theme.label(text, Theme.MUTED, 12, true);
        label.setLabelFor(field);
        input.add(label, BorderLayout.NORTH);
        input.add(field, BorderLayout.CENTER);
        return input;
    }

    private JPanel directory() {
        JPanel card = Theme.card(new BorderLayout(0, 13));
        JPanel toolbar = new JPanel(new BorderLayout(8, 0));
        toolbar.setOpaque(false);
        toolbar.add(Theme.label("Danh sách sinh viên", Theme.INK, 18, true), BorderLayout.WEST);

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        controls.setOpaque(false);
        JLabel searchLabel = Theme.label("Tìm mã/tên", Theme.MUTED, 12, false);
        searchLabel.setLabelFor(search);
        controls.add(searchLabel);
        controls.add(search);
        controls.add(refresh);
        toolbar.add(controls, BorderLayout.EAST);
        card.add(toolbar, BorderLayout.NORTH);
        search.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent event) { render(); }
            public void removeUpdate(DocumentEvent event) { render(); }
            public void changedUpdate(DocumentEvent event) { render(); }
        });

        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getSelectionModel().addListSelectionListener(event -> {
            int row = table.getSelectedRow();
            if (event.getValueIsAdjusting() || row < 0) return;
            id.setText(model.getValueAt(row, 0).toString());
            name.setText(model.getValueAt(row, 1).toString());
            department.setText(model.getValueAt(row, 2).toString());
            score.setText(model.getValueAt(row, 3).toString());
        });
        card.add(Theme.table(table), BorderLayout.CENTER);
        Theme.widths(table, 80, 150, 140, 52, 49, 90);
        card.add(recordCount, BorderLayout.SOUTH);
        return card;
    }

    private void render() {
        String query = search.getText().strip().toLowerCase(Locale.ROOT);
        model.setRowCount(0);
        for (DataRecord record : records) {
            if (record.getId().toLowerCase(Locale.ROOT).contains(query)
                    || record.getName().toLowerCase(Locale.ROOT).contains(query)) {
                model.addRow(new Object[]{record.getId(), record.getName(), record.getDepartment(),
                        record.getScore(), "v" + record.getVersion(), record.getUpdatedByServer()});
            }
        }
        recordCount.setText(model.getRowCount() + " / " + records.size() + " hồ sơ");
    }

    private void save(boolean create) {
        String code = id.getText().strip();
        String fullName = name.getText().strip();
        String faculty = department.getText().strip();
        if (code.isEmpty() || fullName.isEmpty() || code.length() > 50
                || fullName.length() > 100 || faculty.length() > 100) {
            showFeedback("Nhập mã SV (tối đa 50 ký tự) và họ tên. Họ tên và khoa tối đa 100 ký tự.", Theme.ERROR);
            return;
        }
        double gpa;
        try {
            gpa = Double.parseDouble(score.getText().strip().replace(',', '.'));
            if (!Double.isFinite(gpa) || gpa < 0 || gpa > 4) throw new NumberFormatException();
        } catch (NumberFormatException error) {
            showFeedback("GPA phải là số từ 0 đến 4.", Theme.ERROR);
            score.requestFocusInWindow();
            return;
        }

        DataRecord record = new DataRecord(code, fullName, faculty, gpa, "CLIENT");
        run(service -> {
            boolean saved = create ? service.createRecord(record) : service.updateRecord(record);
            if (!saved) return create ? "Mã sinh viên đã tồn tại." : "Không tìm thấy hồ sơ để cập nhật.";
            return null;
        }, false);
    }

    private void deleteRecord() {
        String code = id.getText().strip();
        if (code.isEmpty()) {
            showFeedback("Chọn một hồ sơ hoặc nhập mã sinh viên cần xóa.", Theme.ERROR);
            return;
        }
        if (JOptionPane.showConfirmDialog(this, "Xóa hồ sơ " + code + "?", "Xác nhận xóa",
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) == JOptionPane.YES_OPTION) {
            run(service -> service.deleteRecord(code) ? null : "Không tìm thấy hồ sơ để xóa.", false);
        }
    }

    private void run(Mutation mutation, boolean changeServer) {
        if (busy) return;
        setBusy(true);
        if (mutation != null || changeServer) showFeedback("Đang xử lý…", Theme.MUTED);
        new SwingWorker<Snapshot, Void>() {
            @Override
            protected Snapshot doInBackground() throws Exception {
                boolean switchFailed = changeServer && failoverManager.switchServer() == null;
                IClientService service = failoverManager.getService();
                if (service == null) return null;
                String issue = mutation == null ? null : mutation.apply(service);
                try {
                    return new Snapshot(service.getAllRecords(), issue, switchFailed);
                } catch (Exception error) {
                    if (mutation == null) throw error;
                    return new Snapshot(null, issue, false);
                }
            }

            @Override
            protected void done() {
                setBusy(false);
                try {
                    Snapshot snapshot = get();
                    if (snapshot == null) {
                        connection.setText("Mất kết nối");
                        records = List.of();
                        render();
                        if (changeServer || mutation != null) showFeedback("Không kết nối được với hai máy chủ.", Theme.ERROR);
                        return;
                    }
                    connection.setText(failoverManager.getActiveEndpoint().toString());
                    if (snapshot.records() != null && !sameList(records, snapshot.records())) {
                        records = snapshot.records();
                        render();
                    }
                    if (snapshot.switchFailed()) showFeedback("Máy chủ còn lại hiện không khả dụng.", Theme.ERROR);
                    else if (snapshot.issue() != null) showFeedback(snapshot.issue(), Theme.ERROR);
                    else if (mutation != null) {
                        String message = "Thao tác đã được ghi nhận tại " + failoverManager.getActiveEndpoint().name() + ".";
                        if (snapshot.records() == null) message += " Chưa tải lại được danh sách.";
                        showFeedback(message, Theme.ACCENT);
                    } else if (changeServer) showFeedback("Đã chuyển máy chủ.", Theme.ACCENT);
                } catch (Exception error) {
                    showFeedback("Không nhận được kết quả. Làm mới danh sách để kiểm tra trước khi thao tác lại.", Theme.ERROR);
                }
            }
        }.execute();
    }

    private boolean sameList(List<DataRecord> before, List<DataRecord> after) {
        if (before.size() != after.size()) return false;
        for (int i = 0; i < before.size(); i++) {
            if (!before.get(i).sameState(after.get(i))) return false;
        }
        return true;
    }

    private void setBusy(boolean value) {
        busy = value;
        for (JButton button : List.of(add, update, delete, refresh, switchServer)) button.setEnabled(!value);
    }

    private void showFeedback(String text, Color color) {
        feedback.setText(text);
        feedback.setForeground(color);
    }

    @FunctionalInterface
    private interface Mutation {
        String apply(IClientService service) throws Exception;
    }

    private record Snapshot(List<DataRecord> records, String issue, boolean switchFailed) {}

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            Theme.install();
            String host1 = args.length >= 2 ? args[0] : "127.0.0.1";
            int port1 = args.length >= 2 ? Integer.parseInt(args[1]) : 1099;
            String host2 = args.length >= 4 ? args[2] : "127.0.0.1";
            int port2 = args.length >= 4 ? Integer.parseInt(args[3]) : 1100;
            new RmiClientGUI(host1, port1, host2, port2).setVisible(true);
        });
    }
}
