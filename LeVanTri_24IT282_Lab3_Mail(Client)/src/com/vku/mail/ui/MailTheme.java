package com.vku.mail.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicButtonUI;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.geom.Path2D;

public final class MailTheme {
    public static final Color CANVAS = new Color(0xEDF4F2);
    public static final Color WHITE = Color.WHITE;
    public static final Color INK = new Color(0x172D2B);
    public static final Color MUTED = new Color(0x526863);
    public static final Color BORDER = new Color(0xDCE7E3);
    public static final Color ACCENT = new Color(0x006B57);
    public static final Color TINT = new Color(0xD9F1E8);
    public static final Color RAIL = new Color(0x132E2A);
    public static final Color RAIL_TEXT = new Color(0xC9DDD6);
    public static final Color ERROR_TEXT = new Color(0xA13738);

    private MailTheme() {}

    public static void install() {
        UIManager.put("OptionPane.background", WHITE);
        UIManager.put("Panel.background", WHITE);
        UIManager.put("OptionPane.messageFont", font(14, false));
        UIManager.put("Label.font", font(13, false));
        UIManager.put("TextField.font", font(14, false));
        UIManager.put("PasswordField.font", font(14, false));
        UIManager.put("ComboBox.font", font(14, false));
        UIManager.put("Button.font", font(13, true));
    }

    public static Font font(int size, boolean bold) {
        return new Font("Segoe UI", bold ? Font.BOLD : Font.PLAIN, size);
    }

    public static JLabel label(String text, int size, boolean bold, Color color) {
        JLabel label = new JLabel(text);
        label.putClientProperty("html.disable", true);
        label.setFont(font(size, bold));
        label.setForeground(color);
        return label;
    }

    public static JPanel panel(LayoutManager layout, Color color) {
        JPanel panel = new JPanel(layout);
        panel.setOpaque(color != null);
        if (color != null) panel.setBackground(color);
        return panel;
    }

    public static JPanel stack(Color color) {
        JPanel panel = panel(null, color);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        return panel;
    }

    public static JPanel card(LayoutManager layout) {
        JPanel panel = panel(layout, WHITE);
        panel.setBorder(BorderFactory.createLineBorder(BORDER));
        return panel;
    }

    public static JPanel brand(String name, boolean dark) {
        JPanel panel = panel(new BorderLayout(10, 0), null);
        JLabel mark = new JLabel(new Icon() {
            @Override public int getIconWidth() { return 36; }
            @Override public int getIconHeight() { return 36; }

            @Override public void paintIcon(Component c, Graphics graphics, int x, int y) {
                Graphics2D g = (Graphics2D) graphics.create();
                g.translate(x, y);
                antialias(g);
                g.setColor(dark ? TINT : ACCENT);
                g.fillRoundRect(0, 0, 36, 36, 13, 13);
                g.setColor(dark ? ACCENT : WHITE);
                g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.drawRoundRect(9, 12, 18, 13, 4, 4);
                Path2D flap = new Path2D.Double();
                flap.moveTo(10, 14);
                flap.lineTo(18, 20);
                flap.lineTo(26, 14);
                g.draw(flap);
                g.dispose();
            }
        });
        panel.add(mark, BorderLayout.WEST);
        panel.add(label(name, dark ? 17 : 19, true, dark ? WHITE : INK), BorderLayout.CENTER);
        return panel;
    }

    public static JButton button(String text, boolean primary) {
        JButton button = new JButton(text) {
            @Override protected void paintComponent(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics.create();
                antialias(g);
                Color fill = getBackground();
                if (getModel().isRollover() || getModel().isArmed()) {
                    fill = primary ? fill.darker() : fill.equals(WHITE) ? CANVAS : fill.darker();
                }
                g.setColor(fill);
                g.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                if (Boolean.TRUE.equals(getClientProperty("outlined")) || hasFocus()) {
                    g.setColor(hasFocus() ? ACCENT : BORDER);
                    g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
                }
                g.dispose();
                super.paintComponent(graphics);
            }
        };
        button.setUI(new BasicButtonUI());
        button.setOpaque(false);
        button.setContentAreaFilled(false);
        button.setFocusPainted(false);
        button.setRolloverEnabled(true);
        button.setBorder(new EmptyBorder(9, 14, 10, 14));
        button.setFont(font(14, true));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.setBackground(primary ? ACCENT : WHITE);
        button.setForeground(primary ? WHITE : INK);
        button.putClientProperty("outlined", !primary);
        return button;
    }

    public static JButton navButton(String text, boolean dark) {
        JButton button = button(text, false);
        button.putClientProperty("outlined", false);
        button.setHorizontalAlignment(SwingConstants.LEFT);
        button.setAlignmentX(Component.LEFT_ALIGNMENT);
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        selectNav(button, false, dark);
        return button;
    }

    public static void selectNav(JButton button, boolean selected, boolean dark) {
        button.setBackground(selected ? TINT : dark ? RAIL : CANVAS);
        button.setForeground(selected ? ACCENT : dark ? RAIL_TEXT : INK);
    }

    public static DefaultTableModel model(String... columns) {
        return new DefaultTableModel(columns, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
    }

    public static JTable table(DefaultTableModel model) {
        JTable table = new JTable(model);
        table.setFont(font(13, false));
        table.setRowHeight(40);
        table.setBackground(WHITE);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.setFillsViewportHeight(true);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getTableHeader().setReorderingAllowed(false);
        table.getTableHeader().setPreferredSize(new Dimension(0, 39));
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable owner, Object value,
                    boolean selected, boolean focus, int row, int column) {
                super.getTableCellRendererComponent(owner, value, selected, focus, row, column);
                putClientProperty("html.disable", true);
                setText(value == null ? "" : value.toString());
                setToolTipText(getText().isBlank() ? null : " " + getText());
                setFont(owner.getFont().deriveFont(column == 1 ? Font.BOLD : Font.PLAIN));
                setBackground(selected ? TINT : row % 2 == 0 ? WHITE : new Color(0xF7FAF9));
                setForeground(selected ? ACCENT : INK);
                setBorder(new EmptyBorder(0, 12, 0, 8));
                return this;
            }
        });
        table.getTableHeader().setDefaultRenderer(new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable owner, Object value,
                    boolean selected, boolean focus, int row, int column) {
                super.getTableCellRendererComponent(owner, value, selected, focus, row, column);
                setFont(font(12, true));
                setBackground(CANVAS);
                setForeground(MUTED);
                setBorder(new EmptyBorder(0, 12, 0, 8));
                return this;
            }
        });
        return table;
    }

    public static JScrollPane scroll(Component content) {
        JScrollPane scroll = new JScrollPane(content);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(WHITE);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        return scroll;
    }

    public static JTextArea logArea() {
        JTextArea area = new JTextArea();
        area.setEditable(false);
        area.setFont(new Font("Monospaced", Font.PLAIN, 13));
        area.setBackground(RAIL);
        area.setForeground(RAIL_TEXT);
        area.setBorder(new EmptyBorder(14, 16, 14, 16));
        return area;
    }

    private static void antialias(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    }
}
