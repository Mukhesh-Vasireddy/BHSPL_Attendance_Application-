package com.bhspl.ui;

import com.bhspl.db.DatabaseManager;
import com.bhspl.util.UIHelper;

import javax.swing.*;
import java.awt.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;

public class WeeklyOffForm extends JDialog {
    private final Integer id;
    private final Runnable callback;
    private final DatabaseManager db = DatabaseManager.INSTANCE;
    private JTextField empIdField, fromField, toField, remarksField;
    private JComboBox<String> off1Combo, off2Combo;

    public WeeklyOffForm(JFrame parent, Integer id, Runnable callback) {
        super(parent, id == null ? "Set Weekly Off" : "Edit Weekly Off #" + id, true);
        this.id = id; this.callback = callback;
        setSize(420, 400); UIHelper.centerWindow(this, 420, 400);
        buildUI();
        if (id != null) loadData();
        setVisible(true);
    }

    private void buildUI() {
        JPanel root = new JPanel(new BorderLayout()); root.setBackground(UIHelper.BG_CARD);
        JPanel hdr = new JPanel(); hdr.setBackground(UIHelper.PRIMARY); hdr.setPreferredSize(new Dimension(0, 50));
        JLabel title = new JLabel("📅  Weekly Off Configuration");
        title.setFont(new Font("Segoe UI", Font.BOLD, 14)); title.setForeground(Color.WHITE); hdr.add(title);
        root.add(hdr, BorderLayout.NORTH);

        JPanel form = new JPanel(new GridBagLayout()); form.setBackground(UIHelper.BG_CARD);
        form.setBorder(BorderFactory.createEmptyBorder(20, 35, 20, 35));
        GridBagConstraints gbc = new GridBagConstraints(); gbc.insets = new Insets(6, 6, 6, 6); gbc.anchor = GridBagConstraints.EAST;

        String[] labels = {"Emp ID *", "Off Day 1", "Off Day 2", "Effective From", "Effective To", "Remarks"};
        String[] days = {"", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"};
        
        for (int i = 0; i < labels.length; i++) {
            gbc.gridx = 0; gbc.gridy = i;
            form.add(new JLabel(labels[i] + ":"), gbc);
            gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
            if (i == 1) { off1Combo = new JComboBox<>(days); form.add(off1Combo, gbc); }
            else if (i == 2) { off2Combo = new JComboBox<>(days); form.add(off2Combo, gbc); }
            else {
                JTextField txt = new JTextField(15);
                if (i == 0) empIdField = txt;
                else if (i == 3) { fromField = txt; txt.setText(LocalDate.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy"))); }
                else if (i == 4) toField = txt;
                else if (i == 5) remarksField = txt;
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
            Map<String, Object> r = db.queryOne("SELECT * FROM weekly_offs WHERE id=?", id);
            if (r != null) {
                empIdField.setText(DatabaseManager.str(r, "emp_id"));
                empIdField.setEditable(false);
                off1Combo.setSelectedItem(DatabaseManager.str(r, "off_day1"));
                off2Combo.setSelectedItem(DatabaseManager.str(r, "off_day2"));
                fromField.setText(fmtDate(r.get("effective_from")));
                toField.setText(fmtDate(r.get("effective_to")));
                remarksField.setText(DatabaseManager.str(r, "remarks"));
            }
        } catch (Exception e) {}
    }

    private String fmtDate(Object v) {
        if (v == null) return "";
        if (v instanceof java.sql.Date) return DateTimeFormatter.ofPattern("dd-MM-yyyy").format(((java.sql.Date) v).toLocalDate());
        return v.toString();
    }

    private void save() {
        String eid = empIdField.getText().trim();
        if (eid.isEmpty()) return;
        try {
            String isoFrom = parseDate(fromField.getText().trim());
            String isoTo = parseDate(toField.getText().trim());
            Object[] data = {eid, off1Combo.getSelectedItem(), off2Combo.getSelectedItem(), isoFrom, isoTo, remarksField.getText().trim()};
            if (id == null) db.execute("INSERT INTO weekly_offs (emp_id, off_day1, off_day2, effective_from, effective_to, remarks) VALUES (?,?,?,?,?,?)", data);
            else db.execute("UPDATE weekly_offs SET emp_id=?, off_day1=?, off_day2=?, effective_from=?, effective_to=?, remarks=? WHERE id=?", eid, off1Combo.getSelectedItem(), off2Combo.getSelectedItem(), isoFrom, isoTo, remarksField.getText().trim(), id);
            callback.run(); dispose();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Error: " + e.getMessage());
        }
    }

    private String parseDate(String s) {
        if (s.isEmpty()) return null;
        if (s.matches("\\d{2}-\\d{2}-\\d{4}")) {
            String[] p = s.split("-"); return p[2] + "-" + p[1] + "-" + p[0];
        }
        return s;
    }
}
