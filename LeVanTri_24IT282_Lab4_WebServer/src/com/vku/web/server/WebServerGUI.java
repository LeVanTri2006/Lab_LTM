package com.vku.web.server;

import com.vku.web.common.DesktopTheme;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.net.URI;
import java.util.concurrent.ExecutionException;

import static com.vku.web.common.DesktopTheme.*;

public class WebServerGUI extends JFrame {
    private final HttpWebServer server;
    private JLabel serverStatus;
    private JLabel databaseStatus;
    private JLabel databaseDetail;
    private JLabel requestCount;
    private JTextArea logArea;

    public WebServerGUI(HttpWebServer server) {
        super("VKU Lab 4 · Web Server");
        this.server = server;
        initUI();
        server.setGui(this);
    }

    private void initUI() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(800, 525));
        setSize(1040, 640);
        setLocationRelativeTo(null);

        JPanel main = panel(new BorderLayout(), BACKGROUND);
        JPanel header = panel(new BorderLayout(20, 0), NAVY);
        header.setBorder(new EmptyBorder(20, 24, 20, 24));
        JPanel identity = panel(new GridLayout(0, 1, 0, 3), NAVY);
        identity.add(label("VKU  /  LAB 4", 12, Font.BOLD, MINT));
        identity.add(label("Bảng điều khiển máy chủ", 25, Font.BOLD, SURFACE));
        header.add(identity, BorderLayout.WEST);
        JButton openBrowser = button("Mở trang web", true);
        openBrowser.addActionListener(e -> openWebPage());
        JPanel headerActions = panel(new GridBagLayout(), NAVY);
        headerActions.add(openBrowser);
        header.add(headerActions, BorderLayout.EAST);
        main.add(header, BorderLayout.NORTH);

        JPanel content = panel(new BorderLayout(0, 16), BACKGROUND);
        content.setBorder(new EmptyBorder(16, 24, 20, 24));
        JPanel metrics = panel(new GridLayout(1, 3, 12, 0), BACKGROUND);
        serverStatus = label("Đang khởi động…", 17, Font.BOLD, INK);
        databaseStatus = label("Đang kiểm tra…", 17, Font.BOLD, INK);
        databaseDetail = label("localhost:3306 / db_web", 12, Font.PLAIN, MUTED);
        requestCount = label("0", 21, Font.BOLD, INK);
        metrics.add(metric("MÁY CHỦ HTTP", serverStatus,
                label("localhost:" + server.getPort(), 12, Font.PLAIN, MUTED)));
        metrics.add(metric("CƠ SỞ DỮ LIỆU", databaseStatus, databaseDetail));
        metrics.add(metric("YÊU CẦU HTTP", requestCount,
                label("Từ khi mở máy chủ", 12, Font.PLAIN, MUTED)));
        content.add(metrics, BorderLayout.NORTH);

        JPanel workspace = panel(new BorderLayout(14, 0), BACKGROUND);
        workspace.add(createLogPanel(), BorderLayout.CENTER);
        workspace.add(createServicesPanel(), BorderLayout.EAST);
        content.add(workspace, BorderLayout.CENTER);
        main.add(content, BorderLayout.CENTER);
        setContentPane(main);
    }

    private JPanel metric(String title, JLabel value, JLabel detail) {
        JPanel card = panel(new GridLayout(0, 1, 0, 3), SURFACE);
        card.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(LINE),
                new EmptyBorder(10, 14, 10, 14)));
        card.add(label(title, 12, Font.BOLD, MUTED));
        card.add(value);
        card.add(detail);
        return card;
    }

    private JPanel createLogPanel() {
        JPanel logs = panel(new BorderLayout(0, 10), SURFACE);
        logs.setBorder(new EmptyBorder(12, 14, 14, 14));
        JPanel bar = panel(new BorderLayout(), SURFACE);
        bar.add(label("Nhật ký HTTP", 17, Font.BOLD, INK), BorderLayout.WEST);
        JButton clear = button("Xóa nhật ký", false);
        clear.addActionListener(e -> logArea.setText(""));
        bar.add(clear, BorderLayout.EAST);
        logs.add(bar, BorderLayout.NORTH);

        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font("Consolas", Font.PLAIN, 13));
        logArea.setBackground(NAVY);
        logArea.setForeground(new Color(0xE1ECF9));
        logArea.setLineWrap(true);
        logArea.setWrapStyleWord(true);
        logArea.setMargin(new Insets(12, 12, 12, 12));
        logs.add(scroll(logArea), BorderLayout.CENTER);
        return logs;
    }

    private JPanel createServicesPanel() {
        JPanel services = panel(new BorderLayout(0, 10), SURFACE);
        services.setBorder(new EmptyBorder(13, 14, 14, 14));
        services.add(label("Dịch vụ", 17, Font.BOLD, INK), BorderLayout.NORTH);

        JPanel routes = panel(new GridLayout(0, 1, 0, 5), SURFACE);
        for (String route : new String[]{"Trang chủ  /", "PDF  /sample.pdf", "MP3  /sample.mp3",
                "MP4  /sample.mp4", "GET  /api/stats", "GET  /api/posts", "POST  /api/echo"}) {
            routes.add(label(route, 13, Font.PLAIN, MUTED));
        }
        JPanel list = panel(new BorderLayout(), SURFACE);
        list.add(routes, BorderLayout.NORTH);
        services.add(list, BorderLayout.CENTER);

        JPanel actions = panel(new GridLayout(0, 1, 0, 7), SURFACE);
        JButton check = button("Kiểm tra MySQL", true);
        check.addActionListener(e -> checkDatabase(check));
        JButton folder = button("Mở thư mục webroot", false);
        folder.addActionListener(e -> {
            try {
                Desktop.getDesktop().open(server.getWebRoot());
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Không thể mở thư mục · " + ex.getMessage());
            }
        });
        actions.add(check);
        actions.add(folder);
        services.add(actions, BorderLayout.SOUTH);
        checkDatabase(check);
        return services;
    }

    private void checkDatabase(JButton check) {
        check.setEnabled(false);
        databaseStatus.setText("Đang kiểm tra…");
        databaseStatus.setForeground(INK);
        databaseDetail.setText("localhost:3306 / db_web");
        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() {
                return server.getDatabase().checkMySQL();
            }

            @Override
            protected void done() {
                try {
                    String error = get();
                    boolean usingMySQL = server.getDatabase().isUsingMySQL();
                    if (error == null) {
                        databaseStatus.setText("MySQL khả dụng");
                        databaseStatus.setForeground(ACCENT);
                        if (!usingMySQL) databaseDetail.setText("Khởi động lại để dùng MySQL");
                        server.log(usingMySQL ? "MySQL · Truy vấn db_web.web_posts thành công."
                                : "MySQL · Truy vấn thành công. Khởi động lại server để chuyển từ tệp .dat sang MySQL.");
                    } else {
                        databaseStatus.setText(usingMySQL ? "MySQL mất kết nối" : "Đang dùng tệp .dat");
                        databaseStatus.setForeground(ERROR_COLOR);
                        server.log("MySQL · " + error);
                    }
                } catch (InterruptedException | ExecutionException ex) {
                    if (ex instanceof InterruptedException) Thread.currentThread().interrupt();
                    databaseStatus.setText("Lỗi kiểm tra MySQL");
                    databaseStatus.setForeground(ERROR_COLOR);
                    server.log("MySQL · " + ex.getMessage());
                } finally {
                    check.setEnabled(true);
                }
            }
        }.execute();
    }

    private void openWebPage() {
        try {
            Desktop.getDesktop().browse(URI.create("http://localhost:" + server.getPort() + "/"));
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Không thể mở trình duyệt · " + ex.getMessage());
        }
    }

    public void appendLog(String message) {
        SwingUtilities.invokeLater(() -> {
            logArea.append(message + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    public void updateRequestCount(long count) {
        SwingUtilities.invokeLater(() -> requestCount.setText(String.format("%,d", count)));
    }

    public void updateServerStatus(boolean listening) {
        SwingUtilities.invokeLater(() -> {
            serverStatus.setText(listening ? "Đang lắng nghe" : "Đã dừng");
            serverStatus.setForeground(listening ? ACCENT : ERROR_COLOR);
        });
    }

    public static void main(String[] args) {
        HttpWebServer server = new HttpWebServer();
        SwingUtilities.invokeLater(() -> {
            DesktopTheme.install();
            WebServerGUI gui = new WebServerGUI(server);
            gui.setVisible(true);
            server.start();
        });
    }
}
