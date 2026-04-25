package com.bhspl.ui;

import com.bhspl.db.DatabaseManager;
import com.bhspl.util.UIHelper;

import javax.swing.*;
import java.awt.*;
import java.util.Map;

public class ShiftForm extends JDialog {
    private final Integer id;
    private final Runnable callback;
    private final DatabaseManager db = DatabaseManager.INSTANCE;
    private JTextField nameField, startField, endField, breakField, graceField, workHrsField, otField;
    private JComboBox<String> off1Combo, off2Combo, statusCombo;

    public ShiftForm(JFrame parent, Integer id, Runnable callback) {
        super(parent, id == null ? "Add Shift" : "Edit Shift #" + id, true);
        this.id = id; this.callback = callback;
        setSize(420, 480); UIHelper.centerWindow(this, 420, 480);
        buildUI();
        if (id != null) loadData();
        setVisible(true);
    }

    private void buildUI() {
        JPanel root = new JPanel(new BorderLayout()); root.setBackground(UIHelper.BG_CARD);
        JPanel hdr = new JPanel(); hdr.setBackground(UIHelper.PRIMARY); hdr.setPreferredSize(new Dimension(0, 50));
        JLabel title = new JLabel("🕒  Shift Master");
        title.setFont(new Font("Segoe UI", Font.BOLD, 14)); title.setForeground(Color.WHITE); hdr.add(title);
        root.add(hdr, BorderLayout.NORTH);

        JPanel form = new JPanel(new GridBagLayout()); form.setBackground(UIHelper.BG_CARD);
        form.setBorder(BorderFactory.createEmptyBorder(20, 30, 20, 30));
        GridBagConstraints gbc = new GridBagConstraints(); gbc.insets = new Insets(6, 6, 6, 6); gbc.anchor = GridBagConstraints.EAST;

        String[] labels = {"Shift Name *", "Start Time (HH:mm)", "End Time (HH:mm)", "Break (mins)", "Grace (mins)", "Off Day 1", "Off Day 2", "Std Work Hrs", "OT After (hrs)", "Status"};
        String[] days = {"", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"};
        
        for (int i = 0; i < labels.length; i++) {
            gbc.gridx = 0; gbc.gridy = i;
            form.add(new JLabel(labels[i] + ":"), gbc);
            gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0;
            if (i == 5) { off1Combo = new JComboBox<>(days); form.add(off1Combo, gbc); }
            else if (i == 6) { off2Combo = new JComboBox<>(days); form.add(off2Combo, gbc); }
            else if (i == 9) {
                statusCombo = new JComboBox<>(new String[]{"Active", "Inactive"});
                form.add(statusCombo, gbc);
            } else {
                JTextField txt = new JTextField(15);
                if (i == 0) nameField = txt; else if (i == 1) startField = txt;
                else if (i == 2) endField = txt; else if (i == 3) breakField = txt;
                else if (i == 4) graceField = txt; else if (i == 7) workHrsField = txt;
                else if (i == 8) otField = txt;
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
        if (name.isEmpty()) return;
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
            callback.run(); dispose();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Error: " + e.getMessage());
        }
    }
}
