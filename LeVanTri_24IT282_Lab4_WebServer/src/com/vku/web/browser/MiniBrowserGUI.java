package com.vku.web.browser;

import com.vku.web.common.DesktopTheme;
import com.vku.web.common.HistoryItem;
import com.vku.web.common.HttpResponse;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.HyperlinkEvent;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.File;
import java.net.URI;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

import static com.vku.web.common.DesktopTheme.*;

public class MiniBrowserGUI extends JFrame {
    private static final String HOME = "http://localhost:8080/";
    private static final int PREVIEW = 0;
    private static final int INSPECTOR = 1;
    private static final int POST = 2;

    private final List<HistoryItem> history = new ArrayList<>();
    private final Deque<String> back = new ArrayDeque<>();
    private final Deque<String> forward = new ArrayDeque<>();
    private String currentUrl = HOME;
    private String displayedUrl = HOME;
    private int requestId;

    private JButton backButton;
    private JButton forwardButton;
    private JTextField urlField;
    private JComboBox<String> methodBox;
    private JTabbedPane views;
    private JEditorPane preview;
    private JPanel previewContent;
    private MediaPreview mediaPreview;
    private JPanel mediaActions;
    private JLabel status;
    private JLabel responseInfo;
    private DefaultTableModel headers;
    private JTextArea rawResponse;
    private JTextArea postBody;
    private JTextArea postResponse;

    public MiniBrowserGUI() {
        super("VKU Lab 4 · Mini Browser");
        initUI();
        navigate(HOME, "GET", null, false);
    }

