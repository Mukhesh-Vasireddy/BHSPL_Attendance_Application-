package com.bhspl.ui;

import com.bhspl.db.DatabaseManager;
import com.bhspl.service.SyncService;
import com.bhspl.util.UIHelper;
import net.miginfocom.swing.MigLayout;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.io.File;
import java.io.FileOutputStream;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Overhauled Reports Panel matching the requested premium dashboard design.
 * Features: Tabbed navigation, categorical filters, status legend, and detailed
 * 14-column report.
 */
public class ReportsPanel extends JPanel {

    private UIHelper.StyledTablePanel tablePanel;
    private JTextField dateField;
    private JComboBox<String> deptCombo, empCombo, statusCombo, monthCombo, reportTypeCombo;
    private JComboBox<Integer> yearCombo;
    private JLabel summaryLabel, dateLabel;
    private String activeTab = "Daily";
    private static final DateTimeFormatter DISPLAY_FMT = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    private boolean showHeader = true;

    public ReportsPanel() {
        this(true);
    }

    public ReportsPanel(boolean showHeader) {
        this.showHeader = showHeader;
        setBackground(UIHelper.BG_MAIN);
        setLayout(new MigLayout("ins 16, wrap, gap 0, fill", "[grow]", "[] 0 [] 0 [] 12 [grow] 8 []"));
        buildUI();
        loadMetadata();
    }

    public void setActiveTab(String tabName) {
        this.activeTab = tabName;
        this.removeAll();
        buildUI();
        this.revalidate();
        this.repaint();
    }

