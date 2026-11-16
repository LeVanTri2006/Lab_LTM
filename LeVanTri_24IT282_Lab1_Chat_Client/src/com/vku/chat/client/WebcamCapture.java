package com.vku.chat.client;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.regex.Pattern;

final class WebcamCapture implements AutoCloseable {
    static final int WIDTH = 320;
    static final int HEIGHT = 240;
    private static final Pattern WINDOWS_VIDEO_DEVICE = Pattern.compile("\"([^\"]+)\"\\s+\\(video\\)");
    private Process process;
    private boolean closed;
    private volatile BufferedImage frame;

    WebcamCapture(Consumer<String> unavailable) {
        Thread reader = new Thread(() -> capture(unavailable), "Call-Webcam");
        reader.setDaemon(true);
        reader.start();
    }

    BufferedImage frame() {
        return frame;
    }

    private void capture(Consumer<String> unavailable) {
        String device = System.getenv("VKU_CHAT_CAMERA");
        String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        boolean windows = os.startsWith("windows");
        String ffmpeg = ffmpegCommand(windows, System.getenv("VKU_CHAT_FFMPEG"));
        String failure = "Camera ngừng gửi hình. Hãy tắt rồi bật lại camera.";
        List<String> args = new ArrayList<>(List.of(ffmpeg, "-nostdin", "-hide_banner", "-loglevel", "error"));
        try {
            if (windows) {
                if (device == null || device.isBlank()) device = firstWindowsDevice(ffmpeg);
                args.addAll(List.of("-f", "dshow", "-i", "video=" + device));
            } else if (os.contains("linux")) {
                args.addAll(List.of("-f", "v4l2", "-i", device == null ? "/dev/video0" : device));
            } else {
                throw new IOException("Tính năng camera hỗ trợ Windows và Linux.");
            }
            args.addAll(List.of("-an", "-vf", "fps=12,scale=320:240:force_original_aspect_ratio=decrease,pad=320:240:(ow-iw)/2:(oh-ih)/2",
                    "-pix_fmt", "bgr24", "-threads", "1", "-f", "rawvideo", "pipe:1"));
            Process capture = start(new ProcessBuilder(args).redirectError(ProcessBuilder.Redirect.INHERIT));
            try (var input = capture.getInputStream()) {
                while (true) {
                    var image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_3BYTE_BGR);
                    byte[] pixels = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
                    if (input.readNBytes(pixels, 0, pixels.length) != pixels.length) {
                        if (frame == null) failure = "Camera chưa có hình. Hãy kiểm tra quyền và đóng ứng dụng đang dùng webcam.";
                        break;
                    }
                    frame = image;
                }
            }
        } catch (IOException e) {
            failure = e.getMessage();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            synchronized (this) {
                if (process != null) process.destroyForcibly();
                frame = null;
                if (!closed) unavailable.accept(failure);
            }
        }
    }

    private static String ffmpegCommand(boolean windows, String configured) {
        if (configured != null && !configured.isBlank()) return configured;
        if (windows) {
            Path portable = Path.of("ffmpeg.exe").toAbsolutePath();
            if (Files.isRegularFile(portable)) return portable.toString();
        }
        return "ffmpeg";
    }

    private String firstWindowsDevice(String ffmpeg) throws IOException, InterruptedException {
        Process listing = start(new ProcessBuilder(ffmpeg, "-hide_banner", "-list_devices", "true", "-f", "dshow", "-i", "dummy")
                .redirectErrorStream(true));
        if (!listing.waitFor(4, TimeUnit.SECONDS)) throw new IOException("Camera không phản hồi khi dò thiết bị.");
        try (var input = listing.getInputStream()) {
            var matcher = WINDOWS_VIDEO_DEVICE.matcher(new String(input.readAllBytes(), StandardCharsets.UTF_8));
            if (matcher.find()) return matcher.group(1);
        }
        throw new IOException("FFmpeg không tìm thấy camera. Hãy kiểm tra webcam và quyền camera trên Windows.");
    }

    private synchronized Process start(ProcessBuilder builder) throws IOException {
        if (closed) throw new IOException("Camera was closed");
        try {
            process = builder.start();
        } catch (IOException e) {
            String hint = System.getProperty("os.name").toLowerCase(Locale.ROOT).startsWith("windows")
                    ? "Đặt ffmpeg.exe cạnh run_client.bat hoặc kiểm tra PATH."
                    : "Kiểm tra ffmpeg trong PATH.";
            throw new IOException("Camera không chạy được FFmpeg. " + hint, e);
        }
        return process;
    }

    @Override
    public synchronized void close() {
        closed = true;
        frame = null;
        if (process != null) process.destroyForcibly();
    }
}
