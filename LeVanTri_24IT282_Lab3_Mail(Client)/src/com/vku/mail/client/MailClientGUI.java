package com.vku.mail.client;

import com.vku.mail.model.MailMessage;
import com.vku.mail.ui.MailTheme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import static com.vku.mail.ui.MailTheme.*;

public class MailClientGUI extends JFrame {
    private static final DateTimeFormatter LIST_DATE = DateTimeFormatter.ofPattern("dd/MM HH:mm")
            .withZone(ZoneId.systemDefault());
    private static final Font READING_FONT = new Font(Font.DIALOG, Font.PLAIN, 15);

    private MailClient client;
    private final List<MailMessage> messages = new ArrayList<>();
    private MailMessage selectedMessage;
    private boolean sent;
    private final Map<Boolean, List<MailMessage>> folderCache = new HashMap<>();
    private final Set<Boolean> loadingFolders = new HashSet<>();
    private boolean sentNeedsRefresh;
    private final Set<MailMessage.ImapLocation> loadingDetails = new HashSet<>();
    private MailMessage.ImapLocation cachedDetailLocation;
    private MailMessage cachedDetail;
    private final List<JButton> replyButtons = new ArrayList<>();

    private final JLabel account = label("", 13, true, INK);
    private final JLabel accountMode = label("", 12, false, MUTED);
    private final JLabel folderTitle = label("", 28, true, INK);
    private final JLabel folderStatus = label("", 13, false, MUTED);
    private final JButton inboxButton = navButton("Hộp thư đến", false);
    private final JButton sentButton = navButton("Thư đã gửi", false);
    private final JButton refreshButton = button("Làm mới", false);
    private final DefaultTableModel tableModel = model("Người gửi", "Tiêu đề", "Thời gian");
    private final JTable table = table(tableModel);

    private final CardLayout mailboxLayout = new CardLayout();
    private final JPanel mailbox = panel(mailboxLayout, CANVAS);
    private final CardLayout listLayout = new CardLayout();
    private final JPanel listContent = card(listLayout);
    private final JLabel emptyMessage = label("", 17, true, MUTED);
    private final JButton backButton = button("←  Quay lại", false);
    private final JLabel messageSubject = label("", 21, true, INK);
    private final JLabel messageFrom = label("", 13, false, INK);
    private final JLabel messageTo = label("", 13, false, MUTED);
    private final JLabel messageDate = label("", 12, false, MUTED);
    private final JTextArea messageBody = new JTextArea();
    private final JButton attachmentButton = button("Tải tệp đính kèm", false);
    private final JLabel attachmentLabel = label("", 12, false, MUTED);

    private final JTextArea protocolLog = logArea();
    private JDialog logDialog;

    public MailClientGUI() {
        this(defaultClient());
    }

