package com.bhspl.ui;

import com.bhspl.db.DatabaseManager;
import com.bhspl.util.UIHelper;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import java.awt.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * Modern Weekly Off Configuration Form.
 */
public class WeeklyOffForm extends JDialog {
    private final Integer id;
    private final Runnable callback;
    private final DatabaseManager db = DatabaseManager.INSTANCE;
    private JTextField empIdField, fromField, toField, remarksField;
    private JComboBox<String> off1Combo, off2Combo;

    public WeeklyOffForm(JFrame parent, Integer id, Runnable callback) {
        super(parent, id == null ? "Assign Weekly Off" : "Edit Weekly Off #" + id, true);
        this.id = id; this.callback = callback;
        
        setSize(480, 550);
        UIHelper.centerWindow(this, 480, 550);
        buildUI();
        if (id != null) loadData();
        setVisible(true);
    }

    private void buildUI() {
        JPanel root = new JPanel(new MigLayout("fill, ins 0, gap 0, wrap", "[grow]", "[] [grow] []"));
        root.setBackground(Color.WHITE);

        // Header
        UIHelper.GradientPanel hdr = new UIHelper.GradientPanel(UIHelper.PRIMARY, UIHelper.SECONDARY);
        hdr.setLayout(new MigLayout("ins 20", "[] 15 [grow]"));
        hdr.add(new JLabel(new com.formdev.flatlaf.extras.FlatSVGIcon("icons/holidays.svg", 32, 32)));
        
        JLabel title = new JLabel(id == null ? "Set Weekly Off Days" : "Update Off-Day Config");
        title.setFont(new Font("Segoe UI", Font.BOLD, 18));
        title.setForeground(Color.WHITE);
        hdr.add(title);
        root.add(hdr, "growx, h 80!");

        // Content
        JPanel form = new JPanel(new MigLayout("ins 30, wrap 2, gapy 12, fillx", "[shrink] 20 [grow, fill]"));
        form.setBackground(Color.WHITE);

        addField(form, "Employee ID *", empIdField = tf("e.g. 101"));
        
        String[] days = {"None", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"};
        addField(form, "Weekly Off 1", off1Combo = new JComboBox<>(days));
        addField(form, "Weekly Off 2", off2Combo = new JComboBox<>(days));
        
        addField(form, "Effective From", fromField = tf("dd-mm-yyyy"));
        fromField.setText(LocalDate.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy")));
        
        addField(form, "Effective To", toField = tf("dd-mm-yyyy"));
        addField(form, "Remarks", remarksField = tf("Optional notes"));

        root.add(new JScrollPane(form), "grow, push");

        // Footer
        JPanel footer = new JPanel(new MigLayout("ins 20, gap 12", "push [] []"));
        footer.setBackground(new Color(0xF8FAFC));
        
        JButton cancelBtn = UIHelper.makeButton("Cancel", new Color(0x64748B), "x.svg");
        cancelBtn.addActionListener(e -> dispose());
        
        JButton saveBtn = UIHelper.makeButton("Save Configuration", UIHelper.SUCCESS, "check.svg");
        saveBtn.addActionListener(e -> save());
        
        footer.add(cancelBtn);
        footer.add(saveBtn);
        root.add(footer, "growx");

        setContentPane(root);
    }

    private void addField(JPanel p, String label, JComponent field) {
        JLabel l = new JLabel(label);
        l.setFont(new Font("Segoe UI", Font.BOLD, 13));
        l.setForeground(new Color(0x475569));
        p.add(l);
        p.add(field);
    }

    private JTextField tf(String placeholder) {
        JTextField f = new JTextField();
        f.putClientProperty("JTextField.placeholderText", placeholder);
        f.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        return f;
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
        if (eid.isEmpty()) { UIHelper.showError(this, "Employee ID is required."); return; }
        try {
            String isoFrom = parseDate(fromField.getText().trim());
            String isoTo = parseDate(toField.getText().trim());
            Object[] data = {eid, off1Combo.getSelectedItem(), off2Combo.getSelectedItem(), isoFrom, isoTo, remarksField.getText().trim()};
            if (id == null) db.execute("INSERT INTO weekly_offs (emp_id, off_day1, off_day2, effective_from, effective_to, remarks) VALUES (?,?,?,?,?,?)", data);
            else db.execute("UPDATE weekly_offs SET emp_id=?, off_day1=?, off_day2=?, effective_from=?, effective_to=?, remarks=? WHERE id=?", eid, off1Combo.getSelectedItem(), off2Combo.getSelectedItem(), isoFrom, isoTo, remarksField.getText().trim(), id);
            
            UIHelper.showSuccess(this, "Weekly off configuration saved.");
            callback.run(); dispose();
        } catch (Exception e) {
            UIHelper.showError(this, "Error: " + e.getMessage());
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
