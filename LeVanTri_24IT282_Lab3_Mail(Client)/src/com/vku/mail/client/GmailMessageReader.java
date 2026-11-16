package com.vku.mail.client;

import com.sun.mail.imap.IMAPFolder;
import com.vku.mail.model.MailMessage;

import javax.mail.Address;
import javax.mail.FetchProfile;
import javax.mail.Folder;
import javax.mail.FolderClosedException;
import javax.mail.Message;
import javax.mail.MessageRemovedException;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Part;
import javax.mail.Session;
import javax.mail.Store;
import javax.mail.UIDFolder;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeUtility;
import javax.swing.text.MutableAttributeSet;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.parser.ParserDelegator;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.function.Consumer;

final class GmailMessageReader {
    private static final int FOLDER_LIMIT = 25;
    private static final int ATTACHMENT_LIMIT = 8 * 1024 * 1024;

    private GmailMessageReader() {}

    static List<MailMessage> fetchFolder(String user, String password, boolean sent, Consumer<String> log)
            throws MessagingException {
        try (Store store = newStore()) {
            log.accept("[GMAIL IMAP]: Đang kết nối tới imap.gmail.com:993 để tải "
                    + (sent ? "Thư đã gửi" : "Hộp thư đến") + ".");
            store.connect("imap.gmail.com", 993, user, password);
            Folder folder = sent ? sentFolder(store) : store.getFolder("INBOX");
            folder.open(Folder.READ_ONLY);
            try {
                return readNewest(folder, (UIDFolder) folder, sent, log);
            } finally {
                folder.close(false);
            }
        }
    }

    static MailMessage fetchMessage(String user, String password, MailMessage.ImapLocation location, Consumer<String> log)
            throws MessagingException {
        try (Store store = newStore()) {
            log.accept("[GMAIL IMAP]: Tải nội dung thư đã chọn.");
            store.connect("imap.gmail.com", 993, user, password);
            Folder folder = store.getFolder(location.folder());
            folder.open(Folder.READ_ONLY);
            try {
                UIDFolder ids = (UIDFolder) folder;
                if (ids.getUIDValidity() != location.uidValidity()) {
                    throw new MessagingException("Hộp thư đã thay đổi. Hãy quay lại và bấm Làm mới.");
                }
                Message message = ids.getMessageByUID(location.uid());
                if (message == null) throw new MessagingException("Thư đã được di chuyển hoặc xóa.");
                return readDetail(message, !"INBOX".equalsIgnoreCase(location.folder()), log);
            } finally {
                folder.close(false);
            }
        }
    }

    private static Store newStore() throws MessagingException {
        Properties properties = new Properties();
        properties.setProperty("mail.imaps.connectiontimeout", "10000");
        properties.setProperty("mail.imaps.timeout", "15000");
        properties.setProperty("mail.imaps.writetimeout", "15000");
        properties.setProperty("mail.imaps.ssl.checkserveridentity", "true");
        return Session.getInstance(properties).getStore("imaps");
    }

    static List<MailMessage> readNewest(Folder folder, UIDFolder ids, boolean sent, Consumer<String> log)
            throws MessagingException {
        int count = folder.getMessageCount();
        if (count <= 0) return List.of();

        // IMAP sequence numbers increase as messages are added to the folder.
        int first = Math.max(1, count - FOLDER_LIMIT + 1);
        Message[] window = folder.getMessages(first, count);
        FetchProfile headers = new FetchProfile();
        headers.add(FetchProfile.Item.ENVELOPE);
        headers.add(UIDFolder.FetchProfileItem.UID);
        folder.fetch(window, headers);
        long validity = ids.getUIDValidity();
        log.accept("[GMAIL IMAP]: " + folder.getFullName() + " — đã nhận tiêu đề của " + window.length + " thư.");
        List<MailMessage> messages = new ArrayList<>(window.length);
        for (int i = window.length - 1; i >= 0; i--) {
            try {
                MailMessage summary = readSummary(window[i], sent);
                summary.setImapLocation(new MailMessage.ImapLocation(folder.getFullName(), validity, ids.getUID(window[i])));
                messages.add(summary);
            } catch (MessageRemovedException e) {
                log.accept("[GMAIL IMAP]: Một thư đã được di chuyển hoặc xóa trong lúc tải.");
            }
        }
        return messages;
    }

