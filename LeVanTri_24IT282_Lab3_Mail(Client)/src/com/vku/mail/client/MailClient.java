package com.vku.mail.client;

import com.vku.mail.model.MailMessage;

import javax.activation.DataHandler;
import javax.mail.Message;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.AddressException;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.mail.internet.MimeUtility;
import javax.mail.util.ByteArrayDataSource;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Properties;
import java.util.function.Consumer;

public class MailClient {
    private String smtpHost = "localhost";
    private int smtpPort = 2525;
    private String pop3Host = "localhost";
    private int pop3Port = 1100;

    private String username = "alice";
    private String password = "123456";
    private String emailAddress = "alice@vku.udn.vn";

    private boolean isGmailMode = false;
    private Consumer<String> logListener;

    public MailClient() {}

    public void configureLocal(String user, String pass, String email) {
        configureLocal("127.0.0.1", 2525, 1100, user, pass, email);
    }

    public void configureLocal(String host, int smtpPort, int pop3Port, String user, String pass, String email) {
        this.smtpHost = (host != null && !host.trim().isEmpty()) ? host.trim() : "127.0.0.1";
        this.smtpPort = smtpPort > 0 ? smtpPort : 2525;
        this.pop3Host = (host != null && !host.trim().isEmpty()) ? host.trim() : "127.0.0.1";
        this.pop3Port = pop3Port > 0 ? pop3Port : 1100;
        this.username = user;
        this.password = pass;
        this.emailAddress = email;
        this.isGmailMode = false;
    }

    public void configureGmail(String gmailAddress, String appPassword) {
        this.smtpHost = "smtp.gmail.com";
        this.smtpPort = 465; // SSL
        this.username = gmailAddress;
        this.password = appPassword == null ? "" : appPassword.replace(" ", "");
        this.emailAddress = gmailAddress;
        this.isGmailMode = true;
    }

    public void setLogListener(Consumer<String> listener) {
        this.logListener = listener;
    }

    private void log(String msg) {
        System.out.println(msg);
        if (logListener != null) logListener.accept(msg);
    }

    public boolean sendMail(String to, String subject, String body, String attachName, byte[] attachData) {
        return sendMailError(to, subject, body, attachName, attachData) == null;
    }

    String sendMailError(String to, String subject, String body, String attachName, byte[] attachData) {
        return sendMailError(to, subject, body, attachName, attachData, null, null);
    }

    String sendMailError(String to, String subject, String body, String attachName, byte[] attachData,
            String inReplyTo, String references) {
        InternetAddress[] recipients;
        try {
            recipients = InternetAddress.parse(to, true);
            if (recipients.length == 0) return "Nhập ít nhất một địa chỉ người nhận.";
            for (InternetAddress recipient : recipients) {
                if (!isGmailMode && recipient.getAddress().matches("[A-Za-z0-9._+-]+")) continue;
                if (recipient.isGroup()) return "Nhập địa chỉ email của từng người nhận.";
                recipient.validate();
            }
        } catch (AddressException e) {
            return "Địa chỉ người nhận không hợp lệ. Dùng dấu phẩy để ngăn cách các địa chỉ.";
        }
        if (isGmailMode) {
            return sendMailGmail(recipients, subject, body, attachName, attachData, inReplyTo, references);
        }
        return sendMailLocal(recipients, subject, body, attachName)
                ? null : "Gửi email thất bại. Kiểm tra cài đặt máy chủ.";
    }

