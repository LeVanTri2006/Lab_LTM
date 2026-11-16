package com.vku.chat.client;

import com.vku.chat.server.MediaPacket;
import com.vku.chat.server.UdpMediaStreamer;

import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import javax.sound.sampled.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

final class CallMediaSession implements AutoCloseable {
    private static final AudioFormat AUDIO = new AudioFormat(16_000, 16, 1, true, false);
    private static final int AUDIO_BYTES = 640;
    private static final int MAX_JPEG_BYTES = 55_000;
    private static final int MAX_VIDEO_WIDTH = 426;

    private final DatagramSocket socket;
    private final UUID id;
    private final String username;
    private final String peer;
    private final Consumer<String> mediaStatus;
    private final ArrayBlockingQueue<byte[]> incomingAudio = new ArrayBlockingQueue<>(6);
    private final AtomicReference<MediaPacket> incomingVideo = new AtomicReference<>();
    private volatile boolean running = true;
    private volatile boolean muted;
    private volatile TargetDataLine microphone;
    private volatile SourceDataLine speakers;
    private volatile WebcamCapture webcam;
    private volatile boolean sharing;
    private volatile BufferedImage localFrame;
    private volatile BufferedImage remoteFrame;
    private volatile long remoteFrameTime;

    CallMediaSession(String host, UUID id, String username, String peer, Consumer<String> mediaStatus) throws IOException {
        this.id = id;
        this.username = username;
        this.peer = peer;
        this.mediaStatus = mediaStatus;
        InetAddress address = InetAddress.getByName(host);
        socket = new DatagramSocket();
        socket.connect(address, UdpMediaStreamer.VIDEO_STREAM_PORT);
        worker("Receive", this::receive);
        worker("Register", this::register);
        worker("Microphone", this::captureAudio);
        worker("Playback", this::playAudio);
    }

    void startVideo() {
        worker("Video", this::sendVideo);
        worker("Decode", this::decodeVideo);
    }

    void setMuted(boolean muted) {
        this.muted = muted;
    }

    void setVideoSource(boolean camera, boolean share, Consumer<String> cameraUnavailable) {
        sharing = share;
        WebcamCapture previous = webcam;
        if (previous != null) previous.close();
        webcam = camera ? new WebcamCapture(cameraUnavailable) : null;
        localFrame = null;
    }

    BufferedImage localFrame() { return localFrame; }

    BufferedImage remoteFrame() {
        return System.currentTimeMillis() - remoteFrameTime < 2_500 ? remoteFrame : null;
    }

    private void send(byte type, int sequence, byte[] payload) throws IOException {
        byte[] data = new MediaPacket(type, id, username, peer, sequence, payload).encode();
        socket.send(new DatagramPacket(data, data.length));
    }

    private void register() {
        while (running) {
            try {
                send(MediaPacket.TYPE_REGISTER, 0, new byte[0]);
            } catch (IOException ignored) {
                // Retry the registration while the call is open.
            }
            pause(900);
        }
    }

    private void receive() {
        byte[] buffer = new byte[65_507];
        int lastAudio = -1;
        int lastVideo = -1;
        while (running) {
            try {
                DatagramPacket datagram = new DatagramPacket(buffer, buffer.length);
                socket.receive(datagram);
                MediaPacket packet = MediaPacket.decode(buffer, datagram.getLength());
                if (packet == null || !id.equals(packet.getCallId()) || !peer.equals(packet.getSender())
                        || !username.equals(packet.getTarget())) continue;
                byte[] data = packet.getPayload();
                if (packet.getType() == MediaPacket.TYPE_AUDIO_CHUNK && data.length == AUDIO_BYTES && packet.getSeq() > lastAudio) {
                    lastAudio = packet.getSeq();
                    if (!incomingAudio.offer(data)) {
                        incomingAudio.poll();
                        incomingAudio.offer(data);
                    }
                } else if (packet.getType() == MediaPacket.TYPE_VIDEO_FRAME && packet.getSeq() > lastVideo
                        && data.length <= MAX_JPEG_BYTES) {
                    lastVideo = packet.getSeq();
                    incomingVideo.set(packet);
                } else if (packet.getType() == MediaPacket.TYPE_VIDEO_PAUSED && packet.getSeq() > lastVideo) {
                    lastVideo = packet.getSeq();
                    incomingVideo.set(packet);
                }
            } catch (IOException ignored) {
                if (running) pause(20);
            }
        }
    }

    private void decodeVideo() {
        while (running) {
            MediaPacket frame = incomingVideo.getAndSet(null);
            if (frame != null) {
                if (frame.getType() == MediaPacket.TYPE_VIDEO_PAUSED) {
                    remoteFrame = null;
                } else {
                    try {
                        remoteFrame = decodeFrame(frame.getPayload());
                        remoteFrameTime = System.currentTimeMillis();
                    } catch (IOException ignored) {
                        // Keep displaying the last complete frame.
                    }
                }
            }
            pause(35);
        }
    }

