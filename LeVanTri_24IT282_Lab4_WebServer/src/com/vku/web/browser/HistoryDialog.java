package com.vku.web.browser;

import com.vku.web.common.HistoryItem;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;
import java.util.function.Consumer;

import static com.vku.web.common.DesktopTheme.*;

public class HistoryDialog extends JDialog {
    private DefaultTableModel model;
    private JTable table;

    public HistoryDialog(Frame parent, List<HistoryItem> historyList, Consumer<String> onSelectUrl) {
        super(parent, "Lịch sử duyệt web", true);
        setSize(810, 440);
        setLocationRelativeTo(parent);

        JPanel main = panel(new BorderLayout(0, 12), BACKGROUND);
        main.setBorder(new EmptyBorder(16, 18, 16, 18));
        main.add(label("Lịch sử duyệt web", 21, Font.BOLD, INK), BorderLayout.NORTH);

        model = new DefaultTableModel(new String[]{"Thời gian", "Phương thức", "Mã HTTP", "Địa chỉ URL"}, 0) {
            @Override
            public boolean isCellEditable(int row, int col) { return false; }
        };
        table = new JTable(model);
        styleTable(table);
        table.getColumnModel().getColumn(3).setPreferredWidth(360);

        for (int i = historyList.size() - 1; i >= 0; i--) {
            HistoryItem it = historyList.get(i);
            model.addRow(new Object[]{it.getFormattedTime(), it.getMethod(), it.getStatusCode(), it.getUrl()});
        }

        table.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                if (evt.getClickCount() == 2) {
                    int row = table.getSelectedRow();
                    if (row >= 0) {
                        String url = (String) model.getValueAt(row, 3);
                        onSelectUrl.accept(url);
                        dispose();
                    }
                }
            }
        });

        main.add(scroll(table), BorderLayout.CENTER);

        JPanel bottom = panel(new FlowLayout(FlowLayout.RIGHT, 6, 0), BACKGROUND);
        JButton btnGo = button("Mở URL", true);
        btnGo.setEnabled(false);
        table.getSelectionModel().addListSelectionListener(e -> btnGo.setEnabled(table.getSelectedRow() >= 0));
        btnGo.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row >= 0) {
                String url = (String) model.getValueAt(row, 3);
                onSelectUrl.accept(url);
                dispose();
            }
        });
        JButton btnClose = button("Đóng", false);
        btnClose.addActionListener(e -> dispose());

        bottom.add(btnClose);
        bottom.add(btnGo);
        main.add(bottom, BorderLayout.SOUTH);

        add(main);
    }
}
