package com.bhspl.ui;

import com.bhspl.db.DatabaseManager;
import com.bhspl.service.SyncService;
import com.bhspl.util.CSVExporter;
import com.bhspl.util.ExcelExporter;
import com.bhspl.util.UIHelper;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;

public class RawPunchLogPanel extends JPanel {

    private final DatabaseManager db = DatabaseManager.INSTANCE;
    private UIHelper.StyledTablePanel tablePanel;
    private JTextField dateFromField, dateToField;
    private JComboBox<String> deptCombo, empCombo;

    private static final String[] COLUMNS = {
            "Emp ID", "Name", "Dept", "Shift", "Date", "IN Time", "OUT Time",
            "Punches", "Status"
    };

    public RawPunchLogPanel() {
        setBackground(UIHelper.BG_MAIN);
        setLayout(new MigLayout("ins 24, wrap, gap 0", "[grow]", "[] 16 [grow]"));
        buildUI();

        // Auto-refresh when the panel is shown (e.g., when navigation item is clicked)
        addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentShown(java.awt.event.ComponentEvent e) {
                loadData();
            }
        });

        loadData();
    }

    private void buildUI() {
        // Filter bar
        JPanel filterBar = new JPanel(new MigLayout("ins 0, gap 12, wrap", "[] 8 [grow] [] 8 [grow]"));
        filterBar.setOpaque(false);

        dateFromField = new JTextField(LocalDate.now().toString(), 10);
        dateToField = new JTextField(LocalDate.now().toString(), 10);

        deptCombo = new JComboBox<>(new String[] { "All" });
        empCombo = new JComboBox<>(new String[] { "All" });

        loadFilterData(); // Fetch depts from DB

        filterBar.add(new JLabel("From:"));
        filterBar.add(dateFromField, "growx");
        filterBar.add(new JLabel("To:"));
        filterBar.add(dateToField, "growx");
        filterBar.add(new JLabel("Dept:"));
        filterBar.add(deptCombo, "growx");
        filterBar.add(new JLabel("Employee:"));
        filterBar.add(empCombo, "growx");

        deptCombo.addActionListener(e -> updateEmployeeFilter());

        add(filterBar, "growx");

        // Responsive filter bar
        addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                int w = getWidth();
                String layout;
                String cols;
                if (w < 800) {
                    layout = "ins 0, gap 12, wrap 2";
                    cols = "[] 8 [grow]";
                } else {
                    layout = "ins 0, gap 12, wrap 4";
                    cols = "[] 8 [grow] [] 8 [grow]";
                }
                filterBar.setLayout(new MigLayout(layout, cols));
                filterBar.revalidate();
            }
        });

        // Action Info Hint
        JPanel hintPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        hintPanel.setBackground(new Color(0xFAF5FF)); // Light purple
        JLabel hintLabel = new JLabel(
                "<html><body style='color:#701A75; font-size:10px;'>Each row represents one punch session (IN/OUT pair) grouped per day.</body></html>");
        hintPanel.add(hintLabel);
        add(hintPanel, "growx");

        // Button Bar
        JPanel btnBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        btnBar.setOpaque(false);

        JButton refreshBtn = UIHelper.makeButton("Refresh Data", new Color(0x6366F1), "refresh.svg");
        JButton genBtn     = UIHelper.makeButton("Generate Reports", new Color(0x1E40AF), "reports.svg");
        JButton recalBtn   = UIHelper.makeButton("Recalculate Logs", new Color(0x991B1B), "sync.svg");
        JButton excelBtn   = UIHelper.makeButton("Export to Excel", new Color(0x15803D), "leaves_card.svg");
        JButton csvBtn     = UIHelper.makeButton("Export to CSV",   new Color(0x0F766E), "backup.svg");

        // Set consistent height
        Dimension btnDim = new Dimension(160, 36);
        for(JButton b : new JButton[]{refreshBtn, genBtn, recalBtn, excelBtn, csvBtn}) {
            b.setPreferredSize(btnDim);
        }
        
        refreshBtn.addActionListener(e -> loadData());
        genBtn.addActionListener(e -> loadData());
        recalBtn.addActionListener(e -> {
            String from = dateFromField.getText().trim();
            String to = dateToField.getText().trim();
            if (UIHelper.confirmYesNo(this,
                    "Recalculate Attendance",
                    "This will re-pull today's data and recalculate attendance for " + from + " to " + to
                            + ". Continue?")) {
                setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
                new SwingWorker<Void, Void>() {
                    @Override
                    protected Void doInBackground() throws Exception {
                        SyncService.forceUpdateToday();
                        DatabaseManager.getInstance().execute(
                                "UPDATE raw_logs SET synced=0 WHERE DATE(punch_time) BETWEEN ? AND ?", from, to);
                        SyncService.processRawLogs(null);
                        return null;
                    }

                    @Override
                    protected void done() {
                        setCursor(Cursor.getDefaultCursor());
                        loadData();
                        UIHelper.showSuccess(RawPunchLogPanel.this, "Recalculation complete.");
                    }
                }.execute();
            }
        });
        excelBtn.addActionListener(e -> exportToExcel());
        csvBtn.addActionListener(e -> exportToCSV());

        btnBar.add(refreshBtn);
        btnBar.add(genBtn);
        btnBar.add(recalBtn);
        btnBar.add(excelBtn);
        btnBar.add(csvBtn);
        add(btnBar, "growx");

        // Table Panel
        tablePanel = new UIHelper.StyledTablePanel(COLUMNS);
        tablePanel.setBorder(UIHelper.createCardBorder());
        // Emp ID, Name, Dept, Shift, Date, IN Time, OUT Time, Punches, Status
        int[] colW = {80, 180, 160, 120, 110, 100, 100, 80, 100};
        for (int i = 0; i < colW.length; i++) {
            tablePanel.getTable().getColumnModel().getColumn(i).setPreferredWidth(colW[i]);
        }
        add(tablePanel, "grow, push, wmin 0");
    }

    private void loadFilterData() {
        try {
            List<Map<String, Object>> depts = db.query("SELECT dept_name FROM departments ORDER BY dept_name");
            deptCombo.removeAllItems();
            deptCombo.addItem("All");
            for (Map<String, Object> d : depts) {
                deptCombo.addItem(DatabaseManager.str(d, "dept_name"));
            }
            updateEmployeeFilter();
        } catch (Exception ignored) {
        }
    }

    private void updateEmployeeFilter() {
        String dept = (String) deptCombo.getSelectedItem();
        try {
            StringBuilder sql = new StringBuilder("SELECT emp_name FROM employees WHERE status='Active'");
            List<Object> params = new ArrayList<>();
            if (dept != null && !"All".equals(dept)) {
                sql.append(" AND department = ?");
                params.add(dept);
            }
            sql.append(" ORDER BY emp_name");
            List<Map<String, Object>> emps = db.query(sql.toString(), params.toArray());

            empCombo.removeAllItems();
            empCombo.addItem("All");
            for (Map<String, Object> e : emps) {
                empCombo.addItem(DatabaseManager.str(e, "emp_name"));
            }
        } catch (Exception ignored) {
        }
    }

    private void loadData() {
        String from = dateFromField.getText().trim();
        String to = dateToField.getText().trim();
        String dept = deptCombo.getSelectedItem().toString();
        String empName = empCombo.getSelectedItem().toString();

        tablePanel.clearRows();

        SwingWorker<List<Object[]>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<Object[]> doInBackground() throws Exception {
                // 1. Fetch Raw Logs for range
                String logSql = "SELECT emp_id, punch_time, punch_type FROM raw_logs WHERE punch_time >= ? AND punch_time <= ? ORDER BY punch_time ASC";
                List<Map<String, Object>> logs = db.query(logSql, from + " 00:00:00", to + " 23:59:59");

                // Group logs by emp_id and date
                // Build enroll mapping for resolution
                List<Map<String, Object>> allEmps = db.query("SELECT emp_id, device_enroll_id FROM employees");
                Map<String, String> enrollMap = new HashMap<>();
                for (Map<String, Object> e : allEmps) {
                    String sid = DatabaseManager.str(e, "emp_id");
                    String eid = DatabaseManager.str(e, "device_enroll_id");
                    if (!eid.isEmpty())
                        enrollMap.put(eid, sid);
                }

                Map<String, List<LocalDateTime>> logMap = new HashMap<>(); // key: resolved_emp_id|YYYY-MM-DD
                Set<String> uniqueResolvedEmpIdsInLogs = new HashSet<>();
                for (Map<String, Object> log : logs) {
                    String deviceId = DatabaseManager.str(log, "emp_id");
                    // Resolve deviceId to system empId if possible
                    String resolvedId = enrollMap.getOrDefault(deviceId, deviceId);

                    LocalDateTime pt = (LocalDateTime) log.get("punch_time");
                    String key = resolvedId + "|" + pt.toLocalDate().toString();
                    logMap.computeIfAbsent(key, k -> new ArrayList<>()).add(pt);
                    uniqueResolvedEmpIdsInLogs.add(resolvedId);
                }

                // 2. Fetch Employees & Shifts for identified EmpIDs + All Active Employees
                StringBuilder empSql = new StringBuilder(
                        "SELECT e.emp_id, e.emp_name, e.department, s.shift_name, s.start_time, s.end_time " +
                                "FROM employees e LEFT JOIN shifts s ON e.shift = s.shift_name WHERE e.status='Active'");
                List<Object> empParams = new ArrayList<>();
                if (!"All".equals(dept)) {
                    empSql.append(" AND e.department = ?");
                    empParams.add(dept);
                }
                if (!"All".equals(empName)) {
                    empSql.append(" AND e.emp_name = ?");
                    empParams.add(empName);
                }
                empSql.append(" ORDER BY e.emp_name ASC");
                List<Map<String, Object>> employees = db.query(empSql.toString(), empParams.toArray());

                Map<String, Map<String, Object>> empInfoMap = new HashMap<>();
                for (Map<String, Object> e : employees) {
                    empInfoMap.put(DatabaseManager.str(e, "emp_id"), e);
                }

                List<Object[]> rows = new ArrayList<>();
                DateTimeFormatter tf = DateTimeFormatter.ofPattern("hh:mm a");

                // 3. Process each day
                LocalDate start = LocalDate.parse(from);
                LocalDate end = LocalDate.parse(to);

                for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
                    String dateStr = date.toString();

                    // Combine known employees (filtered) and any unmatched IDs found in logs for
                    // this date. Using LinkedHashSet to preserve the alphabetical order from DB.
                    Set<String> idsToProcess = new LinkedHashSet<>();
                    for (Map<String, Object> e : employees)
                        idsToProcess.add(DatabaseManager.str(e, "emp_id"));

                    for (String logResolvedId : uniqueResolvedEmpIdsInLogs) {
                        if (logMap.containsKey(logResolvedId + "|" + dateStr)) {
                            idsToProcess.add(logResolvedId);
                        }
                    }

                    for (String eid : idsToProcess) {
                        Map<String, Object> e = empInfoMap.get(eid);
                        String name = (e != null) ? DatabaseManager.str(e, "emp_name") : "[Unregistered: " + eid + "]";
                        String dpt = (e != null) ? DatabaseManager.str(e, "department") : "—";
                        String shift = (e != null) ? DatabaseManager.str(e, "shift_name") : "Default";


                        List<LocalDateTime> dayLogs = logMap.getOrDefault(eid + "|" + dateStr, Collections.emptyList());
                        Collections.sort(dayLogs);

                        if (dayLogs.isEmpty()) {
                            if (e != null) {
                                rows.add(new Object[] {
                                        eid, name, dpt, shift, dateStr,
                                        "—", "—", 0, "Absent"
                                });
                            }
                            continue;
                        }

                        // Process punches into multiple pairs (sessions)
                        int i = 0;
                        while (i < dayLogs.size()) {
                            LocalDateTime first = dayLogs.get(i);
                            LocalDateTime last = null;

                            // Find next potential OUT (at least 5 mins after IN)
                            int next = i + 1;
                            while (next < dayLogs.size()) {
                                if (java.time.Duration.between(first, dayLogs.get(next)).toMinutes() >= 5) {
                                    last = dayLogs.get(next);
                                    break;
                                }
                                next++;
                            }

                            String inTime = first.format(tf);
                            String outTime = (last != null) ? last.format(tf) : "Wait...";

                            // Calculations for workTime, lateIn, earlyOut removed as they are not used in this view

                            rows.add(new Object[] {
                                    eid, name, dpt, shift, dateStr,
                                    inTime, outTime, dayLogs.size(), "Present"
                            });

                            if (last != null)
                                i = next + 1;
                            else
                                i++;
                        }
                    }
                }
                return rows;
            }

            @Override
            protected void done() {
                try {
                    for (Object[] row : get()) {
                        tablePanel.addRow(row);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };
        worker.execute();
    }

    private void exportToCSV() {
        if (tablePanel.getTable().getRowCount() == 0) {
            UIHelper.showInfo(this, "No data to export.");
            return;
        }

        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File("raw_punch_logs_" + LocalDate.now() + ".csv"));
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
        try {
            CSVExporter.exportTable(tablePanel.getTable(), chooser.getSelectedFile().getAbsolutePath());
            UIHelper.showSuccess(this, "Export successful!");
        } catch (Exception e) {
            UIHelper.showError(this, "Export failed: " + e.getMessage());
        }
        }
    }

    private void exportToExcel() {
        if (tablePanel.getTable().getRowCount() == 0) {
            UIHelper.showInfo(this, "No data to export.");
            return;
        }

        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File("raw_punch_logs_" + LocalDate.now() + ".xlsx"));
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            String path = chooser.getSelectedFile().getAbsolutePath();
            if (!path.toLowerCase().endsWith(".xlsx"))
                path += ".xlsx";

            try {
                ExcelExporter.exportTable(tablePanel.getTable(), path);
                UIHelper.showSuccess(this, "Data exported successfully to:\n" + path);
            } catch (Exception e) {
                UIHelper.showError(this, "Error exporting data: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

}
