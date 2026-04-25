package com.bhspl.ui;

import com.bhspl.db.DatabaseManager;
import com.bhspl.util.UIHelper;

import javax.swing.*;
import java.awt.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;

public class ODForm extends JDialog {
    private final Integer id;
    private final Runnable callback;
    private final DatabaseManager db = DatabaseManager.INSTANCE;
    private JTextField empIdField, fromField, toField, daysField, reasonField;
    private JComboBox<String> typeCombo, statusCombo;

    public ODForm(JFrame parent, Integer id, Runnable callback) {
        super(parent, id == null ? "OD Application" : "Edit OD #" + id, true);
        this.id = id; this.callback = callback;
        setSize(400, 420); UIHelper.centerWindow(this, 400, 420);
        buildUI();
        if (id != null) loadData();
        setVisible(true);
    }

    private void buildUI() {
        JPanel root = new JPanel(new BorderLayout()); root.setBackground(UIHelper.BG_CARD);
        JPanel hdr = new JPanel(); hdr.setBackground(UIHelper.PRIMARY); hdr.setPreferredSize(new Dimension(0, 50));
        JLabel title = new JLabel("💼  On Duty (OD) Application");
        title.setFont(new Font("Segoe UI", Font.BOLD, 14)); title.setForeground(Color.WHITE); hdr.add(title);
        root.add(hdr, BorderLayout.NORTH);

        JPanel form = new JPanel(new GridBagLayout()); form.setBackground(UIHelper.BG_CARD);
        form.setBorder(BorderFactory.createEmptyBorder(20, 35, 20, 35));
        GridBagConstraints gbc = new GridBagConstraints(); gbc.insets = new Insets(6, 6, 6, 6); gbc.anchor = GridBagConstraints.EAST;

        String[] labels = {"Emp ID *", "OD Type", "From Date", "To Date", "Total Days", "Reason", "Status"};
        for (int i = 0; i < labels.length; i++) {
            gbc.gridx = 0; gbc.gridy = i;
            form.add(new JLabel(labels[i] + ":"), gbc);
            gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
            if (i == 1) { typeCombo = new JComboBox<>(new String[]{"Official Trip", "Client Visit", "Field Work", "Training", "Emergency"}); form.add(typeCombo, gbc); }
            else if (i == 6) {
                statusCombo = new JComboBox<>(new String[]{"Pending", "Approved", "Rejected"});
                form.add(statusCombo, gbc);
            } else {
                JTextField txt = new JTextField(15);
                if (i == 0) empIdField = txt;
                else if (i == 2) { fromField = txt; txt.setText(LocalDate.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy"))); }
                else if (i == 3) toField = txt;
                else if (i == 4) { daysField = txt; txt.setText("1"); }
                else if (i == 5) reasonField = txt;
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
            Map<String, Object> r = db.queryOne("SELECT * FROM od_requests WHERE id=?", id);
            if (r != null) {
                empIdField.setText(DatabaseManager.str(r, "emp_id"));
                typeCombo.setSelectedItem(DatabaseManager.str(r, "od_type"));
                fromField.setText(fmtDate(r.get("od_from")));
                toField.setText(fmtDate(r.get("od_to")));
                daysField.setText(DatabaseManager.str(r, "od_days"));
                reasonField.setText(DatabaseManager.str(r, "reason"));
                statusCombo.setSelectedItem(DatabaseManager.str(r, "status"));
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
            double days = Double.parseDouble(daysField.getText().trim().isEmpty() ? "1" : daysField.getText().trim());
            Object[] data = {eid, typeCombo.getSelectedItem(), isoFrom, isoTo, days, reasonField.getText().trim(), statusCombo.getSelectedItem()};
            
            if (id == null) db.execute("INSERT INTO od_requests (emp_id, od_type, od_from, od_to, od_days, reason, status, applied_on) VALUES (?,?,?,?,?,?,?,NOW())", data);
            else db.execute("UPDATE od_requests SET emp_id=?, od_type=?, od_from=?, od_to=?, od_days=?, reason=?, status=? WHERE id=?", eid, typeCombo.getSelectedItem(), isoFrom, isoTo, days, reasonField.getText().trim(), statusCombo.getSelectedItem(), id);
            
            if ("Approved".equals(statusCombo.getSelectedItem())) {
                LocalDate start = LocalDate.parse(isoFrom);
                LocalDate end = LocalDate.parse(isoTo);
                
                // Fetch shift for work_hours
                Map<String, Object> emp = db.fetchOne(
                    "SELECT s.work_hours FROM employees e JOIN shifts s ON e.shift=s.shift_name WHERE e.emp_id=?", eid);
                double wh = emp != null ? DatabaseManager.dbl(emp, "work_hours") : 8.0;

                for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
                    db.execute("INSERT INTO attendance (emp_id, punch_date, status, work_hours, remarks) " +
                        "VALUES (?,?,?,?,'OD Approved') ON DUPLICATE KEY UPDATE status=?, work_hours=?, remarks='OD Approved'", 
                        eid, d.toString(), "OD", wh, "OD", wh);
                }
            }
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