    private static BufferedImage decodeFrame(byte[] jpeg) throws IOException {
        var reader = ImageIO.getImageReadersByFormatName("jpeg").next();
        try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(jpeg))) {
            reader.setInput(input, true, true);
            if (reader.getWidth(0) > MAX_VIDEO_WIDTH || reader.getHeight(0) > WebcamCapture.HEIGHT) {
                throw new IOException("Video frame exceeds the supported resolution");
            }
            return reader.read(0);
        } finally {
            reader.dispose();
        }
    }

    private void captureAudio() {
        try (TargetDataLine line = AudioSystem.getTargetDataLine(AUDIO)) {
            line.open(AUDIO, AUDIO_BYTES * 5);
            microphone = line;
            if (!running) return;
            line.start();
            byte[] samples = new byte[AUDIO_BYTES];
            int sequence = 0;
            while (running) {
                int read = line.read(samples, 0, samples.length);
                if (read == samples.length && !muted && running) {
                    try {
                        send(MediaPacket.TYPE_AUDIO_CHUNK, sequence++, samples);
                    } catch (IOException ignored) {
                        // A lost packet should not stop microphone capture.
                    }
                }
            }
        } catch (LineUnavailableException | IllegalArgumentException | IllegalStateException | SecurityException e) {
            if (running) mediaStatus.accept("Không mở được mic. Kiểm tra thiết bị và quyền truy cập.");
        }
    }

    private void playAudio() {
        try (SourceDataLine line = AudioSystem.getSourceDataLine(AUDIO)) {
            line.open(AUDIO, AUDIO_BYTES * 5);
            speakers = line;
            if (!running) return;
            line.start();
            while (running) {
                byte[] samples = incomingAudio.poll(150, TimeUnit.MILLISECONDS);
                if (samples != null) line.write(samples, 0, samples.length);
            }
        } catch (LineUnavailableException | IllegalArgumentException | IllegalStateException | SecurityException e) {
            if (running) mediaStatus.accept("Không mở được loa. Kiểm tra thiết bị phát âm thanh.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void sendVideo() {
        Robot robot = null;
        int sequence = 0;
        boolean sendingVideo = false;
        while (running) {
            try {
                WebcamCapture source = webcam;
                boolean screen = sharing;
                BufferedImage frame = null;
                if (screen) {
                    if (robot == null) robot = new Robot();
                    frame = captureScreen(robot);
                } else if (source != null) {
                    frame = source.frame();
                }
                // Discard a frame if the user switched sources during capture.
                if (running && screen == sharing && source == webcam) {
                    localFrame = frame;
                    if (frame != null) {
                        var output = new ByteArrayOutputStream();
                        try (var jpeg = new MemoryCacheImageOutputStream(output)) {
                            ImageIO.write(frame, "jpg", jpeg);
                        }
                        if (output.size() <= MAX_JPEG_BYTES && running && screen == sharing && source == webcam) {
                            send(MediaPacket.TYPE_VIDEO_FRAME, sequence++, output.toByteArray());
                            sendingVideo = true;
                        }
                    } else if (sendingVideo) {
                        send(MediaPacket.TYPE_VIDEO_PAUSED, sequence++, new byte[0]);
                        sendingVideo = false;
                    }
                }
            } catch (AWTException | SecurityException e) {
                sharing = false;
                if (running) mediaStatus.accept("Không thể chia sẻ màn hình trên thiết bị này.");
            } catch (IOException ignored) {
                // Continue with the next frame.
            }
            pause(85);
        }
    }

    private static BufferedImage captureScreen(Robot robot) {
        BufferedImage screen = robot.createScreenCapture(new Rectangle(Toolkit.getDefaultToolkit().getScreenSize()));
        int frameHeight = WebcamCapture.HEIGHT;
        int width = Math.max(1, (int) (screen.getWidth() * ((double) frameHeight / screen.getHeight())));
        var scaled = new BufferedImage(Math.min(MAX_VIDEO_WIDTH, width), frameHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = scaled.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        double scale = Math.min((double) scaled.getWidth() / screen.getWidth(), (double) frameHeight / screen.getHeight());
        int height = (int) (screen.getHeight() * scale);
        g.drawImage(screen, 0, (frameHeight - height) / 2, scaled.getWidth(), height, null);
        g.dispose();
        return scaled;
    }

    private static void pause(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void worker(String name, Runnable work) {
        Thread thread = new Thread(work, "Call-" + name);
        thread.setDaemon(true);
        thread.start();
    }

    @Override
    public void close() {
        running = false;
        socket.close();
        if (webcam != null) webcam.close();
        worker("Close-Audio", () -> {
            if (microphone != null) microphone.close();
            if (speakers != null) speakers.close();
        });
    }
}
