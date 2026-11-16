package com.vku.mail.server;

import com.vku.mail.db.MailDatabase;
import com.vku.mail.model.MailAccount;
import com.vku.mail.model.MailLog;
import com.vku.mail.model.MailMessage;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class Pop3ServerHandler implements Runnable {
    private final Socket socket;
    private final MailServer server;
    private final MailDatabase db;

    private BufferedReader reader;
    private BufferedWriter writer;
    private String clientIp;
    private MailAccount currentAccount;
    private String userAttempt = "";
    private List<MailMessage> userMailbox = new ArrayList<>();
    private List<MailMessage> sentMailbox = List.of();
    private final List<Integer> markedForDeletion = new ArrayList<>();
    private boolean active = true;

    public Pop3ServerHandler(Socket socket, MailServer server, MailDatabase db) {
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

            sendReply("+OK VKU POP3 Server ready");

            String line;
            while (active && (line = reader.readLine()) != null) {
                line = line.trim();
                String loggedCommand = line.regionMatches(true, 0, "PASS ", 0, 5) ? "PASS [Đã ẩn]" : line;
                server.log("[POP3 " + clientIp + "] RECV: " + loggedCommand);
                handleCommand(line);
            }
        } catch (Exception e) {
            // Client closed
        } finally {
            close();
        }
    }

    private void handleCommand(String line) throws IOException {
        String[] parts = line.split(" ", 2);
        String cmd = parts[0].toUpperCase();
        String arg = parts.length > 1 ? parts[1].trim() : "";

        switch (cmd) {
            case "USER":
                currentAccount = null;
                sentMailbox = List.of();
                markedForDeletion.clear();
                userAttempt = arg;
                sendReply("+OK User name accepted, send PASS");
                break;

            case "PASS":
                currentAccount = null;
                sentMailbox = List.of();
                markedForDeletion.clear();
                MailAccount auth = db.authenticate(userAttempt, arg);
                if (auth != null) {
                    this.currentAccount = auth;
                    this.userMailbox = db.getInbox(auth.getEmail());
                    db.log(new MailLog("POP3", clientIp, "Đăng nhập POP3 thành công: " + auth.getEmail(), "SUCCESS"));
                    sendReply("+OK Mailbox open, " + userMailbox.size() + " messages");
                } else {
                    db.log(new MailLog("POP3", clientIp, "Đăng nhập POP3 thất bại cho user: " + userAttempt, "FAILED"));
                    sendReply("-ERR Invalid username or password");
                }
                break;

            case "STAT":
                if (!checkAuth()) return;
                long totalBytes = 0;
                int count = 0;
                for (int i = 0; i < userMailbox.size(); i++) {
                    if (!markedForDeletion.contains(i)) {
                        totalBytes += userMailbox.get(i).toRfc822String().getBytes(StandardCharsets.UTF_8).length;
                        count++;
                    }
                }
                sendReply("+OK " + count + " " + totalBytes);
                break;

            case "LIST":
                if (!checkAuth()) return;
                if (!arg.isEmpty()) {
                    try {
                        int idx = Integer.parseInt(arg) - 1;
                        if (idx >= 0 && idx < userMailbox.size() && !markedForDeletion.contains(idx)) {
                            long size = userMailbox.get(idx).toRfc822String().getBytes(StandardCharsets.UTF_8).length;
                            sendReply("+OK " + (idx + 1) + " " + size);
                        } else {
                            sendReply("-ERR No such message");
                        }
                    } catch (NumberFormatException e) {
                        sendReply("-ERR Invalid message number");
                    }
                } else {
                    sendReply("+OK " + (userMailbox.size() - markedForDeletion.size()) + " messages");
                    for (int i = 0; i < userMailbox.size(); i++) {
                        if (!markedForDeletion.contains(i)) {
                            long size = userMailbox.get(i).toRfc822String().getBytes(StandardCharsets.UTF_8).length;
                            writer.write((i + 1) + " " + size + "\r\n");
                        }
                    }
                    writer.write(".\r\n");
                    writer.flush();
                }
                break;

            case "RETR":
                if (!checkAuth()) return;
                try {
                    int idx = Integer.parseInt(arg) - 1;
                    if (idx >= 0 && idx < userMailbox.size() && !markedForDeletion.contains(idx)) {
                        MailMessage msg = userMailbox.get(idx);
                        String rfc = msg.toRfc822String();
                        byte[] bytes = rfc.getBytes(StandardCharsets.UTF_8);
                        sendReply("+OK " + bytes.length + " octets");
                        writeMessage(rfc);
                        msg.setRead(true);
                        db.log(new MailLog("POP3", clientIp, "Tải thư #" + (idx + 1) + " id=" + msg.getId(), "SUCCESS"));
                    } else {
                        sendReply("-ERR No such message");
                    }
                } catch (Exception e) {
                    sendReply("-ERR Invalid message number");
                }
                break;

            case "XSENT":
                if (!checkAuth()) return;
                sentMailbox = db.getSent(currentAccount.getEmail());
                sentMailbox.sort(Comparator.comparingLong(MailMessage::getTimestamp).reversed());
                sendReply("+OK " + sentMailbox.size() + " messages");
                break;

            case "XRETR":
                if (!checkAuth()) return;
                int sentIndex;
                try {
                    sentIndex = Integer.parseInt(arg) - 1;
                } catch (NumberFormatException e) {
                    sendReply("-ERR Invalid message number");
                    break;
                }
                if (sentIndex < 0 || sentIndex >= sentMailbox.size()) {
                    sendReply("-ERR No such sent message");
                    break;
                }
                sendReply("+OK Sent message follows");
                writeMessage(sentMailbox.get(sentIndex).toRfc822String());
                break;

            case "DELE":
                if (!checkAuth()) return;
                try {
                    int idx = Integer.parseInt(arg) - 1;
                    if (idx >= 0 && idx < userMailbox.size() && !markedForDeletion.contains(idx)) {
                        markedForDeletion.add(idx);
                        sendReply("+OK Message " + (idx + 1) + " marked for deletion");
                    } else {
                        sendReply("-ERR No such message");
                    }
                } catch (Exception e) {
                    sendReply("-ERR Invalid message number");
                }
                break;

            case "RSET":
                if (!checkAuth()) return;
                markedForDeletion.clear();
                sendReply("+OK Reset complete");
                break;

            case "NOOP":
                sendReply("+OK");
                break;

            case "QUIT":
                if (currentAccount != null) {
                    for (int idx : markedForDeletion) {
                        if (idx >= 0 && idx < userMailbox.size()) {
                            db.deleteMail(userMailbox.get(idx).getId());
                        }
                    }
                }
                sendReply("+OK VKU POP3 Server signing off");
                active = false;
                break;

            default:
                sendReply("-ERR Unrecognized command");
                break;
        }
    }

    private boolean checkAuth() throws IOException {
        if (currentAccount == null) {
            sendReply("-ERR Permission denied, please USER and PASS first");
            return false;
        }
        return true;
    }

    private void sendReply(String reply) throws IOException {
        server.log("[POP3 " + clientIp + "] SEND: " + reply.replace("\r\n", " "));
        writer.write(reply + "\r\n");
        writer.flush();
    }

    private void writeMessage(String message) throws IOException {
        String normalized = message.replace("\r\n", "\n").replace('\r', '\n');
        writer.write(normalized.replaceAll("(?m)^\\.", "..").replace("\n", "\r\n"));
        if (!normalized.endsWith("\n")) writer.write("\r\n");
        writer.write(".\r\n");
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
