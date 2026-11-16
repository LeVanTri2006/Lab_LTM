package com.vku.rmi.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;

public final class Theme {
    public static final Color BACKGROUND = new Color(0xEEF2F6);
    public static final Color INK = new Color(0x172638);
    public static final Color MUTED = new Color(0x506278);
    public static final Color NAVY = new Color(0x12253A);
    public static final Color LINE = new Color(0xD6DFE8);
    public static final Color ACCENT = new Color(0x006D65);
    public static final Color ERROR = new Color(0xA1273C);
    public static final Color WARNING = new Color(0x8A5100);
    private static final Color STRIPE = new Color(0xF5F8FA);

    private Theme() {}

    public static void install() {
        UIManager.put("Label.font", font(Font.PLAIN, 14));
        UIManager.put("Button.font", font(Font.BOLD, 13));
        UIManager.put("TextField.font", font(Font.PLAIN, 14));
        UIManager.put("OptionPane.messageFont", font(Font.PLAIN, 14));
    }

    public static Font font(int weight, int size) {
        return new Font("Segoe UI", weight, size);
    }

    public static JLabel label(String text, Color color, int size, boolean bold) {
        JLabel label = new JLabel(text);
        label.setFont(font(bold ? Font.BOLD : Font.PLAIN, size));
        label.setForeground(color);
        return label;
    }

    public static JPanel frameBody() {
        JPanel panel = new JPanel(new BorderLayout(16, 16));
        panel.setBorder(new EmptyBorder(20, 24, 20, 24));
        panel.setBackground(BACKGROUND);
        return panel;
    }

    public static JPanel header(String title, String subtitle) {
        JPanel panel = new JPanel(new BorderLayout(16, 0));
        panel.setBackground(NAVY);
        panel.setBorder(new EmptyBorder(16, 24, 18, 24));
        JPanel copy = new JPanel(new GridLayout(0, 1, 0, 4));
        copy.setOpaque(false);
        copy.add(label(title, Color.WHITE, 25, true));
        copy.add(label(subtitle, new Color(0xCCD8E4), 13, false));
        panel.add(copy, BorderLayout.CENTER);
        return panel;
    }

    public static JPanel card(LayoutManager layout) {
        JPanel panel = new JPanel(layout);
        panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(LINE), new EmptyBorder(15, 16, 15, 16)));
        return panel;
    }

    public static JButton button(String title, Color background, Color foreground) {
        JButton button = new JButton(title);
        button.setBackground(background);
        button.setForeground(foreground);
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(background.equals(Color.WHITE) ? LINE : background),
                new EmptyBorder(8, 13, 8, 13)));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setOpaque(true);
        return button;
    }

    public static JTextField field(int columns) {
        JTextField field = new JTextField(columns);
        field.setForeground(INK);
        field.setBackground(Color.WHITE);
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(LINE), new EmptyBorder(7, 9, 7, 9)));
        return field;
    }

    public static JScrollPane table(JTable table) {
        table.setFont(font(Font.PLAIN, 13));
        table.setForeground(INK);
        table.setRowHeight(36);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.setSelectionBackground(new Color(0xD9EDEB));
        table.setSelectionForeground(NAVY);
        table.setFillsViewportHeight(true);
        table.getTableHeader().setFont(font(Font.BOLD, 12));
        table.getTableHeader().setBackground(STRIPE);
        table.getTableHeader().setForeground(MUTED);
        table.getTableHeader().setPreferredSize(new Dimension(0, 33));
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable source, Object value, boolean selected,
                                                           boolean focused, int row, int column) {
                super.getTableCellRendererComponent(source, value, selected, focused, row, column);
                if (!selected) setBackground(row % 2 == 0 ? Color.WHITE : STRIPE);
                setBorder(new EmptyBorder(0, 8, 0, 8));
                setToolTipText(value == null ? null : value.toString());
                return this;
            }
        });
        JScrollPane scroll = new JScrollPane(table);
        scroll.getViewport().setBackground(Color.WHITE);
        scroll.setBorder(BorderFactory.createLineBorder(LINE));
        return scroll;
    }

    public static void widths(JTable table, int... widths) {
        for (int i = 0; i < widths.length; i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }
    }
}
