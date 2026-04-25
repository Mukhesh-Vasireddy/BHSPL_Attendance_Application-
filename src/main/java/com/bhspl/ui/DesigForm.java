package com.bhspl.ui;

import com.bhspl.db.DatabaseManager;
import com.bhspl.util.UIHelper;

import javax.swing.*;
import java.awt.*;
import java.util.Map;

public class DesigForm extends JDialog {
    private final Integer id;
    private final Runnable callback;
    private final DatabaseManager db = DatabaseManager.INSTANCE;
    private JTextField nameField, levelField, descField;
    private JComboBox<String> statusCombo;

    public DesigForm(JFrame parent, Integer id, Runnable callback) {
        super(parent, id == null ? "Add Designation" : "Edit Designation #" + id, true);
        this.id = id; this.callback = callback;
        setSize(400, 320); UIHelper.centerWindow(this, 400, 320);
        buildUI();
        if (id != null) loadData();
        setVisible(true);
    }

    private void buildUI() {
        JPanel root = new JPanel(new BorderLayout()); root.setBackground(UIHelper.BG_CARD);
        JPanel hdr = new JPanel(); hdr.setBackground(UIHelper.PRIMARY); hdr.setPreferredSize(new Dimension(0, 50));
        JLabel title = new JLabel("🎓  Designation Master");
        title.setFont(new Font("Segoe UI", Font.BOLD, 14)); title.setForeground(Color.WHITE); hdr.add(title);
        root.add(hdr, BorderLayout.NORTH);

        JPanel form = new JPanel(new GridBagLayout()); form.setBackground(UIHelper.BG_CARD);
        form.setBorder(BorderFactory.createEmptyBorder(20, 35, 20, 35));
        GridBagConstraints gbc = new GridBagConstraints(); gbc.insets = new Insets(6, 6, 6, 6); gbc.anchor = GridBagConstraints.EAST;

        String[] labels = {"Designation *", "Level Order", "Description", "Status"};
        for (int i = 0; i < labels.length; i++) {
            gbc.gridx = 0; gbc.gridy = i;
            form.add(new JLabel(labels[i] + ":"), gbc);
            gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
            if (i == 3) {
                statusCombo = new JComboBox<>(new String[]{"Active", "Inactive"});
                form.add(statusCombo, gbc);
            } else {
                JTextField txt = new JTextField(15);
                if (i == 0) nameField = txt; else if (i == 1) levelField = txt;
                else if (i == 2) descField = txt;
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
            Map<String, Object> r = db.queryOne("SELECT * FROM designations WHERE id=?", id);
            if (r != null) {
                nameField.setText(DatabaseManager.str(r, "desig_name"));
                levelField.setText(DatabaseManager.str(r, "level_order"));
                descField.setText(DatabaseManager.str(r, "description"));
                statusCombo.setSelectedItem(DatabaseManager.str(r, "status"));
            }
        } catch (Exception e) {}
    }

    private void save() {
        String name = nameField.getText().trim();
        if (name.isEmpty()) return;
        try {
            int level = Integer.parseInt(levelField.getText().trim().isEmpty() ? "0" : levelField.getText().trim());
            if (id == null) db.execute("INSERT INTO designations (desig_name, level_order, description, status) VALUES (?,?,?,?)",
                name, level, descField.getText().trim(), statusCombo.getSelectedItem());
            else db.execute("UPDATE designations SET desig_name=?, level_order=?, description=?, status=? WHERE id=?",
                name, level, descField.getText().trim(), statusCombo.getSelectedItem(), id);
            callback.run(); dispose();
        } catch (Exception e) {}
    }
}