    MailClientGUI(MailClient connection) {
        super("VKU Mail");
        client = connection;
        attachLog(client);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(910, 610));
        setSize(1200, 760);
        setLocationRelativeTo(null);
        JPanel root = panel(new BorderLayout(), CANVAS);
        root.add(createSidebar(), BorderLayout.WEST);
        mailbox.setBorder(new EmptyBorder(22, 6, 20, 22));
        mailbox.add(createMailbox(), "list");
        mailbox.add(createReader(), "detail");
        root.add(mailbox, BorderLayout.CENTER);
        setContentPane(root);
        updateAccountLabel();
        showFolder(false);
    }

    private static MailClient defaultClient() {
        MailClient connection = new MailClient();
        connection.configureLocal("alice", "123456", "alice@vku.udn.vn");
        return connection;
    }

    private JPanel createSidebar() {
        JPanel sidebar = panel(new BorderLayout(0, 18), CANVAS);
        sidebar.setPreferredSize(new Dimension(225, 0));
        sidebar.setBorder(new EmptyBorder(25, 19, 20, 15));

        JPanel nav = stack(null);
        JPanel brand = brand("VKU Mail", false);
        brand.setAlignmentX(LEFT_ALIGNMENT);
        nav.add(brand);
        nav.add(Box.createVerticalStrut(26));

        JButton compose = button("+  Soạn thư", true);
        compose.setHorizontalAlignment(SwingConstants.LEFT);
        compose.setAlignmentX(LEFT_ALIGNMENT);
        compose.setMaximumSize(new Dimension(Integer.MAX_VALUE, 48));
        compose.addActionListener(e -> openComposeDialog());
        nav.add(compose);
        nav.add(Box.createVerticalStrut(24));

        inboxButton.addActionListener(e -> showFolder(false));
        sentButton.addActionListener(e -> showFolder(true));
        nav.add(inboxButton);
        nav.add(Box.createVerticalStrut(4));
        nav.add(sentButton);
        nav.add(Box.createVerticalStrut(18));
        JSeparator separator = new JSeparator();
        separator.setAlignmentX(LEFT_ALIGNMENT);
        separator.setForeground(BORDER);
        separator.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        nav.add(separator);
        nav.add(Box.createVerticalStrut(12));
        JButton settings = navButton("Cài đặt tài khoản", false);
        settings.addActionListener(e -> showConfigDialog());
        JButton logs = navButton("Nhật ký giao thức", false);
        logs.addActionListener(e -> showLogDialog());
        nav.add(settings);
        nav.add(Box.createVerticalStrut(4));
        nav.add(logs);
        sidebar.add(nav, BorderLayout.NORTH);

        JPanel identity = card(new BorderLayout(0, 5));
        identity.setBorder(BorderFactory.createCompoundBorder(identity.getBorder(),
                new EmptyBorder(11, 12, 12, 12)));
        identity.add(accountMode, BorderLayout.NORTH);
        identity.add(account, BorderLayout.CENTER);
        sidebar.add(identity, BorderLayout.SOUTH);
        return sidebar;
    }

    private JPanel createMailbox() {
        JPanel workspace = panel(new BorderLayout(0, 16), CANVAS);
        JPanel toolbar = panel(new BorderLayout(), null);
        JPanel headings = stack(null);
        headings.add(folderTitle);
        headings.add(Box.createVerticalStrut(3));
        headings.add(folderStatus);
        toolbar.add(headings, BorderLayout.CENTER);
        JPanel actions = panel(new FlowLayout(FlowLayout.RIGHT, 0, 8), null);
        refreshButton.addActionListener(e -> refreshFolder());
        actions.add(refreshButton);
        toolbar.add(actions, BorderLayout.EAST);
        workspace.add(toolbar, BorderLayout.NORTH);

        table.getColumnModel().getColumn(0).setPreferredWidth(200);
        table.getColumnModel().getColumn(1).setPreferredWidth(460);
        table.getColumnModel().getColumn(2).setPreferredWidth(120);
        table.getColumnModel().getColumn(2).setMinWidth(91);
        table.setFont(READING_FONT.deriveFont(13f));
        table.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        table.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) openRow(table.rowAtPoint(e.getPoint()));
            }
        });
        table.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "open-mail");
        table.getActionMap().put("open-mail", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                openRow(table.getSelectedRow());
            }
        });

        listContent.add(scroll(table), "messages");
        JPanel empty = panel(new GridBagLayout(), WHITE);
        empty.add(emptyMessage);
        listContent.add(empty, "empty");
        workspace.add(listContent, BorderLayout.CENTER);
        return workspace;
    }

    private JPanel createReader() {
        JPanel detail = panel(new BorderLayout(0, 16), CANVAS);
        JPanel controls = panel(new FlowLayout(FlowLayout.LEFT, 0, 0), null);
        backButton.addActionListener(e -> returnToList());
        controls.add(backButton);
        detail.add(controls, BorderLayout.NORTH);
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "back-to-list");
        getRootPane().getActionMap().put("back-to-list", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                if (selectedMessage != null) returnToList();
            }
        });

        JPanel reading = card(new BorderLayout(0, 14));
        reading.setBorder(BorderFactory.createCompoundBorder(reading.getBorder(),
                new EmptyBorder(24, 28, 20, 28)));
        JPanel header = panel(new BorderLayout(0, 10), null);
        messageSubject.setFont(READING_FONT.deriveFont(Font.BOLD, 23f));
        header.add(messageSubject, BorderLayout.NORTH);
        JPanel metadata = panel(new GridLayout(0, 1, 0, 3), null);
        metadata.add(messageFrom);
        metadata.add(messageTo);
        metadata.add(messageDate);
        header.add(metadata, BorderLayout.CENTER);
        JSeparator line = new JSeparator();
        line.setForeground(BORDER);
        header.add(line, BorderLayout.SOUTH);
        reading.add(header, BorderLayout.NORTH);

        messageBody.setFont(READING_FONT);
        messageBody.setEditable(false);
        messageBody.setLineWrap(true);
        messageBody.setWrapStyleWord(true);
        messageBody.setForeground(INK);
        messageBody.setBackground(WHITE);
        messageBody.setBorder(new EmptyBorder(2, 0, 8, 8));
        reading.add(scroll(messageBody), BorderLayout.CENTER);
        JPanel attachment = panel(new BorderLayout(8, 0), null);
        attachmentButton.addActionListener(e -> saveAttachment());
        attachment.add(attachmentLabel, BorderLayout.CENTER);
        attachment.add(attachmentButton, BorderLayout.EAST);
        JPanel footer = panel(new BorderLayout(0, 9), null);
        footer.add(attachment, BorderLayout.NORTH);
        JPanel buttons = panel(null, null);
        buttons.setLayout(new BoxLayout(buttons, BoxLayout.X_AXIS));
        for (MailDraft.Action action : MailDraft.Action.values()) {
            JButton actionButton = button(action.title, false);
            replyButtons.add(actionButton);
            actionButton.setIcon(replyIcon(action));
            actionButton.setIconTextGap(7);
            actionButton.addActionListener(e -> {
                if (selectedMessage != null) {
                    openComposeDialog(MailDraft.forMessage(action, selectedMessage, client.getEmailAddress(), sent));
                }
            });
            if (buttons.getComponentCount() > 0) buttons.add(Box.createHorizontalStrut(8));
            buttons.add(actionButton);
        }
        JPanel actions = panel(new BorderLayout(), null);
        actions.add(buttons, BorderLayout.WEST);
        footer.add(actions, BorderLayout.CENTER);
        reading.add(footer, BorderLayout.SOUTH);
        detail.add(reading, BorderLayout.CENTER);
        return detail;
    }

    private static Icon replyIcon(MailDraft.Action action) {
        return new Icon() {
            @Override public int getIconWidth() { return 19; }
            @Override public int getIconHeight() { return 15; }

            @Override public void paintIcon(Component component, Graphics graphics, int x, int y) {
                Graphics2D g = (Graphics2D) graphics.create();
                g.translate(x, y);
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(component.getForeground());
                g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                if (action == MailDraft.Action.FORWARD) {
                    g.translate(getIconWidth(), 0);
                    g.scale(-1, 1);
                }
                Path2D path = new Path2D.Double();
                double start = action == MailDraft.Action.REPLY_ALL ? 6.5 : 3;
                path.moveTo(start + 4, 2);
                path.lineTo(start, 6);
                path.lineTo(start + 4, 10);
                path.moveTo(start, 6);
                path.lineTo(11.5, 6);
                path.quadTo(16, 6, 16, 12);
                if (action == MailDraft.Action.REPLY_ALL) {
                    path.moveTo(6.5, 2);
                    path.lineTo(2.5, 6);
                    path.lineTo(6.5, 10);
                }
                g.draw(path);
                g.dispose();
            }
        };
    }

    private void showFolder(boolean showSent) {
        sent = showSent;
        selectNav(inboxButton, !sent, false);
        selectNav(sentButton, sent, false);
        folderTitle.setText(sent ? "Thư đã gửi" : "Hộp thư đến");
        table.getColumnModel().getColumn(0).setHeaderValue(sent ? "Người nhận" : "Người gửi");
        table.getTableHeader().repaint();
        returnToList();
        List<MailMessage> cached = folderCache.get(sent);
        renderFolder(cached == null ? List.of() : cached);
        if (loadingFolders.contains(sent)) {
            showFolderLoading();
        } else if (cached == null || !client.isGmailMode()) {
            refreshFolder();
        }
    }

    private void refreshFolder() {
        MailClient connection = client;
        boolean showSent = sent;
        if (!loadingFolders.add(showSent)) return;
        showFolderLoading();

        new SwingWorker<List<MailMessage>, Void>() {
            @Override protected List<MailMessage> doInBackground() throws IOException {
                List<MailMessage> result = new ArrayList<>(connection.fetchFolder(showSent));
                result.sort(Comparator.comparingLong(MailMessage::getTimestamp).reversed());
                return result;
            }

            @Override protected void done() {
                if (client != connection) return;
                loadingFolders.remove(showSent);
                boolean visibleFolder = sent == showSent;
                if (visibleFolder) refreshButton.setEnabled(true);
                try {
                    List<MailMessage> result = get();
                    folderCache.put(showSent, result);
                    if (visibleFolder) renderFolder(result);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    if (visibleFolder) showLoadError("Tải thư bị gián đoạn.");
                } catch (ExecutionException e) {
                    String reason = e.getCause().getMessage();
                    if (visibleFolder) showLoadError(reason == null ? "Xem nhật ký giao thức." : reason);
                }
                if (showSent && sentNeedsRefresh) {
                    sentNeedsRefresh = false;
                    if (visibleFolder) refreshFolder();
                    else folderCache.remove(true);
                }
            }
        }.execute();
    }

    private void renderFolder(List<MailMessage> result) {
        table.clearSelection();
        tableModel.setRowCount(0);
        messages.clear();
        messages.addAll(result);
        for (MailMessage mail : messages) {
            String contact = sent ? mail.getRecipient() : mail.getSender();
            tableModel.addRow(new Object[] {
                    MailDisplayText.readable(contactName(contact), table.getFont()),
                    MailDisplayText.readable(mail.getSubject(), table.getFont().deriveFont(Font.BOLD)),
                    LIST_DATE.format(Instant.ofEpochMilli(mail.getTimestamp())) });
        }
        folderStatus.setForeground(MUTED);
        folderStatus.setToolTipText(null);
        folderStatus.setText(messages.size() + " thư");
        refreshButton.setEnabled(!loadingFolders.contains(sent));
        if (messages.isEmpty()) {
            showEmpty(sent ? "Chưa có thư đã gửi" : "Hộp thư đến trống");
        } else {
            listLayout.show(listContent, "messages");
        }
    }

    private void showFolderLoading() {
        if (messages.isEmpty()) showEmpty("Đang tải danh sách thư…");
        folderStatus.setForeground(MUTED);
        folderStatus.setText(messages.isEmpty() ? "Đang tải danh sách…" : messages.size() + " thư · Đang làm mới…");
        folderStatus.setToolTipText(null);
        refreshButton.setEnabled(false);
    }

    private void showLoadError(String reason) {
        folderStatus.setForeground(ERROR_TEXT);
        folderStatus.setText(reason);
        folderStatus.setToolTipText(reason);
        if (messages.isEmpty()) showEmpty("Không tải được thư");
    }

    private void showEmpty(String text) {
        emptyMessage.setText(text);
        listLayout.show(listContent, "empty");
    }

    private void openRow(int viewRow) {
        if (viewRow < 0 || viewRow >= table.getRowCount()) return;
        MailMessage summary = messages.get(table.convertRowIndexToModel(viewRow));
        MailMessage.ImapLocation location = summary.getImapLocation();
        if (location == null) {
            displayMessage(summary);
            return;
        }
        if (location.equals(cachedDetailLocation)) {
            displayMessage(cachedDetail);
            return;
        }
        displayMessage(summary);
        messageBody.setText("Đang tải nội dung thư…");
        if (!loadingDetails.add(location)) return;

        MailClient connection = client;
        new SwingWorker<MailMessage, Void>() {
            @Override protected MailMessage doInBackground() throws IOException {
                return connection.fetchMessage(summary);
            }

            @Override protected void done() {
                if (client != connection) return;
                loadingDetails.remove(location);
                boolean stillOpen = selectedMessage != null && location.equals(selectedMessage.getImapLocation());
                try {
                    MailMessage full = get();
                    if (stillOpen) {
                        cachedDetailLocation = location;
                        cachedDetail = full;
                        displayMessage(full);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    if (stillOpen) messageBody.setText("Tải nội dung bị gián đoạn.");
                } catch (ExecutionException e) {
                    if (stillOpen) {
                        String reason = e.getCause().getMessage();
                        messageBody.setText("Không tải được nội dung thư. "
                                + (reason == null ? "Hãy mở lại thư để thử lại." : reason));
                    }
                }
            }
        }.execute();
    }

    private void returnToList() {
        boolean returning = selectedMessage != null;
        selectedMessage = null;
        mailboxLayout.show(mailbox, "list");
        if (returning) table.requestFocusInWindow();
    }

    private void displayMessage(MailMessage mail) {
        selectedMessage = mail;
        messageSubject.setText(MailDisplayText.readable(mail.getSubject(), messageSubject.getFont()));
        messageSubject.setToolTipText("Tiêu đề  " + messageSubject.getText());
        messageFrom.setText("Từ  " + MailDisplayText.readable(mail.getSender(), messageFrom.getFont()));
        messageTo.setText("Đến  " + MailDisplayText.readable(mail.getRecipient(), messageTo.getFont()));
        messageFrom.setToolTipText(messageFrom.getText());
        messageTo.setToolTipText(messageTo.getText());
        messageDate.setText(mail.getFormattedDate());
        messageBody.setText(MailDisplayText.readable(mail.getBody(), READING_FONT));
        messageBody.setCaretPosition(0);
        attachmentLabel.setText(mail.getAttachmentName() == null ? "" : mail.getAttachmentName());
        attachmentButton.setVisible(mail.getAttachmentData() != null);
        replyButtons.forEach(button -> button.setEnabled(mail.getImapLocation() == null));
        mailboxLayout.show(mailbox, "detail");
        backButton.requestFocusInWindow();
    }

    private void saveAttachment() {
        MailMessage mail = selectedMessage;
        if (mail == null || mail.getAttachmentData() == null) return;
        JFileChooser chooser = new JFileChooser();
        String fileName = new File(mail.getAttachmentName()).getName();
        chooser.setSelectedFile(new File(fileName));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File destination = chooser.getSelectedFile();
        if (destination.exists() && JOptionPane.showConfirmDialog(this, "Ghi đè tệp đã tồn tại?",
                "Lưu tệp đính kèm", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        try {
            Files.write(destination.toPath(), mail.getAttachmentData());
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Không lưu được tệp. " + e.getMessage(), "Lưu tệp",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void openComposeDialog() {
        openComposeDialog(MailDraft.blank());
    }

    private void openComposeDialog(MailDraft draft) {
        MailClient connection = client;
        ComposeDialog dialog = new ComposeDialog(this, connection.getEmailAddress(), connection.isGmailMode(), draft,
                data -> connection.sendMailError(data.to, data.subject, data.body, data.attachName, data.attachData,
                        draft.inReplyTo(), draft.references()),
                () -> {
                    if (client == connection) {
                        if (loadingFolders.contains(true)) sentNeedsRefresh = true;
                        showFolder(true);
                        refreshFolder();
                    }
                });
        dialog.setVisible(true);
    }

    private void updateAccountLabel() {
        account.setText(client.getEmailAddress());
        account.setToolTipText(client.getEmailAddress());
        accountMode.setText(client.isGmailMode() ? "Google Gmail" : "Cục bộ · " + client.getSmtpHost());
    }

    private void showConfigDialog() {
        JPanel form = stack(WHITE);
        form.setBorder(new EmptyBorder(4, 2, 8, 2));
        JComboBox<String> mode = new JComboBox<>(new String[] {"Máy chủ cục bộ / LAN", "Google Gmail"});
        JTextField host = new JTextField(client.isGmailMode() ? "127.0.0.1" : client.getSmtpHost(), 27);
        JTextField email = new JTextField(client.getEmailAddress());
        JPasswordField password = new JPasswordField();
        JLabel passwordLabel = label("", 13, true, INK);
        JLabel title = label("Kết nối hộp thư", 21, true, INK);
        form.add(title);
        addField(form, label("Chế độ", 13, true, INK), mode);
        addField(form, label("Địa chỉ máy chủ cục bộ", 13, true, INK), host);
        addField(form, label("Email", 13, true, INK), email);
        addField(form, passwordLabel, password);
        mode.setSelectedIndex(client.isGmailMode() ? 1 : 0);
        Runnable updateMode = () -> {
            boolean gmail = mode.getSelectedIndex() == 1;
            host.setEnabled(!gmail);
            passwordLabel.setText(gmail ? "Mật khẩu ứng dụng Google" : "Mật khẩu hộp thư");
        };
        updateMode.run();
        mode.addActionListener(e -> {
            boolean gmail = mode.getSelectedIndex() == 1;
            email.setText(gmail ? "" : "alice@vku.udn.vn");
            password.setText("");
            updateMode.run();
        });

        while (JOptionPane.showConfirmDialog(this, form, "Cài đặt tài khoản",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION) {
            String address = email.getText().strip();
            String pass = new String(password.getPassword());
            if (address.isBlank() || pass.isBlank()) {
                JOptionPane.showMessageDialog(this, "Nhập email và mật khẩu để kết nối.");
                continue;
            }
            MailClient connection = new MailClient();
            if (mode.getSelectedIndex() == 0) {
                String user = address.split("@", 2)[0];
                connection.configureLocal(host.getText(), 2525, 1100, user, pass, address);
            } else {
                connection.configureGmail(address, pass);
            }
            attachLog(connection);
            client = connection;
            folderCache.clear();
            loadingFolders.clear();
            sentNeedsRefresh = false;
            loadingDetails.clear();
            cachedDetailLocation = null;
            cachedDetail = null;
            updateAccountLabel();
            showFolder(false);
            return;
        }
    }

    private static void addField(JPanel form, JLabel caption, JComponent input) {
        form.add(Box.createVerticalStrut(12));
        caption.setAlignmentX(LEFT_ALIGNMENT);
        form.add(caption);
        form.add(Box.createVerticalStrut(4));
        input.setAlignmentX(LEFT_ALIGNMENT);
        input.setMaximumSize(new Dimension(Integer.MAX_VALUE, input.getPreferredSize().height + 8));
        form.add(input);
    }

    private void attachLog(MailClient connection) {
        connection.setLogListener(entry -> SwingUtilities.invokeLater(() -> {
            protocolLog.append(entry + "\n");
            protocolLog.setCaretPosition(protocolLog.getDocument().getLength());
        }));
    }

    private void showLogDialog() {
        if (logDialog == null) {
            logDialog = new JDialog(this, "Nhật ký giao thức", false);
            logDialog.setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
            logDialog.add(scroll(protocolLog));
            logDialog.setSize(760, 450);
            logDialog.setLocationRelativeTo(this);
        }
        logDialog.setVisible(true);
        logDialog.toFront();
    }

    private static String contactName(String address) {
        if (address == null) return "";
        int mark = address.indexOf('<');
        if (mark > 0) return address.substring(0, mark).strip().replace("\"", "");
        return address;
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            MailTheme.install();
            new MailClientGUI().setVisible(true);
        });
    }
}