    private boolean sendMailLocal(InternetAddress[] recipients, String subject, String body, String attachName) {
        try (Socket socket = openSocket(smtpHost, smtpPort, false);
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))) {

            readSmtpResponse(reader, "SMTP");

            String greeting = recipients.length > 1 ? "EHLO" : "HELO";
            sendAndLog(writer, greeting + " vku-mail-client", "SMTP");
            String capabilities = readSmtpResponse(reader, "SMTP");
            if (recipients.length > 1 && !capabilities.contains("XVKU-MULTIRCPT")) {
                log("[SMTP LỖI]: Cập nhật Mail Server để gửi tới nhiều người nhận.");
                return false;
            }

            sendAndLog(writer, "MAIL FROM:<" + emailAddress + ">", "SMTP");
            readSmtpResponse(reader, "SMTP");

            if (!sendRecipients(writer, reader, recipients, "SMTP")) return false;

            sendAndLog(writer, "DATA", "SMTP");
            if (!readSmtpResponse(reader, "SMTP").startsWith("354")) return false;

            // Write headers
            writer.write("From: " + emailAddress + "\r\n");
            String[] addresses = java.util.Arrays.stream(recipients).map(InternetAddress::getAddress).toArray(String[]::new);
            writer.write("To: " + String.join(", ", addresses) + "\r\n");
            writer.write("Subject: " + subject + "\r\n");
            if (attachName != null) {
                writer.write("X-Attachment-Name: " + attachName + "\r\n");
            }
            writer.write("Content-Type: text/plain; charset=UTF-8\r\n\r\n");

            // Write body
            writeData(writer, body);
            log("[SMTP SEND]: [Message Data End .]");

            String resp = readSmtpResponse(reader, "SMTP");

            sendAndLog(writer, "QUIT", "SMTP");
            readSmtpResponse(reader, "SMTP");

            return resp.startsWith("250");
        } catch (Exception e) {
            log("[SMTP LỖI]: " + e.getMessage());
            return false;
        }
    }

    private String sendMailGmail(InternetAddress[] recipients, String subject, String body, String attachName,
            byte[] attachData, String inReplyTo, String references) {
        try {
            MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
            message.setFrom(new InternetAddress(emailAddress, true));
            message.setRecipients(Message.RecipientType.TO, recipients);
            message.setSubject(subject, "UTF-8");
            if (inReplyTo != null) {
                message.setHeader("In-Reply-To", inReplyTo);
                message.setHeader("References", MimeUtility.fold("References: ".length(), references));
            }
            if (attachName != null && attachData != null) {
                MimeBodyPart content = new MimeBodyPart();
                content.setText(body == null ? "" : body, "UTF-8");
                MimeBodyPart file = new MimeBodyPart();
                file.setDataHandler(new DataHandler(new ByteArrayDataSource(attachData, "application/octet-stream")));
                file.setFileName(MimeUtility.encodeText(attachName, "UTF-8", null));
                MimeMultipart parts = new MimeMultipart();
                parts.addBodyPart(content);
                parts.addBodyPart(file);
                message.setContent(parts);
            } else {
                message.setText(body == null ? "" : body, "UTF-8");
            }
            message.saveChanges();
            ByteArrayOutputStream mime = new ByteArrayOutputStream();
            message.writeTo(mime);

            log("[GMAIL SMTP]: Đang kết nối SSL tới smtp.gmail.com:465...");
            try (Socket socket = openSocket(smtpHost, smtpPort, true);
                 BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                 BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))) {

                readAndLog(reader, "GMAIL-SMTP");

                sendAndLog(writer, "EHLO localhost", "GMAIL-SMTP");
                readSmtpResponse(reader, "GMAIL-SMTP");

                sendAndLog(writer, "AUTH LOGIN", "GMAIL-SMTP");
                String authResp = readSmtpResponse(reader, "GMAIL-SMTP");
                if (!authResp.startsWith("334")) return gmailAuthError(authResp);

                String userB64 = Base64.getEncoder().encodeToString(username.getBytes(StandardCharsets.UTF_8));
                sendAndLog(writer, userB64, "GMAIL-SMTP (Username B64)");
                authResp = readSmtpResponse(reader, "GMAIL-SMTP");
                if (!authResp.startsWith("334")) return gmailAuthError(authResp);

                String passB64 = Base64.getEncoder().encodeToString(password.getBytes(StandardCharsets.UTF_8));
                sendAndLog(writer, passB64, "GMAIL-SMTP", "[Mật khẩu đã ẩn]");
                authResp = readSmtpResponse(reader, "GMAIL-SMTP");
                if (!authResp.startsWith("235")) return gmailAuthError(authResp);

                sendAndLog(writer, "MAIL FROM:<" + emailAddress + ">", "GMAIL-SMTP");
                readAndLog(reader, "GMAIL-SMTP");

                if (!sendRecipients(writer, reader, recipients, "GMAIL-SMTP")) {
                    return "Gmail từ chối địa chỉ người nhận. Xem Nhật Ký Giao Thức để biết chi tiết.";
                }

                sendAndLog(writer, "DATA", "GMAIL-SMTP");
                if (!readSmtpResponse(reader, "GMAIL-SMTP").startsWith("354")) {
                    return "Gmail chưa cho phép gửi nội dung. Xem Nhật Ký Giao Thức để biết chi tiết.";
                }
                writeData(writer, mime.toString(StandardCharsets.UTF_8));

                String complete = readSmtpResponse(reader, "GMAIL-SMTP");
                sendAndLog(writer, "QUIT", "GMAIL-SMTP");
                readAndLog(reader, "GMAIL-SMTP");

                return complete.startsWith("250") ? null
                        : "Gmail không xác nhận đã nhận thư. Xem Nhật Ký Giao Thức để biết chi tiết.";
            }
        } catch (Exception e) {
            log("[GMAIL SMTP LỖI]: " + e.getMessage());
            return "Gửi qua Gmail thất bại. Xem Nhật Ký Giao Thức để biết chi tiết.";
        }
    }

    private boolean sendRecipients(BufferedWriter writer, BufferedReader reader, InternetAddress[] recipients,
            String tag) throws IOException {
        for (InternetAddress recipient : recipients) {
            sendAndLog(writer, "RCPT TO:<" + recipient.getAddress() + ">", tag);
            if (!readSmtpResponse(reader, tag).startsWith("25")) return false;
        }
        return true;
    }

    private String gmailAuthError(String response) {
        String error = response.startsWith("534") && response.contains("5.7.9")
                ? "Gmail yêu cầu mật khẩu ứng dụng (534-5.7.9). Nhập App Password của tài khoản gửi."
                : "Gmail từ chối xác thực. Kiểm tra địa chỉ email gửi và mật khẩu ứng dụng.";
        log("[GMAIL SMTP LỖI]: " + error);
        return error;
    }

    public List<MailMessage> fetchInbox() {
        try {
            return fetchFolder(false);
        } catch (IOException e) {
            return List.of();
        }
    }

    List<MailMessage> fetchFolder(boolean sent) throws IOException {
        try {
            if (isGmailMode) {
                return GmailMessageReader.fetchFolder(username, password, sent, this::log);
            }
            return fetchPop3(sent);
        } catch (Exception e) {
            log("[TẢI THƯ]: " + e.getMessage());
            throw new IOException(e.getMessage(), e);
        }
    }

    MailMessage fetchMessage(MailMessage summary) throws IOException {
        try {
            return GmailMessageReader.fetchMessage(username, password, summary.getImapLocation(), this::log);
        } catch (javax.mail.MessagingException e) {
            log("[GMAIL IMAP]: " + e.getMessage());
            throw new IOException(e.getMessage(), e);
        }
    }

    private List<MailMessage> fetchPop3(boolean sent) throws IOException {
        List<MailMessage> list = new ArrayList<>();
        String tag = "POP3";
        try (Socket socket = openSocket(pop3Host, pop3Port, false);
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))) {
            readAndLog(reader, tag);
            sendAndLog(writer, "USER " + username, tag);
            readAndLog(reader, tag);
            sendAndLog(writer, "PASS " + password, tag, "PASS [Đã ẩn]");
            if (!readAndLog(reader, tag).startsWith("+OK")) {
                throw new IOException("Không đăng nhập được. Kiểm tra tài khoản và mật khẩu.");
            }

            sendAndLog(writer, sent ? "XSENT" : "STAT", tag);
            String status = readAndLog(reader, tag);
            if (!status.startsWith("+OK ")) {
                throw new IOException(sent ? "Máy chủ chưa hỗ trợ thư đã gửi. Hãy cập nhật Mail Server."
                        : "Máy chủ không trả về số lượng thư.");
            }
            int count = Integer.parseInt(status.split("\\s+")[1]);
            for (int i = 1; i <= count; i++) {
                sendAndLog(writer, (sent ? "XRETR " : "RETR ") + i, tag);
                if (readAndLog(reader, tag).startsWith("+OK")) {
                    list.add(parseLocalMessage(readMessageData(reader)));
                }
            }
            sendAndLog(writer, "QUIT", tag);
            readAndLog(reader, tag);
        }
        return list;
    }

    private static String readMessageData(BufferedReader reader) throws IOException {
        StringBuilder data = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            if (".".equals(line)) return data.toString();
            if (line.startsWith("..")) line = line.substring(1);
            data.append(line).append("\r\n");
        }
        throw new EOFException("Kết nối đóng khi đang tải nội dung thư.");
    }

    private MailMessage parseLocalMessage(String source) throws IOException {
        try (BufferedReader reader = new BufferedReader(new StringReader(source))) {
            String sender = "";
            String recipient = emailAddress;
            String subject = "(Không có tiêu đề)";
            String attachment = null;
            String date = null;
            String line;
            while ((line = reader.readLine()) != null && !line.isEmpty()) {
                int split = line.indexOf(':');
                if (split < 0) continue;
                String value = line.substring(split + 1).strip();
                switch (line.substring(0, split).toLowerCase(java.util.Locale.ROOT)) {
                    case "from" -> sender = value;
                    case "to" -> recipient = value;
                    case "subject" -> subject = value;
                    case "date" -> date = value;
                    case "x-attachment-name" -> attachment = value;
                }
            }
            StringBuilder body = new StringBuilder();
            while ((line = reader.readLine()) != null) body.append(line).append('\n');
            MailMessage mail = new MailMessage(sender, recipient, subject, body.toString().stripTrailing());
            mail.setAttachmentName(attachment);
            if (date != null) {
                try {
                    mail.setTimestamp(ZonedDateTime.parse(date, DateTimeFormatter.RFC_1123_DATE_TIME)
                            .toInstant().toEpochMilli());
                } catch (DateTimeParseException ignored) {
                    // An older server may have sent a localized date.
                }
            }
            return mail;
        }
    }

    private static Socket openSocket(String host, int port, boolean encrypted) throws IOException {
        Socket connection = new Socket();
        try {
            connection.connect(new InetSocketAddress(host, port), 10_000);
            connection.setSoTimeout(15_000);
            if (!encrypted) return connection;
            SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
            SSLSocket socket = (SSLSocket) factory.createSocket(connection, host, port, true);
            var parameters = socket.getSSLParameters();
            parameters.setEndpointIdentificationAlgorithm("HTTPS");
            socket.setSSLParameters(parameters);
            socket.startHandshake();
            return socket;
        } catch (IOException e) {
            connection.close();
            throw e;
        }
    }

    private static void writeData(BufferedWriter writer, String body) throws IOException {
        String normalized = body == null ? "" : body.replace("\r\n", "\n").replace('\r', '\n');
        writer.write(normalized.replaceAll("(?m)^\\.", "..").replace("\n", "\r\n"));
        writer.write("\r\n.\r\n");
        writer.flush();
    }

    private void sendAndLog(BufferedWriter writer, String cmd, String tag) throws IOException {
        sendAndLog(writer, cmd, tag, cmd);
    }

    private void sendAndLog(BufferedWriter writer, String cmd, String tag, String loggedCommand) throws IOException {
        log("[" + tag + " SEND]: " + loggedCommand);
        writer.write(cmd + "\r\n");
        writer.flush();
    }

    private String readAndLog(BufferedReader reader, String tag) throws IOException {
        String line = reader.readLine();
        if (line != null) {
            log("[" + tag + " RECV]: " + line);
        }
        return line != null ? line : "";
    }

    private String readSmtpResponse(BufferedReader reader, String tag) throws IOException {
        String line;
        String lastLine = "";
        while ((line = reader.readLine()) != null) {
            log("[" + tag + " RECV]: " + line);
            lastLine = line;
            if (line.length() >= 4 && line.charAt(3) != '-') {
                break;
            }
            if (line.length() < 4) {
                break;
            }
        }
        return lastLine;
    }

    public String getEmailAddress() { return emailAddress; }
    public String getUsername() { return username; }
    public boolean isGmailMode() { return isGmailMode; }
    public String getSmtpHost() { return smtpHost; }
    public int getSmtpPort() { return smtpPort; }
    public String getPop3Host() { return pop3Host; }
    public int getPop3Port() { return pop3Port; }
}
