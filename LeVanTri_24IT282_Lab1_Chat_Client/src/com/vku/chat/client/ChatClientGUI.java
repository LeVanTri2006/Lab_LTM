package com.vku.chat.client;

import com.vku.chat.model.ChatMessage;
import com.vku.chat.model.OnlineAccount;
import com.vku.chat.model.Room;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.HyperlinkEvent;
import javax.swing.text.Element;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLEditorKit;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

import static com.vku.chat.client.ChatTheme.*;
import static com.vku.chat.client.ChatTheme.Glyph.*;

public class ChatClientGUI extends JFrame {
    private static final Pattern EMOJI_TEXT = Pattern.compile("[\\x{1F300}-\\x{1FAFF}\\x{2600}-\\x{27BF}]\\uFE0F?");
    private volatile ChatClient client;
    private String currentTarget = "general"; // "general" or username or room id
    private boolean isPrivate = false;

    // UI Components
    private JLabel profileNameLabel;
    private JLabel profileStatusLabel;
    private JLabel profileAvatar;
    private JLabel headerAvatar;
    private JLabel headerTitleLabel;
    private JLabel headerSubLabel;
    private JTextPane chatPane;
    private HTMLEditorKit htmlKit;
    private HTMLDocument htmlDoc;
    private JTextField inputField;
    private DefaultListModel<OnlineAccount> onlineUsersModel;
    private JList<OnlineAccount> onlineUsersList;
    private DefaultListModel<Room> channelModel;
    private JList<Room> channelList;
    private JButton createGroupButton;
    private JButton addMemberButton;
    private JButton voiceCallButton;
    private JButton videoCallButton;
    private JTextField searchField;
    private JLabel onlineCount;
    private JButton chatShortcut;
    private JPanel chatShortcutArea;
    private JPanel messageStage;
    private final CardLayout messageCards = new CardLayout();
    private final List<Room> availableRooms = new ArrayList<>();
    private List<OnlineAccount> availableAccounts = List.of();
    private final Map<String, OnlineAccount> recentContacts = new LinkedHashMap<>();
    private final Map<String, Unread> unreadByConversation = new LinkedHashMap<>();
    private final LinkedHashSet<String> recentStudyMessages = new LinkedHashSet<>();
    private boolean refreshingLists;

    private final Map<String, Attachment> attachments = new HashMap<>();
    private final Map<String, Path> outgoingFiles = new HashMap<>();
    private final Map<String, byte[]> voiceNotes = new HashMap<>();
    private boolean playingVoiceNote;
    private final Map<String, StringBuilder> conversationHtml = new HashMap<>();
    private String serverHost = "localhost";
    private int serverPort = 8888;
    private CallDialog activeCallDialog;

    private record Unread(int messages, int missedCalls) {
        int total() { return messages + missedCalls; }
    }

    public ChatClientGUI(String initialUser, String initialPass) {
        this(initialUser, initialPass, "localhost");
    }

    public ChatClientGUI(String initialUser, String initialPass, String host) {
        super("VKU Chat - Messenger (LTM)");
        if (host != null && !host.trim().isEmpty()) {
            this.serverHost = host.trim();
        }
        initUI();
        if (initialUser != null && !initialUser.isEmpty()) {
            SwingUtilities.invokeLater(() -> performLogin(this.serverHost, initialUser, initialPass));
        } else {
            SwingUtilities.invokeLater(this::showLoginDialog);
        }
    }

