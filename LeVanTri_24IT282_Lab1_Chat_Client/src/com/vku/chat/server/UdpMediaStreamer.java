package com.vku.chat.server;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.function.BiConsumer;

public class UdpMediaStreamer {
    public static final int AUDIO_STREAM_PORT = 8891;
    public static final int VIDEO_STREAM_PORT = 8892;

    private DatagramSocket socket;
    private volatile boolean running = false;
    private Thread receiverThread;
    private final BiConsumer<InetSocketAddress, byte[]> onFrameReceived;

    public UdpMediaStreamer(int port, BiConsumer<InetSocketAddress, byte[]> onFrameReceived) {
        this.onFrameReceived = onFrameReceived;
        try {
            this.socket = new DatagramSocket(port);
        } catch (Exception e) {
            System.err.println("Không thể bind cổng UDP Media " + port + ": " + e.getMessage());
        }
    }

    public void startListening() {
        if (socket == null || running) return;
        running = true;
        receiverThread = new Thread(() -> {
            byte[] buf = new byte[65507]; // Max UDP packet size
            while (running && !socket.isClosed()) {
                try {
                    DatagramPacket packet = new DatagramPacket(buf, buf.length);
                    socket.receive(packet);
                    byte[] data = new byte[packet.getLength()];
                    System.arraycopy(packet.getData(), 0, data, 0, packet.getLength());

                    if (onFrameReceived != null) {
                        onFrameReceived.accept(new InetSocketAddress(packet.getAddress(), packet.getPort()), data);
                    }
                } catch (Exception e) {
                    if (!running) break;
                }
            }
        }, "UDP-Media-Listener");
        receiverThread.setDaemon(true);
        receiverThread.start();
    }

    public void sendTo(InetAddress addr, int port, byte[] data) {
        if (socket == null || socket.isClosed()) return;
        try {
            DatagramPacket packet = new DatagramPacket(data, data.length, addr, port);
            socket.send(packet);
        } catch (Exception e) {
            System.err.println("Lỗi forward UDP Media: " + e.getMessage());
        }
    }

    public void stop() {
        running = false;
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
    }
}
