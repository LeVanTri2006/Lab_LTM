package com.vku.web.browser;

import org.eclipse.swt.SWT;
import org.eclipse.swt.SWTError;
import org.eclipse.swt.awt.SWT_AWT;
import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;

import javax.swing.*;
import java.awt.*;
import java.util.Locale;
import java.util.Objects;

final class MediaPreview extends JPanel {
    private final Canvas canvas = new Canvas();
    private final JLabel status = new JLabel("Đang mở trình phát…", SwingConstants.CENTER);
    private volatile Display display;
    private volatile String page;
    private Browser browser; // SWT thread only
    private String displayedPage; // SWT thread only
    private boolean started;

    MediaPreview() {
        super(new BorderLayout());
        if (!System.getProperty("os.name").toLowerCase(Locale.ROOT).startsWith("windows")
                || !System.getProperty("os.arch").equals("amd64")) {
            throw new UnsupportedOperationException("Trình phát cần Windows x64");
        }
        add(canvas, BorderLayout.CENTER);
        add(status, BorderLayout.NORTH);
    }

    void open(String url, boolean video) {
        String source = url.replace("&", "&amp;").replace("\"", "&quot;")
                .replace("<", "&lt;").replace(">", "&gt;");
        String tag = video ? "video" : "audio";
        page = """
                <!doctype html><html lang="vi"><meta charset="UTF-8">
                <style>
                html, body { height:100%%; margin:0; }
                body { display:grid; place-items:center; background:#14283f; color:#edf1f6; font:16px Segoe UI,sans-serif; }
                main { width:100%%; height:100%%; display:grid; align-content:center; justify-items:center; gap:18px; }
                video { max-width:100%%; max-height:98vh; }
                audio { width:min(85%%,680px); }
                </style>
                <main>
                %s
                <%s controls preload="metadata" src="%s"></%s>
                </main></html>
                """.formatted(video ? "" : "<span>Âm thanh MP3</span>", tag, source, tag);
        if (!started) {
            started = true;
            Thread eventLoop = new Thread(this::runBrowser, "MiniBrowser-WebView2");
            eventLoop.setDaemon(true);
            eventLoop.start();
        } else {
            refresh();
        }
    }

    void stopPlayback() {
        page = null;
        refresh();
    }

    private void runBrowser() {
        try {
            display = new Display();
            Shell shell = SWT_AWT.new_Shell(display, canvas);
            shell.setLayout(new FillLayout());
            browser = new Browser(shell, SWT.EDGE);
            shell.layout();
            SwingUtilities.invokeLater(() -> status.setVisible(false));
            loadPage();
            while (!shell.isDisposed()) {
                if (!display.readAndDispatch()) display.sleep();
            }
        } catch (RuntimeException | SWTError | LinkageError error) {
            System.err.println("Mini Browser · WebView2: " + error);
            SwingUtilities.invokeLater(() -> {
                status.setText("Không khởi tạo được trình phát WebView2. Bạn có thể mở tệp bằng trình duyệt ngoài.");
                status.setVisible(true);
            });
        } finally {
            if (display != null && !display.isDisposed()) display.dispose();
            display = null;
        }
    }

    private void refresh() {
        Display current = display;
        if (current == null) return;
        try {
            current.asyncExec(this::loadPage);
        } catch (org.eclipse.swt.SWTException ignored) {
            // The native window closed before the queued update.
        }
    }

    private void loadPage() {
        if (browser == null || browser.isDisposed()) return;
        String content = page;
        if (Objects.equals(content, displayedPage)) return;
        displayedPage = content;
        if (content == null) browser.setUrl("about:blank");
        else browser.setText(content, false);
    }
}
