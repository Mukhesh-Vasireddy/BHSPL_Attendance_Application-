package com.bhspl.ui;

import com.bhspl.db.DatabaseManager;
import com.bhspl.util.UIHelper;
import com.bhspl.util.HashUtil;

import javax.swing.*;
import java.awt.*;
import java.util.Map;

public class UserForm extends JDialog {
    private final Integer id;
    private final Runnable callback;
    private final DatabaseManager db = DatabaseManager.INSTANCE;
    private JTextField userField, empIdField;
    private JPasswordField passField;
    private JComboBox<String> roleCombo, statusCombo;

    public UserForm(JFrame parent, Integer id, Runnable callback) {
        super(parent, id == null ? "Add User" : "Edit User #" + id, true);
        this.id = id; this.callback = callback;
        setSize(400, 380); UIHelper.centerWindow(this, 400, 380);
        buildUI();
        if (id != null) loadData();
        setVisible(true);
    }

    private void buildUI() {
        JPanel root = new JPanel(new BorderLayout()); root.setBackground(UIHelper.BG_CARD);
        JPanel hdr = new JPanel(); hdr.setBackground(UIHelper.PRIMARY); hdr.setPreferredSize(new Dimension(0, 50));
        JLabel title = new JLabel("User Management");
        title.setFont(new Font("Segoe UI", Font.BOLD, 14)); title.setForeground(Color.WHITE); hdr.add(title);
        root.add(hdr, BorderLayout.NORTH);

        JPanel form = new JPanel(new GridBagLayout()); form.setBackground(UIHelper.BG_CARD);
        form.setBorder(BorderFactory.createEmptyBorder(20, 35, 20, 35));
        GridBagConstraints gbc = new GridBagConstraints(); gbc.insets = new Insets(6, 6, 6, 6); gbc.anchor = GridBagConstraints.EAST;

        String[] labels = {"Username *", "Password *", "Role", "Emp ID", "Status"};
        for (int i = 0; i < labels.length; i++) {
            gbc.gridx = 0; gbc.gridy = i;
            form.add(new JLabel(labels[i] + ":"), gbc);
            gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
            if (i == 0) { userField = new JTextField(15); form.add(userField, gbc); }
            else if (i == 1) { passField = new JPasswordField(15); form.add(passField, gbc); }
            else if (i == 2) { 
                roleCombo = new JComboBox<>(new String[]{"Admin", "HR", "Manager", "User"});
                form.add(roleCombo, gbc);
            } else if (i == 3) { empIdField = new JTextField(15); form.add(empIdField, gbc); }
            else if (i == 4) {
                statusCombo = new JComboBox<>(new String[]{"Active", "Inactive"});
                form.add(statusCombo, gbc);
            }
        }
        root.add(form, BorderLayout.CENTER);

        JPanel footer = new JPanel(); footer.setBackground(UIHelper.BG_CARD);
        JButton saveBtn = UIHelper.makeButton("Save", UIHelper.BTN_SUCCESS);
        saveBtn.addActionListener(e -> save()); footer.add(saveBtn);
        JButton cancelBtn = UIHelper.makeButton("Cancel", UIHelper.BTN_DANGER);
        cancelBtn.addActionListener(e -> dispose()); footer.add(cancelBtn);
        root.add(footer, BorderLayout.SOUTH);
        setContentPane(root);
    }

    private void loadData() {
        try {
            Map<String, Object> r = db.queryOne("SELECT * FROM users WHERE id=?", id);
            if (r != null) {
                userField.setText(DatabaseManager.str(r, "username"));
                userField.setEditable(false);
                roleCombo.setSelectedItem(DatabaseManager.str(r, "role"));
                empIdField.setText(DatabaseManager.str(r, "emp_id"));
                statusCombo.setSelectedItem(DatabaseManager.str(r, "status"));
                passField.setToolTipText("Leave blank to keep current password");
            }
        } catch (Exception e) {}
    }

    private void save() {
        String user = userField.getText().trim();
        String pass = new String(passField.getPassword());
        if (user.isEmpty()) return;
        try {
            String hash = pass.isEmpty() ? null : HashUtil.sha256(pass);
            if (id == null) {
                if (pass.isEmpty()) { JOptionPane.showMessageDialog(this, "Password is required for new users."); return; }
                db.execute("INSERT INTO users (username, password_hash, role, emp_id, status) VALUES (?,?,?,?,?)",
                    user, hash, roleCombo.getSelectedItem(), empIdField.getText().trim(), statusCombo.getSelectedItem());
            } else {
                if (hash == null) db.execute("UPDATE users SET role=?, emp_id=?, status=? WHERE id=?",
                    roleCombo.getSelectedItem(), empIdField.getText().trim(), statusCombo.getSelectedItem(), id);
                else db.execute("UPDATE users SET password_hash=?, role=?, emp_id=?, status=? WHERE id=?",
                    hash, roleCombo.getSelectedItem(), empIdField.getText().trim(), statusCombo.getSelectedItem(), id);
            }
            callback.run(); dispose();
        } catch (Exception e) {}
    }
}