    private void buildUI() {
        // 1. Header with Tabs
        if (showHeader) {
            JPanel headerPanel = new JPanel(new MigLayout("ins 0, gap 0", "[] [] [] [] push", "[]"));
            headerPanel.setOpaque(false);

            headerPanel.add(createTabButton("Daily"));
            headerPanel.add(createTabButton("Monthly"));
            headerPanel.add(createTabButton("Leave Report"));
            add(headerPanel, "growx");
        }

        // 2. Filter Bar
        JPanel filterBar = new JPanel(new MigLayout("ins 8, gap 12", "[] 4 [] 16 [] 4 [] 16 [] 4 [] 16 [] 4 []", "[]"));
        filterBar.setBackground(Color.WHITE);
        filterBar.setBorder(BorderFactory.createMatteBorder(1, 0, 1, 0, UIHelper.BORDER));

        dateField = new JTextField(LocalDate.now().format(DISPLAY_FMT), 10);
        deptCombo = new JComboBox<>(new String[] { "All" });
        empCombo = new JComboBox<>(new String[] { "All" });
        statusCombo = new JComboBox<>(new String[] { "All", "Present", "Absent", "Half Day", "On Duty", "Leave" });
        
        String[] months = {"Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};
        monthCombo = new JComboBox<>(months);
        monthCombo.setSelectedIndex(LocalDate.now().getMonthValue() - 1);
        yearCombo = new JComboBox<>(new Integer[]{2024, 2025, 2026});
        yearCombo.setSelectedItem(LocalDate.now().getYear());

        if ("Daily".equals(activeTab) || "Leave Report".equals(activeTab)) {
            filterBar.add(new JLabel("Date:"));
            filterBar.add(dateField);
        } else {
            filterBar.add(new JLabel("Month:"));
            filterBar.add(monthCombo);
            filterBar.add(yearCombo);
            
            if ("Monthly".equals(activeTab)) {
                filterBar.add(new JLabel("Type:"));
                reportTypeCombo = new JComboBox<>(new String[]{"Working Hours Summary", "Attendance Summary (P/A)"});
                filterBar.add(reportTypeCombo, "w 180!");
            }
        }
        filterBar.add(new JLabel("Dept:"));
        filterBar.add(deptCombo, "w 150!");
        filterBar.add(new JLabel("Employee:"));
        filterBar.add(empCombo, "w 180!");
        if (!"Monthly".equals(activeTab)) {
            filterBar.add(new JLabel("Status:"));
            filterBar.add(statusCombo, "w 100!");
        }

        add(filterBar, "growx");

        // 3. Middle Bar & Action Buttons
        JPanel middleBar = new JPanel(new MigLayout("ins 8 0, gap 0", "[grow] push []", "[]"));
        middleBar.setOpaque(false);

        if ("Leave Report".equals(activeTab)) {
            middleBar.add(new JLabel("<html><body style='font-size:10px; color:#64748b;'>Leave application records.</body></html>"));
        } else if ("Monthly".equals(activeTab)) {
            middleBar.add(new JLabel("<html><body style='font-size:10px; color:#64748b;'>Monthly attendance matrix. Choose report type in filters.</body></html>"));
        } else {
            middleBar.add(new JLabel("<html><body style='font-size:10px; color:#64748b;'>Daily attendance report for employees.</body></html>"));
        }

        JPanel actionPanel = new JPanel(new MigLayout("ins 0, gap 8", "[] [] []", "[]"));
        actionPanel.setOpaque(false);

        JButton genBtn = UIHelper.makeButton("Generate", UIHelper.PRIMARY);
        JButton excelBtn = UIHelper.makeButton("Export Excel", UIHelper.SUCCESS);
        JButton csvBtn = UIHelper.makeButton("Export CSV", new Color(0x0d9488));

        genBtn.setIcon(UIManager.getIcon("FileView.fileIcon"));
        genBtn.addActionListener(e -> generateReport());
        excelBtn.addActionListener(e -> exportToExcel());

        actionPanel.add(genBtn);
        actionPanel.add(excelBtn);
        actionPanel.add(csvBtn);
        middleBar.add(actionPanel);

        add(middleBar, "growx");

        // 4. Data Table
        String[] columns;
        if ("Leave Report".equals(activeTab)) {
            columns = new String[]{"Emp ID", "Name", "Leave Type", "From", "To", "Days", "Status", "Applied On"};
        } else if ("Monthly".equals(activeTab)) {
            columns = new String[]{"Emp ID", "Name", "Designation", "Attendance Matrix"};
        } else {
            columns = new String[]{
                    "Emp ID", "Name", "Dept", "Shift", "Sched In", "In Time", "Out Time",
                    "Sched Out", "Work Hrs", "OT Hrs", "Late IN", "Early OUT", "Status", "Remarks"
            };
        }
        
        tablePanel = new UIHelper.StyledTablePanel(columns);
        tablePanel.getTable().setRowHeight(30);
        styleTable();
        add(tablePanel, "grow, push");

        // 5. Footer Summary
        summaryLabel = new JLabel("09-04-2026 | Total: 0 | P:0 A:0 HD:0 OD:0 CL:0 WO:0 PH:0 | Late:0 OT:0");
        summaryLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));
        summaryLabel.setForeground(UIHelper.TEXT_DARK);
        add(summaryLabel, "growx, gaptop 8");
    }

    private JButton createTabButton(String text) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 14));
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setContentAreaFilled(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        boolean isActive = text.equals(activeTab);
        btn.setForeground(isActive ? UIHelper.PRIMARY : UIHelper.TEXT_LIGHT);

        if (isActive) {
            btn.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 3, 0, UIHelper.PRIMARY),
                    BorderFactory.createEmptyBorder(10, 20, 7, 20)));
        } else {
            btn.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));
        }

        btn.addActionListener(e -> {
            activeTab = text;
            this.removeAll();
            buildUI();
            this.revalidate();
            this.repaint();
        });

        return btn;
    }

    private JPanel createLegendBadge(String code, Color bg, String tooltip) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        p.setOpaque(false);

        JLabel badge = new JLabel(code, SwingConstants.CENTER);
        badge.setOpaque(true);
        badge.setBackground(bg);
        badge.setForeground(Color.WHITE);
        badge.setFont(new Font("Segoe UI", Font.BOLD, 10));
        badge.setPreferredSize(new Dimension(24, 20));

        JLabel label = new JLabel(tooltip);
        label.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        label.setForeground(new Color(0x64748b));

        p.add(badge);
        p.add(Box.createHorizontalStrut(4));
        p.add(label);
        return p;
    }

    private void styleTable() {
        JTable table = tablePanel.getTable();
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF); // Prevent automatic column squishing
        
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object v, boolean s, boolean f, int r, int c) {
                Component comp = super.getTableCellRendererComponent(t, v, s, f, r, c);
                
                if (!s) {
                    if ("Daily".equals(activeTab) && t.getColumnCount() > 12) {
                        String status = String.valueOf(t.getValueAt(r, 12));
                        if ("A".equals(status) || "Absent".equalsIgnoreCase(status)) {
                            comp.setBackground(new Color(0xfee2e2));
                        } else {
                            comp.setBackground(r % 2 == 0 ? Color.WHITE : new Color(0xf8fafc));
                        }
                    } else if ("Monthly".equals(activeTab) && c > 2 && reportTypeCombo != null && "Attendance Summary (P/A)".equals(reportTypeCombo.getSelectedItem())) {
                        String status = String.valueOf(t.getValueAt(r, c));
                        if ("A".equals(status)) {
                            comp.setBackground(new Color(0xfee2e2)); // Soft red for absent
                        } else if ("P".equals(status)) {
                            comp.setBackground(new Color(0xf0fdf4)); // Soft green for present
                        } else {
                            comp.setBackground(r % 2 == 0 ? Color.WHITE : new Color(0xf8fafc));
                        }
                    } else {
                        comp.setBackground(r % 2 == 0 ? Color.WHITE : new Color(0xf8fafc));
                    }
                }
                
                if (comp instanceof JLabel) {
                    ((JLabel) comp).setHorizontalAlignment(c < 3 ? SwingConstants.LEFT : SwingConstants.CENTER);
                    ((JLabel) comp).setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 5));
                }
                
                return comp;
            }
        });

        // Set Preferred Widths to prevent compression
        for (int i = 0; i < table.getColumnCount(); i++) {
            int width = 120; // Default
            if (i == 0) width = 80;   // Emp ID
            if (i == 1) width = 180;  // Name
            if (i == 2) width = 140;  // Designation / Dept
            
            // Monthly Matrix columns
            if ("Monthly".equals(activeTab) && i > 2) {
                boolean isPa = reportTypeCombo != null && "Attendance Summary (P/A)".equals(reportTypeCombo.getSelectedItem());
                width = isPa ? 45 : 150;
            }
            
            // Daily/Leave report specific wide columns
            if ("Daily".equals(activeTab) && i == 13) width = 200; // Remarks
            if ("Leave Report".equals(activeTab) && (i == 2 || i == 7)) width = 150; // Leave Type / Applied On

            table.getColumnModel().getColumn(i).setPreferredWidth(width);
        }
    }

    private void loadMetadata() {
        try {
            DatabaseManager db = DatabaseManager.getInstance();
            List<Map<String, Object>> depts = db.fetchAll("SELECT dept_name FROM departments ORDER BY dept_name");
            for (Map<String, Object> d : depts)
                deptCombo.addItem((String) d.get("dept_name"));

            List<Map<String, Object>> emps = db
                    .fetchAll("SELECT emp_name FROM employees WHERE status='Active' ORDER BY emp_name");
            for (Map<String, Object> e : emps)
                empCombo.addItem((String) e.get("emp_name"));
        } catch (Exception ignored) {
        }
    }

    private void generateReport() {
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        String dateStr = dateField.getText().trim();
        String dept = (String) deptCombo.getSelectedItem();
        String emp = (String) empCombo.getSelectedItem();
        String statusFilter = (String) statusCombo.getSelectedItem();
        
        String monthStr;
        if ("Monthly".equals(activeTab)) {
            monthStr = String.format("%04d-%02d", (Integer) yearCombo.getSelectedItem(), monthCombo.getSelectedIndex() + 1);
            // Rebuild matrix columns
            try {
                YearMonth ym = YearMonth.parse(monthStr);
                int days = ym.lengthOfMonth();
                String[] cols = new String[3 + days];
                cols[0] = "Emp ID"; cols[1] = "Name"; cols[2] = "Designation";
                boolean isPa = "Attendance Summary (P/A)".equals(reportTypeCombo.getSelectedItem());
                for (int i = 1; i <= days; i++) {
                    cols[2 + i] = isPa ? String.valueOf(i) : i + "/" + ym.getMonthValue() + "/" + ym.getYear();
                }
                tablePanel.getTable().setModel(new DefaultTableModel(cols, 0));
                styleTable();
            } catch (Exception e) { e.printStackTrace(); }
        } else {
            monthStr = ""; // Unused
        }

        DefaultTableModel model = (DefaultTableModel) tablePanel.getTable().getModel();
        model.setRowCount(0);

        SwingWorker<List<Map<String, Object>>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<Map<String, Object>> doInBackground() throws Exception {
                DatabaseManager db = DatabaseManager.getInstance();
                String date = ("Monthly".equals(activeTab)) ? "" : convertDate(dateStr);
                List<Object> params = new ArrayList<>();

                if ("Leave Report".equals(activeTab)) {
                    StringBuilder sql = new StringBuilder(
                        "SELECT l.emp_id, e.emp_name, l.leave_type, l.from_date, l.to_date, l.days, l.status, l.applied_on " +
                        "FROM leaves l JOIN employees e ON l.emp_id = e.emp_id WHERE 1=1 ");
                    if (!"All".equals(dept)) { sql.append(" AND e.department = ?"); params.add(dept); }
                    if (!"All".equals(emp)) { sql.append(" AND e.emp_name = ?"); params.add(emp); }
                    if (!"All".equals(statusFilter)) { sql.append(" AND l.status = ?"); params.add(statusFilter); }
                    sql.append(" ORDER BY l.from_date DESC");
                    return db.fetchAll(sql.toString(), params.toArray());
                } else if ("Monthly".equals(activeTab)) {
                    YearMonth ym = YearMonth.parse(monthStr);
                    
                    // 1. Fetch Employees with Shift Info for Weekly Offs
                    StringBuilder empSql = new StringBuilder(
                        "SELECT e.emp_id, e.emp_name, e.designation, s.weekly_off1, s.weekly_off2 " +
                        "FROM employees e LEFT JOIN shifts s ON e.shift = s.shift_name WHERE e.status='Active' ");
                    List<Object> empParams = new ArrayList<>();
                    if (!"All".equals(dept)) { empSql.append(" AND e.department = ?"); empParams.add(dept); }
                    if (!"All".equals(emp)) { empSql.append(" AND e.emp_name = ?"); empParams.add(emp); }
                    empSql.append(" ORDER BY e.emp_name ASC");
                    List<Map<String, Object>> emps = db.fetchAll(empSql.toString(), empParams.toArray());

                    // 1b. Fetch Holidays for the month
                    List<Map<String, Object>> holidays = db.fetchAll(
                        "SELECT holiday_date, holiday_name FROM holidays WHERE holiday_date LIKE ?", monthStr + "%");
                    Map<String, String> holidayMap = new HashMap<>();
                    for (Map<String, Object> h : holidays) holidayMap.put(String.valueOf(h.get("holiday_date")), String.valueOf(h.get("holiday_name")));

                    // 1c. Fetch Leaves for the month
                    List<Map<String, Object>> leaves = db.fetchAll(
                        "SELECT emp_id, from_date, to_date, leave_type FROM leaves WHERE status='Approved' " +
                        "AND (from_date <= ? AND to_date >= ?)", 
                        ym.atEndOfMonth().toString(), ym.atDay(1).toString());
                    Map<String, List<Map<String, Object>>> leaveMap = new HashMap<>();
                    for (Map<String, Object> l : leaves) 
                        leaveMap.computeIfAbsent(String.valueOf(l.get("emp_id")), k -> new ArrayList<>()).add(l);

                    // 2. Fetch Attendance
                    List<Map<String, Object>> attnList = db.fetchAll(
                        "SELECT emp_id, punch_date, MIN(in_time) as first_in, MAX(out_time) as last_out, SUM(work_hours) as total_hrs " +
                        "FROM attendance WHERE DATE_FORMAT(punch_date, '%Y-%m') = ? GROUP BY emp_id, punch_date", monthStr);

                    // 3. Map Attendance
                    Map<String, Map<String, String>> attnMap = new HashMap<>();
                    for (Map<String, Object> a : attnList) {
                        String eid = String.valueOf(a.get("emp_id"));
                        String pdate = String.valueOf(a.get("punch_date"));
                        Object inVal = a.get("first_in");
                        Object outVal = a.get("last_out");
                        double hrs = DatabaseManager.dbl(a, "total_hrs");
                        
                        String firstIn = inVal != null ? String.valueOf(inVal) : "";
                        String lastOut = outVal != null ? String.valueOf(outVal) : "";
                        
                        // Extract HH:mm from DATETIME string or format Timestamp
                        String tIn = firstIn.length() >= 16 ? firstIn.substring(11, 16) : (firstIn.length() >= 5 ? firstIn.substring(0, 5) : "--:--");
                        String tOut = lastOut.length() >= 16 ? lastOut.substring(11, 16) : (lastOut.length() >= 5 ? lastOut.substring(0, 5) : "--:--");
                        
                        String summary;
                        if ("Attendance Summary (P/A)".equals(reportTypeCombo.getSelectedItem())) {
                            summary = "P";
                        } else {
                            summary = String.format("%s-%s (%s)", tIn, tOut, com.bhspl.util.AttendanceCalculator.formatDuration(hrs));
                        }
                        attnMap.computeIfAbsent(eid, k -> new HashMap<>()).put(pdate, summary);
                    }

                    // 4. Build Pivoted Rows
                    List<Map<String, Object>> result = new ArrayList<>();
                    int daysInMonth = ym.lengthOfMonth();
                    for (Map<String, Object> e : emps) {
                        String eid = String.valueOf(e.get("emp_id"));
                        Map<String, Object> row = new LinkedHashMap<>(e);
                        Map<String, String> empAttn = attnMap.getOrDefault(eid, Collections.emptyMap());
                        List<Map<String, Object>> empLeaves = leaveMap.getOrDefault(eid, Collections.emptyList());

                        for (int d = 1; d <= daysInMonth; d++) {
                            LocalDate curr = ym.atDay(d);
                            String dbDate = curr.toString();
                            String status = empAttn.get(dbDate);

                            if (status == null) {
                                if (curr.isAfter(LocalDate.now())) {
                                    status = ""; // Don't show status for future days
                                } else {
                                    // Check Day of Week for Weekly Off
                                    String dayName = curr.getDayOfWeek().name(); // MONDAY, etc.
                                    String off1 = String.valueOf(e.get("weekly_off1")).toUpperCase();
                                    String off2 = String.valueOf(e.get("weekly_off2")).toUpperCase();

                                    if (holidayMap.containsKey(dbDate)) {
                                        String hName = holidayMap.get(dbDate);
                                        status = hName.length() > 12 ? hName.substring(0, 12).toUpperCase() : hName.toUpperCase();
                                    } else {
                                        // Check Leaves
                                        boolean onLeave = false;
                                        for (Map<String, Object> l : empLeaves) {
                                            LocalDate start = LocalDate.parse(String.valueOf(l.get("from_date")));
                                            LocalDate end = LocalDate.parse(String.valueOf(l.get("to_date")));
                                            if (!curr.isBefore(start) && !curr.isAfter(end)) {
                                                onLeave = true; break;
                                            }
                                        }
                                        if (onLeave) status = "LVE";
                                        else if (dayName.equals(off1) || dayName.equals(off2)) status = dayName.substring(0, 3);
                                        else status = "A";
                                    }
                                }
                            }
                            row.put("day_" + d, status);
                        }
                        result.add(row);
                    }
                    return result;
                } else {
                    StringBuilder sql = new StringBuilder(
                            "SELECT e.emp_id, e.emp_name, e.department, e.shift, " +
                                    "s.start_time as sched_in, s.end_time as sched_out, " +
                                    "DATE_FORMAT(MIN(a.in_time), '%H:%i') as in_time, " +
                                    "DATE_FORMAT(MAX(a.out_time), '%H:%i') as out_time, " +
                                    "SUM(a.work_hours) as work_hours, SUM(a.overtime) as overtime, " +
                                    "SUM(a.late_mins) as late_mins, SUM(a.early_mins) as early_mins, " +
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
                                    "LEFT JOIN attendance a ON e.emp_id = a.emp_id AND a.punch_date = ? " +
                                    "WHERE e.status = 'Active' ");
                    for (int i = 0; i < 10; i++) params.add(date);
                    if (!"All".equals(dept)) { sql.append(" AND e.department = ? "); params.add(dept); }
                    if (!"All".equals(emp)) { sql.append(" AND e.emp_name = ? "); params.add(emp); }
                    if (!"All".equals(statusFilter)) {
                        if ("Absent".equals(statusFilter)) {
                            sql.append(" AND (a.status = 'Absent' OR a.status IS NULL) ");
                        } else {
                            sql.append(" AND a.status = ? ");
                            params.add(statusFilter);
                        }
                    }
                    sql.append(" GROUP BY e.emp_id, e.emp_name, e.department, e.shift, s.start_time, s.end_time ");
                    sql.append(" ORDER BY e.emp_name ASC");
                    return db.fetchAll(sql.toString(), params.toArray());
                }
            }

            @Override
            protected void done() {
                setCursor(Cursor.getDefaultCursor());
                try {
                    List<Map<String, Object>> data = get();
                    int p = 0, a = 0, hd = 0, ot = 0, late = 0;
                    Set<String> presentIds = new HashSet<>();
                    Set<String> absentIds = new HashSet<>();

                    for (Map<String, Object> r : data) {
                        if ("Leave Report".equals(activeTab)) {
                            model.addRow(new Object[]{
                                String.valueOf(r.get("emp_id")), String.valueOf(r.get("emp_name")), String.valueOf(r.get("leave_type")),
                                String.valueOf(r.get("from_date")), String.valueOf(r.get("to_date")), r.get("days"),
                                String.valueOf(r.get("status")), String.valueOf(r.get("applied_on"))
                            });
                            continue;
                        } else if ("Monthly".equals(activeTab)) {
                            int days = 31; // Default or recalculated
                            try {
                                String mStr = String.format("%04d-%02d", (Integer) yearCombo.getSelectedItem(), monthCombo.getSelectedIndex() + 1);
                                days = YearMonth.parse(mStr).lengthOfMonth();
                            } catch(Exception ignored){}
                            
                            Object[] rowData = new Object[3 + days];
                            rowData[0] = r.get("emp_id");
                            rowData[1] = r.get("emp_name");
                            rowData[2] = r.get("designation");
                            for (int i = 1; i <= days; i++) {
                                rowData[2 + i] = r.get("day_" + i);
                            }
                            model.addRow(rowData);
                            continue;
                        }

                        // Daily Report
                        String st = (r.get("status") != null) ? String.valueOf(r.get("status")) : "Absent";
                        String eid = String.valueOf(r.get("emp_id"));

                        if ("Present".equalsIgnoreCase(st) || "P".equals(st) || "Late".equalsIgnoreCase(st)) {
                            if (!presentIds.contains(eid)) { presentIds.add(eid); p++; }
                        } else if ("Absent".equalsIgnoreCase(st) || "A".equals(st)) {
                            if (!absentIds.contains(eid)) { absentIds.add(eid); a++; }
                        } else if ("Half Day".equalsIgnoreCase(st) || "HD".equals(st))
                            hd++;

                        double rawOt = DatabaseManager.dbl(r, "overtime");
                        if (rawOt > 0) ot++;

                        int rawLate = DatabaseManager.num(r, "late_mins");
                        if (rawLate > 0) late++;

                        String shortStatus = normalizeStatus(st);
                        model.addRow(new Object[] {
                                String.valueOf(r.get("emp_id")),
                                String.valueOf(r.get("emp_name")),
                                String.valueOf(r.get("department")),
                                String.valueOf(r.get("shift")),
                                String.valueOf(r.get("sched_in")),
                                String.valueOf(r.get("in_time")),
                                String.valueOf(r.get("out_time")),
                                String.valueOf(r.get("sched_out")),
                                com.bhspl.util.AttendanceCalculator.formatDuration(DatabaseManager.dbl(r, "work_hours")),
                                com.bhspl.util.AttendanceCalculator.formatDuration(DatabaseManager.dbl(r, "overtime")),
                                rawLate > 0 ? rawLate + "m" : "—",
                                DatabaseManager.num(r, "early_mins") > 0 ? r.get("early_mins") + "m" : "—",
                                shortStatus,
                                String.valueOf(r.get("remarks"))
                        });
                    }

                    if ("Daily".equals(activeTab)) {
                        summaryLabel.setText(String.format("%s | Total: %d | P:%d A:%d HD:%d | Late:%d OT:%d",
                                dateStr, data.size(), p, a, hd, late, ot));
                    } else if ("Monthly".equals(activeTab)) {
                        summaryLabel.setText("Monthly Matrix: " + data.size() + " employees loaded for " + monthStr);
                    } else {
                        summaryLabel.setText("Leave Report: " + data.size() + " records found.");
                    }

                } catch (Exception ex) {
                    ex.printStackTrace();
                    JOptionPane.showMessageDialog(ReportsPanel.this, "Error fetching data: " + ex.getMessage());
                }
            }
        };
        worker.execute();
    }

    private String convertDate(String displayDate) {
        try {
            return LocalDate.parse(displayDate, DISPLAY_FMT).toString();
        } catch (Exception e) {
            return LocalDate.now().toString();
        }
    }

    private void recalculate() {
        String displayDate = dateField.getText().trim();
        LocalDate ld;
        try {
            ld = LocalDate.parse(displayDate, DISPLAY_FMT);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Invalid date format. Use DD-MM-YYYY");
            return;
        }

        String dbDate = ld.toString();
        ProgressMonitor pm = new ProgressMonitor(this, "Recalculating Attendance for " + displayDate, "", 0, 100);

        SwingWorker<Void, Void> w = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                DatabaseManager db = DatabaseManager.getInstance();
                // 1. Reset sync status using a robust datetime range
                String nextDay = ld.plusDays(1).toString();
                int resetCount = db.execute("UPDATE raw_logs SET synced=0 WHERE punch_time >= ? AND punch_time < ?", dbDate + " 00:00:00", nextDay + " 00:00:00");
                System.out.println("Recalc: Reset " + resetCount + " raw logs for " + dbDate);
                pm.setNote("Reset " + resetCount + " logs...");

                // 2. Clear existing records for this date
                db.execute("DELETE FROM attendance WHERE punch_date = ?", dbDate);
                // 3. Process logs again with status updates
                SyncService.processRawLogs(msg -> {
                    pm.setNote(msg);
                    System.out.println("Recalc: " + msg);
                });
                return null;
            }

            @Override
            protected void done() {
                pm.close();
                try {
                    get(); // Check for background exceptions
                    generateReport(); // Refresh table
                    JOptionPane.showMessageDialog(ReportsPanel.this, "Recalculation complete.");
                } catch (Exception e) {
                    e.printStackTrace();
                    JOptionPane.showMessageDialog(ReportsPanel.this, 
                        "Recalculation failed: " + e.getCause().getMessage(), 
                        "Sync Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        w.execute();
    }

    private void exportToExcel() {
        DefaultTableModel model = (DefaultTableModel) tablePanel.getTable().getModel();
        if (model.getRowCount() == 0)
            return;

        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile(new File("Daily_Report_" + dateField.getText() + ".xlsx"));
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION)
            return;

        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Report");
            Row header = sheet.createRow(0);
            for (int i = 0; i < model.getColumnCount(); i++) {
                header.createCell(i).setCellValue(model.getColumnName(i));
            }

            for (int r = 0; r < model.getRowCount(); r++) {
                Row row = sheet.createRow(r + 1);
                for (int c = 0; c < model.getColumnCount(); c++) {
                    Object val = model.getValueAt(r, c);
                    row.createCell(c).setCellValue(val != null ? val.toString() : "");
                }
            }
            try (FileOutputStream out = new FileOutputStream(fc.getSelectedFile())) {
                wb.write(out);
            }
            JOptionPane.showMessageDialog(this, "Exported Successfully!");
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Export Failed: " + ex.getMessage());
        }
    }

    private String normalizeStatus(String st) {
        if (st == null)
            return "A";
        String s = st.trim().toLowerCase();
        switch (s) {
            case "p":
            case "present":
                return "P";
            case "a":
            case "absent":
                return "A";
            case "hd":
            case "half day":
            case "halfday":
                return "HD";
            case "od":
            case "on duty":
            case "onduty":
                return "OD";
            case "cl":
            case "casual leave":
            case "casual":
                return "CL";
            case "sl":
            case "sick leave":
            case "sick":
                return "SL";
            case "el":
            case "earned leave":
            case "earned":
                return "EL";
            case "co":
            case "comp off":
            case "compoff":
                return "CO";
            case "lwp":
            case "loss of pay":
                return "LWP";
            case "wo":
            case "week off":
            case "weekly off":
            case "sunday":
            case "monday":
            case "tuesday":
            case "wednesday":
            case "thursday":
            case "friday":
            case "saturday":
                return st.toUpperCase();
            case "ph":
            case "public holiday":
            case "holiday":
                return st.toUpperCase();
            default:
                return st.toUpperCase();
        }
    }
}
