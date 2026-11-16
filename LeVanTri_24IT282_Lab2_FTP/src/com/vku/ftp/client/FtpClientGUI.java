package com.vku.ftp.client;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

public class FtpClientGUI extends JFrame {
    private static final Color BACKGROUND = new Color(0xF2F6FA);
    private static final Color WHITE = Color.WHITE;
    private static final Color NAVY = new Color(0x11223B);
    private static final Color INK = new Color(0x172740);
    private static final Color MUTED = new Color(0x566782);
    private static final Color LINE = new Color(0xDFE7F1);
    private static final Color BLUE = new Color(0x2155C4);
    private static final Color BLUE_TINT = new Color(0xE8F0FF);
    private static final Color RED = new Color(0xA12C3D);
    private static final Color RED_TINT = new Color(0xFDEEF1);
    private static final String DIRECTORY = "Thư mục";

    private final FtpClient client = new FtpClient();
    private File currentLocalDir = new File(System.getProperty("user.dir"));
    private boolean connected;
    private boolean busy;

    private final JTextField hostField = new JTextField("127.0.0.1", 12);
    private final JTextField portField = new JTextField("2121", 5);
    private final JTextField userField = new JTextField("admin", 9);
    private final JPasswordField passField = new JPasswordField("admin123", 9);
    private final JButton connect = button("Kết nối nhanh", BLUE, WHITE);
    private final JButton disconnect = button("Ngắt kết nối");
    private final JLabel connection = label("Chưa kết nối", 13, Font.BOLD, WHITE);
    private final JLabel feedback = label("", 12, Font.PLAIN, MUTED);
    private final JTextArea logArea = new JTextArea();

    private final JLabel localPath = label("", 12, Font.PLAIN, INK);
    private final JLabel remotePath = label("", 12, Font.PLAIN, INK);
    private final JLabel localCount = label("", 12, Font.PLAIN, MUTED);
    private final JLabel remoteCount = label("", 12, Font.PLAIN, MUTED);
    private final JLabel remoteEmpty = label("Kết nối để xem tệp trên máy chủ", 14, Font.PLAIN, MUTED);
    private final DefaultTableModel localModel = fileModel();
    private final DefaultTableModel remoteModel = fileModel();
    private final JTable localTable = fileTable(localModel);
    private final JTable remoteTable = fileTable(remoteModel);
    private final CardLayout remoteLayout = new CardLayout();
    private final JPanel remoteContent = new JPanel(remoteLayout);
    private final JButton localUp = button("↑ Thư mục cha");
    private final JButton remoteUp = button("↑ Thư mục cha");
    private final JButton remoteRefresh = button("Làm mới");
    private final JButton upload = button("↑ Tải lên", BLUE, WHITE);
    private final JButton download = button("↓ Tải về", BLUE, WHITE);
    private final JButton delete = button("Xóa tệp", RED_TINT, RED);
    private final JButton newFolder = button("Tạo thư mục");

    public FtpClientGUI() {
        super("VKU FTP · Trình quản lý tệp");
        initUI();
        registerActions();
        refreshLocalFiles();
        clearRemoteFiles();
        updateControls();
    }

    private void initUI() {
        setSize(1200, 810);
        setMinimumSize(new Dimension(950, 660));
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        JPanel main = new JPanel(new BorderLayout());
        main.setBackground(BACKGROUND);
        main.add(createHeader(), BorderLayout.NORTH);
        JPanel content = new JPanel(new BorderLayout(0, 15));
        content.setOpaque(false);
        content.setBorder(new EmptyBorder(17, 23, 18, 23));
        content.add(createConnectionPanel(), BorderLayout.NORTH);

        JButton localRefresh = button("Làm mới");
        localRefresh.addActionListener(e -> refreshLocalFiles());
        JPanel localPane = filePane("Máy tính của bạn", localCount, localPath, localUp,
                tableScroll(localTable), actions(upload, localRefresh));
        remoteEmpty.setHorizontalAlignment(SwingConstants.CENTER);
        remoteEmpty.setOpaque(true);
        remoteEmpty.setBackground(BACKGROUND);
        remoteContent.setBorder(new LineBorder(LINE));
        remoteContent.add(tableScroll(remoteTable), "table");
        remoteContent.add(remoteEmpty, "empty");
        JPanel remotePane = filePane("Máy chủ FTP", remoteCount, remotePath, remoteUp,
                remoteContent, actions(download, delete, newFolder, remoteRefresh));

        JSplitPane files = split(JSplitPane.HORIZONTAL_SPLIT, localPane, remotePane, 0.5);
        JSplitPane workspace = split(JSplitPane.VERTICAL_SPLIT, files, createConsole(), 0.78);
        content.add(workspace, BorderLayout.CENTER);
        main.add(content, BorderLayout.CENTER);
        setContentPane(main);
        SwingUtilities.invokeLater(() -> {
            files.setDividerLocation(0.5);
            workspace.setDividerLocation(0.74);
        });
    }

