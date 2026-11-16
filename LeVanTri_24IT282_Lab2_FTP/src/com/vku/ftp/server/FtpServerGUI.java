package com.vku.ftp.server;

import com.vku.ftp.common.FtpLog;
import com.vku.ftp.common.FtpUser;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.File;
import java.util.Comparator;
import java.util.List;

public class FtpServerGUI extends JFrame {
    private static final Color BACKGROUND = new Color(0xF2F6FA);
    private static final Color WHITE = Color.WHITE;
    private static final Color INK = new Color(0x172740);
    private static final Color MUTED = new Color(0x566782);
    private static final Color LINE = new Color(0xDFE7F1);
    private static final Color NAVY = new Color(0x11223B);
    private static final Color NAV_TEXT = new Color(0xC0CFE2);
    private static final Color BLUE = new Color(0x2155C4);
    private static final Color BLUE_TINT = new Color(0xE8F0FF);
    private static final Color GREEN = new Color(0x087554);
    private static final Color ORANGE = new Color(0x9B5108);

    private final FtpServer server;
    private final CardLayout pagesLayout = new CardLayout();
    private final JPanel pages = new JPanel(pagesLayout);
    private final ButtonGroup navigation = new ButtonGroup();
    private final JLabel clientsLabel = label("0", 21, Font.BOLD, INK);
    private final JLabel userCount = label("", 12, Font.PLAIN, MUTED);
    private final JLabel historyCount = label("", 12, Font.PLAIN, MUTED);
    private final JTextArea logArea = new JTextArea();
    private final DefaultTableModel usersModel = model("Tài khoản", "Thư mục gốc", "Ghi tệp", "Xóa tệp", "Trạng thái");
    private final HistoryModel logsModel = new HistoryModel();
    private final JTable usersTable = table(usersModel, 150, 240, 90, 90, 112);
    private final JTable logsTable = table(logsModel, 155, 105, 97, 105, 185, 112, 75);
    private final CardLayout historyLayout = new CardLayout();
    private final JPanel historyContent = new JPanel(historyLayout);

    public FtpServerGUI(FtpServer server) {
        super("VKU FTP · Quản lý máy chủ");
        this.server = server;
        server.setGui(this);
        initUI();
        refreshData();
    }

    private void initUI() {
        setSize(1150, 735);
        setMinimumSize(new Dimension(890, 550));
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        JPanel content = new JPanel(new BorderLayout(0, 20));
        content.setBackground(BACKGROUND);
        content.setBorder(new EmptyBorder(25, 27, 25, 27));
        content.add(createOverview(), BorderLayout.NORTH);
        pages.setOpaque(false);
        pages.add(createHistoryPage(), "history");
        pages.add(createLivePage(), "live");
        pages.add(createUsersPage(), "users");
        content.add(pages, BorderLayout.CENTER);
        add(createSidebar(), BorderLayout.WEST);
        add(content, BorderLayout.CENTER);
    }

    private JPanel createSidebar() {
        JPanel sidebar = new JPanel(new BorderLayout());
        sidebar.setBackground(NAVY);
        sidebar.setBorder(new EmptyBorder(28, 16, 24, 16));
        sidebar.setPreferredSize(new Dimension(205, 0));

        JPanel menu = vertical();
        JPanel brand = new JPanel(new BorderLayout(11, 0));
        brand.setOpaque(false);
        brand.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel mark = label("FTP", 12, Font.BOLD, WHITE);
        mark.setHorizontalAlignment(SwingConstants.CENTER);
        mark.setOpaque(true);
        mark.setBackground(BLUE);
        mark.setPreferredSize(new Dimension(42, 39));
        brand.add(mark, BorderLayout.WEST);
        brand.add(label("VKU FTP", 23, Font.BOLD, WHITE), BorderLayout.CENTER);
        menu.add(brand);
        menu.add(Box.createVerticalStrut(44));
        menu.add(navigationItem("Lịch sử hoạt động", "history", true));
        menu.add(Box.createVerticalStrut(6));
        menu.add(navigationItem("Nhật ký trực tiếp", "live", false));
        menu.add(Box.createVerticalStrut(6));
        menu.add(navigationItem("Tài khoản FTP", "users", false));
        sidebar.add(menu, BorderLayout.NORTH);

        JLabel folder = label("Thư mục gốc · ftp_root", 12, Font.PLAIN, NAV_TEXT);
        folder.setBorder(new EmptyBorder(8, 3, 0, 0));
        sidebar.add(folder, BorderLayout.SOUTH);
        return sidebar;
    }

