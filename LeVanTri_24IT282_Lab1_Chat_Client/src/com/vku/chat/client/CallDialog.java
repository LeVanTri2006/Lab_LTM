package com.vku.chat.client;

import com.vku.chat.model.ChatMessage;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.UUID;
import java.util.function.Consumer;

import static com.vku.chat.client.ChatTheme.*;
import static com.vku.chat.client.ChatTheme.Glyph.*;

final class CallDialog extends JDialog {
    record Outcome(String text, boolean missed) {}

    private static final Color VIDEO_BG = new Color(0x132544);
    private final ChatClient client;
    private final String host;
    private final String peer;
    private final String displayName;
    private final UUID id;
    private final boolean video;
    private final boolean incoming;
    private final Consumer<Outcome> onFinished;
    private final Timer ringTimeout;
    private final Timer ringSound;
    private final Timer refresh;
    private final JLabel status = label("", 13, Font.PLAIN, MUTED);
    private final JLabel deviceStatus = label(" ", 12, Font.PLAIN, MUTED);
    private final JPanel controls = new JPanel(new FlowLayout(FlowLayout.CENTER, 7, 8));
    private final JPanel stage;
    private CallMediaSession media;
    private boolean muted;
    private boolean camera;
    private boolean sharing;
    private boolean closed;
    private boolean answered;
    private long startedAt;
    private int sourceRevision;