    private JPanel createHeader() {
        JPanel header = new JPanel(new BorderLayout(18, 0));
        header.setBackground(NAVY);
        header.setBorder(new EmptyBorder(17, 24, 17, 24));
        JPanel title = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        title.setOpaque(false);
        JLabel brand = label("VKU FTP", 22, Font.BOLD, WHITE);
        title.add(brand);
        JLabel feature = label("Trình quản lý tệp", 14, Font.PLAIN, new Color(0xC2D1E5));
        feature.setBorder(new EmptyBorder(0, 15, 0, 0));
        title.add(feature);
        connection.setOpaque(true);
        connection.setHorizontalAlignment(SwingConstants.CENTER);
        connection.setBorder(new EmptyBorder(7, 12, 7, 12));
        header.add(title, BorderLayout.CENTER);
        header.add(connection, BorderLayout.EAST);
        return header;
    }

    private JPanel createConnectionPanel() {
        JPanel card = card(12, 9);
        JPanel fields = new JPanel(new GridBagLayout());
        fields.setOpaque(false);
        GridBagConstraints cell = new GridBagConstraints();
        cell.gridy = 0;
        cell.fill = GridBagConstraints.HORIZONTAL;
        addField(fields, cell, "Địa chỉ máy chủ", hostField, 0, 1.3);
        addField(fields, cell, "Cổng", portField, 1, 0);
        addField(fields, cell, "Tài khoản", userField, 2, 0.75);
        addField(fields, cell, "Mật khẩu", passField, 3, 0.75);
        cell.gridx = 4;
        cell.weightx = 0;
        cell.anchor = GridBagConstraints.SOUTH;
        cell.fill = GridBagConstraints.NONE;
        cell.insets = new Insets(0, 2, 0, 0);
        fields.add(actions(connect, disconnect), cell);
        card.add(fields);
        return card;
    }

    private void addField(JPanel fields, GridBagConstraints cell, String title, JTextField input,
                          int column, double weight) {
        JLabel caption = label(title, 12, Font.BOLD, MUTED);
        caption.setLabelFor(input);
        input.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        input.setBorder(new CompoundBorder(new LineBorder(LINE), new EmptyBorder(7, 9, 7, 9)));
        JPanel field = new JPanel(new BorderLayout(0, 5));
        field.setOpaque(false);
        field.add(caption, BorderLayout.NORTH);
        field.add(input, BorderLayout.CENTER);
        cell.gridx = column;
        cell.weightx = weight;
        cell.insets = new Insets(0, 0, 0, 12);
        fields.add(field, cell);
    }

    private JPanel filePane(String title, JLabel count, JLabel path, JButton up,
                            JComponent files, JPanel commands) {
        JPanel pane = card(14, 9);
        pane.setMinimumSize(new Dimension(431, 190));
        JPanel heading = new JPanel(new BorderLayout(0, 9));
        heading.setOpaque(false);
        JPanel nameRow = new JPanel(new BorderLayout(10, 0));
        nameRow.setOpaque(false);
        nameRow.add(label(title, 20, Font.BOLD, INK), BorderLayout.CENTER);
        nameRow.add(count, BorderLayout.EAST);
        heading.add(nameRow, BorderLayout.NORTH);

        JPanel address = new JPanel(new BorderLayout(7, 0));
        address.setOpaque(false);
        path.setOpaque(true);
        path.setBackground(BACKGROUND);
        path.setBorder(new CompoundBorder(new LineBorder(LINE), new EmptyBorder(4, 9, 4, 9)));
        address.add(path, BorderLayout.CENTER);
        address.add(up, BorderLayout.EAST);
        heading.add(address, BorderLayout.CENTER);
        pane.add(heading, BorderLayout.NORTH);
        pane.add(files, BorderLayout.CENTER);
        pane.add(commands, BorderLayout.SOUTH);
        return pane;
    }

