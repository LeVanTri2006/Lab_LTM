package com.vku.mail.server;

import com.vku.mail.db.MailDatabase;
import com.vku.mail.model.MailAccount;
import com.vku.mail.model.MailLog;
import com.vku.mail.model.MailMessage;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public class SmtpServerHandler implements Runnable {
    private final Socket socket;
    private final MailServer server;
    private final MailDatabase db;

    private BufferedReader reader;
    private BufferedWriter writer;
    private String clientIp;
    private String mailFrom = "";
    private final Set<String> recipients = new LinkedHashSet<>();
    private boolean active = true;

    public SmtpServerHandler(Socket socket, MailServer server, MailDatabase db) {
        this.socket = socket;
        this.server = server;
        this.db = db;
        this.clientIp = socket.getInetAddress().getHostAddress();
    }

    @Override
    public void run() {
        try {
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));

            sendReply("220 vku.udn.vn ESMTP VKU Mail Server ready");

            String line;
            while (active && (line = reader.readLine()) != null) {
                line = line.trim();
                server.log("[SMTP " + clientIp + "] RECV: " + line);
                handleCommand(line);
            }
        } catch (Exception e) {
            // Client closed
        } finally {
            close();
        }
    }

    private void handleCommand(String line) throws IOException {
        String upper = line.toUpperCase();
        if (upper.startsWith("EHLO")) {
            sendReply("250-vku.udn.vn Hello [" + clientIp + "]\r\n250-SIZE 20971520\r\n250-8BITMIME\r\n250 XVKU-MULTIRCPT");
        } else if (upper.startsWith("HELO")) {
            sendReply("250 vku.udn.vn Hello [" + clientIp + "]");
        } else if (upper.startsWith("MAIL FROM:")) {
            mailFrom = extractAddress(line.substring(10).trim());
            recipients.clear();
            sendReply("250 2.1.0 Sender <" + mailFrom + "> OK");
        } else if (upper.startsWith("RCPT TO:")) {
            String recipient = extractAddress(line.substring(8).trim()).toLowerCase(Locale.ROOT);
            MailAccount account = db.getAccount(recipient);
            if (account != null) recipient = account.getEmail();
            if (recipient.isBlank() || recipient.length() > 100 || recipient.contains(",")) {
                sendReply("501 5.1.3 Invalid recipient address");
            } else if (recipients.size() >= 100 && !recipients.contains(recipient)) {
                sendReply("452 4.5.3 Too many recipients");
            } else {
                recipients.add(recipient);
                sendReply("250 2.1.5 Recipient <" + recipient + "> OK");
            }
        } else if (upper.equals("DATA")) {
            if (recipients.isEmpty()) sendReply("503 5.5.1 Specify a recipient before DATA");
            else handleData();
        } else if (upper.equals("RSET")) {
            mailFrom = "";
            recipients.clear();
            sendReply("250 2.0.0 OK Reset state");
        } else if (upper.equals("NOOP")) {
            sendReply("250 2.0.0 OK");
        } else if (upper.equals("QUIT")) {
            sendReply("221 2.0.0 vku.udn.vn closing transmission channel");
            active = false;
        } else {
            sendReply("500 5.5.1 Command unrecognized");
        }
    }

    private void handleData() throws IOException {
        sendReply("354 Start mail input; end with <CRLF>.<CRLF>");
        StringBuilder body = new StringBuilder();
        boolean readingHeaders = true;
        String subject = "(Không có tiêu đề)";
        String toHeader = "";
        String ccHeader = "";

        String line;
        while ((line = reader.readLine()) != null) {
            if (line.equals(".")) break; // End of DATA

            if (readingHeaders) {
                if (line.isEmpty()) {
                    readingHeaders = false;
                } else {
                    if (line.toLowerCase().startsWith("subject:")) {
                        subject = line.substring(8).trim();
                    } else if (line.toLowerCase().startsWith("from:") && mailFrom.isEmpty()) {
                        mailFrom = extractAddress(line.substring(5).trim());
                    } else if (line.regionMatches(true, 0, "To:", 0, 3)) {
                        toHeader = line.substring(3).trim();
                    } else if (line.regionMatches(true, 0, "Cc:", 0, 3)) {
                        ccHeader = line.substring(3).trim();
                    }
                }
            } else {
                if (line.startsWith("..")) line = line.substring(1);
                body.append(line).append("\n");
            }
        }

        if (line == null) return;
        String visible = toHeader.isBlank() ? ccHeader : ccHeader.isBlank() ? toHeader : toHeader + ", " + ccHeader;
        String id = "MAIL-" + UUID.randomUUID();
        String content = body.toString().trim();
        List<MailMessage> deliveries = new ArrayList<>();
        for (String recipient : recipients) {
            MailMessage message = new MailMessage(mailFrom.isEmpty() ? "unknown@localhost" : mailFrom,
                    recipient, subject, content);
            message.setId(deliveries.isEmpty() ? id : id + ":" + deliveries.size());
            if (!visible.isBlank()) message.setVisibleRecipients(visible);
            deliveries.add(message);
        }
        if (!db.saveMails(deliveries)) {
            sendReply("451 4.3.0 Unable to store the message");
            return;
        }
        db.log(new MailLog("SMTP", clientIp, "Nhận thư từ " + mailFrom + " gửi tới "
                + String.join(", ", recipients) + " [Tiêu đề: " + subject + "]", "SUCCESS"));
        server.notifyNewMail();
        recipients.clear();
        mailFrom = "";
        sendReply("250 2.0.0 OK Message accepted for delivery id=" + id);
    }

    private String extractAddress(String raw) {
        int start = raw.indexOf("<");
        int end = raw.indexOf(">");
        if (start != -1 && end > start) {
            return raw.substring(start + 1, end).trim();
        }
        return raw.trim();
    }

    private void sendReply(String reply) throws IOException {
        server.log("[SMTP " + clientIp + "] SEND: " + reply.replace("\r\n", " "));
        writer.write(reply + "\r\n");
        writer.flush();
    }

    public void close() {
        active = false;
        try {
            if (reader != null) reader.close();
            if (writer != null) writer.close();
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (Exception ignored) {}
    }
}
