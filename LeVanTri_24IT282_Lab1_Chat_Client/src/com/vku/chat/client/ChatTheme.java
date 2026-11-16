package com.vku.chat.client;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicButtonUI;
import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.util.Locale;

final class ChatTheme {
    static final Color BLUE = new Color(0x0068FF);
    static final Color NAV = new Color(0x0754DB);
    static final Color INK = new Color(0x14233D);
    static final Color MUTED = new Color(0x5D708D);
    static final Color LINE = new Color(0xE2E8F1);
    static final Color CHAT_BACKGROUND = new Color(0xEAF0F8);
    static final Color SOFT = new Color(0xE9F2FF);
    static final Color RED = new Color(0xD93250);
    private static final Color[] AVATAR_COLORS = {
            new Color(0x2558BE), new Color(0x117A77), new Color(0x8051B1),
            new Color(0xB4531D), new Color(0x3B6F37), new Color(0xB04476)
    };

    enum Glyph { CHAT, SEARCH, PLUS, PHONE, VIDEO, ADD_USER, EMOJI, ATTACH, MIC, SCREEN, SEND }

    private ChatTheme() {}

    static Font font(int size, int style) {
        return new Font("Segoe UI", style, size);
    }

    static JLabel label(String text, int size, int style, Color color) {
        JLabel label = new JLabel(text);
        label.putClientProperty("html.disable", true);
        label.setFont(font(size, style));
        label.setForeground(color);
        return label;
    }

    static void paintCountBadge(Graphics graphics, int right, int top, int count, int height) {
        if (count == 0) return;
        String display = count > 99 ? "99+" : Integer.toString(count);
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setFont(font(Math.round(height * 0.58f), Font.BOLD));
        FontMetrics metrics = g.getFontMetrics();
        int width = Math.max(height, metrics.stringWidth(display) + height / 2);
        g.setColor(RED);
        g.fillRoundRect(right - width, top, width, height, height, height);
        g.setColor(Color.WHITE);
        g.drawString(display, right - (width + metrics.stringWidth(display)) / 2,
                top + (height + metrics.getAscent() - metrics.getDescent()) / 2);
        g.dispose();
    }

    static Image taskbarBadge(int count) {
        if (count == 0) return null;
        var image = new BufferedImage(40, 40, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        paintCountBadge(graphics, 39, 7, count, 26);
        graphics.dispose();
        return image;
    }

    static JButton button(String text, Glyph glyph, Color background, Color foreground) {
        JButton button = new JButton(text) {
            @Override
            protected void paintComponent(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color shade = !isEnabled() ? new Color(0xF2F5FA)
                        : getModel().isPressed() ? background.darker()
                        : getModel().isRollover() ? blend(background, foreground) : background;
                g.setColor(shade);
                g.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);
                if (isFocusOwner()) {
                    g.setColor(background.equals(Color.WHITE) || background.equals(SOFT) ? BLUE : Color.WHITE);
                    g.drawRoundRect(2, 2, getWidth() - 5, getHeight() - 5, 13, 13);
                }
                g.dispose();
                super.paintComponent(graphics);
            }
        };
        button.setUI(new BasicButtonUI());
        button.setForeground(foreground);
        button.setFont(font(13, Font.BOLD));
        button.setOpaque(false);
        button.setContentAreaFilled(false);
        button.setBorder(new EmptyBorder(9, text.isEmpty() ? 10 : 12, 9, text.isEmpty() ? 10 : 12));
        button.setIconTextGap(7);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setRolloverEnabled(true);
        if (glyph != null) {
            button.setIcon(icon(glyph, foreground, 19));
            button.setDisabledIcon(icon(glyph, new Color(0x8B9AB0), 19));
        }
        return button;
    }