    private JToggleButton navigationItem(String title, String page, boolean selected) {
        JToggleButton item = new JToggleButton(title);
        styleButton(item, new Color(0x203856), NAV_TEXT);
        item.setHorizontalAlignment(SwingConstants.LEFT);
        item.setAlignmentX(Component.LEFT_ALIGNMENT);
        item.setMaximumSize(new Dimension(Integer.MAX_VALUE, 43));
        item.setPreferredSize(new Dimension(173, 43));
        Runnable updateStyle = () -> {
            item.setBackground(item.isSelected() ? new Color(0x25487C) : NAVY);
            item.setForeground(item.isSelected() ? WHITE : NAV_TEXT);
        };
        navigation.add(item);
        item.addItemListener(e -> updateStyle.run());
        item.setSelected(selected);
        updateStyle.run();
        item.addActionListener(e -> {
            pagesLayout.show(pages, page);
            if (!"live".equals(page)) refreshData();
        });
        return item;
    }

    private JPanel createOverview() {
        JPanel overview = vertical();
        overview.add(label("Quản lý máy chủ FTP", 29, Font.BOLD, INK));
        overview.add(Box.createVerticalStrut(17));

        JPanel summary = new JPanel(new GridLayout(1, 3, 12, 0));
        summary.setOpaque(false);
        summary.setAlignmentX(Component.LEFT_ALIGNMENT);
        summary.add(summaryCard("CỔNG FTP", label(Integer.toString(server.getPort()), 21, Font.BOLD, INK)));
        summary.add(summaryCard("MÁY KHÁCH ĐANG KẾT NỐI", clientsLabel));

        boolean mysqlActive = server.getDatabase().isUsingMySQL();
        JLabel storage = label(mysqlActive ? "MySQL · db_ftp" : "Tệp cục bộ (.dat)",
                21, Font.BOLD, mysqlActive ? GREEN : ORANGE);
        JPanel databaseCard = summaryCard("NGUỒN DỮ LIỆU", storage);
        if (!mysqlActive) {
            String error = server.getDatabase().getMySQLError();
            databaseCard.setToolTipText(error);
            storage.setToolTipText(error);
        }
        summary.add(databaseCard);
        overview.add(summary);
        return overview;
    }

    private JPanel summaryCard(String title, JLabel value) {
        JPanel card = vertical();
        card.setOpaque(true);
        card.setBackground(WHITE);
        card.setBorder(new CompoundBorder(new LineBorder(LINE), new EmptyBorder(13, 15, 13, 15)));
        card.add(label(title, 11, Font.BOLD, MUTED));
        card.add(Box.createVerticalStrut(4));
        card.add(value);
        return card;
    }

    private JPanel createHistoryPage() {
        JButton refresh = button("Làm mới", true);
        refresh.addActionListener(e -> refreshData());
        historyContent.add(scroll(logsTable), "rows");
        JLabel empty = label("Chưa có hoạt động FTP được ghi lại", 14, Font.PLAIN, MUTED);
        empty.setOpaque(true);
        empty.setBackground(BACKGROUND);
        empty.setHorizontalAlignment(SwingConstants.CENTER);
        empty.setBorder(new LineBorder(LINE));
        historyContent.add(empty, "empty");
        return page("Lịch sử hoạt động", historyCount, actions(refresh), historyContent);
    }

