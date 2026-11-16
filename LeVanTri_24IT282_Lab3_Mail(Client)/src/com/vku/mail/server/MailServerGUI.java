package com.vku.mail.server;

import com.vku.mail.model.MailAccount;
import com.vku.mail.model.MailLog;
import com.vku.mail.model.MailMessage;
import com.vku.mail.ui.MailTheme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

import static com.vku.mail.ui.MailTheme.*;

public class MailServerGUI extends JFrame {
    private final MailServer server;
    private final JTextArea protocolLog = logArea();
    private final DefaultTableModel messages = model("Thời gian", "Tiêu đề", "Người gửi", "Người nhận", "Nội dung", "ID thư");
    private final DefaultTableModel accounts = model("Tài khoản", "Họ tên", "Địa chỉ email");
    private final DefaultTableModel audit = model("Thời gian", "Giao thức", "Địa chỉ IP", "Chi tiết", "Trạng thái");

    private final CardLayout pages = new CardLayout();
    private final JPanel pagePanel = card(pages);
    private final JLabel heading = label("", 28, true, INK);
    private final JLabel description = label("", 13, false, MUTED);
    private final JLabel mailCount = label("0 thư", 21, true, INK);
    private final JButton action = button("Làm mới", false);
    private final JButton[] navigation = new JButton[Section.values().length];
    private Section current = Section.MESSAGES;

    private enum Section {
        MESSAGES("Dữ liệu thư"), ACCOUNTS("Tài khoản hộp thư"),
        PROTOCOL("Nhật ký giao thức"), AUDIT("Nhật ký cơ sở dữ liệu");

        final String title;

        Section(String title) {
            this.title = title;
        }
    }

    public MailServerGUI(MailServer server) {
        super("VKU Mail Server");
        this.server = server;
        this.server.setGui(this);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(960, 560));
        setSize(1160, 710);
        setLocationRelativeTo(null);