    private void initUI() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(820, 570));
        setSize(1100, 715);
        setLocationRelativeTo(null);

        JPanel main = panel(new BorderLayout(), BACKGROUND);
        JPanel top = panel(new BorderLayout(), NAVY);
        top.add(createHeader(), BorderLayout.NORTH);
        JPanel controls = panel(new BorderLayout(0, 10), BACKGROUND);
        controls.setBorder(new EmptyBorder(12, 24, 0, 24));
        controls.add(createNavigation(), BorderLayout.NORTH);
        controls.add(createShortcuts(), BorderLayout.SOUTH);
        top.add(controls, BorderLayout.CENTER);
        main.add(top, BorderLayout.NORTH);

        JPanel workspace = panel(new BorderLayout(), BACKGROUND);
        workspace.setBorder(new EmptyBorder(14, 24, 14, 24));
        views = new JTabbedPane();
        views.setForeground(INK);
        views.addTab("Xem trang", createPreview());
        views.addTab("Phản hồi HTTP", createInspector());
        views.addTab("Gửi POST", createPostPane());
        workspace.add(views, BorderLayout.CENTER);
        main.add(workspace, BorderLayout.CENTER);

        JPanel footer = panel(new BorderLayout(16, 0), SURFACE);
        footer.setBorder(new EmptyBorder(10, 24, 10, 24));
        status = label("Sẵn sàng", 13, Font.BOLD, MUTED);
        status.setBorder(new EmptyBorder(0, 0, 0, 10));
        responseInfo = label("", 12, Font.PLAIN, MUTED);
        footer.add(status, BorderLayout.WEST);
        footer.add(responseInfo, BorderLayout.EAST);
        main.add(footer, BorderLayout.SOUTH);
        setContentPane(main);
    }

    private JPanel createHeader() {
        JPanel header = panel(new BorderLayout(20, 0), NAVY);
        header.setBorder(new EmptyBorder(16, 24, 16, 24));
        JPanel identity = panel(new BorderLayout(0, 3), NAVY);
        identity.add(label("VKU  /  LAB 4", 12, Font.BOLD, MINT), BorderLayout.NORTH);
        identity.add(label("Mini Browser", 25, Font.BOLD, SURFACE), BorderLayout.CENTER);
        header.add(identity, BorderLayout.WEST);

        JPanel tools = panel(new GridBagLayout(), NAVY);
        JPanel actions = panel(new FlowLayout(FlowLayout.RIGHT, 6, 0), NAVY);
        JButton historyButton = button("Lịch sử", false);
        historyButton.addActionListener(e -> showHistory());
        JButton download = button("Tải tệp", false);
        download.addActionListener(e -> openDownloadManager(urlField.getText().trim()));
        actions.add(historyButton);
        actions.add(download);
        tools.add(actions);
        header.add(tools, BorderLayout.EAST);
        return header;
    }

    private JPanel createNavigation() {
        JPanel navigation = panel(new BorderLayout(10, 0), BACKGROUND);
        JPanel arrows = panel(new FlowLayout(FlowLayout.LEFT, 4, 0), BACKGROUND);
        backButton = navigationButton("←", "Quay lại", this::goBack);
        forwardButton = navigationButton("→", "Tiến tới", this::goForward);
        arrows.add(backButton);
        arrows.add(forwardButton);
        arrows.add(navigationButton("Tải lại", "Tải lại",
                () -> navigate(currentUrl, (String) methodBox.getSelectedItem(), null, false)));
        arrows.add(navigationButton("Trang chủ", "Trang chủ", () -> navigate(HOME, "GET", null, true)));
        updateNavButtons();
        navigation.add(arrows, BorderLayout.WEST);

        JPanel address = panel(new BorderLayout(6, 0), BACKGROUND);
        methodBox = new JComboBox<>(new String[]{"GET", "HEAD", "POST"});
        methodBox.getAccessibleContext().setAccessibleName("Phương thức HTTP");
        methodBox.setBackground(SURFACE);
        methodBox.setForeground(INK);
        urlField = new JTextField(HOME);
        urlField.getAccessibleContext().setAccessibleName("Địa chỉ URL");
        urlField.setForeground(INK);
        urlField.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(LINE),
                new EmptyBorder(5, 10, 5, 10)));
        urlField.addActionListener(e -> sendFromAddress());
        JButton send = button("Gửi yêu cầu", true);
        send.addActionListener(e -> sendFromAddress());
        address.add(methodBox, BorderLayout.WEST);
        address.add(urlField, BorderLayout.CENTER);
        address.add(send, BorderLayout.EAST);
        navigation.add(address, BorderLayout.CENTER);
        return navigation;
    }

    private JButton navigationButton(String text, String description, Runnable action) {
        JButton navButton = button(text, false);
        navButton.setToolTipText(description);
        navButton.getAccessibleContext().setAccessibleName(description);
        navButton.addActionListener(e -> action.run());
        return navButton;
    }

    private JPanel createShortcuts() {
        JPanel shortcuts = panel(new FlowLayout(FlowLayout.LEFT, 6, 0), BACKGROUND);
        shortcuts.add(label("Dịch vụ", 13, Font.BOLD, MUTED));
        shortcuts.add(navigationButton("PDF", "Tài liệu PDF", () -> navigate(HOME + "sample.pdf", "GET", null, true)));
        shortcuts.add(navigationButton("MP3", "Âm thanh MP3", () -> navigate(HOME + "sample.mp3", "GET", null, true)));
        shortcuts.add(navigationButton("MP4", "Video MP4", () -> navigate(HOME + "sample.mp4", "GET", null, true)));
        shortcuts.add(navigationButton("API thống kê", "GET /api/stats", () -> navigate(HOME + "api/stats", "GET", null, true)));
        shortcuts.add(navigationButton("HEAD video", "HEAD /sample.mp4", () -> navigate(HOME + "sample.mp4", "HEAD", null, true)));
        return shortcuts;
    }

    private JPanel createPreview() {
        preview = new JEditorPane();
        preview.setEditable(false);
        preview.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true);
        preview.setMargin(new Insets(16, 18, 16, 18));
        preview.addHyperlinkListener(e -> {
            if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED && e.getDescription() != null) {
                try {
                    navigate(URI.create(displayedUrl).resolve(e.getDescription()).toString(), "GET", null, true);
                } catch (IllegalArgumentException ex) {
                    setStatus("Liên kết không hợp lệ", ERROR_COLOR);
                }
            }
        });
        JPanel view = panel(new BorderLayout(), SURFACE);
        previewContent = panel(new CardLayout(), SURFACE);
        previewContent.add(scroll(preview), "page");
        view.add(previewContent, BorderLayout.CENTER);
        mediaActions = panel(new FlowLayout(FlowLayout.LEFT, 12, 8), SURFACE);
        JButton open = button("Mở trong trình duyệt ngoài", true);
        open.addActionListener(e -> {
            try {
                Desktop.getDesktop().browse(URI.create(displayedUrl));
            } catch (Exception ex) {
                setStatus("Không mở được trình duyệt ngoài", ERROR_COLOR);
            }
        });
        mediaActions.add(open);
        mediaActions.setVisible(false);
        view.add(mediaActions, BorderLayout.SOUTH);
        return view;
    }

    private JPanel createInspector() {
        JPanel inspector = panel(new BorderLayout(), SURFACE);
        inspector.setBorder(new EmptyBorder(12, 12, 12, 12));
        headers = new DefaultTableModel(new String[]{"Header", "Giá trị"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };
        JTable table = new JTable(headers);
        styleTable(table);
        rawResponse = textArea(false);
        inspector.add(split(section("Response headers", scroll(table)),
                section("Nội dung phản hồi", scroll(rawResponse))), BorderLayout.CENTER);
        return inspector;
    }

    private JPanel createPostPane() {
        JPanel post = panel(new BorderLayout(0, 12), SURFACE);
        post.setBorder(new EmptyBorder(12, 12, 12, 12));
        JPanel bar = panel(new BorderLayout(), SURFACE);
        bar.add(label("Gửi dữ liệu đến URL trên thanh địa chỉ", 14, Font.PLAIN, MUTED), BorderLayout.WEST);
        JButton submit = button("Gửi POST", true);
        submit.addActionListener(e -> {
            String url = urlField.getText().trim();
            if (url.endsWith("/")) url += "api/echo";
            navigate(url, "POST", postBody.getText(), true);
        });
        bar.add(submit, BorderLayout.EAST);
        post.add(bar, BorderLayout.NORTH);

        postBody = textArea(true);
        postBody.setText("name=NguyenVanA&mssv=24NS001&monhoc=LTM");
        postResponse = textArea(false);
        post.add(split(section("Dữ liệu gửi", scroll(postBody)),
                section("Phản hồi POST", scroll(postResponse))), BorderLayout.CENTER);
        return post;
    }

    private JPanel section(String title, JScrollPane content) {
        JPanel section = panel(new BorderLayout(0, 7), SURFACE);
        section.add(label(title, 14, Font.BOLD, INK), BorderLayout.NORTH);
        section.add(content, BorderLayout.CENTER);
        return section;
    }

    private JSplitPane split(JComponent top, JComponent bottom) {
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, top, bottom);
        split.setBorder(null);
        split.setBackground(SURFACE);
        split.setDividerSize(10);
        split.setResizeWeight(0.42);
        return split;
    }

    private JTextArea textArea(boolean editable) {
        JTextArea text = new JTextArea();
        text.setEditable(editable);
        text.setForeground(INK);
        text.setFont(new Font("Consolas", Font.PLAIN, 14));
        text.setMargin(new Insets(10, 10, 10, 10));
        text.setLineWrap(true);
        text.setWrapStyleWord(true);
        return text;
    }

    private void sendFromAddress() {
        navigate(urlField.getText().trim(), (String) methodBox.getSelectedItem(), null, true);
    }

    public void navigate(String url, String method, String body, boolean addToBack) {
        if (url.isBlank()) {
            setStatus("Nhập địa chỉ URL", ERROR_COLOR);
            urlField.requestFocusInWindow();
            return;
        }
        String destination = url.contains("://") ? url : "http://" + url;
        urlField.setText(destination);
        methodBox.setSelectedItem(method);
        if (addToBack && !currentUrl.equals(destination)) {
            back.push(currentUrl);
            forward.clear();
        }
        currentUrl = destination;
        updateNavButtons();
        int id = ++requestId;
        if (mediaPreview != null) mediaPreview.stopPlayback();
        ((CardLayout) previewContent.getLayout()).show(previewContent, "page");
        setStatus("Đang gửi " + method + "…", MUTED);
        responseInfo.setText("");

        new Thread(() -> {
            try {
                HttpResponse response = HttpEngine.execute(method, destination, body, true);
                SwingUtilities.invokeLater(() -> {
                    history.add(new HistoryItem(destination, method, response.getStatusCode()));
                    if (id == requestId) showResponse(response, method, destination);
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    if (id == requestId) showError(ex, method);
                });
            }
        }, "MiniBrowser-HTTP").start();
    }

    private void showResponse(HttpResponse response, String method, String destination) {
        int code = response.getStatusCode();
        setStatus(method + "  ·  " + code + " " + response.getStatusMessage(),
                code < 300 ? ACCENT : code < 400 ? MUTED : ERROR_COLOR);

        headers.setRowCount(0);
        response.getHeaders().forEach((name, value) -> headers.addRow(new Object[]{name, value}));
        String mime = response.getHeader("content-type");
        String type = mime == null ? "" : mime.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        responseInfo.setText(type);
        boolean media = type.startsWith("audio/") || type.startsWith("video/") || type.equals("application/pdf");
        boolean head = "HEAD".equals(method);
        boolean post = "POST".equals(method);
        String body = head ? "" : media ? "Tệp nhị phân · Bạn có thể mở bằng trình duyệt ngoài hoặc tải về máy."
                : response.getBodyAsString();
        rawResponse.setText(body);
        rawResponse.setCaretPosition(0);
        if (post) {
            postResponse.setText(body);
            postResponse.setCaretPosition(0);
        }

        displayedUrl = destination;
        mediaActions.setVisible(media && !head && code < 300);
        setPreview(!head && type.equals("text/html") ? "text/html" : "text/plain",
                head ? "Xem các header trong tab Phản hồi HTTP." : body);
        views.setSelectedIndex(head ? INSPECTOR : post ? POST : PREVIEW);
        if ("GET".equals(method) && code >= 200 && code < 300 && HttpEngine.isPlayableMedia(type)) {
            showMedia(destination, type.equals("video/mp4"));
        }
    }

    private void showMedia(String url, boolean video) {
        try {
            if (mediaPreview == null) {
                mediaPreview = new MediaPreview();
                previewContent.add(mediaPreview, "media");
            }
            ((CardLayout) previewContent.getLayout()).show(previewContent, "media");
            mediaPreview.open(url, video);
        } catch (LinkageError | RuntimeException error) {
            ((CardLayout) previewContent.getLayout()).show(previewContent, "page");
            setPreview("text/plain", "Không khởi tạo được trình phát. Bạn vẫn có thể tải tệp hoặc mở ngoài.");
        }
    }

    private void showError(Exception error, String method) {
        String message = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
        setStatus("Yêu cầu " + method + " thất bại", ERROR_COLOR);
        headers.setRowCount(0);
        mediaActions.setVisible(false);
        rawResponse.setText(message);
        setPreview("text/plain", "Không thể đọc phản hồi HTTP.\n\n" + message);
        if ("POST".equals(method)) postResponse.setText(message);
        views.setSelectedIndex("POST".equals(method) ? POST : PREVIEW);
    }

    private void setPreview(String contentType, String text) {
        preview.setContentType(contentType);
        preview.setText(text);
        preview.setCaretPosition(0);
    }

    private void setStatus(String text, Color color) {
        status.setText(text);
        status.setForeground(color);
    }

    private void goBack() {
        if (!back.isEmpty()) {
            forward.push(currentUrl);
            navigate(back.pop(), "GET", null, false);
        }
    }

    private void goForward() {
        if (!forward.isEmpty()) {
            back.push(currentUrl);
            navigate(forward.pop(), "GET", null, false);
        }
    }

    private void updateNavButtons() {
        backButton.setEnabled(!back.isEmpty());
        forwardButton.setEnabled(!forward.isEmpty());
    }

    private void openDownloadManager(String url) {
        if (url.isBlank()) {
            setStatus("Nhập URL của tệp cần tải", ERROR_COLOR);
            return;
        }
        String destination = url.contains("://") ? url : "http://" + url;
        String path;
        try {
            path = URI.create(destination).getRawPath();
        } catch (IllegalArgumentException ex) {
            setStatus("Địa chỉ URL không hợp lệ", ERROR_COLOR);
            return;
        }
        String filename = path == null ? "" : path.substring(path.lastIndexOf('/') + 1);
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File(filename.isEmpty() ? "download.html" : filename));
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            new DownloadManagerDialog(this, destination, chooser.getSelectedFile()).setVisible(true);
        }
    }

    private void showHistory() {
        new HistoryDialog(this, history, url -> navigate(url, "GET", null, true)).setVisible(true);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            DesktopTheme.install();
            new MiniBrowserGUI().setVisible(true);
        });
    }
}
