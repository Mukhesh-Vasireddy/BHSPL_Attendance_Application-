package com.bhspl.ui;

import com.bhspl.core.Config;
import com.bhspl.db.DatabaseManager;
import com.bhspl.util.UIHelper;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import java.awt.*;
import java.awt.Color;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

public class SettingsPanel extends JPanel {

    private UIHelper.StyledTablePanel userTablePanel;

    public SettingsPanel() {
        setBackground(UIHelper.BG_MAIN);
        setLayout(new MigLayout("ins 24, wrap, gap 0, fill", "[grow]", "[] 16 [grow]"));
        buildUI();
        loadUsers();
    }

    private void buildUI() {
        JLabel title = new JLabel("System Settings");
        title.setFont(UIHelper.FNT_TITLE);
        title.setForeground(UIHelper.PRIMARY);
        add(title, "growx");

        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(UIHelper.FNT_MEDIUM);
        tabs.putClientProperty("JTabbedPane.showTabSeparators", true);

        tabs.addTab("User Accounts", buildUsersTab());
        tabs.addTab("Database Config", buildDbTab());

        add(tabs, "grow, push");
    }

    // ── USERS TAB ──────────────────────────────────────────────────────────
    private JPanel buildUsersTab() {
        JPanel p = new JPanel(new MigLayout("ins 20, wrap, gap 0", "[grow]", "[] 12 [grow]"));
        p.setOpaque(false);

        JPanel toolbar = new JPanel(new MigLayout("ins 0, gap 12", "[] push [] 8 [] 8 []"));
        toolbar.setOpaque(false);

        JButton addBtn    = UIHelper.makeButton("Add User", UIHelper.SUCCESS);
        JButton resetBtn  = UIHelper.makeButton("Reset Pasword", UIHelper.PRIMARY);
        JButton toggleBtn = UIHelper.makeButton("Toggle Active", new Color(0x334155));

        addBtn.addActionListener(e    -> openAddUser());
        resetBtn.addActionListener(e  -> resetPassword());
        toggleBtn.addActionListener(e -> toggleUserStatus());

        JLabel lblTitle = new JLabel("User Management");
        lblTitle.setFont(UIHelper.FNT_BOLD);
        toolbar.add(lblTitle);
        toolbar.add(addBtn, "right");
        toolbar.add(resetBtn);
        toolbar.add(toggleBtn);
        p.add(toolbar, "growx");

        String[] cols = {"ID", "Username", "Role", "Emp ID", "Status", "Last Login"};
        userTablePanel = new UIHelper.StyledTablePanel(cols);
        userTablePanel.setBorder(UIHelper.createCardBorder());
        userTablePanel.getTable().getColumnModel().getColumn(0).setMaxWidth(60);
        
        p.add(userTablePanel, "grow, push");
        return p;
    }

    private void loadUsers() {
        userTablePanel.clearRows();
        SwingWorker<List<Map<String, Object>>, Void> w = new SwingWorker<>() {
            @Override protected List<Map<String, Object>> doInBackground() throws Exception {
                return DatabaseManager.getInstance().fetchAll(
                    "SELECT id,username,role,emp_id,status,last_login FROM users ORDER BY username");
            }
            @Override protected void done() {
                try {
                    for (Map<String, Object> r : get())
                        userTablePanel.addRow(new Object[]{
                            r.get("id"), r.get("username"), r.get("role"), r.get("emp_id"), r.get("status"), r.get("last_login")
                        });
                } catch (Exception ignored) {}
            }
        };
        w.execute();
    }

    private void openAddUser() {
        JDialog dlg = new JDialog((JFrame) SwingUtilities.getWindowAncestor(this), "Create System User", true);
        dlg.setSize(400, 320);
        UIHelper.centerWindow(dlg, 400, 320);

        JPanel p = new JPanel(new MigLayout("ins 24, wrap, gap 12", "[][grow]", "[]"));
        p.setBackground(Color.WHITE);

        JTextField uname = new JTextField(14);
        JPasswordField pass = new JPasswordField(14);
        JComboBox<String> role = new JComboBox<>(new String[]{"Admin", "Operator", "HR"});
        JTextField empId = new JTextField(14);

        Object[][] rows = {{"Username", uname}, {"Password", pass}, {"System Role", role}, {"Employee ID", empId}};
        for (Object[] row : rows) {
            p.add(new JLabel((String) row[0]));
            p.add((Component) row[1], "growx");
        }

        JButton save = UIHelper.makeButton("Create Account", UIHelper.SUCCESS);
        save.addActionListener(e -> {
            try {
                String u = uname.getText().trim();
                if (u.isEmpty()) { JOptionPane.showMessageDialog(dlg, "Username is required."); return; }
                
                String ph = DatabaseManager.getInstance().hashPw(new String(pass.getPassword()));
                DatabaseManager.getInstance().execute(
                    "INSERT INTO users (username, password_hash, role, emp_id) VALUES(?,?,?,?)",
                    u, ph, role.getSelectedItem(),
                    empId.getText().trim().isEmpty() ? null : empId.getText().trim());
                dlg.dispose(); 
                loadUsers();
            } catch (SQLException ex) { JOptionPane.showMessageDialog(dlg, "Error: " + ex.getMessage()); }
        });
        
        p.add(save, "skip, growx, gaptop 12");
        dlg.setContentPane(p);
        dlg.setVisible(true);
    }

