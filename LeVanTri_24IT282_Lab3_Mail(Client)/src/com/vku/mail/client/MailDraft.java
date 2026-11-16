package com.vku.mail.client;

import com.vku.mail.model.MailMessage;

import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

record MailDraft(String title, String to, String subject, String body, String attachment,
                 byte[] attachmentData, String inReplyTo, String references) {
    private static final Pattern MESSAGE_ID = Pattern.compile("<[^<>\\s\\x00-\\x1F\\x7F]+>");

    enum Action {
        REPLY("Trả lời"), REPLY_ALL("Trả lời tất cả"), FORWARD("Chuyển tiếp");

        final String title;

        Action(String title) {
            this.title = title;
        }
    }

    static MailDraft blank() {
        return new MailDraft("Soạn thư", "", "", "", null, null, null, null);
    }

    static MailDraft forMessage(Action action, MailMessage mail, String ownAddress, boolean sent) {
        String originalSubject = value(mail.getSubject());
        String body = value(mail.getBody()).replace("\r\n", "\n").replace('\r', '\n');
        if (action == Action.FORWARD) {
            String forwarded = "\n\n---------- Thư được chuyển tiếp ----------\n"
                    + "Từ: " + value(mail.getSender()) + "\n"
                    + "Ngày: " + mail.getFormattedDate() + "\n"
                    + "Tiêu đề: " + originalSubject + "\n"
                    + "Đến: " + value(mail.getVisibleRecipients()) + "\n\n" + body;
            return new MailDraft(action.title, "", prefix(originalSubject, "Fwd"), forwarded,
                    mail.getAttachmentName(), mail.getAttachmentData(), null, null);
        }

        Map<String, String> recipients = new LinkedHashMap<>();
        addRecipients(recipients, sent ? mail.getVisibleRecipients() : mail.getReplyTo());
        if (action == Action.REPLY_ALL) addRecipients(recipients, mail.getVisibleRecipients());
        if (ownAddress != null) {
            String own = ownAddress.toLowerCase(Locale.ROOT);
            recipients.remove(own);
            recipients.remove(own.split("@", 2)[0]);
        }
        String quoted = "\n\nVào " + mail.getFormattedDate() + ", " + value(mail.getSender()) + " đã viết:\n"
                + "> " + body.replace("\n", "\n> ");

        String id = value(mail.getMessageId()).strip();
        String references = null;
        if (MESSAGE_ID.matcher(id).matches()) {
            var originalReferences = MESSAGE_ID.matcher(value(mail.getReferences()));
            StringBuilder chain = new StringBuilder();
            while (originalReferences.find()) {
                if (!originalReferences.group().equals(id)) chain.append(originalReferences.group()).append(' ');
            }
            references = chain.append(id).toString();
        } else {
            id = null;
        }
        return new MailDraft(action.title, String.join(", ", recipients.values()),
                prefix(originalSubject, "Re"), quoted, null, null, id, references);
    }

    private static void addRecipients(Map<String, String> recipients, String source) {
        if (source == null || source.isBlank()) return;
        try {
            for (InternetAddress address : InternetAddress.parseHeader(source, false)) {
                if (address.isGroup()) {
                    InternetAddress[] group = address.getGroup(false);
                    if (group != null) {
                        for (InternetAddress member : group) addAddress(recipients, member);
                    }
                } else {
                    addAddress(recipients, address);
                }
            }
        } catch (AddressException ignored) {
            // The composer lets the user fill in or correct missing recipients.
        }
    }

    private static void addAddress(Map<String, String> recipients, InternetAddress address) {
        String value = address.getAddress();
        if (value != null && !value.isBlank()) recipients.putIfAbsent(value.toLowerCase(Locale.ROOT), value);
    }

    private static String prefix(String subject, String prefix) {
        String result = subject.strip();
        return result.regionMatches(true, 0, prefix + ":", 0, prefix.length() + 1)
                ? result : (prefix + ": " + result).strip();
    }

    private static String value(String text) {
        return text == null ? "" : text;
    }
}
