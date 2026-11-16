package com.vku.chat.client;

import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.TargetDataLine;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.ByteArrayOutputStream;
import java.util.function.Consumer;

import static com.vku.chat.client.ChatTheme.*;
import static com.vku.chat.client.ChatTheme.Glyph.*;

final class VoiceRecorderDialog extends JDialog {
    private static final int MAX_BYTES = VoiceNote.BYTES_PER_SECOND * 60;
    private static final int MIN_BYTES = VoiceNote.BYTES_PER_SECOND / 5;
    private final JLabel status = label("Đang mở mic…", 13, Font.PLAIN, MUTED);
    private final JLabel elapsed = label("00:00 / 01:00", 24, Font.BOLD, INK);
    private final JButton send = button("Gửi bản ghi", SEND, BLUE, Color.WHITE);
    private final Timer refresh;
    private final Consumer<VoiceNote> onSend;
    private volatile TargetDataLine microphone;
    private volatile int recordedBytes;
    private volatile boolean stopping;
    private boolean cancelled;
    private boolean sendRequested;
    private VoiceNote recording;

    VoiceRecorderDialog(Frame owner, Consumer<VoiceNote> onSend) {
        super(owner, "Ghi âm tin nhắn", true);
        this.onSend = onSend;
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent event) { cancel(); }
        });

        JPanel panel = new JPanel(new BorderLayout(0, 14));
        panel.setBackground(Color.WHITE);
        panel.setBorder(new EmptyBorder(19, 22, 19, 22));
        JLabel title = label("Ghi âm tin nhắn", 18, Font.BOLD, INK);
        title.setIcon(icon(MIC, BLUE, 21));
        title.setIconTextGap(9);
        panel.add(title, BorderLayout.NORTH);

        JPanel readout = new JPanel(new GridLayout(2, 1, 0, 5));
        readout.setBackground(SOFT);
        readout.setBorder(new EmptyBorder(14, 14, 14, 14));
        elapsed.setHorizontalAlignment(SwingConstants.CENTER);
        status.setHorizontalAlignment(SwingConstants.CENTER);
        status.setPreferredSize(new Dimension(310, status.getPreferredSize().height));
        readout.add(elapsed);
        readout.add(status);
        panel.add(readout, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        JButton cancel = button("Hủy", null, SOFT, NAV);
        cancel.addActionListener(event -> cancel());
        send.setEnabled(false);
        send.addActionListener(event -> finish());
        actions.add(cancel);
        actions.add(send);
        panel.add(actions, BorderLayout.SOUTH);
        setContentPane(panel);
        setResizable(false);
        pack();
        setLocationRelativeTo(owner);

        refresh = new Timer(100, event -> refreshReadout());
        refresh.start();
        capture();
    }

    private void capture() {
        new SwingWorker<VoiceNote, Void>() {
            @Override protected VoiceNote doInBackground() throws Exception {
                var pcm = new ByteArrayOutputStream();
                byte[] samples = new byte[640];
                try (TargetDataLine line = AudioSystem.getTargetDataLine(VoiceNote.FORMAT)) {
                    line.open(VoiceNote.FORMAT, samples.length * 5);
                    microphone = line;
                    if (stopping) return null;
                    line.start();
                    while (!stopping && pcm.size() < MAX_BYTES) {
                        int count = line.read(samples, 0, Math.min(samples.length, MAX_BYTES - pcm.size()));
                        if (count <= 0) break;
                        pcm.write(samples, 0, count);
                        recordedBytes = pcm.size();
                    }
                } catch (IllegalStateException e) {
                    if (!stopping) throw e;
                }
                return pcm.size() < MIN_BYTES ? null : VoiceNote.fromPcm(pcm.toByteArray());
            }

            @Override protected void done() {
                if (cancelled) return;
                refresh.stop();
                refreshReadout();
                try {
                    recording = get();
                    if (recording == null) {
                        status.setText("Chưa thu được âm thanh từ mic.");
                        send.setEnabled(false);
                    } else if (sendRequested) {
                        sendRecording();
                    } else {
                        status.setText("Bản ghi đã sẵn sàng để gửi.");
                        send.setEnabled(true);
                    }
                } catch (Exception e) {
                    status.setText("Không mở được mic. Hãy kiểm tra thiết bị.");
                    send.setEnabled(false);
                }
            }
        }.execute();
    }

    private void refreshReadout() {
        int seconds = recordedBytes / VoiceNote.BYTES_PER_SECOND;
        elapsed.setText(String.format("%02d:%02d / 01:00", seconds / 60, seconds % 60));
        if (!stopping && recordedBytes > 0) {
            status.setText("Đang ghi âm…");
            send.setEnabled(recordedBytes >= MIN_BYTES);
        }
    }

    private void finish() {
        if (recording != null) {
            sendRecording();
            return;
        }
        sendRequested = true;
        send.setEnabled(false);
        status.setText("Đang hoàn tất bản ghi…");
        stopCapture();
    }

    private void sendRecording() {
        dispose();
        onSend.accept(recording);
    }

    private void cancel() {
        cancelled = true;
        refresh.stop();
        stopCapture();
        dispose();
    }

    private void stopCapture() {
        stopping = true;
        TargetDataLine line = microphone;
        if (line != null) {
            Thread closer = new Thread(line::close, "VoiceNote-ReleaseMic");
            closer.setDaemon(true);
            closer.start();
        }
    }
}