    CallDialog(Frame owner, ChatClient client, String host, String peer, String displayName,
               UUID id, boolean video, boolean incoming, Consumer<Outcome> onFinished) {
        super(owner, video ? "Cuộc gọi video" : "Cuộc gọi thoại", false);
        this.client = client;
        this.host = host;
        this.peer = peer;
        this.displayName = displayName;
        this.id = id;
        this.video = video;
        this.incoming = incoming;
        this.onFinished = onFinished;

        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent event) { hangUp(); }
        });
        JPanel content = new JPanel(new BorderLayout(0, 10));
        content.setBackground(Color.WHITE);
        content.setBorder(new EmptyBorder(14, 16, 12, 16));
        setContentPane(content);
        JPanel header = new JPanel(new BorderLayout(9, 0));
        header.setOpaque(false);
        header.add(label(displayName, 17, Font.BOLD, INK), BorderLayout.WEST);
        status.setHorizontalAlignment(SwingConstants.RIGHT);
        status.setText(incoming ? "Đang gọi đến" : "Đang đổ chuông…");
        header.add(status, BorderLayout.EAST);
        content.add(header, BorderLayout.NORTH);

        stage = new JPanel() {
            @Override protected void paintComponent(Graphics graphics) {
                super.paintComponent(graphics);
                Graphics2D g = (Graphics2D) graphics.create();
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                BufferedImage remote = media == null ? null : media.remoteFrame();
                if (video && remote != null) {
                    drawFrame(g, remote, 0, 0, getWidth(), getHeight());
                } else {
                    Icon avatar = avatar(displayName, peer, 84);
                    avatar.paintIcon(this, g, (getWidth() - 84) / 2, (getHeight() - 108) / 2);
                    g.setFont(font(14, Font.BOLD));
                    g.setColor(video ? Color.WHITE : INK);
                    String title = video ? "Video" : "Gọi thoại";
                    int width = g.getFontMetrics().stringWidth(title);
                    g.drawString(title, (getWidth() - width) / 2, (getHeight() + 110) / 2);
                }
                BufferedImage local = media == null ? null : media.localFrame();
                if (video && local != null) {
                    int width = Math.min(154, getWidth() / 4);
                    int height = width * 3 / 4;
                    int x = getWidth() - width - 12;
                    int y = getHeight() - height - 12;
                    g.setColor(new Color(0x233E62));
                    g.fillRoundRect(x - 2, y - 2, width + 4, height + 4, 10, 10);
                    drawFrame(g, local, x, y, width, height);
                }
                g.dispose();
            }
        };
        stage.setBackground(video ? VIDEO_BG : SOFT);
        content.add(stage, BorderLayout.CENTER);

        JPanel footer = new JPanel(new BorderLayout());
        footer.setOpaque(false);
        deviceStatus.setHorizontalAlignment(SwingConstants.CENTER);
        footer.add(deviceStatus, BorderLayout.NORTH);
        controls.setOpaque(false);
        footer.add(controls, BorderLayout.CENTER);
        content.add(footer, BorderLayout.SOUTH);
        updateControls();
        setMinimumSize(video ? new Dimension(620, 445) : new Dimension(525, 345));
        setSize(video ? new Dimension(820, 570) : new Dimension(545, 370));
        setLocationRelativeTo(owner);

        ringTimeout = new Timer(30_000, event -> {
            send(incoming ? ChatMessage.Type.CALL_REJECT : ChatMessage.Type.CALL_END, "Không có người trả lời.");
            finish("Không có người trả lời.", incoming);
        });
        ringTimeout.setRepeats(false);
        ringSound = new Timer(3_000, event -> Toolkit.getDefaultToolkit().beep());
        refresh = new Timer(115, event -> {
            long seconds = (System.currentTimeMillis() - startedAt) / 1000;
            status.setText(String.format("%02d:%02d", seconds / 60, seconds % 60));
            stage.repaint();
        });
        ringTimeout.start();
    }

    void showCall() {
        setVisible(true);
        if (incoming) {
            Toolkit.getDefaultToolkit().beep();
            ringSound.start();
        } else {
            send(ChatMessage.Type.CALL_REQUEST, video ? "VIDEO" : "VOICE");
        }
    }

    void receive(ChatMessage message) {
        if (!id.equals(message.getCallId()) || !peer.equals(message.getSender()) || closed) return;
        switch (message.getType()) {
            case CALL_ACCEPT -> connectMedia();
            case CALL_REJECT -> finish(message.getContent());
            case CALL_END -> finish("END".equals(message.getContent()) ? "Cuộc gọi đã kết thúc." : message.getContent(),
                    incoming && !answered);
            default -> { }
        }
    }

    void disconnected() {
        finish("Đã mất kết nối với máy chủ.", incoming && !answered);
    }

    private void updateControls() {
        controls.removeAll();
        if (media != null) {
            JButton mic = button(muted ? "Bật mic" : "Tắt mic", MIC, SOFT, NAV);
            mic.addActionListener(event -> {
                muted = !muted;
                media.setMuted(muted);
                updateControls();
            });
            controls.add(mic);
            if (video) {
                JButton cameraButton = button(camera ? "Tắt camera" : "Bật camera", VIDEO, SOFT, NAV);
                cameraButton.addActionListener(event -> changeSource(!camera, false));
                controls.add(cameraButton);
                JButton share = button(sharing ? "Dừng chia sẻ" : "Chia sẻ màn hình", SCREEN, SOFT, NAV);
                share.addActionListener(event -> changeSource(false, !sharing));
                controls.add(share);
            }
        } else if (incoming) {
            JButton accept = button("Trả lời", video ? VIDEO : PHONE, BLUE, Color.WHITE);
            accept.addActionListener(event -> {
                send(ChatMessage.Type.CALL_ACCEPT, "ACCEPT");
                connectMedia();
            });
            controls.add(accept);
        }
        String hangUpText = media != null ? "Gác máy" : incoming ? "Từ chối" : "Hủy cuộc gọi";
        JButton end = button(hangUpText, PHONE, RED, Color.WHITE);
        end.addActionListener(event -> hangUp());
        controls.add(end);
        controls.revalidate();
        controls.repaint();
    }

    private void connectMedia() {
        if (closed || media != null) return;
        answered = true;
        ringTimeout.stop();
        ringSound.stop();
        try {
            media = new CallMediaSession(host, id, client.getCurrentUser(), peer,
                    notice -> SwingUtilities.invokeLater(() -> showDeviceNotice(notice)));
            startedAt = System.currentTimeMillis();
            refresh.start();
            if (video) {
                media.startVideo();
                changeSource(true, false);
            } else {
                updateControls();
            }
            status.setText("00:00");
        } catch (IOException e) {
            send(ChatMessage.Type.CALL_END, "Không mở được luồng cuộc gọi.");
            finish("Không mở được luồng cuộc gọi.");
        }
    }

    private void changeSource(boolean useCamera, boolean share) {
        camera = useCamera;
        sharing = share;
        int revision = ++sourceRevision;
        if (deviceStatus.getText().contains("camera")) deviceStatus.setText(" ");
        media.setVideoSource(useCamera, share, error -> SwingUtilities.invokeLater(() -> {
            if (!closed && revision == sourceRevision) {
                camera = false;
                showDeviceNotice(error);
                updateControls();
            }
        }));
        updateControls();
    }

    private void showDeviceNotice(String notice) {
        if (closed) return;
        deviceStatus.setText(notice);
        deviceStatus.setToolTipText(notice);
        if (notice.contains("Không thể chia sẻ")) {
            sharing = false;
            updateControls();
        }
    }

    private void send(ChatMessage.Type type, String detail) {
        ChatMessage message = new ChatMessage(type, client.getCurrentUser(), detail);
        message.setRecipient(peer);
        message.setCallId(id);
        client.send(message);
    }

    private void hangUp() {
        if (closed) return;
        if (incoming && media == null) {
            send(ChatMessage.Type.CALL_REJECT, "Đối phương đã từ chối cuộc gọi.");
            finish("Bạn đã từ chối cuộc gọi.");
        } else {
            send(ChatMessage.Type.CALL_END, "END");
            finish(media == null ? "Đã hủy cuộc gọi." : "Cuộc gọi đã kết thúc.");
        }
    }

    private void finish(String detail) {
        finish(detail, false);
    }

    private void finish(String detail, boolean missed) {
        if (closed) return;
        closed = true;
        ringTimeout.stop();
        ringSound.stop();
        refresh.stop();
        if (media != null) media.close();
        dispose();
        String kind = video ? "Gọi video" : "Gọi thoại";
        onFinished.accept(new Outcome(missed ? "Cuộc gọi nhỡ · " + kind : kind + " · " + detail, missed));
    }

    private static void drawFrame(Graphics2D g, BufferedImage frame, int x, int y, int width, int height) {
        double scale = Math.min((double) width / frame.getWidth(), (double) height / frame.getHeight());
        int w = (int) (frame.getWidth() * scale);
        int h = (int) (frame.getHeight() * scale);
        g.drawImage(frame, x + (width - w) / 2, y + (height - h) / 2, w, h, null);
    }
}
