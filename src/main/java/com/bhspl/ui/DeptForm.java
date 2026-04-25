package com.bhspl.ui;

import com.bhspl.db.DatabaseManager;
import com.bhspl.util.UIHelper;

import javax.swing.*;
import java.awt.*;
import java.util.Map;

/**
 * Master forms for simple table CRUD (Departments, Designations, Users).
 * Grouped into one file for convenience or separate if needed.
 * But for this port, I'll create them as separate files to keep UIHelper logic clean.
 */
public class DeptForm extends JDialog {
    private final Integer id;
    private final Runnable callback;
    private final DatabaseManager db = DatabaseManager.INSTANCE;
    private JTextField nameField, codeField, headField, descField;
    private JComboBox<String> statusCombo;

    public DeptForm(JFrame parent, Integer id, Runnable callback) {
        super(parent, id == null ? "Add Department" : "Edit Department #" + id, true);
        this.id = id; this.callback = callback;
        setSize(400, 350); UIHelper.centerWindow(this, 400, 350);
        buildUI();
        if (id != null) loadData();
        setVisible(true);
    }

    private void buildUI() {
        JPanel root = new JPanel(new BorderLayout()); root.setBackground(UIHelper.BG_CARD);
        JPanel hdr = new JPanel(); hdr.setBackground(UIHelper.PRIMARY); hdr.setPreferredSize(new Dimension(0, 50));
        JLabel title = new JLabel("🏢  Department Master");
        title.setFont(new Font("Segoe UI", Font.BOLD, 14)); title.setForeground(Color.WHITE); hdr.add(title);
        root.add(hdr, BorderLayout.NORTH);

        JPanel form = new JPanel(new GridBagLayout()); form.setBackground(UIHelper.BG_CARD);
        form.setBorder(BorderFactory.createEmptyBorder(20, 35, 20, 35));
        GridBagConstraints gbc = new GridBagConstraints(); gbc.insets = new Insets(6, 6, 6, 6); gbc.anchor = GridBagConstraints.EAST;

        String[] labels = {"Dept Name *", "Dept Code", "Dept Head", "Description", "Status"};
        for (int i = 0; i < labels.length; i++) {
            gbc.gridx = 0; gbc.gridy = i;
            form.add(new JLabel(labels[i] + ":"), gbc);
            gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
            if (i == 4) {
                statusCombo = new JComboBox<>(new String[]{"Active", "Inactive"});
                form.add(statusCombo, gbc);
            } else {
                JTextField txt = new JTextField(15);
                if (i == 0) nameField = txt; else if (i == 1) codeField = txt;
                else if (i == 2) headField = txt; else if (i == 3) descField = txt;
                form.add(txt, gbc);
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
            Map<String, Object> r = db.queryOne("SELECT * FROM departments WHERE id=?", id);
            if (r != null) {
                nameField.setText(DatabaseManager.str(r, "dept_name"));
                codeField.setText(DatabaseManager.str(r, "dept_code"));
                headField.setText(DatabaseManager.str(r, "head_name"));
                descField.setText(DatabaseManager.str(r, "description"));
                statusCombo.setSelectedItem(DatabaseManager.str(r, "status"));
            }
        } catch (Exception e) {}
    }

    private void save() {
        String name = nameField.getText().trim();
        if (name.isEmpty()) return;
        try {
            if (id == null) db.execute("INSERT INTO departments (dept_name, dept_code, head_name, description, status) VALUES (?,?,?,?,?)",
                name, codeField.getText().trim(), headField.getText().trim(), descField.getText().trim(), statusCombo.getSelectedItem());
            else db.execute("UPDATE departments SET dept_name=?, dept_code=?, head_name=?, description=?, status=? WHERE id=?",
                name, codeField.getText().trim(), headField.getText().trim(), descField.getText().trim(), statusCombo.getSelectedItem(), id);
            callback.run(); dispose();
        } catch (Exception e) {}
    }
}
