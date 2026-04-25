package com.bhspl.ui;

import com.bhspl.db.DatabaseManager;
import com.bhspl.util.UIHelper;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import java.awt.*;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

public class AttendancePanel extends JPanel {

    private UIHelper.StyledTablePanel tablePanel;
    private JTextField dateField;
    private JTextField empSearchField;

    private static final String[] COLUMNS = {
        "Emp ID", "Name", "Date", "In Time", "Out Time", "Work Hrs", "OT Hrs", "Status", "Remarks"
    };

    public AttendancePanel() {
        setBackground(UIHelper.BG_MAIN);
        setLayout(new MigLayout("ins 24, wrap, gap 0, fill", "[grow]", "[] 16 [grow]"));
        buildUI();
        loadData();

        // Auto-refresh every 3 seconds
        Timer autoRefresh = new Timer(3000, e -> loadData());
        autoRefresh.start();
    }

    private void buildUI() {
        // Filter bar
        JPanel filterBar = new JPanel(new MigLayout("ins 0, gap 12", "[] 8 [] 24 [] 8 [] push [] 8 []"));
        filterBar.setOpaque(false);

        dateField = new JTextField(LocalDate.now().toString(), 10);
        dateField.setFont(new Font("Segoe UI", Font.PLAIN, 13));

        empSearchField = new JTextField(14);
        empSearchField.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        empSearchField.putClientProperty("JTextField.placeholderText", "Name or ID...");

        JButton loadBtn   = UIHelper.makeButton("Load Data", UIHelper.PRIMARY);
        JButton manualBtn = UIHelper.makeButton("Manual Punch", UIHelper.SUCCESS);
        JButton todayBtn  = UIHelper.makeButton("Today", new Color(0x334155));

        loadBtn.addActionListener(e -> loadData());
        empSearchField.addActionListener(e -> loadData());
        dateField.addActionListener(e -> loadData());
        manualBtn.addActionListener(e -> openManualPunch());
        todayBtn.addActionListener(e -> {
            dateField.setText(LocalDate.now().toString());
            loadData();
        });

        filterBar.add(new JLabel("Date:"));
        filterBar.add(dateField);
        filterBar.add(new JLabel("Employee:"));
        filterBar.add(empSearchField);
        filterBar.add(loadBtn);
        filterBar.add(manualBtn, "right");
        filterBar.add(todayBtn);

        add(filterBar, "growx");

        // Table Panel
        tablePanel = new UIHelper.StyledTablePanel(COLUMNS);
        tablePanel.setBorder(UIHelper.createCardBorder());
        add(tablePanel, "grow, push");
    }