    private JPanel createConsole() {
        JPanel console = card(12, 7);
        console.setMinimumSize(new Dimension(0, 103));
        JPanel heading = new JPanel(new BorderLayout(8, 0));
        heading.setOpaque(false);
        JPanel labels = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        labels.setOpaque(false);
        labels.add(label("Nhật ký FTP", 15, Font.BOLD, INK));
        labels.add(feedback);
        heading.add(labels, BorderLayout.CENTER);
        JButton clear = button("Xóa nhật ký");
        clear.addActionListener(e -> logArea.setText(""));
        heading.add(clear, BorderLayout.EAST);
        console.add(heading, BorderLayout.NORTH);
        logArea.setEditable(false);
        logArea.setFont(new Font("Consolas", Font.PLAIN, 12));
        logArea.setBackground(NAVY);
        logArea.setForeground(new Color(0xDCE7F8));
        logArea.setMargin(new Insets(8, 11, 8, 11));
        JScrollPane scroll = new JScrollPane(logArea);
        scroll.setBorder(new LineBorder(LINE));
        scroll.getViewport().setBackground(NAVY);
        console.add(scroll, BorderLayout.CENTER);
        client.setLogListener(message -> SwingUtilities.invokeLater(() -> {
            logArea.append(message + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        }));
        return console;
    }

    private void registerActions() {
        connect.addActionListener(e -> performConnect());
        disconnect.addActionListener(e -> runOperation("Đang ngắt kết nối…", () -> {
            client.disconnect();
            return null;
        }, ignored -> clearRemoteFiles()));
        getRootPane().setDefaultButton(connect);
        localUp.addActionListener(e -> openLocalDirectory(currentLocalDir.getParentFile()));
        remoteUp.addActionListener(e -> changeRemoteDirectory(".."));
        remoteRefresh.addActionListener(e -> runOperation("Đang đọc danh sách tệp…", this::readRemoteFiles, this::showRemoteFiles));
        upload.addActionListener(e -> performUpload());
        download.addActionListener(e -> performDownload());
        delete.addActionListener(e -> performDelete());
        newFolder.addActionListener(e -> performMakeDir());
        localTable.getSelectionModel().addListSelectionListener(e -> updateControls());
        remoteTable.getSelectionModel().addListSelectionListener(e -> updateControls());
        openOnActivation(localTable, () -> {
            String name = selectedName(localTable);
            if (name != null) openLocalDirectory(new File(currentLocalDir, name));
        });
        openOnActivation(remoteTable, () -> {
            if (isDirectorySelected(remoteTable)) changeRemoteDirectory(selectedName(remoteTable));
        });
    }

    private void performConnect() {
        if (busy || connected) return;
        String host = hostField.getText().trim();
        String user = userField.getText().trim();
        int port;
        try {
            port = Integer.parseInt(portField.getText().trim());
            if (port < 1 || port > 65535) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            showError("Nhập cổng FTP từ 1 đến 65535.");
            portField.requestFocusInWindow();
            return;
        }
        if (host.isEmpty() || user.isEmpty()) {
            showError("Nhập địa chỉ máy chủ và tài khoản FTP.");
            return;
        }
        String password = new String(passField.getPassword()).trim();
        runOperation("Đang kết nối…", () -> {
            requireSuccess(client.connect(host, port, user, password),
                    "Kết nối thất bại. Kiểm tra địa chỉ, cổng và tài khoản FTP.");
            return readRemoteFiles();
        }, this::showRemoteFiles);
    }

    private void openLocalDirectory(File directory) {
        if (directory != null && directory.isDirectory()) {
            currentLocalDir = directory;
            refreshLocalFiles();
        }
    }

    private void refreshLocalFiles() {
        setPath(localPath, currentLocalDir.getAbsolutePath());
        localModel.setRowCount(0);
        File[] files = currentLocalDir.listFiles();
        if (files != null) {
            Arrays.sort(files, Comparator.comparing(File::isDirectory).reversed()
                    .thenComparing(File::getName, String.CASE_INSENSITIVE_ORDER));
            SimpleDateFormat format = new SimpleDateFormat("dd/MM/yyyy HH:mm");
            for (File entry : files) {
                boolean folder = entry.isDirectory();
                localModel.addRow(fileRow(entry.getName(), folder, entry.length(), format.format(entry.lastModified())));
            }
        }
        localCount.setText(localModel.getRowCount() + " mục");
        updateControls();
    }

    private record RemoteFiles(String path, List<FtpClient.FtpItem> items) {}

    private RemoteFiles readRemoteFiles() throws IOException {
        List<FtpClient.FtpItem> items = client.listFiles();
        items.sort(Comparator.<FtpClient.FtpItem, Boolean>comparing(item -> item.isDirectory).reversed()
                .thenComparing(item -> item.name, String.CASE_INSENSITIVE_ORDER));
        return new RemoteFiles(client.getCurrentRemoteDir(), items);
    }

    private void showRemoteFiles(RemoteFiles files) {
        setPath(remotePath, files.path());
        remoteModel.setRowCount(0);
        for (FtpClient.FtpItem item : files.items()) {
            remoteModel.addRow(fileRow(item.name, item.isDirectory, item.size, item.date));
        }
        remoteCount.setText(files.items().size() + " mục");
        remoteEmpty.setText("Thư mục này chưa có tệp");
        remoteLayout.show(remoteContent, files.items().isEmpty() ? "empty" : "table");
    }

    private void clearRemoteFiles() {
        setPath(remotePath, "—");
        remoteModel.setRowCount(0);
        remoteCount.setText("");
        remoteEmpty.setText("Kết nối để xem tệp trên máy chủ");
        remoteLayout.show(remoteContent, "empty");
    }

    private void changeRemoteDirectory(String name) {
        if (!connected || busy) return;
        runOperation("Đang mở thư mục…", () -> {
            requireSuccess(client.changeDirectory(name), "Không thể mở thư mục " + name);
            return readRemoteFiles();
        }, this::showRemoteFiles);
    }

    private void performUpload() {
        String name = selectedName(localTable);
        if (!connected || busy || name == null || isDirectorySelected(localTable)) return;
        File source = new File(currentLocalDir, name);
        changeRemoteFiles("Đang tải lên…", "Đã tải lên " + name, "Tải lên thất bại.",
                () -> client.uploadFile(source, name));
    }

    private void performDownload() {
        String name = selectedName(remoteTable);
        if (!connected || busy || name == null || isDirectorySelected(remoteTable)) return;
        File destination = new File(currentLocalDir, name);
        runOperation("Đang tải về…", () -> {
            requireSuccess(client.downloadFile(name, destination), "Tải về thất bại.");
            return destination;
        }, file -> {
            refreshLocalFiles();
            showFeedback("Đã tải về " + file.getName());
        });
    }

    private void performDelete() {
        String name = selectedName(remoteTable);
        if (!connected || busy || name == null || isDirectorySelected(remoteTable)) return;
        int answer = JOptionPane.showConfirmDialog(this, "Xóa tệp “" + name + "” trên máy chủ?",
                "Xóa tệp", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
        if (answer == JOptionPane.OK_OPTION) {
            changeRemoteFiles("Đang xóa tệp…", "Đã xóa " + name,
                    "Không thể xóa tệp. Kiểm tra quyền của tài khoản.", () -> client.deleteFile(name));
        }
    }

    private void performMakeDir() {
        if (!connected || busy) return;
        String value = JOptionPane.showInputDialog(this, "Tên thư mục mới trên máy chủ");
        if (value == null || value.isBlank()) return;
        String name = value.trim();
        changeRemoteFiles("Đang tạo thư mục…", "Đã tạo thư mục " + name,
                "Không thể tạo thư mục.", () -> client.createDirectory(name));
    }

    private void changeRemoteFiles(String progress, String success, String failure, Callable<Boolean> change) {
        runOperation(progress, () -> {
            requireSuccess(change.call(), failure);
            return readRemoteFiles();
        }, files -> {
            showRemoteFiles(files);
            showFeedback(success);
        });
    }

    private <T> void runOperation(String progress, Callable<T> work, Consumer<T> completed) {
        if (busy) return;
        busy = true;
        showFeedback(progress);
        updateControls();
        new SwingWorker<T, Void>() {
            @Override
            protected T doInBackground() throws Exception {
                return work.call();
            }

            @Override
            protected void done() {
                busy = false;
                try {
                    T result = get();
                    showFeedback("");
                    completed.accept(result);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    showFeedback("Thao tác đã bị gián đoạn");
                } catch (ExecutionException e) {
                    if (client.isConnected() && !client.getCurrentRemoteDir().equals(remotePath.getText())) {
                        setPath(remotePath, client.getCurrentRemoteDir());
                        remoteModel.setRowCount(0);
                        remoteCount.setText("");
                        remoteEmpty.setText("Bấm Làm mới để đọc danh sách tệp");
                        remoteLayout.show(remoteContent, "empty");
                    }
                    showError(e.getCause().getMessage());
                } finally {
                    connected = client.isConnected();
                    updateControls();
                }
            }
        }.execute();
    }

    private void updateControls() {
        boolean remoteReady = connected && !busy;
        boolean remoteFile = selectedName(remoteTable) != null && !isDirectorySelected(remoteTable);
        connect.setEnabled(!connected && !busy);
        disconnect.setEnabled(remoteReady);
        hostField.setEditable(!connected && !busy);
        portField.setEditable(!connected && !busy);
        userField.setEditable(!connected && !busy);
        passField.setEditable(!connected && !busy);
        localUp.setEnabled(currentLocalDir.getParentFile() != null);
        remoteUp.setEnabled(remoteReady && !"/".equals(remotePath.getText()));
        remoteRefresh.setEnabled(remoteReady);
        upload.setEnabled(remoteReady && selectedName(localTable) != null && !isDirectorySelected(localTable));
        download.setEnabled(remoteReady && remoteFile);
        delete.setEnabled(remoteReady && remoteFile);
        newFolder.setEnabled(remoteReady);
        connection.setText(connected ? "Đã kết nối" : busy ? "Đang kết nối…" : "Chưa kết nối");
        connection.setBackground(connected ? new Color(0x155846) : new Color(0x314663));
    }

    private void showFeedback(String message) {
        feedback.setText(message);
    }

    private void showError(String message) {
        showFeedback("Thao tác thất bại");
        JOptionPane.showMessageDialog(this, message, "VKU FTP", JOptionPane.ERROR_MESSAGE);
    }

    private static void requireSuccess(boolean success, String message) throws IOException {
        if (!success) throw new IOException(message);
    }

    private static void setPath(JLabel label, String path) {
        label.setText(path);
        label.setToolTipText(path);
    }

    private static String selectedName(JTable table) {
        int row = table.getSelectedRow();
        return row < 0 ? null : (String) table.getValueAt(row, 0);
    }

    private static boolean isDirectorySelected(JTable table) {
        int row = table.getSelectedRow();
        return row >= 0 && DIRECTORY.equals(table.getValueAt(row, 2));
    }

    private static void openOnActivation(JTable table, Runnable open) {
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e) && table.rowAtPoint(e.getPoint()) >= 0) {
                    open.run();
                }
            }
        });
        table.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "openFolder");
        table.getActionMap().put("openFolder", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                open.run();
            }
        });
    }

    private static Object[] fileRow(String name, boolean folder, long bytes, String date) {
        return new Object[]{name, folder ? "—" : formatBytes(bytes), folder ? DIRECTORY : "Tệp tin", date};
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }

    private static DefaultTableModel fileModel() {
        return new DefaultTableModel(new String[]{"Tên", "Kích thước", "Loại", "Ngày sửa"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
    }

    private static JTable fileTable(DefaultTableModel model) {
        JTable table = new JTable(model);
        table.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        table.setRowHeight(31);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.setFillsViewportHeight(true);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getTableHeader().setReorderingAllowed(false);
        int[] widths = {205, 82, 78, 124};
        int[] minimums = {95, 82, 70, 117};
        for (int i = 0; i < widths.length; i++) {
            var column = table.getColumnModel().getColumn(i);
            column.setMinWidth(minimums[i]);
            column.setPreferredWidth(widths[i]);
        }
        Icon folderIcon = UIManager.getIcon("FileView.directoryIcon");
        Icon fileIcon = UIManager.getIcon("FileView.fileIcon");
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable view, Object value, boolean selected,
                                                           boolean focused, int row, int column) {
                super.getTableCellRendererComponent(view, value, selected, focused, row, column);
                boolean folder = DIRECTORY.equals(view.getValueAt(row, 2));
                setBackground(selected ? BLUE_TINT : row % 2 == 0 ? WHITE : new Color(0xF5F8FC));
                setForeground(column == 0 ? INK : MUTED);
                setFont(view.getFont().deriveFont(column == 0 && folder ? Font.BOLD : Font.PLAIN));
                setIcon(column == 0 ? folder ? folderIcon : fileIcon : null);
                setIconTextGap(7);
                setBorder(new EmptyBorder(0, 9, 0, 9));
                setToolTipText(getText());
                return this;
            }
        });
        table.getTableHeader().setDefaultRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable view, Object value, boolean selected,
                                                           boolean focused, int row, int column) {
                super.getTableCellRendererComponent(view, value, selected, focused, row, column);
                setBackground(new Color(0xECF2FA));
                setForeground(MUTED);
                setFont(view.getFont().deriveFont(Font.BOLD, 12f));
                setBorder(new EmptyBorder(7, 9, 7, 9));
                return this;
            }
        });
        return table;
    }

    private static JScrollPane tableScroll(JTable table) {
        JScrollPane scroll = new JScrollPane(table);
        scroll.setColumnHeaderView(table.getTableHeader());
        scroll.getViewport().setBackground(WHITE);
        scroll.setBorder(new LineBorder(LINE));
        return scroll;
    }

    private static JSplitPane split(int orientation, Component first, Component second, double weight) {
        JSplitPane pane = new JSplitPane(orientation, true, first, second);
        pane.setBackground(BACKGROUND);
        pane.setBorder(null);
        pane.setDividerSize(12);
        pane.setResizeWeight(weight);
        if (pane.getUI() instanceof javax.swing.plaf.basic.BasicSplitPaneUI ui) {
            ui.getDivider().setBorder(null);
            ui.getDivider().setBackground(BACKGROUND);
        }
        return pane;
    }

    private static JPanel card(int inset, int gap) {
        JPanel panel = new JPanel(new BorderLayout(0, gap));
        panel.setBackground(WHITE);
        panel.setBorder(new CompoundBorder(new LineBorder(LINE), new EmptyBorder(inset, inset, inset, inset)));
        return panel;
    }

    private static JPanel actions(JButton... buttons) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
        panel.setOpaque(false);
        for (JButton button : buttons) {
            if (panel.getComponentCount() > 0) panel.add(Box.createHorizontalStrut(7));
            panel.add(button);
        }
        return panel;
    }

    private static JLabel label(String text, int size, int style, Color color) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("Segoe UI", style, size));
        label.setForeground(color);
        return label;
    }

    private static JButton button(String title) {
        return button(title, BLUE_TINT, BLUE);
    }

    private static JButton button(String title, Color background, Color text) {
        JButton button = new JButton(title);
        button.setUI(new javax.swing.plaf.basic.BasicButtonUI() {
            @Override
            public void paint(Graphics graphics, JComponent component) {
                Graphics2D g = (Graphics2D) graphics.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                ButtonModel model = button.getModel();
                Color fill = !button.isEnabled() ? BACKGROUND : model.isPressed() ? background.darker()
                        : model.isRollover() && WHITE.equals(text) ? new Color(0x1945A7) : background;
                g.setColor(fill);
                g.fillRoundRect(0, 0, component.getWidth(), component.getHeight(), 9, 9);
                if (button.hasFocus()) {
                    g.setColor(new Color(0x6397F4));
                    g.drawRoundRect(1, 1, component.getWidth() - 3, component.getHeight() - 3, 8, 8);
                }
                g.dispose();
                super.paint(graphics, component);
            }

            @Override
            protected void paintText(Graphics graphics, AbstractButton component, Rectangle bounds, String value) {
                if (component.isEnabled()) {
                    super.paintText(graphics, component, bounds, value);
                } else {
                    graphics.setColor(MUTED);
                    graphics.drawString(value, bounds.x, bounds.y + graphics.getFontMetrics().getAscent());
                }
            }
        });
        button.setOpaque(false);
        button.setContentAreaFilled(false);
        button.setRolloverEnabled(true);
        button.setFont(new Font("Segoe UI", Font.BOLD, 12));
        button.setForeground(text);
        button.setBorder(new EmptyBorder(7, 12, 7, 12));
        return button;
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {}
            new FtpClientGUI().setVisible(true);
        });
    }
}