    static Icon avatar(String name, String key, int size) {
        String text = name == null ? "" : name.strip();
        String[] words = text.split("\\s+");
        String initials = firstLetter(words[0]) + (words.length > 1 ? firstLetter(words[words.length - 1]) : "");
        Color tone = AVATAR_COLORS[Math.floorMod(key.hashCode(), AVATAR_COLORS.length)];
        return new Icon() {
            @Override public int getIconWidth() { return size; }
            @Override public int getIconHeight() { return size; }

            @Override
            public void paintIcon(Component component, Graphics graphics, int x, int y) {
                Graphics2D g = (Graphics2D) graphics.create();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(tone);
                g.fillOval(x, y, size, size);
                g.setColor(Color.WHITE);
                g.setFont(font(Math.max(12, size / 3), Font.BOLD));
                FontMetrics metrics = g.getFontMetrics();
                g.drawString(initials.isEmpty() ? "?" : initials, x + (size - metrics.stringWidth(initials)) / 2,
                        y + (size + metrics.getAscent() - metrics.getDescent()) / 2);
                g.dispose();
            }
        };
    }

    private static String firstLetter(String text) {
        return text.isEmpty() ? "" : text.substring(0, text.offsetByCodePoints(0, 1)).toUpperCase(Locale.ROOT);
    }

    static Icon icon(Glyph glyph, Color color, int size) {
        return new Icon() {
            @Override public int getIconWidth() { return size; }
            @Override public int getIconHeight() { return size; }

            @Override
            public void paintIcon(Component component, Graphics graphics, int x, int y) {
                Graphics2D g = (Graphics2D) graphics.create();
                g.translate(x, y);
                g.scale(size / 24.0, size / 24.0);
                g.setColor(color);
                g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                switch (glyph) {
                    case CHAT -> {
                        g.drawRoundRect(2, 3, 20, 14, 5, 5);
                        path(g, 6, 17, 5, 21, 11, 17);
                        g.drawLine(7, 9, 17, 9);
                        g.drawLine(7, 12, 13, 12);
                    }
                    case SEARCH -> {
                        g.drawOval(3, 3, 12, 12);
                        g.drawLine(14, 14, 21, 21);
                    }
                    case PLUS -> path(g, 4, 12, 20, 12, 12, 12, 12, 4, 12, 20);
                    case PHONE -> path(g, 3, 5, 7, 3, 11, 8, 8, 11, 13, 16, 16, 13, 21, 17, 19, 21, 14, 21,
                            8, 17, 4, 12, 3, 7, 3, 5);
                    case VIDEO -> {
                        g.drawRoundRect(2, 6, 13, 12, 3, 3);
                        path(g, 15, 10, 22, 6, 22, 18, 15, 14);
                    }
                    case ADD_USER -> {
                        g.drawOval(5, 3, 7, 7);
                        g.drawArc(1, 13, 15, 14, 0, 180);
                        g.drawLine(16, 9, 23, 9);
                        g.drawLine(19, 6, 19, 12);
                    }
                    case EMOJI -> {
                        g.drawOval(2, 2, 20, 20);
                        g.drawLine(8, 8, 8, 9);
                        g.drawLine(16, 8, 16, 9);
                        g.drawArc(7, 9, 10, 8, 180, 180);
                    }
                    case ATTACH -> {
                        g.rotate(Math.PI / 5, 12, 12);
                        g.drawRoundRect(7, 2, 10, 20, 9, 9);
                        g.drawRoundRect(11, 5, 3, 13, 3, 3);
                    }
                    case MIC -> {
                        g.drawRoundRect(9, 2, 6, 13, 6, 6);
                        g.drawArc(5, 6, 14, 13, 180, 180);
                        path(g, 12, 19, 12, 22, 8, 22, 16, 22);
                    }
                    case SCREEN -> {
                        g.drawRoundRect(2, 3, 20, 14, 3, 3);
                        path(g, 12, 17, 12, 21, 7, 21, 17, 21);
                    }
                    case SEND -> path(g, 2, 3, 22, 12, 2, 21, 6, 12, 2, 3, 6, 12, 16, 12);
                }
                g.dispose();
            }
        };
    }

    private static void path(Graphics2D g, int... points) {
        Path2D path = new Path2D.Double();
        path.moveTo(points[0], points[1]);
        for (int index = 2; index < points.length; index += 2) path.lineTo(points[index], points[index + 1]);
        g.draw(path);
    }

    private static Color blend(Color background, Color foreground) {
        return new Color((background.getRed() * 9 + foreground.getRed()) / 10,
                (background.getGreen() * 9 + foreground.getGreen()) / 10,
                (background.getBlue() * 9 + foreground.getBlue()) / 10);
    }
}
