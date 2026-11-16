package com.vku.chat.client;

import com.vku.chat.model.ChatMessage;
import com.vku.chat.server.UdpMulticastService;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.function.Consumer;

public class ChatClient {
    private final String host;
    private final int port;
    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private volatile boolean connected = false;

    private String currentUser;
    private String currentFullName;

    private Consumer<ChatMessage> messageListener;
    private Consumer<String> statusListener;
    private UdpMulticastService udpMulticastService;

    public ChatClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public boolean connect(String username, String password, String fullName) {
        try {
            socket = new java.net.Socket();
            socket.connect(new java.net.InetSocketAddress(host, port), 4000);
            out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
            in = new ObjectInputStream(socket.getInputStream());

            // Send login message
            ChatMessage loginMsg = new ChatMessage(ChatMessage.Type.LOGIN, username, username + ":" + password);
            out.writeObject(loginMsg);
            out.flush();

            // Wait for login response
            Object resp = in.readObject();
            if (resp instanceof ChatMessage) {
                ChatMessage reply = (ChatMessage) resp;
                if (reply.getContent().startsWith("SUCCESS:")) {
                    this.currentUser = reply.getRecipient() != null ? reply.getRecipient() : username;
                    this.currentFullName = reply.getContent().substring("SUCCESS:".length());
                    this.connected = true;

                    // Start background TCP receiver thread
                    startReceiverThread();

                    // Start UDP Multicast listener
                    initUdpMulticast();

                    return true;
                } else {
                    if (statusListener != null) {
                        statusListener.accept(reply.getContent());
                    }
                    disconnect();
                    return false;
                }
            }
        } catch (Exception e) {
            if (statusListener != null) {
                statusListener.accept("Lỗi kết nối đến máy chủ: " + e.getMessage());
            }
            disconnect();
        }
        return false;
    }

    private void startReceiverThread() {
        Thread thread = new Thread(() -> {
            while (connected) {
                try {
                    Object obj = in.readObject();
                    if (obj instanceof ChatMessage) {
                        ChatMessage msg = (ChatMessage) obj;
                        if (messageListener != null) messageListener.accept(msg);
                    }
                } catch (Exception e) {
                    if (connected) {
                        disconnect();
                        if (statusListener != null) statusListener.accept("Mất kết nối với máy chủ!");
                    }
                    break;
                }
            }
        }, "TCP-Client-Receiver");
        thread.setDaemon(true);
        thread.start();
    }

    private void initUdpMulticast() {
        udpMulticastService = new UdpMulticastService(
                UdpMulticastService.DEFAULT_MULTICAST_GROUP,
                UdpMulticastService.DEFAULT_MULTICAST_PORT,
                this::onMulticastMessage
        );
        udpMulticastService.start();
    }

    private void onMulticastMessage(String text) {
        ChatMessage message;
        if (text.startsWith(UdpMulticastService.CHAT_PREFIX)) {
            String[] parts = text.split("\t", 4);
            if (parts.length != 4 || parts[1].isEmpty() || parts[2].isEmpty()) return;
            message = new ChatMessage(ChatMessage.Type.MULTICAST, parts[2], parts[3]);
            message.setId(parts[1]);
        } else {
            message = new ChatMessage(ChatMessage.Type.MULTICAST, "UDP-Multicast", text);
        }
        message.setRoom("study");
        if (messageListener != null) messageListener.accept(message);
    }

    public synchronized void send(ChatMessage message) {
        if (!connected || out == null) return;
        try {
            message.setSender(currentUser);
            out.writeObject(message);
            out.flush();
            out.reset();
        } catch (IOException e) {
            if (statusListener != null) statusListener.accept("Lỗi gửi tin: " + e.getMessage());
        }
    }

    public void disconnect() {
        connected = false;
        try {
            if (out != null) {
                out.writeObject(new ChatMessage(ChatMessage.Type.LOGOUT, currentUser, ""));
                out.flush();
            }
        } catch (Exception ignored) {}
        try {
            if (udpMulticastService != null) udpMulticastService.stop();
            if (in != null) in.close();
            if (out != null) out.close();
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (Exception ignored) {}
    }

    // Setters for listeners
    public void setMessageListener(Consumer<ChatMessage> listener) { this.messageListener = listener; }
    public void setStatusListener(Consumer<String> listener) { this.statusListener = listener; }

    public String getCurrentUser() { return currentUser; }
    public String getCurrentFullName() { return currentFullName; }
    public boolean isConnected() { return connected; }
}