    private void initUI() {
        setSize(1160, 760);
        setMinimumSize(new Dimension(890, 600));
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent event) {
                if (activeCallDialog != null) activeCallDialog.disconnected();
                if (client != null) client.disconnect();
            }
        });
        addWindowFocusListener(new WindowAdapter() {
            @Override public void windowGainedFocus(WindowEvent event) {
                markRead(currentConversation(), false);
            }
        });

        JPanel root = new JPanel(new BorderLayout());
        JPanel left = new JPanel(new BorderLayout());
        left.add(createRail(), BorderLayout.WEST);
        left.add(createSidebar(), BorderLayout.CENTER);
        root.add(left, BorderLayout.WEST);
        root.add(createChatArea(), BorderLayout.CENTER);
        setContentPane(root);
        refreshLists();
        updateRoomHeader(availableRooms.get(0));
    }

    private JPanel createRail() {
        JPanel rail = new JPanel(new BorderLayout());
        rail.setPreferredSize(new Dimension(66, 0));
        rail.setBackground(NAV);
        rail.setBorder(new EmptyBorder(19, 10, 22, 10));
        chatShortcut = button("", CHAT, BLUE, Color.WHITE);
        chatShortcut.setToolTipText("Tìm cuộc trò chuyện");
        chatShortcut.getAccessibleContext().setAccessibleName("Tìm cuộc trò chuyện");
        chatShortcut.addActionListener(event -> openLatestUnread());
        chatShortcutArea = new JPanel(new BorderLayout()) {
            @Override protected void paintChildren(Graphics graphics) {
                super.paintChildren(graphics);
                paintCountBadge(graphics, getWidth() - 1, 0, unreadTotal(), 16);
            }
        };
        chatShortcutArea.setOpaque(false);
        chatShortcutArea.add(chatShortcut, BorderLayout.CENTER);
        rail.add(chatShortcutArea, BorderLayout.NORTH);
        profileAvatar = new JLabel(avatar("VKU", "vku", 40), SwingConstants.CENTER);
        profileAvatar.setToolTipText("Chưa đăng nhập");
        rail.add(profileAvatar, BorderLayout.SOUTH);
        return rail;
    }

    private JPanel createSidebar() {
        JPanel sidebar = new JPanel(new BorderLayout());
        sidebar.setPreferredSize(new Dimension(330, 0));
        sidebar.setBackground(Color.WHITE);
        sidebar.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, LINE));

        JPanel sidebarTop = new JPanel(new BorderLayout(0, 13));
        sidebarTop.setBackground(Color.WHITE);
        sidebarTop.setBorder(new EmptyBorder(19, 17, 12, 17));
        JPanel title = new JPanel(new BorderLayout(7, 0));
        title.setOpaque(false);
        title.add(label("Tin nhắn", 24, Font.BOLD, INK), BorderLayout.WEST);
        createGroupButton = button("Tạo nhóm", PLUS, SOFT, NAV);
        createGroupButton.addActionListener(event -> showGroupDialog(null));
        title.add(createGroupButton, BorderLayout.EAST);
        sidebarTop.add(title, BorderLayout.NORTH);

        JPanel searchBox = new JPanel(new BorderLayout(8, 0));
        searchBox.setBackground(new Color(0xF0F4F9));
        searchBox.setBorder(new EmptyBorder(3, 10, 3, 10));
        searchBox.add(new JLabel(icon(SEARCH, MUTED, 17)), BorderLayout.WEST);
        searchField = textField("Tìm theo tên, tài khoản, IP");
        searchField.setOpaque(false);
        onTextChanged(searchField, this::refreshLists);
        searchBox.add(searchField, BorderLayout.CENTER);
        sidebarTop.add(searchBox, BorderLayout.CENTER);
        sidebar.add(sidebarTop, BorderLayout.NORTH);

        availableRooms.add(new Room("general", "Phòng chung",
                "Kênh trò chuyện chung của máy chủ", false, null, 0));
        availableRooms.add(new Room("study", "Nhóm học tập",
                "Kênh thảo luận môn học", true, "224.2.2.3", 8889));
        availableRooms.add(new Room("tech", "Công nghệ & dự án",
                "Kênh trao đổi kỹ thuật", true, "224.2.2.4", 8890));

        channelModel = new DefaultListModel<>();
        channelList = new JList<>(channelModel);
        configureList(channelList);
        channelList.setCellRenderer(new ConversationRenderer(true));
        markReadOnRowClick(channelList);
        channelList.addListSelectionListener(event -> {
            Room room = channelList.getSelectedValue();
            if (refreshingLists || event.getValueIsAdjusting() || room == null) return;
            onlineUsersList.clearSelection();
            if (client != null && client.isConnected() && room.isMulticastRoom()
                    && (isPrivate || !room.getId().equals(currentTarget))) {
                ChatMessage join = new ChatMessage(ChatMessage.Type.JOIN_ROOM, client.getCurrentUser(), "");
                join.setRoom(room.getId());
                client.send(join);
            }
            selectConversation(room.getId(), false);
            updateRoomHeader(room);
        });

        onlineUsersModel = new DefaultListModel<>();
        onlineUsersList = new JList<>(onlineUsersModel);
        configureList(onlineUsersList);
        onlineUsersList.setCellRenderer(new ConversationRenderer(true));
        markReadOnRowClick(onlineUsersList);
        onlineUsersList.addListSelectionListener(event -> {
            OnlineAccount account = onlineUsersList.getSelectedValue();
            if (refreshingLists || event.getValueIsAdjusting() || account == null) return;
            recentContacts.put(account.username(), account);
            channelList.clearSelection();
            selectConversation(account.username(), true);
            updatePrivateHeader(account);
        });

        onlineCount = label("", 12, Font.PLAIN, MUTED);
        JPanel rooms = sidebarSection("Kênh và nhóm", null, channelList);
        JPanel accounts = sidebarSection("Tài khoản", onlineCount, onlineUsersList);
        JSplitPane lists = new JSplitPane(JSplitPane.VERTICAL_SPLIT, rooms, accounts);
        lists.setBorder(new EmptyBorder(0, 0, 0, 0));
        lists.setDividerSize(5);
        lists.setDividerLocation(211);
        lists.setResizeWeight(0.36);
        sidebar.add(lists, BorderLayout.CENTER);

        JPanel profile = new JPanel(new GridLayout(2, 1, 0, 2));
        profile.setBackground(Color.WHITE);
        profile.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, LINE), new EmptyBorder(12, 18, 13, 18)));
        profileNameLabel = label("Chưa đăng nhập", 14, Font.BOLD, INK);
        profileStatusLabel = label("Ngoại tuyến", 12, Font.PLAIN, MUTED);
        profile.add(profileNameLabel);
        profile.add(profileStatusLabel);
        sidebar.add(profile, BorderLayout.SOUTH);
        return sidebar;
    }

    private JPanel sidebarSection(String heading, JLabel count, JList<?> list) {
        JPanel section = new JPanel(new BorderLayout());
        section.setBackground(Color.WHITE);
        JPanel title = new JPanel(new BorderLayout());
        title.setBackground(Color.WHITE);
        title.setBorder(new EmptyBorder(8, 18, 6, 18));
        title.add(label(heading, 13, Font.BOLD, MUTED), BorderLayout.WEST);
        if (count != null) title.add(count, BorderLayout.EAST);
        section.add(title, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(list, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        section.add(scroll, BorderLayout.CENTER);
        section.setMinimumSize(new Dimension(0, 96));
        return section;
    }

    private void configureList(JList<?> list) {
        list.setBackground(Color.WHITE);
        list.setFixedCellHeight(54);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setBorder(new EmptyBorder(0, 8, 0, 8));
    }

    private void markReadOnRowClick(JList<?> list) {
        list.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent event) {
                if (!SwingUtilities.isLeftMouseButton(event)) return;
                int index = list.locationToIndex(event.getPoint());
                if (index >= 0 && list.getCellBounds(index, index).contains(event.getPoint())) {
                    Object value = list.getModel().getElementAt(index);
                    String conversation = value instanceof Room room ? room.getId() : "@" + ((OnlineAccount) value).username();
                    markRead(conversation, true);
                }
            }
        });
    }

    private void refreshLists() {
        String query = searchField.getText().strip().toLowerCase(Locale.ROOT);
        refreshingLists = true;
        try {
            channelModel.clear();
            for (Room room : availableRooms) {
                if (room.getName().toLowerCase(Locale.ROOT).contains(query)) {
                    channelModel.addElement(room);
                    if (!isPrivate && room.getId().equals(currentTarget)) channelList.setSelectedIndex(channelModel.size() - 1);
                }
            }
            onlineUsersModel.clear();
            Map<String, OnlineAccount> accounts = new LinkedHashMap<>();
            availableAccounts.forEach(account -> accounts.put(account.username(), account));
            recentContacts.forEach(accounts::putIfAbsent);
            for (OnlineAccount account : accounts.values()) {
                if ((account + " " + account.ip()).toLowerCase(Locale.ROOT).contains(query)) {
                    onlineUsersModel.addElement(account);
                    if (isPrivate && account.username().equals(currentTarget)) {
                        onlineUsersList.setSelectedIndex(onlineUsersModel.size() - 1);
                    }
                }
            }
            onlineCount.setText(availableAccounts.size() + " trực tuyến");
        } finally {
            refreshingLists = false;
        }
    }

    private final class ConversationRenderer extends DefaultListCellRenderer {
        private final boolean showUnread;
        private int unreadCount;

        ConversationRenderer(boolean showUnread) {
            this.showUnread = showUnread;
        }

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                      boolean selected, boolean focused) {
            super.getListCellRendererComponent(list, "", index, selected, focused);
            setBackground(selected ? SOFT : Color.WHITE);
            setIconTextGap(10);
            setFont(font(13, Font.PLAIN));
            String title;
            String detail;
            String key;
            String conversation;
            if (value instanceof OnlineAccount account) {
                title = account.fullName();
                key = account.username();
                conversation = "@" + key;
                detail = "@" + key + "  ·  " + (!showUnread || findOnlineAccount(key) != null ? account.ip() : "Ngoại tuyến");
            } else if (value instanceof Room room) {
                title = room.getName();
                detail = room.isCustomGroup() ? room.getMembers().size() + " thành viên" : room.getDescription();
                key = room.getId();
                conversation = key;
            } else {
                unreadCount = 0;
                return this;
            }
            Unread unread = showUnread ? unreadByConversation.get(conversation) : null;
            unreadCount = unread == null ? 0 : unread.total();
            boolean missed = unread != null && unread.missedCalls() > 0;
            if (missed) detail = "Cuộc gọi nhỡ" + (unread.missedCalls() > 1 ? " · " + unread.missedCalls() : "");
            setBorder(new EmptyBorder(5, 10, 5, unreadCount > 0 ? 49 : 10));
            setIcon(avatar(title, key, 39));
            setText("<html><b style='color:#14233D'>" + escapeHtml(title)
                    + "</b><br><span style='color:" + (missed ? "#B42340" : "#5D708D") + ";font-size:10pt'>"
                    + escapeHtml(detail) + "</span></html>");
            getAccessibleContext().setAccessibleName(title + ", " + detail
                    + (unreadCount == 0 ? "" : ", " + unreadCount + " thông báo chưa đọc"));
            return this;
        }

        @Override protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            paintCountBadge(graphics, getWidth() - 10, (getHeight() - 21) / 2, unreadCount, 21);
        }
    }

    private Room currentRoom() {
        if (isPrivate) return null;
        return availableRooms.stream().filter(room -> room.getId().equals(currentTarget)).findFirst().orElse(null);
    }

    private void updateGroupButtons() {
        Room room = currentRoom();
        boolean connected = client != null && client.isConnected();
        createGroupButton.setEnabled(connected);
        addMemberButton.setVisible(room != null && room.isCustomGroup());
        addMemberButton.setEnabled(connected);
        boolean canCall = connected && isPrivate && activeCallDialog == null && findOnlineAccount(currentTarget) != null;
        voiceCallButton.setEnabled(canCall);
        videoCallButton.setEnabled(canCall);
    }

    private void updatePrivateHeader(OnlineAccount account) {
        boolean online = findOnlineAccount(account.username()) != null;
        headerTitleLabel.setText(account.fullName());
        headerSubLabel.setText("@" + account.username() + (online ? "  ·  Trực tuyến" : "  ·  Ngoại tuyến"));
        headerSubLabel.setToolTipText(online ? "IP " + account.ip() : null);
        headerAvatar.setIcon(avatar(account.fullName(), account.username(), 46));
        updateGroupButtons();
    }

    private void updateRoomHeader(Room room) {
        headerTitleLabel.setText(room.getName());
        headerAvatar.setIcon(avatar(room.getName(), room.getId(), 46));
        if (room.isCustomGroup()) {
            String members = String.join(", ", room.getMembers().stream().sorted().map(user -> "@" + user).toList());
            headerSubLabel.setText(room.getMembers().size() + " thành viên  ·  " + members);
            headerSubLabel.setToolTipText(members);
        } else {
            headerSubLabel.setText(room.getDescription());
            headerSubLabel.setToolTipText(null);
        }
        updateGroupButtons();
    }

    private void showGroupDialog(Room group) {
        if (client == null || !client.isConnected()) return;
        boolean creating = group == null;
        JTextField nameField = new JTextField(24);
        JTextField ipField = new JTextField(24);
        JPanel inputs = new JPanel(new GridLayout(0, 1, 0, 8));
        if (creating) inputs.add(labeledField("Tên nhóm", nameField));
        inputs.add(labeledField("Địa chỉ IP (tùy chọn)", ipField));

        DefaultListModel<OnlineAccount> options = new DefaultListModel<>();
        JList<OnlineAccount> online = new JList<>(options);
        configureList(online);
        online.setCellRenderer(new ConversationRenderer(false));
        online.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        online.setVisibleRowCount(4);
        online.getAccessibleContext().setAccessibleName("Tài khoản đang trực tuyến");
        JLabel hint = label("", 12, Font.PLAIN, MUTED);
        Runnable refresh = () -> {
            String ip = ipField.getText().strip();
            options.clear();
            availableAccounts.stream()
                    .filter(account -> group == null || !group.hasMember(account.username()))
                    .filter(account -> ip.isEmpty() || account.matchesIp(ip))
                    .forEach(options::addElement);
            hint.setText(options.isEmpty() ? "Không có tài khoản phù hợp để thêm."
                    : "Giữ Ctrl để chọn nhiều tài khoản.");
        };
        onTextChanged(ipField, refresh);
        refresh.run();

        JPanel members = new JPanel(new BorderLayout(0, 5));
        members.add(label("Tài khoản đang trực tuyến", 13, Font.BOLD, INK), BorderLayout.NORTH);
        members.add(new JScrollPane(online), BorderLayout.CENTER);
        members.add(hint, BorderLayout.SOUTH);
        JPanel fields = new JPanel(new BorderLayout(0, 11));
        fields.add(inputs, BorderLayout.NORTH);
        fields.add(members, BorderLayout.CENTER);

        String title = creating ? "Tạo nhóm" : "Thêm thành viên · " + group.getName();
        while (JOptionPane.showConfirmDialog(this, fields, title, JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION) {
            if (!client.isConnected()) return;
            String ip = ipField.getText().strip();
            String name = nameField.getText().strip();
            List<String> invited = online.getSelectedValuesList().stream().map(OnlineAccount::username).toList();
            String error = null;
            if (creating && (name.isEmpty() || name.length() > 60)) error = "Tên nhóm phải có từ 1 đến 60 ký tự.";
            else if (!creating && ip.isEmpty() && invited.isEmpty()) error = "Nhập IP hoặc chọn tài khoản đang trực tuyến.";
            else if (!ip.isEmpty() && options.size() > 1 && invited.isEmpty()) error = "Hãy chọn tài khoản tại IP này.";
            if (error != null) {
                JOptionPane.showMessageDialog(this, error, title, JOptionPane.WARNING_MESSAGE);
                continue;
            }
            ChatMessage request = new ChatMessage(creating ? ChatMessage.Type.CREATE_GROUP : ChatMessage.Type.ADD_GROUP_MEMBER,
                    client.getCurrentUser(), name);
            request.setMemberIp(ip);
            request.setMemberUsernames(invited);
            if (group != null) request.setRoom(group.getId());
            client.send(request);
            return;
        }
    }

    private JPanel createChatArea() {
        JPanel conversation = new JPanel(new BorderLayout());
        conversation.setBackground(CHAT_BACKGROUND);
        JPanel header = new JPanel(new BorderLayout(14, 0));
        header.setBackground(Color.WHITE);
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, LINE), new EmptyBorder(15, 20, 15, 20)));

        headerAvatar = new JLabel();
        header.add(headerAvatar, BorderLayout.WEST);
        JPanel identity = new JPanel(new GridLayout(2, 1, 0, 3));
        identity.setOpaque(false);
        headerTitleLabel = label("", 18, Font.BOLD, INK);
        headerSubLabel = label("", 12, Font.PLAIN, MUTED);
        identity.add(headerTitleLabel);
        identity.add(headerSubLabel);
        header.add(identity, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 1));
        actions.setOpaque(false);
        addMemberButton = button("Thêm thành viên", ADD_USER, SOFT, NAV);
        addMemberButton.addActionListener(event -> showGroupDialog(currentRoom()));
        voiceCallButton = button("Gọi thoại", PHONE, SOFT, NAV);
        voiceCallButton.addActionListener(event -> startCall(false));
        videoCallButton = button("Gọi video", VIDEO, SOFT, NAV);
        videoCallButton.addActionListener(event -> startCall(true));
        actions.add(addMemberButton);
        actions.add(voiceCallButton);
        actions.add(videoCallButton);
        header.add(actions, BorderLayout.EAST);
        conversation.add(header, BorderLayout.NORTH);

        chatPane = new JTextPane();
        chatPane.setEditable(false);
        chatPane.setBackground(CHAT_BACKGROUND);
        chatPane.setMargin(new Insets(15, 16, 14, 16));
        htmlKit = new HTMLEditorKit();
        chatPane.setEditorKit(htmlKit);
        chatPane.addHyperlinkListener(event -> {
            if (event.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                activateMessageLink(event.getDescription());
            }
        });
        initChatPaneContent();

        JScrollPane chatScroll = new JScrollPane(chatPane);
        chatScroll.setBorder(BorderFactory.createEmptyBorder());
        chatScroll.getVerticalScrollBar().setUnitIncrement(22);
        chatScroll.getViewport().setBackground(CHAT_BACKGROUND);
        messageStage = new JPanel(messageCards);
        messageStage.add(chatScroll, "messages");

        JPanel empty = new JPanel(new GridBagLayout());
        empty.setBackground(CHAT_BACKGROUND);
        JPanel emptyContent = new JPanel(new GridLayout(3, 1, 0, 9));
        emptyContent.setOpaque(false);
        JLabel mark = new JLabel(icon(CHAT, BLUE, 46), SwingConstants.CENTER);
        JLabel emptyTitle = label("Bắt đầu trò chuyện", 21, Font.BOLD, INK);
        emptyTitle.setHorizontalAlignment(SwingConstants.CENTER);
        JLabel emptyText = label("Gửi tin nhắn đầu tiên trong cuộc trò chuyện này.", 13, Font.PLAIN, MUTED);
        emptyText.setHorizontalAlignment(SwingConstants.CENTER);
        emptyContent.add(mark);
        emptyContent.add(emptyTitle);
        emptyContent.add(emptyText);
        empty.add(emptyContent);
        messageStage.add(empty, "empty");
        messageCards.show(messageStage, "empty");
        conversation.add(messageStage, BorderLayout.CENTER);

        JPanel composer = new JPanel(new BorderLayout());
        composer.setBackground(Color.WHITE);
        composer.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, LINE));

        JPanel tools = new JPanel(new FlowLayout(FlowLayout.LEFT, 3, 5));
        tools.setOpaque(false);
        tools.setBorder(new EmptyBorder(0, 13, 0, 13));
        tools.add(toolButton("Biểu cảm", EMOJI, this::showEmojiPicker));
        tools.add(toolButton("Đính kèm tệp", ATTACH, this::selectAndSendFile));
        tools.add(toolButton("Ghi âm", MIC, this::recordVoiceNote));
        composer.add(tools, BorderLayout.NORTH);

        JPanel inputRow = new JPanel(new BorderLayout(10, 0));
        inputRow.setBackground(Color.WHITE);
        inputRow.setBorder(new EmptyBorder(0, 16, 15, 16));
        inputField = textField("Nhập tin nhắn…");
        inputField.setBackground(new Color(0xF1F5FA));
        inputField.setBorder(new EmptyBorder(11, 13, 11, 13));
        inputField.addActionListener(event -> sendTextMessage());
        inputRow.add(inputField, BorderLayout.CENTER);
        JButton send = button("Gửi", SEND, BLUE, Color.WHITE);
        send.addActionListener(event -> sendTextMessage());
        inputRow.add(send, BorderLayout.EAST);
        composer.add(inputRow, BorderLayout.SOUTH);
        conversation.add(composer, BorderLayout.SOUTH);
        return conversation;
    }

    private JButton toolButton(String description, Glyph glyph, Runnable action) {
        JButton result = button("", glyph, Color.WHITE, NAV);
        result.setToolTipText(description);
        result.getAccessibleContext().setAccessibleName(description);
        result.addActionListener(event -> action.run());
        return result;
    }

    private JTextField textField(String prompt) {
        JTextField field = new JTextField() {
            @Override protected void paintComponent(Graphics graphics) {
                super.paintComponent(graphics);
                if (!getText().isEmpty()) return;
                Graphics2D g = (Graphics2D) graphics.create();
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g.setColor(MUTED);
                g.setFont(getFont());
                FontMetrics metrics = g.getFontMetrics();
                g.drawString(prompt, getInsets().left, (getHeight() + metrics.getAscent() - metrics.getDescent()) / 2);
                g.dispose();
            }
        };
        field.getAccessibleContext().setAccessibleName(prompt);
        field.setForeground(INK);
        field.setFont(font(14, Font.PLAIN));
        field.setBorder(new EmptyBorder(7, 0, 7, 0));
        return field;
    }

    private static void onTextChanged(JTextField field, Runnable callback) {
        field.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent event) { callback.run(); }
            @Override public void removeUpdate(DocumentEvent event) { callback.run(); }
            @Override public void changedUpdate(DocumentEvent event) { callback.run(); }
        });
    }

    private static JPanel labeledField(String title, JTextField field) {
        JPanel row = new JPanel(new BorderLayout(0, 5));
        row.setOpaque(false);
        JLabel caption = label(title, 13, Font.BOLD, MUTED);
        caption.setLabelFor(field);
        row.add(caption, BorderLayout.NORTH);
        row.add(field, BorderLayout.CENTER);
        return row;
    }

    private void showLoginDialog() {
        createLoginDialog().setVisible(true);
    }

    private JDialog createLoginDialog() {
        JDialog login = new JDialog(this, "Đăng nhập VKU Chat", true);
        login.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        login.addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent event) { dispose(); }
        });
        JPanel panel = new JPanel(new BorderLayout(0, 20));
        panel.setBackground(Color.WHITE);
        panel.setBorder(new EmptyBorder(22, 26, 24, 26));
        login.setContentPane(panel);

        JLabel title = label("Đăng nhập VKU Chat", 22, Font.BOLD, INK);
        title.setIcon(icon(CHAT, BLUE, 25));
        title.setIconTextGap(9);
        panel.add(title, BorderLayout.NORTH);

        JPanel form = new JPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        form.setOpaque(false);
        JTextField hostField = textField("IP của máy chủ");
        hostField.setText(serverHost);
        JTextField userField = textField("Tên đăng nhập");
        JPasswordField passField = new JPasswordField();
        for (JTextField field : List.of(hostField, userField, passField)) {
            field.setFont(font(14, Font.PLAIN));
            field.setBackground(ChatTheme.CHAT_BACKGROUND);
            field.setBorder(new EmptyBorder(11, 12, 11, 12));
            field.setColumns(26);
        }
        form.add(labeledField("Máy chủ", hostField));
        form.add(Box.createVerticalStrut(13));
        form.add(labeledField("Tên đăng nhập", userField));
        form.add(Box.createVerticalStrut(13));
        form.add(labeledField("Mật khẩu", passField));
        panel.add(form, BorderLayout.CENTER);

        JButton signIn = button("Đăng nhập", null, BLUE, Color.WHITE);
        signIn.addActionListener(event -> {
            String username = userField.getText().strip();
            if (username.isEmpty()) {
                userField.requestFocusInWindow();
                return;
            }
            String host = hostField.getText().trim();
            serverHost = host.isEmpty() ? "localhost" : host;
            login.dispose();
            performLogin(serverHost, username, new String(passField.getPassword()));
        });
        panel.add(signIn, BorderLayout.SOUTH);
        login.getRootPane().setDefaultButton(signIn);
        login.setResizable(false);
        login.pack();
        login.setLocationRelativeTo(this);
        SwingUtilities.invokeLater(userField::requestFocusInWindow);
        return login;
    }

    private void performLogin(String host, String username, String password) {
        new Thread(() -> {
            ChatClient connection = new ChatClient(host, serverPort);
            client = connection;
            AtomicReference<String> statusText = new AtomicReference<>();
            connection.setMessageListener(this::onMessageReceived);
            connection.setStatusListener(status -> {
                statusText.set(status);
                SwingUtilities.invokeLater(() -> {
                    if (!connection.isConnected() && connection.getCurrentUser() != null) {
                        if (activeCallDialog != null) activeCallDialog.disconnected();
                        profileStatusLabel.setText("@" + connection.getCurrentUser() + " · Bạn · Ngoại tuyến");
                        availableAccounts = List.of();
                        refreshLists();
                        updateGroupButtons();
                    }
                    if (connection.getCurrentUser() != null) appendHtml(noticeHtml(status));
                });
            });

            boolean ok = connection.connect(username, password, username);
            SwingUtilities.invokeLater(() -> {
                if (ok) {
                    String account = connection.getCurrentFullName();
                    String handle = "@" + connection.getCurrentUser();
                    profileNameLabel.setText(account);
                    profileNameLabel.setToolTipText(handle);
                    profileStatusLabel.setText(handle + " · Bạn · " + (connection.isConnected() ? "Trực tuyến" : "Ngoại tuyến"));
                    profileAvatar.setIcon(avatar(account, connection.getCurrentUser(), 40));
                    profileAvatar.setToolTipText(account + " (" + handle + ")");
                    setTitle("VKU Chat · " + account);
                    updateGroupButtons();
                } else {
                    String error = statusText.get();
                    JOptionPane.showMessageDialog(this, error == null ? "Đăng nhập thất bại." : error.replaceFirst("^FAILED:", ""),
                            "Đăng nhập thất bại", JOptionPane.ERROR_MESSAGE);
                    showLoginDialog();
                }
            });
        }, "Login-Thread").start();
    }

    private void sendTextMessage() {
        String text = inputField.getText().trim();
        if (text.isEmpty() || client == null || !client.isConnected()) return;

        ChatMessage msg;
        if (isPrivate) {
            msg = ChatMessage.text(client.getCurrentUser(), currentTarget, text, true);
        } else if (!"general".equals(currentTarget)) {
            msg = ChatMessage.roomMessage(client.getCurrentUser(), currentTarget, text);
        } else {
            msg = new ChatMessage(ChatMessage.Type.BROADCAST, client.getCurrentUser(), text);
        }

        client.send(msg);
        inputField.setText("");
    }

    private void selectAndSendFile() {
        if (client == null || !client.isConnected()) return;
        ChatClient connection = client;
        String recipient = isPrivate ? currentTarget : "ALL";
        String room = isPrivate ? null : currentTarget;
        JFileChooser chooser = new JFileChooser();
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File file = chooser.getSelectedFile();
        ChatMessage message = ChatMessage.file(connection.getCurrentUser(), recipient, room, file.getName(), null);
        Path source = file.toPath().toAbsolutePath();
        outgoingFiles.put(message.getId(), source);
        new SwingWorker<Void, Void>() {
            @Override protected Void doInBackground() throws IOException {
                message.setFileData(Files.readAllBytes(source));
                if (!connection.isConnected()) throw new IOException("Đã mất kết nối với máy chủ.");
                connection.send(message);
                return null;
            }
            @Override protected void done() {
                try {
                    get();
                } catch (Exception e) {
                    outgoingFiles.remove(message.getId());
                    JOptionPane.showMessageDialog(ChatClientGUI.this, "Không thể gửi tệp: " + failureMessage(e));
                }
            }
        }.execute();
    }

    private void recordVoiceNote() {
        if (client == null || !client.isConnected()) return;
        ChatClient connection = client;
        String recipient = isPrivate ? currentTarget : "ALL";
        String room = isPrivate ? null : currentTarget;
        new VoiceRecorderDialog(this, recording -> {
            if (!connection.isConnected()) {
                JOptionPane.showMessageDialog(this, "Đã mất kết nối. Không thể gửi bản ghi.");
                return;
            }
            ChatMessage message = ChatMessage.audio(connection.getCurrentUser(), recipient, room,
                    recording.wav(), recording.seconds());
            CompletableFuture.runAsync(() -> connection.send(message));
        }).setVisible(true);
    }

    private void showEmojiPicker() {
        EmojiDialog dialog = new EmojiDialog(this, inputField::replaceSelection);
        dialog.setVisible(true);
        inputField.requestFocusInWindow();
    }

    private void startCall(boolean video) {
        if (client == null || !client.isConnected() || !isPrivate || activeCallDialog != null) return;
        OnlineAccount account = findOnlineAccount(currentTarget);
        if (account != null) openCall(account.username(), account.fullName(), UUID.randomUUID(), video, false);
    }

    private void handleIncomingCall(ChatMessage message) {
        if (message.getCallId() == null || !client.isConnected()) return;
        String caller = message.getSender();
        boolean video = "VIDEO".equals(message.getContent());
        if (activeCallDialog != null) {
            ChatMessage reject = ChatMessage.callReject(client.getCurrentUser(), caller, "Đối phương đang bận.");
            reject.setCallId(message.getCallId());
            client.send(reject);
            recordCallOutcome(caller, new CallDialog.Outcome("Cuộc gọi nhỡ · " + (video ? "Gọi video" : "Gọi thoại"), true));
            return;
        }
        OnlineAccount account = findOnlineAccount(caller);
        openCall(caller, account == null ? "@" + caller : account.fullName(), message.getCallId(), video, true);
    }

    private void openCall(String peer, String displayName, UUID callId, boolean video, boolean incoming) {
        rememberPeer(peer);
        activeCallDialog = new CallDialog(this, client, serverHost, peer, displayName, callId, video, incoming, outcome -> {
            activeCallDialog = null;
            updateGroupButtons();
            recordCallOutcome(peer, outcome);
        });
        updateGroupButtons();
        activeCallDialog.showCall();
    }

    private void recordCallOutcome(String peer, CallDialog.Outcome outcome) {
        rememberPeer(peer);
        String conversation = "@" + peer;
        appendConversationHtml(conversation, noticeHtml(outcome.text(), outcome.missed() ? "#B42340" : "#5D708D"));
        if (outcome.missed()) incrementUnread(conversation, true);
    }

    private static final class Attachment {
        final String name;
        final byte[] data;
        Path localPath;
        boolean downloading;

        Attachment(String name, byte[] data, Path localPath) {
            this.name = name;
            this.data = data;
            this.localPath = localPath;
        }
    }

    private void activateMessageLink(String link) {
        if (link == null) return;
        int separator = link.indexOf(':');
        if (separator < 0) return;
        String action = link.substring(0, separator);
        String id = link.substring(separator + 1);
        if ("play".equals(action)) {
            byte[] audio = voiceNotes.get(id);
            if (audio != null) playVoiceNote(audio);
            return;
        }
        Attachment attachment = attachments.get(id);
        if (attachment == null) return;
        if ("download".equals(action)) {
            downloadAttachment(attachment, false);
        } else if ("folder".equals(action)) {
            if (attachment.localPath != null && Files.isRegularFile(attachment.localPath)) {
                openAttachmentFolder(attachment.localPath);
            } else {
                downloadAttachment(attachment, true);
            }
        }
    }

    private void downloadAttachment(Attachment attachment, boolean reveal) {
        if (attachment.downloading) return;
        Path downloads = Path.of(System.getProperty("user.home"), "Downloads");
        JFileChooser chooser = Files.isDirectory(downloads) ? new JFileChooser(downloads.toFile()) : new JFileChooser();
        chooser.setDialogTitle("Tải về · " + attachment.name);
        chooser.setSelectedFile(new File(chooser.getCurrentDirectory(), suggestedFileName(attachment.name)));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        Path destination = chooser.getSelectedFile().toPath().toAbsolutePath().normalize();
        boolean overwrite = Files.exists(destination);
        if (overwrite && JOptionPane.showConfirmDialog(this, "Ghi đè tệp " + destination.getFileName() + "?",
                "Tệp đã tồn tại", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        attachment.downloading = true;
        new SwingWorker<Void, Void>() {
            @Override protected Void doInBackground() throws IOException {
                writeAttachment(destination, attachment.data, overwrite);
                return null;
            }

            @Override protected void done() {
                attachment.downloading = false;
                try {
                    get();
                    attachment.localPath = destination;
                    if (reveal) openAttachmentFolder(destination);
                    else JOptionPane.showMessageDialog(ChatClientGUI.this, "Đã tải về " + destination.getFileName() + ".");
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(ChatClientGUI.this, "Không thể tải tệp: " + failureMessage(e));
                }
            }
        }.execute();
    }

    private static String suggestedFileName(String name) {
        String normalized = name.replace('\\', '/');
        String leaf = normalized.substring(normalized.lastIndexOf('/') + 1)
                .replaceAll("[\\p{Cntrl}<>:\"|?*]", "_").strip();
        return leaf.isEmpty() || ".".equals(leaf) || "..".equals(leaf) ? "tep-dinh-kem" : leaf;
    }

    private static void writeAttachment(Path destination, byte[] bytes, boolean overwrite) throws IOException {
        Path temporary = Files.createTempFile(destination.getParent(), ".vku-download-", ".part");
        try {
            Files.write(temporary, bytes);
            if (overwrite) Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
            else Files.move(temporary, destination);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private void openAttachmentFolder(Path file) {
        try {
            Desktop desktop = Desktop.getDesktop();
            if (desktop.isSupported(Desktop.Action.BROWSE_FILE_DIR)) desktop.browseFileDirectory(file.toFile());
            else desktop.open(file.getParent().toFile());
        } catch (IOException | UnsupportedOperationException | IllegalArgumentException | SecurityException e) {
            JOptionPane.showMessageDialog(this, "Thư mục chứa tệp:\n" + file.getParent());
        }
    }

    private void playVoiceNote(byte[] data) {
        if (playingVoiceNote) return;
        playingVoiceNote = true;
        new SwingWorker<Void, Void>() {
            @Override protected Void doInBackground() throws Exception {
                VoiceNote.play(data);
                return null;
            }

            @Override protected void done() {
                playingVoiceNote = false;
                try {
                    get();
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(ChatClientGUI.this, "Không thể phát bản ghi: " + failureMessage(e));
                }
            }
        }.execute();
    }

    private static String failureMessage(Exception exception) {
        Throwable cause = exception.getCause() == null ? exception : exception.getCause();
        return cause.getMessage() == null ? "Thiết bị hoặc tệp chưa sẵn sàng." : cause.getMessage();
    }

    private void onMessageReceived(ChatMessage msg) {
        SwingUtilities.invokeLater(() -> {
            boolean isMe = client != null && msg.getSender() != null && msg.getSender().equalsIgnoreCase(client.getCurrentUser());
            String conversation = messageConversation(msg, isMe);
            boolean visibleHere = isFocused() && conversation.equals(currentConversation());

            String bodyHtml = "";
            switch (msg.getType()) {
                case USER_LIST:
                    updateOnlineUsers(msg.getOnlineAccounts());
                    return;
                case ROOM_LIST:
                    updateGroups(msg.getRooms());
                    return;
                case GROUP_ERROR:
                    JOptionPane.showMessageDialog(this, msg.getContent(), "Nhóm chat", JOptionPane.WARNING_MESSAGE);
                    return;
                case TEXT:
                case EMOJI:
                case BROADCAST:
                case MULTICAST:
                case PRIVATE:
                    if ("study".equals(conversation) && !rememberStudyMessage(msg.getId())) return;
                    bodyHtml = "<span style='font-size:13px;'>" + EMOJI_TEXT.matcher(escapeHtml(msg.getContent()))
                            .replaceAll("<span style='font-family:Segoe UI Emoji'>$0</span>") + "</span>";
                    break;
                case FILE:
                case VIDEO:
                    String attachmentName = msg.getType() == ChatMessage.Type.VIDEO ? "Ảnh đính kèm.png" : msg.getFileName();
                    if (msg.getFileData() != null && attachmentName != null) {
                        String key = UUID.randomUUID().toString();
                        Path source = isMe ? outgoingFiles.remove(msg.getId()) : null;
                        attachments.put(key, new Attachment(attachmentName, msg.getFileData(), source));
                        int bytes = msg.getFileData().length;
                        String size = bytes < 1024 ? bytes + " B" : String.format("%.1f KB", bytes / 1024.0);
                        bodyHtml = "<b>" + escapeHtml(attachmentName) + "</b>"
                                + "<br><small style='color:#5D708D'>" + size + "</small><br>"
                                + messageLink("download", key, "Tải về") + "&nbsp;&nbsp;·&nbsp;&nbsp;"
                                + messageLink("folder", key, "Mở&nbsp;trong&nbsp;thư&nbsp;mục");
                    } else {
                        bodyHtml = escapeHtml(msg.getContent());
                    }
                    break;
                case AUDIO:
                    bodyHtml = "<b>Tin nhắn thoại</b> · " + msg.getAudioDurationSec() + "&nbsp;giây";
                    if (msg.getFileData() != null) {
                        String key = UUID.randomUUID().toString();
                        voiceNotes.put(key, msg.getFileData());
                        bodyHtml += "<br>" + messageLink("play", key, "Nghe lại");
                    }
                    break;
                case CALL_REQUEST:
                    handleIncomingCall(msg);
                    return;
                case CALL_ACCEPT:
                case CALL_REJECT:
                case CALL_END:
                    if (activeCallDialog != null) activeCallDialog.receive(msg);
                    return;
                case SYSTEM:
                    if (msg.getContent().startsWith("Chào mừng ") || msg.getContent().startsWith("Người dùng ")) return;
                    appendHtml(noticeHtml(msg.getContent()));
                    return;
                default:
                    bodyHtml = escapeHtml(msg.getContent());
                    break;
            }

            String align = isMe ? "right" : "left";
            String bubbleBg = isMe ? "#D1E5FF" : "#FFFFFF";
            String nameColor = isMe ? "#0754DB" : "#465D83";
            String senderName = (isMe ? "Bạn · @" : "@") + msg.getSender();
            String bubbleWidth = switch (msg.getType()) {
                case FILE, VIDEO -> " width='270'";
                case AUDIO -> " width='220'";
                default -> "";
            };

            String html = String.format(
                    "<table width='100%%' border='0' cellpadding='4' cellspacing='0'>"
                    + "<tr><td align='%s'>"
                    + "<table%s border='0' cellpadding='10' cellspacing='0' bgcolor='%s' style='background-color:%s;'>"
                    + "<tr><td>"
                    + "<div style='font-size:10px;font-weight:bold;color:%s;margin-bottom:2px;'>%s"
                    + "<span style='font-weight:normal;color:#64748b;font-size:9px;'>&nbsp;·&nbsp;%s</span></div>"
                    + "<div style='font-size:13px;color:#0f172a;'>%s</div>"
                    + "</td></tr>"
                    + "</table>"
                    + "</td></tr>"
                    + "</table>",
                    align, bubbleWidth, bubbleBg, bubbleBg, nameColor, escapeHtml(senderName), msg.getFormattedTime(), bodyHtml
            );
            if (conversation.startsWith("@")) rememberPeer(conversation.substring(1));
            appendConversationHtml(conversation, html);
            // Legacy UDP summaries do not carry a sender or message ID.
            if (!isMe && !visibleHere && !"UDP-Multicast".equals(msg.getSender())) {
                incrementUnread(conversation, false);
            }
        });
    }

    private boolean rememberStudyMessage(String id) {
        if (id == null) return true;
        if (!recentStudyMessages.add(id)) return false;
        if (recentStudyMessages.size() > 2048) recentStudyMessages.remove(recentStudyMessages.iterator().next());
        return true;
    }

    private static String messageLink(String action, String key, String text) {
        return "<a style='color:#0754DB;font-size:11px;font-weight:bold' href='" + action + ":" + key + "'>"
                + text + "</a>";
    }

    private void updateOnlineUsers(List<OnlineAccount> accounts) {
        if (accounts == null || !client.isConnected()) return;
        availableAccounts = accounts.stream().filter(account -> !account.username().equals(client.getCurrentUser())).toList();
        availableAccounts.forEach(account -> recentContacts.replace(account.username(), account));
        refreshLists();
        for (OnlineAccount account : accounts) {
            if (account.username().equals(client.getCurrentUser())) {
                profileStatusLabel.setToolTipText("IP " + account.ip());
            }
        }
        if (isPrivate) {
            OnlineAccount selected = recentContacts.get(currentTarget);
            if (selected != null) updatePrivateHeader(selected);
        }
        updateGroupButtons();
    }

    private OnlineAccount findOnlineAccount(String username) {
        return availableAccounts.stream().filter(account -> account.username().equals(username)).findFirst().orElse(null);
    }

    private void rememberPeer(String username) {
        if (recentContacts.containsKey(username)) return;
        OnlineAccount online = findOnlineAccount(username);
        recentContacts.put(username, online == null ? new OnlineAccount(username, "@" + username, "") : online);
        if (online == null) refreshLists();
    }

    private void incrementUnread(String conversation, boolean missedCall) {
        Unread previous = unreadByConversation.remove(conversation);
        int messages = previous == null ? 0 : previous.messages();
        int missedCalls = previous == null ? 0 : previous.missedCalls();
        unreadByConversation.put(conversation, missedCall ? new Unread(messages, missedCalls + 1)
                : new Unread(messages + 1, missedCalls));
        updateUnreadIndicators();
    }

    private void markRead(String conversation, boolean includeMissedCalls) {
        Unread unread = unreadByConversation.get(conversation);
        if (unread == null || (!includeMissedCalls && unread.messages() == 0)) return;
        if (includeMissedCalls || unread.missedCalls() == 0) unreadByConversation.remove(conversation);
        else unreadByConversation.put(conversation, new Unread(0, unread.missedCalls()));
        updateUnreadIndicators();
    }

    private int unreadTotal() {
        return unreadByConversation.values().stream().mapToInt(Unread::total).sum();
    }

    private void updateUnreadIndicators() {
        int count = unreadTotal();
        String hint = count == 0 ? "Tìm cuộc trò chuyện" : count + " thông báo chưa đọc";
        chatShortcut.setToolTipText(hint);
        chatShortcut.getAccessibleContext().setAccessibleName(hint);
        chatShortcutArea.repaint();
        onlineUsersList.repaint();
        channelList.repaint();
        if (isVisible() && Taskbar.isTaskbarSupported()) {
            try {
                Taskbar taskbar = Taskbar.getTaskbar();
                if (taskbar.isSupported(Taskbar.Feature.ICON_BADGE_IMAGE_WINDOW)) {
                    taskbar.setWindowIconBadge(this, taskbarBadge(count));
                } else if (taskbar.isSupported(Taskbar.Feature.ICON_BADGE_NUMBER)) {
                    taskbar.setIconBadge(count == 0 ? null : Integer.toString(count));
                }
            } catch (UnsupportedOperationException | SecurityException ignored) {
                // The badges inside the app remain available.
            }
        }
    }

    private void openLatestUnread() {
        String latest = null;
        for (String conversation : unreadByConversation.keySet()) latest = conversation;
        if (latest == null) {
            searchField.requestFocusInWindow();
            return;
        }
        searchField.setText("");
        if (latest.startsWith("@")) {
            OnlineAccount account = recentContacts.get(latest.substring(1));
            onlineUsersList.setSelectedValue(account, true);
        } else {
            for (Room room : availableRooms) {
                if (room.getId().equals(latest)) {
                    channelList.setSelectedValue(room, true);
                    break;
                }
            }
        }
        if (latest.equals(currentConversation())) markRead(latest, true);
    }

    private void updateGroups(List<Room> groups) {
        if (groups == null) return;
        availableRooms.removeIf(Room::isCustomGroup);
        availableRooms.addAll(groups);
        refreshLists();
        Room room = currentRoom();
        if (room != null) updateRoomHeader(room);
    }

    private String messageConversation(ChatMessage msg, boolean isMe) {
        if (msg.getRoom() != null) return msg.getRoom();
        String recipient = msg.getRecipient();
        if (recipient != null) return "@" + (isMe ? recipient : msg.getSender());
        return "general";
    }

    private String currentConversation() {
        return isPrivate ? "@" + currentTarget : currentTarget;
    }

    private void selectConversation(String target, boolean privateChat) {
        markRead(privateChat ? "@" + target : target, true);
        if (privateChat == isPrivate && target.equals(currentTarget)) return;
        currentTarget = target;
        isPrivate = privateChat;
        initChatPaneContent();
        StringBuilder previous = conversationHtml.get(currentConversation());
        if (previous != null) {
            insertChatHtml(previous.toString());
            messageCards.show(messageStage, "messages");
        } else {
            messageCards.show(messageStage, "empty");
        }
    }

    private void initChatPaneContent() {
        chatPane.setText("<html><head><style>"
                + "body { font-family: 'Segoe UI', Tahoma, Arial, sans-serif; margin: 8px; padding: 0; background-color: #EAF0F8; }"
                + "</style></head><body><div id='chat-box'>"
                + "</div></body></html>");
        htmlDoc = (HTMLDocument) chatPane.getDocument();
    }

    private void appendHtml(String html) {
        appendConversationHtml(currentConversation(), html);
    }

    private void appendConversationHtml(String conversation, String html) {
        conversationHtml.computeIfAbsent(conversation, key -> new StringBuilder()).append(html);
        if (conversation.equals(currentConversation())) {
            messageCards.show(messageStage, "messages");
            insertChatHtml(html);
        }
    }

    private String noticeHtml(String text) {
        return noticeHtml(text, "#5D708D");
    }

    private String noticeHtml(String text, String color) {
        return "<table width='100%' border='0' cellpadding='8' cellspacing='0'><tr>"
                + "<td align='center' style='color:" + color + ";font-size:11px;'>" + escapeHtml(text) + "</td></tr></table>";
    }

    private void insertChatHtml(String html) {
        try {
            Element chatBox = htmlDoc.getElement("chat-box");
            if (chatBox != null) {
                htmlDoc.insertBeforeEnd(chatBox, html);
            } else {
                htmlKit.insertHTML(htmlDoc, htmlDoc.getLength(), html, 0, 0, javax.swing.text.html.HTML.Tag.DIV);
            }
            chatPane.setCaretPosition(chatPane.getDocument().getLength());
        } catch (Exception ignored) {}
    }

    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br/>");
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {}
            String user = args.length > 0 ? args[0] : null;
            String pass = args.length > 1 ? args[1] : "123456";
            String host = args.length > 2 ? args[2] : "localhost";
            ChatClientGUI gui = new ChatClientGUI(user, pass, host);
            gui.setVisible(true);
        });
    }
}