        JPanel root = panel(new BorderLayout(), CANVAS);
        root.add(createSidebar(), BorderLayout.WEST);
        root.add(createWorkspace(), BorderLayout.CENTER);
        setContentPane(root);
        select(Section.MESSAGES);
        refreshData();
    }

    private JPanel createSidebar() {
        JPanel sidebar = panel(new BorderLayout(), RAIL);
        sidebar.setPreferredSize(new Dimension(227, 0));
        sidebar.setBorder(new EmptyBorder(26, 16, 20, 16));
        JPanel menu = stack(null);
        JPanel brand = brand("VKU Mail Server", true);
        brand.setAlignmentX(LEFT_ALIGNMENT);
        menu.add(brand);
        menu.add(Box.createVerticalStrut(33));
        for (Section section : Section.values()) {
            JButton item = navButton(section.title, true);
            navigation[section.ordinal()] = item;
            item.addActionListener(e -> select(section));
            menu.add(item);
            menu.add(Box.createVerticalStrut(5));
        }
        sidebar.add(menu, BorderLayout.NORTH);

        JPanel identity = stack(null);
        identity.setBorder(new EmptyBorder(4, 10, 0, 5));
        identity.add(label("Địa chỉ máy chủ", 12, false, RAIL_TEXT));
        identity.add(Box.createVerticalStrut(5));
        identity.add(label(serverAddress(), 15, true, WHITE));
        sidebar.add(identity, BorderLayout.SOUTH);
        return sidebar;
    }

    private JPanel createWorkspace() {
        JPanel workspace = panel(new BorderLayout(0, 18), CANVAS);
        workspace.setBorder(new EmptyBorder(22, 24, 23, 24));
        JPanel top = panel(new BorderLayout(0, 19), null);
        JPanel toolbar = panel(new BorderLayout(10, 0), null);
        JPanel titles = stack(null);
        titles.add(heading);
        titles.add(Box.createVerticalStrut(4));
        titles.add(description);
        toolbar.add(titles, BorderLayout.CENTER);
        JPanel actions = panel(new FlowLayout(FlowLayout.RIGHT, 0, 7), null);
        action.addActionListener(e -> {
            if (current == Section.PROTOCOL) protocolLog.setText("");
            else refreshData();
        });
        actions.add(action);
        toolbar.add(actions, BorderLayout.EAST);
        top.add(toolbar, BorderLayout.NORTH);

        JPanel overview = panel(new GridLayout(1, 4, 11, 0), null);
        overview.add(metric("SMTP · Cổng gửi", label(Integer.toString(MailServer.SMTP_PORT), 21, true, INK)));
        overview.add(metric("POP3 · Cổng nhận", label(Integer.toString(MailServer.POP3_PORT), 21, true, INK)));
        boolean mysql = server.getDatabase().isUsingMySQL();
        overview.add(metric("Cơ sở dữ liệu", label(mysql ? "MySQL · db_mail" : "Tệp .dat", 17, true, mysql ? ACCENT : INK)));
        overview.add(metric("Thư đang lưu", mailCount));
        top.add(overview, BorderLayout.CENTER);
        workspace.add(top, BorderLayout.NORTH);

        pagePanel.add(tablePage(messages, 140, 184, 135, 135, 135, 134), Section.MESSAGES.name());
        pagePanel.add(tablePage(accounts, 150, 245, 345), Section.ACCOUNTS.name());
        pagePanel.add(scroll(protocolLog), Section.PROTOCOL.name());
        pagePanel.add(tablePage(audit, 148, 83, 104, 320, 100), Section.AUDIT.name());
        workspace.add(pagePanel, BorderLayout.CENTER);
        return workspace;
    }

    private JPanel metric(String title, JLabel value) {
        JPanel card = card(new BorderLayout(0, 7));
        card.setBorder(BorderFactory.createCompoundBorder(card.getBorder(), new EmptyBorder(12, 13, 13, 13)));
        card.add(label(title, 12, true, MUTED), BorderLayout.NORTH);
        card.add(value, BorderLayout.CENTER);
        return card;
    }

    private JScrollPane tablePage(DefaultTableModel model, int... widths) {
        JTable table = table(model);
        table.setAutoCreateRowSorter(true);
        for (int i = 0; i < widths.length; i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }
        return scroll(table);
    }

    private void select(Section section) {
        current = section;
        for (Section item : Section.values()) {
            selectNav(navigation[item.ordinal()], section == item, true);
        }
        heading.setText(section.title);
        pages.show(pagePanel, section.name());
        action.setText(section == Section.PROTOCOL ? "Xóa nội dung hiển thị" : "Làm mới");
        updateDescription();
    }

    private void updateDescription() {
        description.setText(switch (current) {
            case MESSAGES -> messages.getRowCount() + " thư";
            case ACCOUNTS -> accounts.getRowCount() + " tài khoản";
            case PROTOCOL -> "Kết nối SMTP và POP3 theo thời gian thực";
            case AUDIT -> audit.getRowCount() + " giao dịch";
        });
    }

    public void appendLog(String message) {
        SwingUtilities.invokeLater(() -> {
            protocolLog.append(message + "\n");
            protocolLog.setCaretPosition(protocolLog.getDocument().getLength());
        });
    }

    public void refreshData() {
        SwingUtilities.invokeLater(() -> {
            messages.setRowCount(0);
            List<MailMessage> stored = server.getDatabase().getAllMessages();
            for (int i = stored.size() - 1; i >= 0; i--) {
                MailMessage mail = stored.get(i);
                String body = mail.getBody() == null ? "" : mail.getBody().replace('\n', ' ');
                messages.addRow(new Object[]{mail.getFormattedDate(), mail.getSubject(), mail.getSender(),
                        mail.getRecipient(), body, mail.getId()});
            }
            mailCount.setText(messages.getRowCount() + " thư");

            accounts.setRowCount(0);
            for (MailAccount account : server.getDatabase().getAllAccounts()) {
                accounts.addRow(new Object[]{account.getUsername(), account.getFullName(), account.getEmail()});
            }

            audit.setRowCount(0);
            List<MailLog> logs = server.getDatabase().getAllLogs();
            for (int i = logs.size() - 1; i >= 0; i--) {
                MailLog log = logs.get(i);
                audit.addRow(new Object[]{log.getFormattedDate(), log.getProtocol(), log.getClientIp(),
                        log.getDetails(), log.getStatus()});
            }
            updateDescription();
        });
    }

    private static String serverAddress() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            return "127.0.0.1";
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            MailTheme.install();
            MailServer server = new MailServer();
            new MailServerGUI(server).setVisible(true);
            server.start();
        });
    }
}