    private void resetPassword() {
        Object idVal = userTablePanel.getSelectedValue();
        if (idVal == null) { JOptionPane.showMessageDialog(this, "Select a user first."); return; }
        
        int id = (int) idVal;
        String newPass = JOptionPane.showInputDialog(this, "Enter new password for the user:");
        if (newPass == null || newPass.trim().isEmpty()) return;
        try {
            String ph = DatabaseManager.getInstance().hashPw(newPass.trim());
            DatabaseManager.getInstance().execute("UPDATE users SET password_hash=? WHERE id=?", ph, id);
            JOptionPane.showMessageDialog(this, "Password reset successfully.");
        } catch (SQLException ex) { JOptionPane.showMessageDialog(this, "Operation Failed: " + ex.getMessage()); }
    }

    private void toggleUserStatus() {
        Object idVal = userTablePanel.getSelectedValue();
        if (idVal == null) { JOptionPane.showMessageDialog(this, "Select a user first."); return; }
        
        int id = (int) idVal;
        // Status is 5th column (index 4)
        int row = userTablePanel.getTable().getSelectedRow();
        String current = (String) userTablePanel.getTable().getValueAt(row, 4);
        String newStatus = "Active".equals(current) ? "Inactive" : "Active";
        
        try {
            DatabaseManager.getInstance().execute("UPDATE users SET status=? WHERE id=?", newStatus, id);
            loadUsers();
        } catch (SQLException ex) { JOptionPane.showMessageDialog(this, "Error: " + ex.getMessage()); }
    }

    // ── DB TAB ─────────────────────────────────────────────────────────────
    private JPanel buildDbTab() {
        JPanel p = new JPanel(new MigLayout("ins 40, wrap, gap 20, fill", "[center, grow]", "[] 20 []"));
        p.setOpaque(false);

        Map<String, String> cfg = Config.loadDbConfig();

        JPanel card = new JPanel(new MigLayout("ins 24, wrap 2, gap 16", "[right][left]", "[] 10 [] 10 [] 10 []"));
        card.setBackground(Color.WHITE);
        card.setBorder(UIHelper.createCardBorder());

        JLabel header = new JLabel("Database Connection Profile");
        header.setFont(UIHelper.FNT_BOLD);
        header.setForeground(UIHelper.PRIMARY);
        card.add(header, "span 2, center, gapbottom 10");

        String[][] fields = {
            {"Host Address:", cfg != null ? cfg.getOrDefault("host", "?") : "Missing"},
            {"Port:", cfg != null ? cfg.getOrDefault("port", "?") : "Missing"},
            {"Database Name:", cfg != null ? cfg.getOrDefault("database", "?") : "Missing"},
            {"Service User:", cfg != null ? cfg.getOrDefault("user", "?") : "Missing"},
        };
        for (String[] f : fields) {
            JLabel lbl = new JLabel(f[0]);
            lbl.setFont(UIHelper.FNT_BOLD);
            JLabel val = new JLabel(f[1]);
            val.setFont(UIHelper.FNT_MAIN);
            card.add(lbl); 
            card.add(val);
        }

        JButton reconfigBtn = UIHelper.makeButton("Change Database Configuration", UIHelper.DANGER);
        reconfigBtn.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(this,
                "This action will reset your connection settings and require a restart.\nContinue?",
                "Reconfigure Database", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (confirm == JOptionPane.YES_OPTION) {
                Config.clearDbConfig();
                DatabaseManager.getInstance().close();
                Window win = SwingUtilities.getWindowAncestor(this);
                if (win != null) win.dispose();
                new DBSetupWindow().setVisible(true);
            }
        });

        p.add(card, "width 400!");
        p.add(reconfigBtn, "center, gaptop 20");
        return p;
    }
}
