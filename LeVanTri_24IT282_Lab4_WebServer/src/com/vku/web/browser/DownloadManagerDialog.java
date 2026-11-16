package com.vku.web.browser;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.File;

import static com.vku.web.common.DesktopTheme.*;

public class DownloadManagerDialog extends JDialog {
    private JProgressBar progressBar;
    private JLabel statusLabel;
    private JLabel fileLabel;
    private JButton btnOpen;

    public DownloadManagerDialog(Frame parent, String url, File targetFile) {
        super(parent, "Tải xuống", false);
        setSize(535, 222);
        setLocationRelativeTo(parent);

        JPanel main = panel(new BorderLayout(0, 12), BACKGROUND);
        main.setBorder(new EmptyBorder(16, 18, 16, 18));

        fileLabel = label(targetFile.getName(), 19, Font.BOLD, INK);
        fileLabel.setToolTipText(targetFile.getAbsolutePath());
        main.add(fileLabel, BorderLayout.NORTH);

        JPanel center = panel(new GridLayout(2, 1, 0, 6), BACKGROUND);
        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);

        statusLabel = label("Đang kết nối…", 13, Font.PLAIN, MUTED);

        center.add(progressBar);
        center.add(statusLabel);
        main.add(center, BorderLayout.CENTER);

        JPanel bottom = panel(new FlowLayout(FlowLayout.RIGHT, 6, 0), BACKGROUND);
        btnOpen = button("Mở thư mục", true);
        btnOpen.setEnabled(false);
        btnOpen.addActionListener(e -> {
            try {
                Desktop.getDesktop().open(targetFile.getParentFile());
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Không thể mở: " + ex.getMessage());
            }
        });
        JButton btnClose = button("Đóng", false);
        btnClose.addActionListener(e -> dispose());
        bottom.add(btnClose);
        bottom.add(btnOpen);
        main.add(bottom, BorderLayout.SOUTH);

        add(main);

        // Start async download
        new Thread(() -> {
            try {
                HttpEngine.download(url, targetFile, (downloaded, total) -> {
                    SwingUtilities.invokeLater(() -> {
                        if (total > 0) {
                            int pct = (int) ((downloaded * 100) / total);
                            progressBar.setValue(pct);
                            statusLabel.setText(String.format("Đã tải: %d KB / %d KB (%d%%)", downloaded / 1024, total / 1024, pct));
                        } else {
                            progressBar.setIndeterminate(true);
                            statusLabel.setText(String.format("Đã tải: %d KB", downloaded / 1024));
                        }
                    });
                });
                SwingUtilities.invokeLater(() -> {
                    progressBar.setIndeterminate(false);
                    progressBar.setValue(100);
                    statusLabel.setText("Tải xuống hoàn tất");
                    statusLabel.setForeground(ACCENT);
                    btnOpen.setEnabled(true);
                });
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    progressBar.setIndeterminate(false);
                    statusLabel.setText("Tải xuống thất bại");
                    statusLabel.setForeground(ERROR_COLOR);
                    statusLabel.setToolTipText(e.getMessage());
                });
            }
        }).start();
    }
}