    private void loadData() {
        String date = dateField.getText().trim();
        String search = empSearchField.getText().trim();
        if (date.isEmpty()) return;

        SwingWorker<List<Object[]>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<Object[]> doInBackground() throws Exception {
                String sql = "SELECT e.emp_id, e.emp_name, ? AS punch_date, " +
                                    "DATE_FORMAT(MIN(a.in_time),'%H:%i') AS in_time, " +
                                    "DATE_FORMAT(MAX(a.out_time),'%H:%i') AS out_time, " +
                                    "SUM(a.work_hours) as work_hours, SUM(a.overtime) as overtime, " +
                                    "COALESCE(" +
                                    "  (CASE " +
                                    "    WHEN SUM(CASE WHEN a.status='Half Day' THEN 1 ELSE 0 END) > 0 THEN 'Half Day' " +
                                    "    WHEN SUM(CASE WHEN a.status='Late' THEN 1 ELSE 0 END) > 0 THEN 'Late' " +
                                    "    WHEN SUM(CASE WHEN a.status='OD' OR a.status='On Duty' THEN 1 ELSE 0 END) > 0 THEN 'On Duty' " +
                                    "    WHEN SUM(CASE WHEN a.status='Present' THEN 1 ELSE 0 END) > 0 THEN 'Present' " +
                                    "    WHEN MAX(a.status) IS NOT NULL THEN MAX(a.status) " +
                                    "  END), " +
                                    "  (SELECT UPPER(leave_type) FROM leaves l WHERE l.emp_id = e.emp_id AND l.status='Approved' AND ? BETWEEN l.from_date AND l.to_date LIMIT 1), " +
                                    "  (SELECT UPPER(holiday_name) FROM holidays h WHERE h.holiday_date = ? LIMIT 1), " +
                                    "  (SELECT UPPER(CASE WHEN DAYNAME(?) = off_day1 THEN off_day1 ELSE off_day2 END) FROM weekly_offs w " +
                                    "   WHERE w.emp_id = e.emp_id AND (? >= w.effective_from) AND (w.effective_to IS NULL OR ? <= w.effective_to) " +
                                    "   AND (DAYNAME(?) = w.off_day1 OR DAYNAME(?) = w.off_day2) LIMIT 1), " +
                                    "  (CASE WHEN DAYNAME(?) = s.weekly_off1 THEN UPPER(s.weekly_off1) WHEN DAYNAME(?) = s.weekly_off2 THEN UPPER(s.weekly_off2) END), " +
                                    "  'Absent' " +
                                    ") as status, " +
                                    "GROUP_CONCAT(a.remarks SEPARATOR '; ') as remarks " +
                                    "FROM employees e " +
                                    "LEFT JOIN shifts s ON e.shift = s.shift_name " +
                                    "LEFT JOIN attendance a ON e.emp_id=a.emp_id AND a.punch_date=? " +
                                    "WHERE e.status='Active' AND (e.emp_id LIKE ? OR e.emp_name LIKE ?) " +
                                    "GROUP BY e.emp_id, e.emp_name " +
                                    "ORDER BY e.emp_name ASC";
                String like = "%" + search + "%";
                List<Map<String, Object>> data = DatabaseManager.getInstance().fetchAll(sql, date, date, date, date, date, date, date, date, date, date, date, like, like);
                
                List<Object[]> rows = new java.util.ArrayList<>();
                for (Map<String, Object> r : data) {
                    rows.add(new Object[]{
                        r.get("emp_id"), r.get("emp_name"), r.get("punch_date"),
                        r.get("in_time"), r.get("out_time"),
                        com.bhspl.util.AttendanceCalculator.formatDuration(DatabaseManager.dbl(r, "work_hours")),
                        com.bhspl.util.AttendanceCalculator.formatDuration(DatabaseManager.dbl(r, "overtime")),
                        r.get("status"), r.get("remarks")
                    });
                }
                return rows;
            }

            @Override
            protected void done() {
                try {
                    List<Object[]> newRows = get();
                    // Basic comparison to avoid unnecessary flickering
                    if (isDifferent(newRows)) {
                        tablePanel.clearRows();
                        for (Object[] row : newRows) {
                            tablePanel.addRow(row);
                        }
                    }
                } catch (Exception ignored) {}
            }
        };
        worker.execute();
    }

    private boolean isDifferent(List<Object[]> newRows) {
        if (newRows.size() != tablePanel.getModel().getRowCount()) return true;
        for (int i = 0; i < newRows.size(); i++) {
            Object[] current = new Object[tablePanel.getModel().getColumnCount()];
            for (int j = 0; j < current.length; j++) {
                Object v1 = newRows.get(i)[j];
                Object v2 = tablePanel.getModel().getValueAt(i, j);
                if (v1 == null && v2 == null) continue;
                if (v1 == null || v2 == null || !v1.toString().equals(v2.toString())) return true;
            }
        }
        return false;
    }

    private void openManualPunch() {
        JDialog dlg = new JDialog((JFrame) SwingUtilities.getWindowAncestor(this), "Manual Punch Entry", true);
        dlg.setSize(400, 360);
        UIHelper.centerWindow(dlg, 400, 360);

        JPanel p = new JPanel(new MigLayout("ins 24, wrap, gap 12", "[][grow]", "[]"));
        p.setBackground(Color.WHITE);

        JTextField empId  = new JTextField(12);
        JTextField date   = new JTextField(LocalDate.now().toString(), 12);
        JTextField inTime = new JTextField("09:00", 12);
        JTextField outTime= new JTextField("18:00", 12);
        JComboBox<String> status = new JComboBox<>(new String[]{"Present","Absent","Half Day","Late"});

        Object[][] rows = {{"Emp ID", empId}, {"Date", date}, {"In Time", inTime}, {"Out Time", outTime}, {"Status", status}};
        for (Object[] row : rows) {
            p.add(new JLabel((String) row[0]));
            p.add((Component) row[1], "growx");
        }

        JButton save = UIHelper.makeButton("Save Entry", UIHelper.SUCCESS);
        save.addActionListener(e -> {
            try {
                DatabaseManager db = DatabaseManager.getInstance();
                String eid = empId.getText().trim();
                String ds = date.getText().trim();
                
                // Fetch shift for calc
                Map<String, Object> emp = db.fetchOne("SELECT shift FROM employees WHERE emp_id=?", eid);
                String sname = emp != null ? DatabaseManager.str(emp, "shift") : "General";
                Map<String, Object> sInfo = db.fetchOne("SELECT * FROM shifts WHERE shift_name=?", sname);

                LocalDateTime inDt = LocalDateTime.of(LocalDate.parse(ds), LocalTime.parse(inTime.getText().trim()));
                LocalDateTime outDt = LocalDateTime.of(LocalDate.parse(ds), LocalTime.parse(outTime.getText().trim()));

                com.bhspl.util.AttendanceCalculator.Metrics m = com.bhspl.util.AttendanceCalculator.calculate(inDt, outDt, sInfo);

                db.execute(
                    "INSERT INTO attendance (emp_id, punch_date, in_time, out_time, work_hours, overtime, status, late_mins, punch_type) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'Manual') " +
                    "ON DUPLICATE KEY UPDATE in_time=VALUES(in_time), out_time=VALUES(out_time), " +
                    "work_hours=VALUES(work_hours), overtime=VALUES(overtime), status=VALUES(status), late_mins=VALUES(late_mins)",
                    eid, ds, inDt, outDt, m.workHours, m.overtime, status.getSelectedItem().toString(), m.lateMins
                );
                JOptionPane.showMessageDialog(dlg, "Punch saved successfully.");
                dlg.dispose();
                loadData();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(dlg, "Error: " + ex.getMessage());
            }
        });

        p.add(save, "skip, growx, gaptop 12");
        dlg.setContentPane(p);
        dlg.setVisible(true);
    }
}
