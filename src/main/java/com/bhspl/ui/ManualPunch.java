package com.bhspl.ui;

import com.bhspl.db.DatabaseManager;
import com.bhspl.util.UIHelper;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import java.awt.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * Manual attendance entry dialog.
 */
public class ManualPunch extends JDialog {

    private final Runnable callback;
    private JTextField empIdField, dateField, inTimeField, outTimeField, remarksField;
    private JComboBox<String> statusCombo, punchTypeCombo;
    private final DateTimeFormatter df = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    public ManualPunch(JFrame parent, Runnable callback) {
        super(parent, "Manual Attendance Entry", true);
        this.callback = callback;
        setSize(440, 520);
        UIHelper.centerWindow(this, 440, 520);
        
        getContentPane().setBackground(Color.WHITE);
        setLayout(new MigLayout("ins 24, wrap, gap 12", "[grow, fill]", "[] 16 [grow] 24 []"));

        buildUI();
        setVisible(true);
    }

    private void buildUI() {
        // Header
        JLabel title = new JLabel("Manual Attendance Entry");
        title.setFont(UIHelper.FNT_TITLE);
        title.setForeground(UIHelper.PRIMARY);
        add(title);

        // Form
        JPanel form = new JPanel(new MigLayout("ins 0, wrap 2, gap 16", "[right][grow, fill]"));
        form.setOpaque(false);

        empIdField = new JTextField(15);
        dateField = new JTextField(LocalDate.now().format(df), 15);
        inTimeField = new JTextField(15);
        outTimeField = new JTextField(15);
        remarksField = new JTextField(15);
        
        statusCombo = new JComboBox<>(new String[]{"Present", "Absent", "Half Day", "On Leave", "Holiday", "WeekOff"});
        punchTypeCombo = new JComboBox<>(new String[]{"Manual", "Device", "Override"});

        Object[][] rows = {
            {"Employee ID *", empIdField},
            {"Date (DD-MM-YYYY) *", dateField},
            {"In Time (HH:mm)", inTimeField},
            {"Out Time (HH:mm)", outTimeField},
            {"Attendance Status", statusCombo},
            {"Punch Type", punchTypeCombo},
            {"Remarks / Reason", remarksField}
        };

        for (Object[] row : rows) {
            JLabel lbl = new JLabel((String) row[0]);
            lbl.setFont(UIHelper.FNT_BOLD);
            form.add(lbl);
            
            Component comp = (Component) row[1];
            comp.setFont(UIHelper.FNT_MAIN);
            if (comp instanceof JTextField) ((JTextField) comp).setPreferredSize(new Dimension(0, 32));
            form.add(comp);
        }
        add(form, "grow");

        // Buttons
        JPanel footer = new JPanel(new MigLayout("ins 0, gap 12", "grow [] 8 []"));
        footer.setOpaque(false);

        JButton saveBtn = UIHelper.makeButton("Save Entry", UIHelper.SUCCESS);
        saveBtn.addActionListener(e -> save());
        
        JButton cancelBtn = UIHelper.makeButton("Cancel", UIHelper.DANGER);
        cancelBtn.addActionListener(e -> dispose());

        footer.add(saveBtn, "cell 1 0, h 36!");
        footer.add(cancelBtn, "cell 2 0, h 36!");
        add(footer, "growx");

        // Default button
        getRootPane().setDefaultButton(saveBtn);
    }

    private void save() {
        String eid = empIdField.getText().trim();
        String dateStr = dateField.getText().trim();
        
        if (eid.isEmpty() || dateStr.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Employee ID and Date are required fields.", "Input Error", JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            DatabaseManager db = DatabaseManager.getInstance();
            Map<String, Object> emp = db.fetchOne("SELECT * FROM employees WHERE emp_id=?", eid);
            if (emp == null) {
                JOptionPane.showMessageDialog(this, "Employee not found in database.", "Not Found", JOptionPane.ERROR_MESSAGE);
                return;
            }

            LocalDate punchDate = parseDate(dateStr);
            if (punchDate == null) {
                JOptionPane.showMessageDialog(this, "Invalid date format. Please use DD-MM-YYYY.", "Date Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            LocalDateTime inDt = parseTime(inTimeField.getText().trim(), punchDate);
            LocalDateTime outDt = parseTime(outTimeField.getText().trim(), punchDate);

            String shiftName = emp.get("shift") != null ? (String) emp.get("shift") : "General";
            Map<String, Object> shiftInfo = db.fetchOne("SELECT * FROM shifts WHERE shift_name=?", shiftName);

            // Use centralized calculator
            com.bhspl.util.AttendanceCalculator.Metrics m = com.bhspl.util.AttendanceCalculator.calculate(inDt, outDt, shiftInfo);
            
            double workHours = m.workHours;
            double overtime = m.overtime;
            int lateMins = m.lateMins;
            String status = statusCombo.getSelectedItem().toString();
            // Allow status override from calculator if it detected lateness, 
            // but respect if user manually selected Half Day/Absent etc.
            if ("Present".equals(status) && !"Present".equals(m.status)) {
                status = m.status;
            }

            db.execute("INSERT INTO attendance (emp_id, punch_date, in_time, out_time, work_hours, overtime, status, late_mins, punch_type, remarks) " +
                "VALUES (?,?,?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE in_time=?, out_time=?, work_hours=?, overtime=?, status=?, late_mins=?, punch_type=?, remarks=?",
                eid, punchDate.toString(), inDt, outDt, workHours, overtime, statusCombo.getSelectedItem().toString(), lateMins, punchTypeCombo.getSelectedItem().toString(), remarksField.getText().trim(),
                inDt, outDt, workHours, overtime, statusCombo.getSelectedItem().toString(), lateMins, punchTypeCombo.getSelectedItem().toString(), remarksField.getText().trim());

            JOptionPane.showMessageDialog(this, "Attendance successfully recorded.");
            if (callback != null) callback.run();
            dispose();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Database Error: " + e.getMessage());
        }
    }

    private LocalDate parseDate(String s) {
        try {
            if (s.matches("\\d{2}-\\d{2}-\\d{4}")) {
                String[] p = s.split("-");
                return LocalDate.of(Integer.parseInt(p[2]), Integer.parseInt(p[1]), Integer.parseInt(p[0]));
            }
            return LocalDate.parse(s);
        } catch (Exception e) {
            return null;
        }
    }

    private LocalDateTime parseTime(String s, LocalDate baseDate) {
        if (s == null || s.isEmpty()) return null;
        try {
            String t = s.contains(" ") ? s.split(" ")[0] : s;
            if (t.length() == 5) t += ":00";
            return LocalDateTime.of(baseDate, LocalTime.parse(t));
        } catch (Exception e) {
            return null;
        }
    }
}
