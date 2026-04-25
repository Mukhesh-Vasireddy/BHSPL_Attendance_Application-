package com.bhspl.ui;

import com.bhspl.db.DatabaseManager;
import com.bhspl.util.UIHelper;

import javax.swing.*;
import java.awt.*;

public class ResetPasswordDialog extends JDialog {
    private final int userId;
    private final String username;
    private JPasswordField passField, confField;

    public ResetPasswordDialog(JFrame parent, int userId, String username) {
        super(parent, "Reset Password — " + username, true);
        this.userId = userId; this.username = username;
        setSize(320, 240); UIHelper.centerWindow(this, 320, 240);
        buildUI();
        setVisible(true);
    }

    private void buildUI() {
        JPanel root = new JPanel(new BorderLayout()); root.setBackground(UIHelper.BG_CARD);
        JPanel hdr = new JPanel(); hdr.setBackground(UIHelper.PRIMARY); hdr.setPreferredSize(new Dimension(0, 45));
        JLabel title = new JLabel("🔑  Reset: " + username);
        title.setFont(new Font("Segoe UI", Font.BOLD, 12)); title.setForeground(Color.WHITE); hdr.add(title);
        root.add(hdr, BorderLayout.NORTH);

        JPanel form = new JPanel(new GridBagLayout()); form.setBackground(UIHelper.BG_CARD);
        form.setBorder(BorderFactory.createEmptyBorder(15, 20, 15, 20));
        GridBagConstraints gbc = new GridBagConstraints(); gbc.insets = new Insets(6, 6, 6, 6); gbc.anchor = GridBagConstraints.EAST;

        gbc.gridx = 0; gbc.gridy = 0; form.add(new JLabel("New Password:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        passField = new JPasswordField(15); form.add(passField, gbc);

        gbc.gridx = 0; gbc.gridy = 1; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0;
        form.add(new JLabel("Confirm:"), gbc);
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
        confField = new JPasswordField(15); form.add(confField, gbc);

        root.add(form, BorderLayout.CENTER);

        JPanel footer = new JPanel(); footer.setBackground(UIHelper.BG_CARD);
        JButton resetBtn = UIHelper.makeButton("Reset", UIHelper.BTN_SUCCESS);
        resetBtn.addActionListener(e -> reset()); footer.add(resetBtn);
        JButton canBtn = UIHelper.makeButton("Cancel", UIHelper.BTN_DANGER);
        canBtn.addActionListener(e -> dispose()); footer.add(canBtn);
        root.add(footer, BorderLayout.SOUTH);
        setContentPane(root);
    }

    private void reset() {
        String p1 = new String(passField.getPassword());
        String p2 = new String(confField.getPassword());
        if (p1.isEmpty()) return;
        if (!p1.equals(p2)) { JOptionPane.showMessageDialog(this, "Passwords do not match."); return; }
        try {
            String hash = com.bhspl.util.HashUtil.sha256(p1);
            DatabaseManager.INSTANCE.execute("UPDATE users SET password_hash=? WHERE id=?", hash, userId);
            JOptionPane.showMessageDialog(this, "Password updated successfully.");
            dispose();
        } catch (Exception e) {}
    }
}
