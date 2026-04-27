package com.bhspl.ui;

import com.bhspl.db.DatabaseManager;
import com.bhspl.util.UIHelper;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import java.awt.*;
import java.util.Map;

/**
 * Modern Shift Master Form.
 */
public class ShiftForm extends JDialog {
    private final Integer id;
    private final Runnable callback;
    private final DatabaseManager db = DatabaseManager.INSTANCE;
    private JTextField nameField, startField, endField, breakField, graceField, workHrsField, otField;
    private JComboBox<String> off1Combo, off2Combo, statusCombo;

    public ShiftForm(JFrame parent, Integer id, Runnable callback) {
        super(parent, id == null ? "New Shift" : "Edit Shift #" + id, true);
        this.id = id; this.callback = callback;
        
        setSize(480, 620);
        UIHelper.centerWindow(this, 480, 620);
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
        hdr.add(new JLabel(new com.formdev.flatlaf.extras.FlatSVGIcon("icons/shifts.svg", 32, 32)));
        
        JLabel title = new JLabel(id == null ? "Create New Shift" : "Modify Shift Settings");
        title.setFont(new Font("Segoe UI", Font.BOLD, 18));
        title.setForeground(Color.WHITE);
        hdr.add(title);
        root.add(hdr, "growx, h 80!");

        // Content
        JPanel form = new JPanel(new MigLayout("ins 30, wrap 2, gapy 12, fillx", "[shrink] 20 [grow, fill]"));
        form.setBackground(Color.WHITE);

        addField(form, "Shift Name *", nameField = tf("e.g. Day Shift"));
        addField(form, "Start Time", startField = tf("HH:mm:ss"));
        addField(form, "End Time", endField = tf("HH:mm:ss"));
        addField(form, "Break (Mins)", breakField = tf("30"));
        addField(form, "Grace (Mins)", graceField = tf("15"));
        
        String[] days = {"None", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"};
        addField(form, "Weekly Off 1", off1Combo = new JComboBox<>(days));
        addField(form, "Weekly Off 2", off2Combo = new JComboBox<>(days));
        
        addField(form, "Standard Hours", workHrsField = tf("8.5"));
        addField(form, "OT Starts After", otField = tf("9.0"));
        addField(form, "Status", statusCombo = new JComboBox<>(new String[]{"Active", "Inactive"}));

        root.add(new JScrollPane(form), "grow, push");

        // Footer
        JPanel footer = new JPanel(new MigLayout("ins 20, gap 12", "push [] []"));
        footer.setBackground(new Color(0xF8FAFC));
        
        JButton cancelBtn = UIHelper.makeButton("Cancel", new Color(0x64748B), "x.svg");
        cancelBtn.addActionListener(e -> dispose());
        
        JButton saveBtn = UIHelper.makeButton("Save Shift", UIHelper.SUCCESS, "check.svg");
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
            Map<String, Object> r = db.queryOne("SELECT * FROM shifts WHERE id=?", id);
            if (r != null) {
                nameField.setText(DatabaseManager.str(r, "shift_name"));
                startField.setText(DatabaseManager.str(r, "start_time"));
                endField.setText(DatabaseManager.str(r, "end_time"));
                breakField.setText(DatabaseManager.str(r, "break_mins"));
                graceField.setText(DatabaseManager.str(r, "grace_mins"));
                off1Combo.setSelectedItem(DatabaseManager.str(r, "weekly_off1"));
                off2Combo.setSelectedItem(DatabaseManager.str(r, "weekly_off2"));
                workHrsField.setText(DatabaseManager.str(r, "work_hours"));
                otField.setText(DatabaseManager.str(r, "overtime_after"));
                statusCombo.setSelectedItem(DatabaseManager.str(r, "status"));
            }
        } catch (Exception e) {}
    }

    private void save() {
        String name = nameField.getText().trim();
        if (name.isEmpty()) { UIHelper.showError(this, "Shift Name is required."); return; }
        try {
            double wh = Double.parseDouble(workHrsField.getText().trim().isEmpty() ? "8.5" : workHrsField.getText().trim());
            double ot = Double.parseDouble(otField.getText().trim().isEmpty() ? "9.0" : otField.getText().trim());
            int brk = Integer.parseInt(breakField.getText().trim().isEmpty() ? "30" : breakField.getText().trim());
            int grc = Integer.parseInt(graceField.getText().trim().isEmpty() ? "15" : graceField.getText().trim());
            
            Object[] data = {name, startField.getText().trim(), endField.getText().trim(), brk, grc,
                off1Combo.getSelectedItem(), off2Combo.getSelectedItem(), wh, ot, statusCombo.getSelectedItem()};
                
            if (id == null) {
                db.execute("INSERT INTO shifts (shift_name, start_time, end_time, break_mins, grace_mins, weekly_off1, weekly_off2, work_hours, overtime_after, status) VALUES (?,?,?,?,?,?,?,?,?,?)", data);
            } else {
                Object[] updateData = new Object[data.length + 1];
                System.arraycopy(data, 0, updateData, 0, data.length);
                updateData[data.length] = id;
                db.execute("UPDATE shifts SET shift_name=?, start_time=?, end_time=?, break_mins=?, grace_mins=?, weekly_off1=?, weekly_off2=?, work_hours=?, overtime_after=?, status=? WHERE id=?", updateData);
            }
            UIHelper.showSuccess(this, "Shift saved successfully.");
            callback.run(); dispose();
        } catch (Exception e) {
            UIHelper.showError(this, "Error: " + e.getMessage());
        }
    }
}