    private JPanel createLivePage() {
        logArea.setEditable(false);
        logArea.setFont(new Font("Consolas", Font.PLAIN, 13));
        logArea.setBackground(NAVY);
        logArea.setForeground(new Color(0xDCE7F8));
        logArea.setMargin(new Insets(14, 16, 14, 16));
        JScrollPane terminal = scroll(logArea);
        terminal.getViewport().setBackground(NAVY);

        JButton open = button("Mở thư mục", true);
        open.addActionListener(e -> {
            try {
                Desktop.getDesktop().open(new File("ftp_root"));
            } catch (Exception ex) {
                showError("Không thể mở thư mục: " + ex.getMessage());
            }
        });
        JButton clear = button("Xóa nội dung", false);
        clear.addActionListener(e -> logArea.setText(""));
        return page("Nhật ký trực tiếp", label("Thông báo từ máy chủ FTP", 12, Font.PLAIN, MUTED),
                actions(clear, open), terminal);
    }

    private JPanel createUsersPage() {
        JButton add = button("Thêm tài khoản", true);
        add.addActionListener(e -> showAddUserDialog());
        JButton delete = button("Xóa", false);
        delete.setEnabled(false);
        usersTable.getSelectionModel().addListSelectionListener(e -> {
            int row = usersTable.getSelectedRow();
            delete.setEnabled(row >= 0 && !"admin".equalsIgnoreCase((String) usersTable.getValueAt(row, 0)));
        });
        delete.addActionListener(e -> deleteSelectedUser());
        JButton refresh = button("Làm mới", false);
        refresh.addActionListener(e -> refreshData());
        return page("Tài khoản FTP", userCount, actions(refresh, delete, add), scroll(usersTable));
    }

    private JPanel page(String title, JLabel detail, JPanel actions, JComponent body) {
        JPanel panel = new JPanel(new BorderLayout(0, 17));
        panel.setBackground(WHITE);
        panel.setBorder(new CompoundBorder(new LineBorder(LINE), new EmptyBorder(18, 18, 18, 18)));
        JPanel header = new JPanel(new BorderLayout(12, 0));
        header.setOpaque(false);
        JPanel heading = vertical();
        heading.add(label(title, 21, Font.BOLD, INK));
        heading.add(Box.createVerticalStrut(4));
        heading.add(detail);
        header.add(heading, BorderLayout.CENTER);
        header.add(actions, BorderLayout.EAST);
        panel.add(header, BorderLayout.NORTH);
        panel.add(body, BorderLayout.CENTER);
        return panel;
    }