    private static Folder sentFolder(Store store) throws MessagingException {
        for (Folder folder : store.getDefaultFolder().list("*")) {
            if (folder instanceof IMAPFolder imap) {
                for (String attribute : imap.getAttributes()) {
                    if ("\\Sent".equalsIgnoreCase(attribute)) return folder;
                }
            }
        }
        throw new MessagingException("Không tìm thấy thư mục Đã gửi trong tài khoản Gmail.");
    }

    private static MailMessage readSummary(Message message, boolean sent) throws MessagingException {
        String subject = message.getSubject();
        String to = addresses(message.getRecipients(Message.RecipientType.TO));
        String cc = addresses(message.getRecipients(Message.RecipientType.CC));
        String recipients = to.isBlank() ? cc : cc.isBlank() ? to : to + ", " + cc;
        MailMessage result = new MailMessage(addresses(message.getFrom()), recipients,
                subject == null ? "(Không có tiêu đề)" : subject, null);
        Date date = sent ? message.getSentDate() : message.getReceivedDate();
        if (date == null) date = sent ? message.getReceivedDate() : message.getSentDate();
        if (date != null) result.setTimestamp(date.getTime());
        return result;
    }

    static MailMessage readDetail(Message message, boolean sent, Consumer<String> log) throws MessagingException {
        MailMessage result = readSummary(message, sent);
        result.setReplyTo(addresses(message.getReplyTo()));
        if (message instanceof MimeMessage mime) {
            result.setMessageId(mime.getMessageID());
            result.setReferences(mime.getHeader("References", " "));
        }
        Body body = new Body();
        try {
            readPart(message, body, result);
            result.setBody((body.plain.isBlank() ? body.html : body.plain).strip());
        } catch (FolderClosedException | MessageRemovedException e) {
            throw e;
        } catch (MessagingException | IOException e) {
            log.accept("[GMAIL IMAP]: Không thể giải mã nội dung một thư.");
            result.setBody("Không đọc được nội dung thư này.");
        }
        return result;
    }

    private static void readPart(Part part, Body body, MailMessage result) throws MessagingException, IOException {
        String fileName = part.getFileName();
        if (Part.ATTACHMENT.equalsIgnoreCase(part.getDisposition()) || fileName != null) {
            if (result.getAttachmentName() == null) {
                result.setAttachmentName(fileName == null ? "tep-dinh-kem" : MimeUtility.decodeText(fileName));
                if (part.getSize() <= ATTACHMENT_LIMIT) {
                    try (var input = part.getInputStream()) {
                        byte[] bytes = input.readNBytes(ATTACHMENT_LIMIT + 1);
                        if (bytes.length <= ATTACHMENT_LIMIT) result.setAttachmentData(bytes);
                    }
                }
            }
        } else if (part.isMimeType("multipart/*")) {
            Multipart parts = (Multipart) part.getContent();
            for (int i = 0; i < parts.getCount(); i++) readPart(parts.getBodyPart(i), body, result);
        } else if (part.isMimeType("text/plain") && body.plain.isBlank()) {
            body.plain = String.valueOf(part.getContent());
        } else if (part.isMimeType("text/html") && body.html.isBlank() && body.plain.isBlank()) {
            body.html = htmlText(String.valueOf(part.getContent()));
        }
    }

    private static String addresses(Address[] addresses) {
        return addresses == null ? "" : InternetAddress.toUnicodeString(addresses);
    }

    private static String htmlText(String html) throws IOException {
        StringBuilder text = new StringBuilder();
        new ParserDelegator().parse(new StringReader(html), new HTMLEditorKit.ParserCallback() {
            @Override public void handleText(char[] data, int pos) {
                text.append(data);
            }

            @Override public void handleSimpleTag(HTML.Tag tag, MutableAttributeSet attrs, int pos) {
                if (tag == HTML.Tag.BR) text.append('\n');
            }

            @Override public void handleEndTag(HTML.Tag tag, int pos) {
                if (tag.isBlock()) text.append('\n');
            }
        }, true);
        return text.toString();
    }

    private static final class Body {
        String plain = "";
        String html = "";
    }
}
