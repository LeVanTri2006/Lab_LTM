package com.vku.web.common;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.FontUIResource;
import java.awt.*;

public final class DesktopTheme {
    public static final Color BACKGROUND = new Color(0xEDF1F6);
    public static final Color SURFACE = Color.WHITE;
    public static final Color INK = new Color(0x15253A);
    public static final Color MUTED = new Color(0x52657A);
    public static final Color LINE = new Color(0xCCD7E3);
    public static final Color NAVY = new Color(0x14283F);
    public static final Color ACCENT = new Color(0x086C60);
    public static final Color MINT = new Color(0xA8EFDC);
    public static final Color ERROR_COLOR = new Color(0xA43931);

    private DesktopTheme() {}

    public static void install() {
        try {
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        } catch (ReflectiveOperationException | UnsupportedLookAndFeelException ignored) {}
        FontUIResource font = new FontUIResource("Segoe UI", Font.PLAIN, 14);
        for (Object key : UIManager.getLookAndFeelDefaults().keySet().toArray()) {
            if (UIManager.get(key) instanceof FontUIResource) UIManager.put(key, font);
        }
        UIManager.put("TabbedPane.selected", SURFACE);
        UIManager.put("TabbedPane.background", BACKGROUND);
        UIManager.put("TabbedPane.contentAreaColor", SURFACE);
        UIManager.put("TabbedPane.tabInsets", new Insets(9, 16, 9, 16));
        UIManager.put("ProgressBar.foreground", ACCENT);
    }

    public static JLabel label(String text, int size, int weight, Color color) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("Segoe UI", weight, size));
        label.setForeground(color);
        return label;
    }

    public static JPanel panel(LayoutManager layout, Color background) {
        JPanel panel = new JPanel(layout);
        panel.setBackground(background);
        return panel;
    }

    public static JButton button(String text, boolean primary) {
        JButton button = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D graphics = (Graphics2D) g.create();
                graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                Color fill = primary ? ACCENT : SURFACE;
                if (!isEnabled()) fill = BACKGROUND;
                else if (getModel().isPressed()) fill = primary ? NAVY : LINE;
                else if (getModel().isRollover()) fill = primary ? new Color(0x07584F) : BACKGROUND;
                graphics.setColor(fill);
                graphics.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                graphics.setColor(isFocusOwner() ? (primary ? MINT : ACCENT) : (primary ? fill : LINE));
                graphics.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
                graphics.dispose();
                super.paintComponent(g);
            }
        };
        button.setContentAreaFilled(false);
        button.setFocusPainted(false);
        button.setBorder(new EmptyBorder(8, 12, 8, 12));
        button.setForeground(primary ? SURFACE : INK);
        button.setFont(new Font("Segoe UI", Font.BOLD, 13));
        button.setRolloverEnabled(true);
        return button;
    }

    public static JScrollPane scroll(JComponent content) {
        JScrollPane pane = new JScrollPane(content);
        pane.setBorder(BorderFactory.createLineBorder(LINE));
        pane.getViewport().setBackground(SURFACE);
        return pane;
    }

    public static void styleTable(JTable table) {
        table.setRowHeight(30);
        table.setFillsViewportHeight(true);
        table.setForeground(INK);
        table.setGridColor(BACKGROUND);
        table.setSelectionBackground(MINT);
        table.setSelectionForeground(INK);
        table.getTableHeader().setPreferredSize(new Dimension(0, 34));
    }
}
