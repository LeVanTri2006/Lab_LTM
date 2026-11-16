package com.vku.mail.model;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MailMessage implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id;
    private String sender;
    private String recipient;
    private String visibleRecipients;
    private String replyTo;
    private String messageId;
    private String references;
    private String subject;
    private String body;
    private String attachmentName;
    private byte[] attachmentData;
    private long timestamp;
    private boolean isRead;
    private boolean isDeleted;
    private transient ImapLocation imapLocation;

    public record ImapLocation(String folder, long uidValidity, long uid) {}

    public MailMessage() {
        this.timestamp = System.currentTimeMillis();
        this.id = "MAIL-" + this.timestamp + "-" + (int)(Math.random() * 1000);
    }

    public MailMessage(String sender, String recipient, String subject, String body) {
        this();
        this.sender = sender;
        this.recipient = recipient;
        this.subject = subject;
        this.body = body;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getSender() { return sender; }
    public void setSender(String sender) { this.sender = sender; }

    public String getRecipient() { return recipient; }
    public void setRecipient(String recipient) { this.recipient = recipient; }
    public String getVisibleRecipients() { return visibleRecipients == null ? recipient : visibleRecipients; }
    public void setVisibleRecipients(String recipients) { this.visibleRecipients = recipients; }
    public String getReplyTo() { return replyTo == null || replyTo.isBlank() ? sender : replyTo; }
    public void setReplyTo(String replyTo) { this.replyTo = replyTo; }
    public String getMessageId() { return messageId; }
    public void setMessageId(String messageId) { this.messageId = messageId; }
    public String getReferences() { return references; }
    public void setReferences(String references) { this.references = references; }
    public ImapLocation getImapLocation() { return imapLocation; }
    public void setImapLocation(ImapLocation location) { this.imapLocation = location; }

    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }

    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }

    public String getAttachmentName() { return attachmentName; }
    public void setAttachmentName(String attachmentName) { this.attachmentName = attachmentName; }

    public byte[] getAttachmentData() { return attachmentData; }
    public void setAttachmentData(byte[] attachmentData) { this.attachmentData = attachmentData; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public boolean isRead() { return isRead; }
    public void setRead(boolean read) { isRead = read; }

    public boolean isDeleted() { return isDeleted; }
    public void setDeleted(boolean deleted) { isDeleted = deleted; }

    public String getFormattedDate() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(timestamp));
    }

    public String toRfc822String() {
        StringBuilder sb = new StringBuilder();
        sb.append("Message-ID: <").append(id.replace(':', '.')).append("@vku.udn.vn>\r\n");
        sb.append("Date: ").append(new SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss Z", Locale.US).format(new Date(timestamp))).append("\r\n");
        sb.append("From: ").append(sender).append("\r\n");
        sb.append("To: ").append(getVisibleRecipients()).append("\r\n");
        sb.append("Subject: ").append(subject != null ? subject : "(Không có tiêu đề)").append("\r\n");
        if (attachmentName != null) {
            sb.append("X-Attachment-Name: ").append(attachmentName).append("\r\n");
        }
        sb.append("Content-Type: text/plain; charset=UTF-8\r\n\r\n");
        sb.append(body != null ? body : "").append("\r\n");
        return sb.toString();
    }
}
