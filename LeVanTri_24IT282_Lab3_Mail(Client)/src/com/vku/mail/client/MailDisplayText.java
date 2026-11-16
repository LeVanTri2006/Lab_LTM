package com.vku.mail.client;

import java.awt.Font;
import java.text.Normalizer;
import java.util.regex.Pattern;

final class MailDisplayText {
    private static final Pattern DECORATIVE_RULE = Pattern.compile(
            "(?m)^\\h*(?:[\\u2500-\\u259F\\u25A0\\u25A1\\u25AA\\u25AB\\u25FB-\\u25FE\\uFFFD\\p{Co}]\\h*){3,}$");

    private MailDisplayText() {}

    static String readable(String text, Font font) {
        if (text == null) return "";
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFC)
                .replace("\r\n", "\n").replace('\r', '\n');
        normalized = DECORATIVE_RULE.matcher(normalized).replaceAll("------------------------");
        StringBuilder display = new StringBuilder(normalized.length());
        normalized.codePoints().forEach(codePoint -> {
            int type = Character.getType(codePoint);
            if (codePoint == '\n' || codePoint == '\t') {
                display.appendCodePoint(codePoint);
            } else if (codePoint == 0xFFFD || codePoint == 0x25A1 || type == Character.PRIVATE_USE) {
                display.append(' ');
            } else if (type == Character.FORMAT || (codePoint >= 0xFE00 && codePoint <= 0xFE0F)) {
                display.appendCodePoint(codePoint);
            } else if (!Character.isISOControl(codePoint)) {
                display.appendCodePoint(font.canDisplay(codePoint) ? codePoint : '·');
            }
        });
        return display.toString();
    }
}
