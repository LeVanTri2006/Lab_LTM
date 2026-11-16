package com.vku.chat.server;

import com.vku.chat.db.ChatDatabase;
import com.vku.chat.model.ChatMessage;
import com.vku.chat.model.OnlineAccount;
import com.vku.chat.model.Room;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.InetSocketAddress;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ChatServer {
    public static final int TCP_PORT = 8888;
    private final ChatDatabase database;
    private final Map<String, ClientHandler> activeClients = new ConcurrentHashMap<>();
    private final CallRouter calls = new CallRouter(activeClients::get);
    private final ExecutorService threadPool = Executors.newCachedThreadPool();
    private ServerSocket serverSocket;
    private boolean running = false;

    private UdpMulticastService udpMulticastService;
    private UdpMediaStreamer udpAudioStreamer;
    private UdpMediaStreamer udpVideoStreamer;

    // Optional GUI components
    private JFrame frame;
    private JTextArea logArea;
    private JLabel clientCountLabel;
    private DefaultListModel<String> clientListModel;

    public ChatServer() {
        this.database = new ChatDatabase();
        initUdpServices();
    }

    private void initUdpServices() {
        // UDP Multicast receiver for announcements
        udpMulticastService = new UdpMulticastService(
                UdpMulticastService.DEFAULT_MULTICAST_GROUP,
                UdpMulticastService.DEFAULT_MULTICAST_PORT,
                (msg) -> log("[UDP Multicast]: " + msg)
        );
        udpMulticastService.start();

        // UDP Audio Streamer
        udpAudioStreamer = new UdpMediaStreamer(UdpMediaStreamer.AUDIO_STREAM_PORT, (from, data) -> {
            log("[UDP Audio Stream]: Nhận gói âm thanh từ " + from + " (" + data.length + " bytes)");
        });
        udpAudioStreamer.startListening();

        // UDP Video Streamer & Relay
        udpVideoStreamer = new UdpMediaStreamer(UdpMediaStreamer.VIDEO_STREAM_PORT, this::relayCallMedia);
        udpVideoStreamer.startListening();
    }

    private void relayCallMedia(InetSocketAddress source, byte[] data) {
        MediaPacket packet = MediaPacket.decode(data, data.length);
        if (packet == null) {
            log("[UDP Video Stream]: Nhận khung hình video từ " + source + " (" + data.length + " bytes)");
            return;
        }
        InetSocketAddress target = calls.relayTarget(packet, source);
        if (target != null) udpVideoStreamer.sendTo(target.getAddress(), target.getPort(), data);
    }

    public void startServer() {
        if (running) return;
        running = true;

        new Thread(() -> {
            try {
                serverSocket = new ServerSocket(TCP_PORT);
                log("Chat Server đã khởi động trên cổng TCP: " + TCP_PORT);
                log("UDP Multicast: " + UdpMulticastService.DEFAULT_MULTICAST_GROUP + ":" + UdpMulticastService.DEFAULT_MULTICAST_PORT);
                log("UDP Audio Port: " + UdpMediaStreamer.AUDIO_STREAM_PORT + " | UDP Video Port: " + UdpMediaStreamer.VIDEO_STREAM_PORT);

                while (running && !serverSocket.isClosed()) {
                    Socket socket = serverSocket.accept();
                    ClientHandler handler = new ClientHandler(socket, this);
                    threadPool.submit(handler);
                }
            } catch (IOException e) {
                if (running) log("Lỗi ServerSocket: " + e.getMessage());
            }
        }, "TCP-Acceptor").start();
    }

    public synchronized boolean onClientConnected(ClientHandler handler) {
        String username = handler.getUsername();
        if (activeClients.containsKey(username)) return false;

        ChatMessage success = new ChatMessage(ChatMessage.Type.LOGIN, "SERVER", "SUCCESS:" + handler.getUser().getFullName());
        success.setRecipient(username);
        handler.sendMessage(success);
        activeClients.put(username, handler);
        log("Client kết nối thành công: " + username + " (" + handler.getUser().getFullName() + ")");

        updateGuiClientList();
        broadcastUserList();
        sendRoomList(handler);

        handler.sendMessage(ChatMessage.system("Chào mừng " + handler.getUser().getFullName() + " đến với VKU Chat Server!"));
        broadcastMessage(ChatMessage.system("Người dùng " + handler.getUser().getFullName() + " đã tham gia phòng chat."));
        return true;
    }

    public synchronized void onClientDisconnected(ClientHandler handler) {
        String username = handler.getUsername();
        if (activeClients.remove(username, handler)) {
            calls.disconnected(username);
            database.setUserOffline(username);
            log("Client ngắt kết nối: " + username);
            updateGuiClientList();
            broadcastUserList();
            broadcastMessage(ChatMessage.system("Người dùng " + username + " đã rời khỏi phòng chat."));
        }
    }

    public void broadcastMessage(ChatMessage message) {
        message.setRoom(null);
        message.setRecipient(null);
        database.saveMessage(message);
        log("[BROADCAST] " + message.getSender() + ": " + message.getContent());
        for (ClientHandler client : activeClients.values()) {
            client.sendMessage(message);
        }
    }

    public void sendToRoom(ChatMessage message) {
        Room room = findRoom(message.getRoom());
        if (room == null) return;
        if (room.isCustomGroup() && !room.hasMember(message.getSender())) {
            ClientHandler sender = activeClients.get(message.getSender());
            if (sender != null) sendGroupError(sender, "Bạn chưa là thành viên của nhóm này.");
            return;
        }
        message.setRoom(room.getId());
        database.saveMessage(message);
        log("[ROOM: " + room.getName() + "] " + message.getSender() + ": " + message.getContent());

        for (ClientHandler client : activeClients.values()) {
            boolean member = room.isCustomGroup() ? room.hasMember(client.getUsername()) : client.isInRoom(room.getId());
            if (member) {
                client.sendMessage(message);
            }
        }

        // Also broadcast over UDP Multicast if it's the multicast group
        if ("study".equals(room.getId())) {
            switch (message.getType()) {
                case MULTICAST, TEXT, EMOJI -> udpMulticastService.sendMulticast(UdpMulticastService.CHAT_PREFIX
                        + message.getId() + "\t" + message.getSender() + "\t" + message.getContent());
                default -> udpMulticastService.sendMulticast("[" + message.getSender() + "]: " + message.getContent());
            }
        }
    }

    public void sendPrivateMessage(ChatMessage message) {
        if (message.getRecipient() == null) return;
        message.setRoom(null);
        database.saveMessage(message);
        String recipient = message.getRecipient();
        log("[PRIVATE] " + message.getSender() + " -> " + recipient + ": " + message.getContent());

        ClientHandler target = activeClients.get(recipient);
        if (target != null) {
            target.sendMessage(message);
        }

        // Echo back to sender so they see their sent message
        ClientHandler senderHandler = activeClients.get(message.getSender());
        if (senderHandler != null && senderHandler != target) {
            senderHandler.sendMessage(message);
        }
    }

    public void handleCallSignaling(ChatMessage message) {
        log("[CALL] " + message.getType() + ": " + message.getSender() + " -> " + message.getRecipient());
        calls.signal(message);
    }

    private void broadcastUserList() {
        List<OnlineAccount> accounts = activeClients.values().stream()
                .map(ClientHandler::getOnlineAccount)
                .sorted(Comparator.comparing(OnlineAccount::username))
                .toList();
        ChatMessage listMsg = new ChatMessage(ChatMessage.Type.USER_LIST, "SERVER", "");
        listMsg.setOnlineAccounts(accounts);
        for (ClientHandler client : activeClients.values()) {
            client.sendMessage(listMsg);
        }
    }

    private void sendRoomList(ClientHandler client) {
        ChatMessage message = new ChatMessage(ChatMessage.Type.ROOM_LIST, "SERVER", "");
        message.setRooms(database.getAllRooms().stream()
                .filter(room -> room.isCustomGroup() && room.hasMember(client.getUsername()))
                .sorted(Comparator.comparing(Room::getName))
                .toList());
        client.sendMessage(message);
    }

    public synchronized void handleGroupRequest(ClientHandler requester, ChatMessage message) {
        boolean creating = message.getType() == ChatMessage.Type.CREATE_GROUP;
        Room group;
        if (creating) {
            String name = message.getContent() == null ? "" : message.getContent().strip();
            if (name.isEmpty() || name.length() > 60) {
                sendGroupError(requester, "Tên nhóm phải có từ 1 đến 60 ký tự.");
                return;
            }
            group = new Room("group-" + UUID.randomUUID(), name, "", false, null, 0);
            group.addMember(requester.getUsername());
        } else {
            Room existing = database.getRoom(message.getRoom());
            if (existing == null || !existing.isCustomGroup() || !existing.hasMember(requester.getUsername())) {
                sendGroupError(requester, "Bạn chỉ có thể thêm thành viên vào nhóm của mình.");
                return;
            }
            group = new Room(existing.getId(), existing.getName(), "", false, null, 0);
            group.getMembers().addAll(existing.getMembers());
        }

        String ip = message.getMemberIp() == null ? "" : message.getMemberIp().strip();
        Set<String> selected = new LinkedHashSet<>();
        if (message.getMemberUsernames() != null) selected.addAll(message.getMemberUsernames());
        if (message.getRecipient() != null) selected.add(message.getRecipient());
        List<OnlineAccount> invited = new ArrayList<>();
        if (!selected.isEmpty()) {
            for (String username : selected) {
                ClientHandler handler = username == null ? null : activeClients.get(username);
                if (handler == null || requester.getUsername().equals(username)) {
                    sendGroupError(requester, "Tài khoản @" + username + " không còn trong danh sách trực tuyến.");
                    return;
                }
                OnlineAccount account = handler.getOnlineAccount();
                if (!ip.isEmpty() && !account.matchesIp(ip)) {
                    sendGroupError(requester, account + " hiện không kết nối từ IP " + ip + ".");
                    return;
                }
                invited.add(account);
            }
        } else if (!ip.isEmpty()) {
            List<OnlineAccount> matches = activeClients.values().stream()
                    .map(ClientHandler::getOnlineAccount)
                    .filter(account -> !group.hasMember(account.username()) && account.matchesIp(ip))
                    .toList();
            if (matches.isEmpty()) {
                sendGroupError(requester, "Không tìm thấy tài khoản khác đang trực tuyến tại IP " + ip + ".");
                return;
            }
            if (matches.size() > 1) {
                sendGroupError(requester, "Nhiều tài khoản dùng IP " + ip + ". Hãy nhập lại IP và chọn tài khoản.");
                return;
            }
            invited.add(matches.get(0));
        } else if (!creating) {
            sendGroupError(requester, "Hãy nhập IP hoặc chọn tài khoản đang trực tuyến.");
            return;
        }
        for (OnlineAccount account : invited) {
            if (group.hasMember(account.username())) {
                sendGroupError(requester, account + " đã có trong nhóm.");
                return;
            }
        }
        invited.forEach(account -> group.addMember(account.username()));

        try {
            database.saveGroup(group);
        } catch (IOException e) {
            log("Lỗi lưu nhóm: " + e.getMessage());
            sendGroupError(requester, "Máy chủ không lưu được nhóm. Vui lòng thử lại.");
            return;
        }
        for (String username : group.getMembers()) {
            ClientHandler member = activeClients.get(username);
            if (member != null) sendRoomList(member);
        }
        String names = String.join(", ", invited.stream().map(OnlineAccount::toString).toList());
        String notice = creating ? "Đã tạo nhóm " + group.getName() : "Đã thêm " + names + " vào nhóm " + group.getName();
        requester.sendMessage(ChatMessage.system(notice + "."));
    }

    private void sendGroupError(ClientHandler requester, String text) {
        requester.sendMessage(new ChatMessage(ChatMessage.Type.GROUP_ERROR, "SERVER", text));
    }

    public Room findRoom(String name) {
        Room room = database.getRoom(name);
        if (room != null || name == null) return room;
        for (Room r : database.getAllRooms()) {
            if (!r.isCustomGroup() && (r.getId().equalsIgnoreCase(name) || r.getName().equalsIgnoreCase(name))) {
                return r;
            }
        }
        return null;
    }

    public ChatDatabase getDatabase() {
        return database;
    }

    public void log(String message) {
        String timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());
        String line = "[" + timestamp + "] " + message;
        System.out.println(line);
        if (logArea != null) {
            SwingUtilities.invokeLater(() -> {
                logArea.append(line + "\n");
                logArea.setCaretPosition(logArea.getDocument().getLength());
            });
        }
    }

    private void updateGuiClientList() {
        if (clientListModel == null || clientCountLabel == null) return;
        SwingUtilities.invokeLater(() -> {
            clientListModel.clear();
            for (String user : activeClients.keySet()) {
                clientListModel.addElement(user);
            }
            clientCountLabel.setText("Đang online: " + activeClients.size() + " client");
        });
    }

    public void initGUI() {
        frame = new JFrame("VKU Chat Server - Quản Trị Trung Tâm (LTM)");
        frame.setSize(800, 550);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLocationRelativeTo(null);

        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Top Header
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(new Color(24, 119, 242));
        JLabel titleLabel = new JLabel("  HỆ THỐNG MÁY CHỦ CHAT LTM (TCP + UDP MULTICAST)");
        titleLabel.setForeground(Color.WHITE);
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 15));
        titleLabel.setPreferredSize(new Dimension(500, 40));
        clientCountLabel = new JLabel("Đang online: 0 client  ");
        clientCountLabel.setForeground(Color.WHITE);
        clientCountLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));
        headerPanel.add(titleLabel, BorderLayout.WEST);
        headerPanel.add(clientCountLabel, BorderLayout.EAST);
        mainPanel.add(headerPanel, BorderLayout.NORTH);

        // Center Log
        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font("Consolas", Font.PLAIN, 13));
        logArea.setBackground(new Color(245, 247, 250));
        JScrollPane scrollLog = new JScrollPane(logArea);
        scrollLog.setBorder(BorderFactory.createTitledBorder("Nhật Ký Hoạt Động Hệ Thống (TCP/UDP Logs)"));

        // Right side: online clients list
        clientListModel = new DefaultListModel<>();
        JList<String> clientJList = new JList<>(clientListModel);
        clientJList.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        JScrollPane clientScroll = new JScrollPane(clientJList);
        clientScroll.setPreferredSize(new Dimension(180, 0));
        clientScroll.setBorder(BorderFactory.createTitledBorder("Clients Online"));

        JPanel centerPanel = new JPanel(new BorderLayout(8, 8));
        centerPanel.add(scrollLog, BorderLayout.CENTER);
        centerPanel.add(clientScroll, BorderLayout.EAST);
        mainPanel.add(centerPanel, BorderLayout.CENTER);

        // Bottom Controls
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btnClearLog = new JButton("Xóa Nhật Ký");
        btnClearLog.addActionListener(e -> logArea.setText(""));
        JButton btnSendAnnounce = new JButton("Gửi Thông Báo Hệ Thống");
        btnSendAnnounce.addActionListener(e -> {
            String text = JOptionPane.showInputDialog(frame, "Nhập nội dung thông báo broadcast toàn bộ client:");
            if (text != null && !text.trim().isEmpty()) {
                broadcastMessage(ChatMessage.system("[THÔNG BÁO TỪ MÁY CHỦ]: " + text.trim()));
            }
        });
        bottomPanel.add(btnClearLog);
        bottomPanel.add(btnSendAnnounce);
        mainPanel.add(bottomPanel, BorderLayout.SOUTH);

        frame.add(mainPanel);
        frame.setVisible(true);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {}
            ChatServer server = new ChatServer();
            server.initGUI();
            server.startServer();
        });
    }
}