    private void deleteSelectedUser() {
        int row = usersTable.getSelectedRow();
        if (row < 0) return;
        String username = (String) usersTable.getValueAt(row, 0);
        if ("admin".equalsIgnoreCase(username)) return;
        int answer = JOptionPane.showConfirmDialog(this, "Xóa tài khoản FTP “" + username + "”?",
                "Xóa tài khoản", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
        if (answer == JOptionPane.OK_OPTION) {
            if (server.getDatabase().deleteUser(username)) refreshData();
            else showError("Không thể xóa tài khoản. Hãy kiểm tra kết nối hoặc quyền ghi dữ liệu.");
        }
    }

    private void showAddUserDialog() {
        JPanel form = new JPanel(new GridLayout(0, 2, 10, 10));
        form.setBorder(new EmptyBorder(8, 0, 8, 0));
        JTextField username = new JTextField(17);
        JPasswordField password = new JPasswordField(17);
        JTextField home = new JTextField("ftp_root", 17);
        JCheckBox write = new JCheckBox("Cho phép ghi tệp", true);
        JCheckBox delete = new JCheckBox("Cho phép xóa tệp");
        form.add(new JLabel("Tên đăng nhập"));
        form.add(username);
        form.add(new JLabel("Mật khẩu"));
        form.add(password);
        form.add(new JLabel("Thư mục gốc"));
        form.add(home);
        form.add(write);
        form.add(delete);

        while (JOptionPane.showConfirmDialog(this, form, "Thêm tài khoản FTP", JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION) {
            String name = username.getText().trim();
            String pass = new String(password.getPassword()).trim();
            String directory = home.getText().trim();
            if (name.isEmpty() || pass.isEmpty()) {
                showError("Nhập tên đăng nhập và mật khẩu.");
            } else if (server.getDatabase().getAllUsers().stream().anyMatch(u -> u.getUsername().equalsIgnoreCase(name))) {
                showError("Tên đăng nhập đã tồn tại.");
            } else if (server.getDatabase().addUser(new FtpUser(name, pass, directory.isEmpty() ? "ftp_root" : directory,
                    write.isSelected(), delete.isSelected()))) {
                refreshData();
                return;
            } else {
                showError("Không thể lưu tài khoản. Hãy kiểm tra kết nối hoặc quyền ghi dữ liệu.");
            }
        }
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message, "Quản lý máy chủ FTP", JOptionPane.ERROR_MESSAGE);
    }

    public void appendLog(String message) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(message + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    public void updateClientCount(int count) {
        SwingUtilities.invokeLater(() -> clientsLabel.setText(Integer.toString(count)));
    }

    public void refreshData() {
        SwingUtilities.invokeLater(() -> {
            List<FtpUser> users = server.getDatabase().getAllUsers();
            users.sort(Comparator.comparing(FtpUser::getUsername, String.CASE_INSENSITIVE_ORDER));
            usersModel.setRowCount(0);
            for (FtpUser user : users) {
                usersModel.addRow(new Object[]{user.getUsername(), user.getHomeDirectory(),
                        user.isCanWrite() ? "Có" : "Không", user.isCanDelete() ? "Có" : "Không",
                        user.isEnabled() ? "Đã bật" : "Đã tắt"});
            }
            userCount.setText(users.size() + " tài khoản");
            updateHistory();
        });
    }

    public void refreshHistory() {
        SwingUtilities.invokeLater(this::updateHistory);
    }

    private void updateHistory() {
        List<FtpLog> logs = server.getDatabase().getAllLogs();
        if (logs.size() == logsModel.getRowCount() && !historyCount.getText().isEmpty()) return;
        logs.sort(Comparator.comparingLong(FtpLog::getTimestamp).reversed());
        logsModel.setLogs(logs);
        historyCount.setText(String.format("%,d bản ghi · Mới nhất trước", logs.size()));
        historyLayout.show(historyContent, logs.isEmpty() ? "empty" : "rows");
    }

    private static class HistoryModel extends DefaultTableModel {
        HistoryModel() {
            super(new String[]{"Thời gian", "IP máy khách", "Tài khoản", "Thao tác", "Tệp / đường dẫn", "Trạng thái", "Số byte"}, 0);
        }

        void setLogs(List<FtpLog> logs) {
            dataVector.clear();
            for (FtpLog log : logs) {
                String path = log.getTargetPath();
                dataVector.add(convertToVector(new Object[]{log.getFormattedTime(), log.getClientIp(), log.getUsername(),
                        log.getAction(), path == null || path.isEmpty() ? "—" : path, log.getStatus(), log.getBytesTransferred()}));
            }
            fireTableDataChanged();
        }

        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    }

    private static DefaultTableModel model(String... columns) {
        return new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
    }

    private static JTable table(DefaultTableModel model, int... widths) {
        JTable table = new JTable(model);
        table.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        table.setRowHeight(34);
        table.setFillsViewportHeight(true);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getTableHeader().setReorderingAllowed(false);
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable view, Object value, boolean selected,
                                                           boolean focused, int row, int column) {
                super.getTableCellRendererComponent(view, value, selected, focused, row, column);
                setBackground(selected ? BLUE_TINT : row % 2 == 0 ? WHITE : new Color(0xF5F8FC));
                boolean positive = "SUCCESS".equals(value) || "Đã bật".equals(value);
                boolean failed = "FAILED".equals(value);
                setText(positive && "SUCCESS".equals(value) ? "Thành công" : failed ? "Thất bại" : getText());
                if (value instanceof Long) setText(String.format("%,d", value));
                setHorizontalAlignment(value instanceof Long ? RIGHT : LEFT);
                setForeground(positive ? GREEN : failed ? new Color(0xA82D3F) : INK);
                setFont(view.getFont().deriveFont(positive || failed ? Font.BOLD : Font.PLAIN));
                setBorder(new EmptyBorder(0, 10, 0, 10));
                setToolTipText(getText());
                return this;
            }
        });
        DefaultTableCellRenderer header = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable view, Object value, boolean selected,
                                                           boolean focused, int row, int column) {
                super.getTableCellRendererComponent(view, value, selected, focused, row, column);
                setBackground(new Color(0xECF2FA));
                setForeground(MUTED);
                setFont(view.getFont().deriveFont(Font.BOLD, 12f));
                setBorder(new EmptyBorder(9, 10, 9, 10));
                return this;
            }
        };
        table.getTableHeader().setDefaultRenderer(header);
        for (int i = 0; i < widths.length; i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }
        return table;
    }

    private static JScrollPane scroll(Component view) {
        JScrollPane scroll = new JScrollPane(view);
        if (view instanceof JTable table) scroll.setColumnHeaderView(table.getTableHeader());
        scroll.setBorder(new LineBorder(LINE));
        scroll.getViewport().setBackground(WHITE);
        return scroll;
    }

    private static JPanel vertical() {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        return panel;
    }

    private static JPanel actions(JButton... buttons) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 7, 5));
        panel.setOpaque(false);
        for (JButton button : buttons) panel.add(button);
        return panel;
    }

    private static JLabel label(String text, int size, int style, Color color) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("Segoe UI", style, size));
        label.setForeground(color);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        label.setMaximumSize(new Dimension(Integer.MAX_VALUE, label.getFontMetrics(label.getFont()).getHeight()));
        return label;
    }

    private static JButton button(String title, boolean primary) {
        JButton button = new JButton(title);
        styleButton(button, primary ? BLUE : BLUE_TINT, primary ? WHITE : BLUE);
        return button;
    }

    private static void styleButton(AbstractButton button, Color background, Color foreground) {
        button.setUI(new javax.swing.plaf.basic.BasicButtonUI() {
            @Override
            public void paint(Graphics graphics, JComponent component) {
                Graphics2D g = (Graphics2D) graphics.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color color = component.getBackground();
                if (button.getModel().isRollover() && button.isEnabled()) {
                    color = color.equals(NAVY) ? new Color(0x203856) : color.darker();
                }
                g.setColor(color);
                g.fillRoundRect(0, 0, component.getWidth(), component.getHeight(), 9, 9);
                if (button.hasFocus()) {
                    g.setColor(new Color(0x6397F4));
                    g.drawRoundRect(1, 1, component.getWidth() - 3, component.getHeight() - 3, 8, 8);
                }
                g.dispose();
                super.paint(graphics, component);
            }
        });
        button.setOpaque(false);
        button.setContentAreaFilled(false);
        button.setRolloverEnabled(true);
        button.setBackground(background);
        button.setForeground(foreground);
        button.setFont(new Font("Segoe UI", Font.BOLD, 13));
        button.setBorder(new EmptyBorder(9, 14, 9, 14));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    public static void main(String[] args) {
        FtpServer server = new FtpServer();
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {}
            FtpServerGUI gui = new FtpServerGUI(server);
            gui.setVisible(true);
            server.start();
        });
    }
}
