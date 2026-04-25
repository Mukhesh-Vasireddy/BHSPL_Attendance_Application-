package com.bhspl.ui;

import com.bhspl.db.DatabaseManager;
import com.bhspl.util.UIHelper;

import javax.swing.*;
import java.awt.*;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * Edit an existing attendance record.
 */
public class EditAttendance extends JDialog {

    private final int recId;
    private final Runnable callback;
    private final DatabaseManager db = DatabaseManager.INSTANCE;
    private JTextField inTimeField, outTimeField, remarksField;
    private JComboBox<String> statusCombo;
    private final DateTimeFormatter tf = DateTimeFormatter.ofPattern("HH:mm");

    public EditAttendance(JFrame parent, int recId, Runnable callback) {
        super(parent, "Edit Attendance Record #" + recId, true);
        this.recId = recId;
        this.callback = callback;
        setSize(380, 400);
        UIHelper.centerWindow(this, 380, 400);
        buildUI();
        loadData();
        setVisible(true);
    }

    private void buildUI() {
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(UIHelper.BG_CARD);

        JPanel hdr = new JPanel();
        hdr.setBackground(UIHelper.PRIMARY);
        hdr.setPreferredSize(new Dimension(0, 50));
        JLabel title = new JLabel("Edit Record #" + recId);
        title.setFont(new Font("Segoe UI", Font.BOLD, 14));
        title.setForeground(Color.WHITE);
        hdr.add(title);
        root.add(hdr, BorderLayout.NORTH);

        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(UIHelper.BG_CARD);
        form.setBorder(BorderFactory.createEmptyBorder(25, 30, 25, 30));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.anchor = GridBagConstraints.EAST;

        String[] labels = {"In Time", "Out Time", "Status", "Remarks"};
        for (int i = 0; i < labels.length; i++) {
            gbc.gridx = 0; gbc.gridy = i; gbc.anchor = GridBagConstraints.EAST;
            JLabel lbl = new JLabel(labels[i] + ":");
            lbl.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            form.add(lbl, gbc);

            gbc.gridx = 1; gbc.gridy = i; gbc.anchor = GridBagConstraints.WEST; gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.weightx = 1.0;
            
            if (i == 2) {
                statusCombo = new JComboBox<>(new String[]{"Present", "Absent", "Half Day", "On Leave", "Holiday", "WeekOff"});
                statusCombo.setFont(new Font("Segoe UI", Font.PLAIN, 12));
                form.add(statusCombo, gbc);
            } else {
                JTextField txt = new JTextField(15);
                txt.setFont(new Font("Segoe UI", Font.PLAIN, 12));
                if (i == 0) inTimeField = txt;
                else if (i == 1) outTimeField = txt;
                else if (i == 3) remarksField = txt;
                form.add(txt, gbc);
            }
        }
        root.add(form, BorderLayout.CENTER);

        JPanel footer = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 10));
        footer.setBackground(UIHelper.BG_CARD);
        JButton saveBtn = UIHelper.makeButton("Update", UIHelper.BTN_SUCCESS);
        saveBtn.addActionListener(e -> save());
        footer.add(saveBtn);
        JButton cancelBtn = UIHelper.makeButton("Cancel", UIHelper.BTN_DANGER);
        cancelBtn.addActionListener(e -> dispose());
        footer.add(cancelBtn);
        root.add(footer, BorderLayout.SOUTH);

        setContentPane(root);
    }

    private void loadData() {
        try {
            Map<String, Object> r = db.queryOne("SELECT * FROM attendance WHERE id=?", recId);
            if (r == null) return;
            
            inTimeField.setText(fmtTime(r.get("in_time")));
            outTimeField.setText(fmtTime(r.get("out_time")));
            statusCombo.setSelectedItem(DatabaseManager.str(r, "status"));
            remarksField.setText(DatabaseManager.str(r, "remarks"));
        } catch (Exception e) {}
    }

    private String fmtTime(Object v) {
        if (v == null) return "";
        try {
            if (v instanceof java.sql.Timestamp) return tf.format(((java.sql.Timestamp) v).toLocalDateTime());
            if (v instanceof java.time.LocalDateTime) return tf.format((java.time.LocalDateTime) v);
            return v.toString().substring(11, 16);
        } catch (Exception e) { return ""; }
    }

    private void save() {
        try {
            // Fetch record along with shift break mins
            Map<String, Object> r = db.queryOne(
                "SELECT a.*, s.break_mins FROM attendance a " +
                "LEFT JOIN employees e ON a.emp_id = e.emp_id " +
                "LEFT JOIN shifts s ON e.shift = s.shift_name " +
                "WHERE a.id=?", recId);
            
            if (r == null) return;
            java.sql.Date punchDate = (java.sql.Date) r.get("punch_date");
            int breakMins = DatabaseManager.num(r, "break_mins");
            if (breakMins <= 0) breakMins = 30; // Fallback
            
            LocalDateTime inDt = parseTime(inTimeField.getText().trim(), punchDate.toLocalDate());
            LocalDateTime outDt = parseTime(outTimeField.getText().trim(), punchDate.toLocalDate());
            
            // Fetch shift info
            Map<String, Object> shiftInfo = db.fetchOne(
                "SELECT s.* FROM employees e JOIN shifts s ON e.shift=s.shift_name WHERE e.emp_id=?", r.get("emp_id"));

            // Use centralized calculator
            com.bhspl.util.AttendanceCalculator.Metrics m = com.bhspl.util.AttendanceCalculator.calculate(inDt, outDt, shiftInfo);

            db.execute("UPDATE attendance SET in_time=?, out_time=?, work_hours=?, overtime=?, late_mins=?, early_mins=?, status=?, remarks=? WHERE id=?",
                inDt, outDt, m.workHours, m.overtime, m.lateMins, m.earlyMins, statusCombo.getSelectedItem().toString(), remarksField.getText().trim(), recId);
            
            JOptionPane.showMessageDialog(this, "Attendance record updated.");
            if (callback != null) callback.run();
            dispose();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Error: " + e.getMessage());
        }
    }

    private LocalDateTime parseTime(String s, java.time.LocalDate d) {
        if (s == null || s.isEmpty()) return null;
        try { return LocalDateTime.of(d, LocalTime.parse(s.length() == 5 ? s + ":00" : s)); }
        catch (Exception e) { return null; }
    }
}
