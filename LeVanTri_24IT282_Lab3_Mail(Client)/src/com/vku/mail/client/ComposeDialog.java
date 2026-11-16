package com.vku.mail.client;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;

import static com.vku.mail.ui.MailTheme.*;

public class ComposeDialog extends JDialog {
    public static class ComposeData {
        public String to;
        public String subject;
        public String body;
        public String attachName;
        public byte[] attachData;
    }

    private final JTextField toField = new JTextField();
    private final JTextField subjectField = new JTextField();
    private final JTextArea bodyArea = new JTextArea();
    private final JLabel attachment = label("", 12, false, MUTED);
    private final JLabel status = label("", 13, false, MUTED);
    private final JButton send = button("Gửi thư", true);
    private final JButton cancel = button("Hủy", false);
    private final JButton chooseFile = button("Đính kèm", false);
    private File attachedFile;
    private String forwardedName;
    private byte[] forwardedData;

    public ComposeDialog(Frame parent, String from, boolean gmail,
            Function<ComposeData, String> onSend, Runnable onSent) {
        this(parent, from, gmail, MailDraft.blank(), onSend, onSent);
    }

    ComposeDialog(Frame parent, String from, boolean gmail, MailDraft draft,
            Function<ComposeData, String> onSend, Runnable onSent) {
        super(parent, draft.title() + " · VKU Mail", true);
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setMinimumSize(new Dimension(540, 430));
        setSize(680, 530);
        setLocationRelativeTo(parent);

        JPanel root = panel(new BorderLayout(0, 15), CANVAS);
        root.setBorder(new EmptyBorder(19, 22, 19, 22));
        JPanel top = stack(null);
        top.add(label(draft.title(), 25, true, INK));
        top.add(Box.createVerticalStrut(5));
        top.add(label("Gửi từ  " + from, 13, false, MUTED));
        root.add(top, BorderLayout.NORTH);

        JPanel editor = card(new BorderLayout(0, 10));
        editor.setBorder(BorderFactory.createCompoundBorder(editor.getBorder(), new EmptyBorder(12, 15, 12, 15)));
        JPanel fields = panel(new GridLayout(2, 1, 0, 7), WHITE);
        fields.add(fieldRow("Đến", toField));
        fields.add(fieldRow("Tiêu đề", subjectField));
        toField.addActionListener(e -> subjectField.requestFocusInWindow());
        editor.add(fields, BorderLayout.NORTH);

        bodyArea.setFont(font(14, false));
        bodyArea.setForeground(INK);
        bodyArea.setLineWrap(true);
        bodyArea.setWrapStyleWord(true);
        bodyArea.setBorder(new EmptyBorder(10, 0, 5, 6));
        JPanel content = panel(new BorderLayout(), WHITE);
        content.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER), new EmptyBorder(10, 0, 0, 0)));
        content.add(label("Nội dung", 13, true, MUTED), BorderLayout.NORTH);
        content.add(scroll(bodyArea), BorderLayout.CENTER);
        bodyArea.getAccessibleContext().setAccessibleName("Nội dung thư");
        editor.add(content, BorderLayout.CENTER);
        root.add(editor, BorderLayout.CENTER);

        JPanel footer = panel(new BorderLayout(8, 7), null);
        if (gmail) {
            if (draft.attachmentData() != null) {
                forwardedName = draft.attachment();
                forwardedData = draft.attachmentData();
                showAttachment(forwardedName);
            }
            JPanel files = panel(new BorderLayout(8, 0), null);
            chooseFile.addActionListener(e -> toggleAttachment());
            files.add(chooseFile, BorderLayout.WEST);
            files.add(attachment, BorderLayout.CENTER);
            footer.add(files, BorderLayout.CENTER);
        }
        JPanel actions = panel(new FlowLayout(FlowLayout.RIGHT, 7, 0), null);
        cancel.addActionListener(e -> dispose());
        send.addActionListener(e -> send(onSend, onSent));
        actions.add(cancel);
        actions.add(send);
        footer.add(actions, BorderLayout.EAST);
        footer.add(status, BorderLayout.SOUTH);
        root.add(footer, BorderLayout.SOUTH);
        setContentPane(root);
        getRootPane().setDefaultButton(send);
        toField.setText(draft.to());
        subjectField.setText(draft.subject());
        bodyArea.setText(draft.body());
        bodyArea.setCaretPosition(0);
        addWindowListener(new WindowAdapter() {
            @Override public void windowOpened(WindowEvent event) {
                if (draft.to().isBlank()) toField.requestFocusInWindow();
                else bodyArea.requestFocusInWindow();
            }
        });
    }

    private JPanel fieldRow(String name, JTextField input) {
        JPanel row = panel(new BorderLayout(10, 0), WHITE);
        JLabel label = label(name, 13, true, MUTED);
        label.setPreferredSize(new Dimension(55, 28));
        label.setLabelFor(input);
        row.add(label, BorderLayout.WEST);
        input.setBorder(new EmptyBorder(4, 8, 4, 8));
        input.setBackground(CANVAS);
        input.setForeground(INK);
        row.add(input, BorderLayout.CENTER);
        return row;
    }

    private void toggleAttachment() {
        if (attachedFile != null || forwardedData != null) {
            attachedFile = null;
            forwardedName = null;
            forwardedData = null;
            attachment.setText("");
            attachment.setToolTipText(null);
            chooseFile.setText("Đính kèm");
            return;
        }
        JFileChooser chooser = new JFileChooser();
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            attachedFile = chooser.getSelectedFile();
            showAttachment(attachedFile.getName());
        }
    }

    private void showAttachment(String name) {
        attachment.setText(name);
        attachment.setToolTipText("Tệp  " + name);
        chooseFile.setText("Bỏ tệp");
    }

    private void send(Function<ComposeData, String> onSend, Runnable onSent) {
        String to = toField.getText().strip();
        if (to.isEmpty()) {
            setStatus("Nhập địa chỉ người nhận.", true);
            toField.requestFocusInWindow();
            return;
        }
        ComposeData data = new ComposeData();
        data.to = to;
        data.subject = subjectField.getText().strip();
        data.body = bodyArea.getText();
        data.attachName = forwardedName;
        data.attachData = forwardedData;
        File file = attachedFile;
        setSending(true);
        setStatus("Đang gửi…", false);
        new SwingWorker<String, Void>() {
            @Override protected String doInBackground() throws IOException {
                if (file != null) {
                    if (Files.size(file.toPath()) > 8 * 1024 * 1024) {
                        return "Chọn tệp đính kèm tối đa 8 MB.";
                    }
                    data.attachData = Files.readAllBytes(file.toPath());
                    data.attachName = file.getName();
                }
                return onSend.apply(data);
            }

            @Override protected void done() {
                setSending(false);
                try {
                    String error = get();
                    if (error != null) {
                        setStatus(error, true);
                        return;
                    }
                    dispose();
                    onSent.run();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    setStatus("Gửi thư bị gián đoạn.", true);
                } catch (ExecutionException e) {
                    setStatus("Không thể gửi thư. " + e.getCause().getMessage(), true);
                }
            }
        }.execute();
    }

    private void setSending(boolean busy) {
        setDefaultCloseOperation(busy ? WindowConstants.DO_NOTHING_ON_CLOSE : WindowConstants.DISPOSE_ON_CLOSE);
        send.setEnabled(!busy);
        cancel.setEnabled(!busy);
        chooseFile.setEnabled(!busy);
        toField.setEditable(!busy);
        subjectField.setEditable(!busy);
        bodyArea.setEditable(!busy);
    }

    private void setStatus(String text, boolean error) {
        status.setForeground(error ? ERROR_TEXT : MUTED);
        status.setText(text);
        status.setToolTipText(text);
    }
}
