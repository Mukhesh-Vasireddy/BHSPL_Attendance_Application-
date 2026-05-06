package com.bhspl.web;

import com.bhspl.db.DatabaseManager;
import com.bhspl.service.SyncService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.ResponseBody;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;

@Controller
public class WebController {

    @GetMapping("/")
    public String index(Model model) {
        try {
            DatabaseManager db = DatabaseManager.getInstance();
            
            // Stats
            long totalEmps = db.queryLong("SELECT COUNT(*) FROM employees WHERE status='Active'");
            long presentCount = db.queryLong("SELECT COUNT(DISTINCT emp_id) FROM raw_logs WHERE DATE(punch_time) = CURDATE()");
            long leaveCount = db.queryLong("SELECT COUNT(*) FROM leaves WHERE status='Approved' AND CURDATE() BETWEEN from_date AND to_date");
            long absentCount = totalEmps - presentCount - leaveCount;
            if (absentCount < 0) absentCount = 0;

            // Recent Logs (Live Attendance Overview)
            List<Map<String, Object>> recentLogs = db.query(
                "SELECT r.emp_id, " +
                "CASE WHEN e.emp_name IS NOT NULL THEN e.emp_name " +
                "     WHEN r.emp_id = '0' OR r.emp_id = '0000' THEN 'System/Admin' " +
                "     ELSE 'Unknown User' END as emp_name, " +
                "MIN(r.punch_time) as in_time, COUNT(*) as punches " +
                "FROM raw_logs r " +
                "LEFT JOIN employees e ON r.emp_id = e.emp_id " +
                "WHERE DATE(r.punch_time) = CURDATE() " +
                "GROUP BY r.emp_id, e.emp_name " +
                "ORDER BY MAX(r.punch_time) DESC LIMIT 10"
            );

            model.addAttribute("totalEmps", totalEmps);
            model.addAttribute("presentCount", presentCount);
            model.addAttribute("absentCount", absentCount);
            model.addAttribute("leaveCount", leaveCount);
            model.addAttribute("recentLogs", recentLogs);
            
        } catch (Exception e) {
            e.printStackTrace();
            model.addAttribute("error", e.getMessage());
        }
        return "dashboard";
    }

    @GetMapping("/employees")
    public String employees(Model model, @RequestParam(name="search", required=false) String search) {
        try {
            DatabaseManager db = DatabaseManager.getInstance();
            String sql = "SELECT * FROM employees WHERE 1=1";
            if (search != null && !search.isEmpty()) {
                sql += " AND (emp_name LIKE '%" + search + "%' OR emp_id LIKE '%" + search + "%')";
            }
            sql += " ORDER BY emp_name";
            
            List<Map<String, Object>> employees = db.query(sql);
            model.addAttribute("employees", employees);
            model.addAttribute("selSearch", search);
            model.addAttribute("depts", db.query("SELECT dept_name FROM departments ORDER BY dept_name"));
        } catch (Exception e) {
            model.addAttribute("error", e.getMessage());
        }
        return "employees";
    }

    @PostMapping("/employees/save")
    public String saveEmployee(@RequestParam Map<String, String> params) {
        try {
            DatabaseManager db = DatabaseManager.getInstance();
            String isEdit = params.get("is_edit");
            String empId = params.get("emp_id");
            String name = params.get("emp_name");
            String dept = params.get("department");
            String desig = params.get("designation");
            String shift = params.get("shift");
            String status = params.get("status");

            if ("false".equals(isEdit)) {
                db.execute("INSERT INTO employees (emp_id, emp_name, department, designation, shift, status) VALUES (?,?,?,?,?,?)", 
                    empId, name, dept, desig, shift, status);
            } else {
                db.execute("UPDATE employees SET emp_name=?, department=?, designation=?, shift=?, status=? WHERE emp_id=?", 
                    name, dept, desig, shift, status, empId);
            }
        } catch (Exception e) { e.printStackTrace(); }
        return "redirect:/employees";
    }

    @GetMapping("/employees/delete/{id}")
    public String deleteEmployee(@PathVariable("id") String id) {
        try {
            DatabaseManager.getInstance().execute("DELETE FROM employees WHERE emp_id=?", id);
        } catch (Exception e) { e.printStackTrace(); }
        return "redirect:/employees";
    }

    @GetMapping("/attendance")
    public String attendance(Model model, @RequestParam(name="date", required=false) String date) {
        try {
            DatabaseManager db = DatabaseManager.getInstance();
            String filterDate = (date != null) ? date : java.time.LocalDate.now().toString();
            
            String sql = "SELECT e.emp_id, e.emp_name, e.shift, a.in_time, a.out_time, a.work_hours, a.status " +
                         "FROM employees e " +
                         "LEFT JOIN attendance a ON e.emp_id = a.emp_id AND a.punch_date = ? " +
                         "WHERE e.status='Active' ORDER BY a.in_time DESC, e.emp_name ASC";
            
            List<Map<String, Object>> data = db.query(sql, filterDate);
            model.addAttribute("attendance", data);
            model.addAttribute("selDate", filterDate);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "attendance";
    }

    @GetMapping("/reports")
    public String reports() {
        return "redirect:/reports/daily";
    }

    @GetMapping("/reports/daily")
    public String reportsDaily(Model model, 
                               @RequestParam(name="date", required=false) String date,
                               @RequestParam(name="dept", required=false) String dept) {
        List<Map<String, Object>> data = new java.util.ArrayList<>();
        try {
            DatabaseManager db = DatabaseManager.getInstance();
            String filterDate = (date != null) ? date : java.time.LocalDate.now().toString();
            String sql = "SELECT e.emp_id, e.emp_name, e.department, a.status, a.in_time as punch_in, a.out_time as punch_out, a.work_hours " +
                         "FROM employees e LEFT JOIN attendance a ON e.emp_id = a.emp_id AND a.punch_date = ? WHERE 1=1 ";
            if (dept != null && !"All".equals(dept)) sql += " AND e.department = '" + dept + "'";
            sql += " ORDER BY e.emp_name ASC";
            data = db.query(sql, filterDate);
            model.addAttribute("selDate", filterDate);
            model.addAttribute("selDept", (dept != null) ? dept : "All");
            model.addAttribute("depts", db.query("SELECT dept_name FROM departments ORDER BY dept_name"));
        } catch (Exception e) { e.printStackTrace(); }
        model.addAttribute("data", data);
        return "reports-daily";
    }

    @GetMapping("/reports/monthly")
    public String reportsMonthly(Model model, 
                                 @RequestParam(name="month", required=false) String month,
                                 @RequestParam(name="year", required=false) String year,
                                 @RequestParam(name="dept", required=false) String dept,
                                 @RequestParam(name="type", required=false) String type) {
        List<Map<String, Object>> matrix = new java.util.ArrayList<>();
        try {
            DatabaseManager db = DatabaseManager.getInstance();
            int curM = (month != null) ? Integer.parseInt(month) : 5;
            int curY = (year != null) ? Integer.parseInt(year) : 2026;
            String reportType = (type != null) ? type : "PA";
            
            java.time.YearMonth yearMonth = java.time.YearMonth.of(curY, curM);
            int daysInMonth = yearMonth.lengthOfMonth();
            List<Integer> daysList = new java.util.ArrayList<>();
            for (int i = 1; i <= daysInMonth; i++) daysList.add(i);
            
            // Fetch holidays
            List<Map<String, Object>> holidays = db.query("SELECT holiday_date, holiday_name FROM holidays WHERE MONTH(holiday_date) = ? AND YEAR(holiday_date) = ?", curM, curY);
            Map<Integer, String> holidayMap = new HashMap<>();
            for (Map<String, Object> h : holidays) {
                java.sql.Date d = (java.sql.Date) h.get("holiday_date");
                holidayMap.put(d.toLocalDate().getDayOfMonth(), (String) h.get("holiday_name"));
            }

            String empSql = "SELECT emp_id, emp_name, designation, department FROM employees WHERE 1=1";
            if (dept != null && !"All".equals(dept)) empSql += " AND department = '" + dept + "'";
            empSql += " ORDER BY emp_name ASC";
            List<Map<String, Object>> employees = db.query(empSql);

            for (Map<String, Object> emp : employees) {
                String eid = (String) emp.get("emp_id");
                Map<String, Object> row = new HashMap<>(emp);
                Map<Integer, Map<String, Object>> attendanceMap = new HashMap<>();
                
                List<Map<String, Object>> att = db.query("SELECT DAY(punch_date) as d, status, in_time, out_time, work_hours FROM attendance WHERE emp_id=? AND MONTH(punch_date)=? AND YEAR(punch_date)=?", eid, curM, curY);
                for (Map<String, Object> a : att) {
                    int dayNum = DatabaseManager.num(a, "d");
                    attendanceMap.put(dayNum, a);
                }

                List<String> dayStatuses = new ArrayList<>();
                for (int d = 1; d <= daysInMonth; d++) {
                    java.time.LocalDate date = java.time.LocalDate.of(curY, curM, d);
                    if (holidayMap.containsKey(d)) {
                        dayStatuses.add(holidayMap.get(d).toUpperCase());
                    } else if (date.getDayOfWeek() == java.time.DayOfWeek.SUNDAY) {
                        dayStatuses.add("SUN");
                    } else {
                        Map<String, Object> data = attendanceMap.get(d);
                        if (data == null) {
                            dayStatuses.add("A");
                        } else {
                            if ("WH".equals(reportType)) {
                                String inStr = DatabaseManager.str(data, "in_time");
                                String outStr = DatabaseManager.str(data, "out_time");
                                double wh = DatabaseManager.dbl(data, "work_hours");
                                
                                String in = "";
                                if (inStr.contains(" ")) in = inStr.split(" ")[1];
                                else if (inStr.contains("T")) in = inStr.split("T")[1];
                                if (in.length() > 5) in = in.substring(0, 5);
                                
                                String out = "";
                                if (outStr.contains(" ")) out = outStr.split(" ")[1];
                                else if (outStr.contains("T")) out = outStr.split("T")[1];
                                if (out.length() > 5) out = out.substring(0, 5);
                                
                                if (in.isEmpty() || !in.contains(":")) {
                                    dayStatuses.add("P");
                                } else {
                                    int h = (int) wh;
                                    int mins = (int) Math.round((wh - h) * 60);
                                    String dur = String.format("(%02d:%02d)", h, mins);
                                    dayStatuses.add(String.format("%s %s %s", in, out.isEmpty() ? "--:--" : out, dur));
                                }
                            } else {
                                String s = DatabaseManager.str(data, "status");
                                String inTime = DatabaseManager.str(data, "in_time");
                                
                                if ("Present".equals(s) || "Late".equals(s) || "Early".equals(s) || !inTime.isEmpty()) {
                                    dayStatuses.add("P");
                                } else if (s.isEmpty()) {
                                    dayStatuses.add("A");
                                } else {
                                    dayStatuses.add(s.substring(0, 1));
                                }
                            }
                        }
                    }
                }
                row.put("days", dayStatuses);
                matrix.add(row);
            }

            model.addAttribute("days", daysList);
            model.addAttribute("selMonth", String.format("%02d", curM));
            model.addAttribute("selYear", String.valueOf(curY));
            model.addAttribute("selDept", (dept != null) ? dept : "All");
            model.addAttribute("selType", reportType);
            try {
                model.addAttribute("depts", db.query("SELECT dept_name FROM departments ORDER BY dept_name"));
            } catch (Exception de) {
                System.err.println("Note: No departments found or table missing.");
                model.addAttribute("depts", new ArrayList<>());
            }
        } catch (Exception e) {
            System.err.println("CRITICAL ERROR in reportsMonthly: " + e.getMessage());
            e.printStackTrace();
        }
        model.addAttribute("data", matrix);
        return "reports-monthly";
    }

    @GetMapping("/reports/leave")
    public String reportsLeave(Model model, 
                               @RequestParam(name="from", required=false) String from,
                               @RequestParam(name="to", required=false) String to,
                               @RequestParam(name="dept", required=false) String dept) {
        List<Map<String, Object>> data = new java.util.ArrayList<>();
        try {
            DatabaseManager db = DatabaseManager.getInstance();
            String f = (from != null) ? from : java.time.LocalDate.now().withDayOfMonth(1).toString();
            String t = (to != null) ? to : java.time.LocalDate.now().toString();
            
            String sql = "SELECT l.*, e.emp_name, e.department FROM leaves l " +
                         "JOIN employees e ON l.emp_id = e.emp_id WHERE l.from_date BETWEEN ? AND ? ";
            if (dept != null && !"All".equals(dept)) sql += " AND e.department = '" + dept + "'";
            sql += " ORDER BY e.emp_name ASC, l.from_date DESC";
            
            data = db.query(sql, f, t);
            model.addAttribute("selFrom", f);
            model.addAttribute("selTo", t);
            model.addAttribute("selDept", (dept != null) ? dept : "All");
            model.addAttribute("depts", db.query("SELECT dept_name FROM departments ORDER BY dept_name"));
        } catch (Exception e) { e.printStackTrace(); }
        model.addAttribute("data", data);
        return "reports-leave";
    }

    @GetMapping("/raw-logs")
    public String rawLogs(Model model, 
                          @RequestParam(name="from", required=false) String from,
                          @RequestParam(name="to", required=false) String to,
                          @RequestParam(name="dept", required=false) String dept,
                          @RequestParam(name="emp", required=false) String emp) {
        System.err.println("[" + new java.util.Date() + "] CRITICAL DEBUG: rawLogs called with from=" + from + ", to=" + to);
        try {
            DatabaseManager db = DatabaseManager.getInstance();
            String f = (from != null) ? from : java.time.LocalDate.now().toString();
            String t = (to != null) ? to : java.time.LocalDate.now().toString();
            String d = (dept != null) ? dept : "All";
            String eId = (emp != null) ? emp : "All";

            java.time.LocalDate startDate = java.time.LocalDate.parse(f);
            java.time.LocalDate endDate = java.time.LocalDate.parse(t);

            // Fetch metadata for filters
            model.addAttribute("depts", db.query("SELECT dept_name FROM departments ORDER BY dept_name"));
            model.addAttribute("employees", db.query("SELECT emp_id, emp_name FROM employees WHERE status='Active' ORDER BY emp_name"));
            
            // Fetch all active employees for "Absent" check - SORTED BY NAME
            String empBaseSql = "SELECT emp_id, emp_name, department, shift FROM employees WHERE status='Active'";
            if (!"All".equals(d)) empBaseSql += " AND department = '" + d + "'";
            if (!"All".equals(eId)) empBaseSql += " AND emp_id = '" + eId + "'";
            empBaseSql += " ORDER BY emp_name ASC";
            List<Map<String, Object>> activeEmps = db.query(empBaseSql);
            System.err.println("[" + new java.util.Date() + "] DEBUG: Active employees found: " + activeEmps.size());

            // Fetch all raw logs for the range (we will map them in Java for maximum robustness)
            StringBuilder logSql = new StringBuilder(
                "SELECT * FROM raw_logs WHERE DATE(punch_time) BETWEEN ? AND ? "
            );
            logSql.append(" ORDER BY punch_time ASC");
            
            List<Map<String, Object>> rawLogs = db.query(logSql.toString(), f, t);
            System.err.println("[" + new java.util.Date() + "] DEBUG: Found " + rawLogs.size() + " raw logs in DB for range " + f + " to " + t);
            
            List<Map<String, Object>> sessions = new ArrayList<>();
            java.time.format.DateTimeFormatter timeFmt = java.time.format.DateTimeFormatter.ofPattern("hh:mm a");

            // Build Enrollment Map (SyncService logic)
            Map<String, String> enrollMap = new HashMap<>();
            for (Map<String, Object> e : activeEmps) {
                String sid = DatabaseManager.str(e, "emp_id");
                String eid = DatabaseManager.str(e, "device_enroll_id");
                enrollMap.put(sid, sid);
                if (!eid.isEmpty()) enrollMap.put(eid, sid);
                try {
                    enrollMap.put(String.valueOf(Long.parseLong(sid)), sid);
                    if (!eid.isEmpty()) enrollMap.put(String.valueOf(Long.parseLong(eid)), sid);
                } catch (Exception ignored) {}
            }

            // Map logs by Matched Employee + Date
            Map<String, List<Map<String, Object>>> logMap = new HashMap<>();
            for (Map<String, Object> log : rawLogs) {
                String fullTime = log.get("punch_time").toString().replace("T", " ");
                String date = fullTime.split(" ")[0];
                String rawEid = log.get("emp_id").toString().trim();
                
                String matchedSid = enrollMap.get(rawEid);
                if (matchedSid == null) {
                    try { matchedSid = enrollMap.get(String.valueOf(Long.parseLong(rawEid))); } catch (Exception ignored) {}
                }

                if (matchedSid != null) {
                    String key = matchedSid + "_" + date;
                    logMap.computeIfAbsent(key, k -> new ArrayList<>()).add(log);
                }
            }

            // Iterate over every employee (Alphabetical) and then every day to build the full report
            for (Map<String, Object> employee : activeEmps) {
                String sid = (String) employee.get("emp_id");
                for (java.time.LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
                    String dateStr = date.toString();
                    String key = sid + "_" + dateStr;
                    List<Map<String, Object>> dayLogs = logMap.get(key);

                    if (dayLogs == null || dayLogs.isEmpty()) {
                        // Mark as Absent as per requirement
                        Map<String, Object> row = new HashMap<>(employee);
                        row.put("date", dateStr);
                        row.put("in_time", "—");
                        row.put("out_time", "—");
                        row.put("punch_count", 0);
                        row.put("status", "Absent");
                        sessions.add(row);
                    } else {
                        // Process Present sessions
                        int totalPunches = dayLogs.size();
                        for (int i = 0; i < dayLogs.size(); i += 2) {
                            Map<String, Object> session = new HashMap<>(employee);
                            session.put("date", dateStr);
                            session.put("punch_count", totalPunches);
                            session.put("status", "Present");
                            
                            // IN Time
                            java.time.LocalDateTime inDt = java.time.LocalDateTime.parse(dayLogs.get(i).get("punch_time").toString().replace(" ", "T"));
                            session.put("in_time", inDt.format(timeFmt));
                            
                            // OUT Time
                            if (i + 1 < dayLogs.size()) {
                                java.time.LocalDateTime outDt = java.time.LocalDateTime.parse(dayLogs.get(i+1).get("punch_time").toString().replace(" ", "T"));
                                session.put("out_time", outDt.format(timeFmt));
                            } else {
                                session.put("out_time", "Wait...");
                            }
                            sessions.add(session);
                        }
                    }
                }
            }
            
            model.addAttribute("sessions", sessions);
            model.addAttribute("selFrom", f);
            model.addAttribute("selTo", t);
            model.addAttribute("selDept", d);
            model.addAttribute("selEmp", eId);
            
        } catch (Exception ex) {
            ex.printStackTrace();
            model.addAttribute("error", ex.getMessage());
        }
        return "raw-logs";
    }

    @GetMapping("/devices")
    public String devices(Model model) {
        try {
            DatabaseManager db = DatabaseManager.getInstance();
            List<Map<String, Object>> devices = db.query("SELECT * FROM devices");
            model.addAttribute("devices", devices);
        } catch (Exception e) {
            model.addAttribute("error", e.getMessage());
        }
        return "devices";
    }

    @GetMapping("/leave")
    public String leave(Model model) {
        List<Map<String, Object>> leaves = new java.util.ArrayList<>();
        List<Map<String, Object>> types = new java.util.ArrayList<>();
        try {
            DatabaseManager db = DatabaseManager.getInstance();
            leaves = db.query("SELECT l.*, e.emp_name FROM leaves l JOIN employees e ON l.emp_id=e.emp_id ORDER BY l.applied_on DESC");
            types = db.query("SELECT leave_type FROM leave_policy WHERE status='Active'");
        } catch (Exception e) {
            model.addAttribute("error", e.getMessage());
        }
        model.addAttribute("leaves", leaves);
        model.addAttribute("leaveTypes", types);
        return "leave";
    }

    @PostMapping("/leave/save")
    public String saveLeave(@RequestParam Map<String, String> params) {
        try {
            DatabaseManager db = DatabaseManager.getInstance();
            String id = params.get("id");
            String empId = params.get("empId");
            String type = params.get("type");
            String from = params.get("from");
            String to = params.get("to");
            String days = params.get("days");
            String reason = params.get("reason");
            String status = params.get("status");

            if (id == null || id.isEmpty()) {
                db.execute("INSERT INTO leaves (emp_id, leave_type, from_date, to_date, days, reason, status, applied_on) VALUES (?,?,?,?,?,?,?,NOW())", 
                    empId, type, from, to, days, reason, status);
            } else {
                db.execute("UPDATE leaves SET emp_id=?, leave_type=?, from_date=?, to_date=?, days=?, reason=?, status=? WHERE id=?", 
                    empId, type, from, to, days, reason, status, id);
            }

            // Desktop App Logic: Sync with attendance if approved
            if ("Approved".equals(status)) {
                java.time.LocalDate start = java.time.LocalDate.parse(from);
                java.time.LocalDate end = java.time.LocalDate.parse(to);
                for (java.time.LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
                    db.execute("INSERT INTO attendance (emp_id, punch_date, status, remarks) VALUES (?,?,?,?) ON DUPLICATE KEY UPDATE status=?, remarks=?", 
                        empId, d.toString(), type, "Leave Approved", type, "Leave Approved");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "redirect:/leave";
    }

    @PostMapping("/leave/update-status")
    public String updateLeaveStatus(@RequestParam("id") String id, @RequestParam("status") String status) {
        try {
            DatabaseManager db = DatabaseManager.getInstance();
            db.execute("UPDATE leaves SET status=? WHERE id=?", status, id);
            
            // Sync with attendance if approved
            if ("Approved".equals(status)) {
                Map<String, Object> l = db.queryOne("SELECT * FROM leaves WHERE id=?", id);
                if (l != null) {
                    String empId = l.get("emp_id").toString();
                    String type = l.get("leave_type").toString();
                    java.time.LocalDate start = java.time.LocalDate.parse(l.get("from_date").toString());
                    java.time.LocalDate end = java.time.LocalDate.parse(l.get("to_date").toString());
                    for (java.time.LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
                        db.execute("INSERT INTO attendance (emp_id, punch_date, status, remarks) VALUES (?,?,?,?) ON DUPLICATE KEY UPDATE status=?, remarks=?", 
                            empId, d.toString(), type, "Leave Approved", type, "Leave Approved");
                    }
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
        return "redirect:/leave";
    }

    // Leave Sub-menus
    @GetMapping("/leave/manager") public String leaveManager(Model model) { return leave(model); }
    @GetMapping("/leave/od")
    public String leaveOD(Model model) {
        List<Map<String, Object>> depts = new java.util.ArrayList<>();
        List<Map<String, Object>> ods = new java.util.ArrayList<>();
        try {
            DatabaseManager db = DatabaseManager.getInstance();
            depts = db.query("SELECT dept_name FROM departments WHERE status='Active'");
            ods = db.query("SELECT o.*, e.emp_name, e.department FROM od_requests o JOIN employees e ON o.emp_id = e.emp_id ORDER BY o.applied_on DESC");
        } catch (Exception e) {
            model.addAttribute("error", e.getMessage());
        }
        model.addAttribute("depts", depts);
        model.addAttribute("ods", ods);
        return "leave-od";
    }

    @PostMapping("/leave/od/update-status")
    public String updateODStatus(@RequestParam("id") String id, @RequestParam("status") String status) {
        try {
            DatabaseManager db = DatabaseManager.getInstance();
            db.execute("UPDATE od_requests SET status=? WHERE id=?", status, id);
            
            if ("Approved".equals(status)) {
                Map<String, Object> req = db.queryOne("SELECT * FROM od_requests WHERE id=?", id);
                if (req != null) {
                    String eid = req.get("emp_id").toString();
                    java.time.LocalDate start = java.time.LocalDate.parse(req.get("od_from").toString());
                    java.time.LocalDate end = java.time.LocalDate.parse(req.get("od_to").toString());
                    
                    // Fetch shift hours for attendance sync
                    Map<String, Object> emp = db.queryOne(
                        "SELECT s.work_hours FROM employees e JOIN shifts s ON e.shift=s.shift_name WHERE e.emp_id=?", eid);
                    double wh = (emp != null) ? Double.parseDouble(emp.get("work_hours").toString()) : 8.0;

                    for (java.time.LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
                        db.execute("INSERT INTO attendance (emp_id, punch_date, status, work_hours, remarks) " +
                            "VALUES (?,?,?,?,'OD Approved') ON DUPLICATE KEY UPDATE status=?, work_hours=?, remarks='OD Approved'", 
                            eid, d.toString(), "OD", wh, "OD", wh);
                    }
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
        return "redirect:/leave/od";
    }

    @GetMapping("/leave/policy")
    public String leavePolicy(Model model) {
        List<Map<String, Object>> policies = new java.util.ArrayList<>();
        try {
            DatabaseManager db = DatabaseManager.getInstance();
            policies = db.query("SELECT * FROM leave_policy ORDER BY id");
        } catch (Exception e) {
            e.printStackTrace();
            model.addAttribute("error", e.getMessage());
        }
        model.addAttribute("policies", policies);
        return "leave-policy";
    }

    @GetMapping("/leave/balance")
    public String leaveBalance(Model model, @RequestParam(name="year", required = false) String year, 
                               @RequestParam(name="dept", required = false) String dept, 
                               @RequestParam(name="type", required = false) String type) {
        try {
            DatabaseManager db = DatabaseManager.getInstance();
            int curY = java.time.LocalDate.now().getYear();
            String filterYear = (year != null) ? year : String.valueOf(curY);
            
            List<Map<String, Object>> depts = db.query("SELECT dept_name FROM departments WHERE status='Active' ORDER BY dept_name");
            List<Map<String, Object>> types = db.query("SELECT leave_type FROM leave_policy WHERE status='Active' ORDER BY leave_type");
            
            StringBuilder sql = new StringBuilder(
                "SELECT b.*, e.emp_name, e.department FROM leave_balance b " +
                "JOIN employees e ON b.emp_id = e.emp_id WHERE b.year = ?"
            );
            
            if (dept != null && !"All".equals(dept)) sql.append(" AND e.department = '").append(dept).append("'");
            if (type != null && !"All".equals(type)) sql.append(" AND b.leave_type = '").append(type).append("'");
            sql.append(" ORDER BY e.department, e.emp_id, b.leave_type");

            List<Map<String, Object>> balances = db.query(sql.toString(), filterYear);
            
            // Summary Stats
            double tBal = 0, tUsed = 0, tLapsed = 0;
            java.util.Set<String> uniqueEmps = new java.util.HashSet<>();
            if (balances != null) {
                for (Map<String, Object> r : balances) {
                    tBal += DatabaseManager.dbl(r, "closing_bal");
                    tUsed += DatabaseManager.dbl(r, "used");
                    tLapsed += DatabaseManager.dbl(r, "lapsed");
                    uniqueEmps.add(DatabaseManager.str(r, "emp_id"));
                }
            }
            long totalActive = db.queryLong("SELECT COUNT(*) FROM employees WHERE status='Active'");

            model.addAttribute("balances", (balances != null) ? balances : new java.util.ArrayList<>());
            model.addAttribute("depts", depts);
            model.addAttribute("types", types);
            model.addAttribute("selYear", filterYear);
            model.addAttribute("selDept", (dept != null) ? dept : "All");
            model.addAttribute("selType", (type != null) ? type : "All");
            model.addAttribute("stats", Map.of(
                "empCount", uniqueEmps.size() + " / " + totalActive,
                "totalBal", String.format("%.1f", tBal),
                "totalUsed", String.format("%.1f", tUsed),
                "totalLapsed", String.format("%.1f", tLapsed)
            ));
        } catch (Exception e) {
            model.addAttribute("error", e.getMessage());
        }
        return "leave-balance";
    }

    @GetMapping("/leave/holidays")
    public String leaveHolidays(Model model, @RequestParam(name="type", required = false) String type) {
        List<Map<String, Object>> holidays = new java.util.ArrayList<>();
        try {
            DatabaseManager db = DatabaseManager.getInstance();
            String sql = "SELECT * FROM holidays ";
            if (type != null && !"All".equals(type)) sql += " WHERE holiday_type = '" + type + "'";
            sql += " ORDER BY holiday_date";
            
            holidays = db.query(sql);
            model.addAttribute("selType", (type != null) ? type : "All");
        } catch (Exception e) {
            model.addAttribute("error", e.getMessage());
        }
        model.addAttribute("holidays", holidays);
        return "holiday";
    }

    @PostMapping("/leave/holidays/load-defaults")
    public String loadDefaultHolidays() {
        try {
            DatabaseManager db = DatabaseManager.getInstance();
            Object[][] defaults = {
                { "2026-01-01", "New Year's Day", "National Holiday" },
                { "2026-01-26", "Republic Day", "National Holiday" },
                { "2026-03-25", "Holi", "Festival Holiday" },
                { "2026-04-14", "Dr. Ambedkar Jayanti", "National Holiday" },
                { "2026-04-18", "Good Friday", "Festival Holiday" },
                { "2026-05-01", "Labour Day", "National Holiday" },
                { "2026-08-15", "Independence Day", "National Holiday" },
                { "2026-08-27", "Janmashtami", "Festival Holiday" },
                { "2026-10-02", "Gandhi Jayanti", "National Holiday" },
                { "2026-10-24", "Dussehra", "Festival Holiday" },
                { "2026-11-01", "Diwali", "Festival Holiday" },
                { "2026-11-05", "Bhai Dooj", "Festival Holiday" },
                { "2026-12-25", "Christmas Day", "Public Holiday" }
            };
            for (Object[] h : defaults) {
                db.execute("INSERT IGNORE INTO holidays (holiday_date, holiday_name, holiday_type) VALUES (?,?,?)", 
                    h[0], h[1], h[2]);
            }
        } catch (Exception e) { e.printStackTrace(); }
        return "redirect:/leave/holidays";
    }

    @GetMapping("/masters") public String masters(Model model) { return "masters"; }
    @GetMapping("/system") public String system(Model model) { return "system"; }

    @GetMapping("/masters/departments")
    public String mastersDepts(Model model) {
        List<Map<String, Object>> depts = new java.util.ArrayList<>();
        try {
            DatabaseManager db = DatabaseManager.getInstance();
            depts = db.query("SELECT * FROM departments ORDER BY dept_name");
        } catch (Exception e) {
            model.addAttribute("error", e.getMessage());
        }
        model.addAttribute("depts", depts);
        return "masters-depts";
    }

    @PostMapping("/masters/departments/save")
    public String saveDept(@RequestParam Map<String, String> params) {
        try {
            DatabaseManager db = DatabaseManager.getInstance();
            String id = params.get("id");
            String name = params.get("name");
            String code = params.get("code");
            String head = params.get("head");
            String status = params.get("status");

            if (id == null || id.isEmpty()) {
                db.execute("INSERT INTO departments (dept_name, dept_code, head_name, status) VALUES (?,?,?,?)", 
                    name, code, head, status);
            } else {
                db.execute("UPDATE departments SET dept_name=?, dept_code=?, head_name=?, status=? WHERE id=?", 
                    name, code, head, status, id);
            }
        } catch (Exception e) { e.printStackTrace(); }
        return "redirect:/masters/departments";
    }

    @GetMapping("/masters/designations")
    public String mastersDesig(Model model, @RequestParam(name="search", required = false) String search) {
        List<Map<String, Object>> desigs = new java.util.ArrayList<>();
        try {
            DatabaseManager db = DatabaseManager.getInstance();
            String sql = "SELECT * FROM designations";
            if (search != null && !search.isEmpty()) sql += " WHERE desig_name LIKE '%" + search + "%'";
            sql += " ORDER BY level_order ASC, desig_name ASC";
            desigs = db.query(sql);
        } catch (Exception e) {
            e.printStackTrace();
            model.addAttribute("error", e.getMessage());
        }
        model.addAttribute("designations", desigs);
        return "masters-desig";
    }

    @PostMapping("/masters/designations/save")
    public String saveDesig(@RequestParam Map<String, String> params) {
        try {
            DatabaseManager db = DatabaseManager.getInstance();
            String id = params.get("id");
            String name = params.get("name");
            String level = params.get("level");
            String desc = params.get("description");
            String status = params.get("status");

            if (id == null || id.isEmpty()) {
                db.execute("INSERT INTO designations (desig_name, level_order, description, status) VALUES (?,?,?,?)", 
                    name, level, desc, status);
            } else {
                db.execute("UPDATE designations SET desig_name=?, level_order=?, description=?, status=? WHERE id=?", 
                    name, level, desc, status, id);
            }
        } catch (Exception e) { e.printStackTrace(); }
        return "redirect:/masters/designations";
    }

    @GetMapping("/masters/shifts")
    public String mastersShifts(Model model) {
        List<Map<String, Object>> shifts = new java.util.ArrayList<>();
        try {
            DatabaseManager db = DatabaseManager.getInstance();
            shifts = db.query("SELECT * FROM shifts ORDER BY shift_name");
        } catch (Exception e) {
            model.addAttribute("error", e.getMessage());
        }
        model.addAttribute("shifts", shifts);
        return "masters-shifts";
    }

    @PostMapping("/masters/shifts/save")
    public String saveShift(@RequestParam Map<String, String> params) {
        try {
            DatabaseManager db = DatabaseManager.getInstance();
            String id = params.get("id");
            String name = params.get("name");
            String start = params.get("start");
            String end = params.get("end");
            String breakM = params.get("breakM");
            String grace = params.get("grace");
            String off1 = params.get("off1");
            String off2 = params.get("off2");

            if (id == null || id.isEmpty()) {
                db.execute("INSERT INTO shifts (shift_name, start_time, end_time, break_mins, grace_mins, weekly_off1, weekly_off2) VALUES (?,?,?,?,?,?,?)", 
                    name, start, end, breakM, grace, off1, off2);
            } else {
                db.execute("UPDATE shifts SET shift_name=?, start_time=?, end_time=?, break_mins=?, grace_mins=?, weekly_off1=?, weekly_off2=? WHERE id=?", 
                    name, start, end, breakM, grace, off1, off2, id);
            }
        } catch (Exception e) { e.printStackTrace(); }
        return "redirect:/masters/shifts";
    }

    @GetMapping("/masters/weekly-off")
    public String mastersWeeklyOff(Model model, @RequestParam(name="dept", required = false) String dept) {
        List<Map<String, Object>> depts = new java.util.ArrayList<>();
        List<Map<String, Object>> offs = new java.util.ArrayList<>();
        List<Map<String, Object>> employees = new java.util.ArrayList<>();
        try {
            DatabaseManager db = DatabaseManager.getInstance();
            depts = db.query("SELECT dept_name FROM departments ORDER BY dept_name");
            
            String sql = "SELECT w.*, e.emp_name, e.department as dept_name " +
                         "FROM weekly_offs w " +
                         "JOIN employees e ON w.emp_id = e.emp_id";
            if (dept != null && !"All".equals(dept)) sql += " WHERE e.department = '" + dept + "'";
            
            offs = db.query(sql);
            employees = db.query("SELECT emp_id, emp_name FROM employees ORDER BY emp_name");
            
            model.addAttribute("selDept", (dept != null) ? dept : "All");
        } catch (Exception e) {
            model.addAttribute("error", e.getMessage());
        }
        model.addAttribute("offs", offs);
        model.addAttribute("depts", depts);
        model.addAttribute("employees", employees);
        return "masters-weekly-off";
    }

    @PostMapping("/masters/weekly-off/save")
    public String saveWeeklyOff(@RequestParam Map<String, String> params) {
        try {
            DatabaseManager db = DatabaseManager.getInstance();
            db.execute("INSERT INTO weekly_offs (emp_id, off_day1, off_day2, effective_from, effective_to, remarks) VALUES (?,?,?,?,?,?)", 
                params.get("empId"), params.get("day1"), params.get("day2"), params.get("from"), params.get("to"), params.get("remarks"));
        } catch (Exception e) { e.printStackTrace(); }
        return "redirect:/masters/weekly-off";
    }

    // System Sub-menus
    @GetMapping("/system/users")
    public String systemUsers(Model model) {
        List<Map<String, Object>> users = new java.util.ArrayList<>();
        try {
            DatabaseManager db = DatabaseManager.getInstance();
            users = db.query("SELECT id, username, role, emp_id, status, last_login FROM users ORDER BY username");
        } catch (Exception e) {
            model.addAttribute("error", e.getMessage());
        }
        model.addAttribute("users", users);
        return "system-users";
    }

    @PostMapping("/system/users/save")
    public String saveUser(@RequestParam Map<String, String> params) {
        try {
            DatabaseManager db = DatabaseManager.getInstance();
            String u = params.get("username");
            String p = params.get("password");
            String r = params.get("role");
            String e = params.get("empId");
            
            // Note: In a real app we'd hash the password here. 
            // For now matching desktop logic which calls db.hashPw
            String ph = db.hashPw(p);
            db.execute("INSERT INTO users (username, password_hash, role, emp_id) VALUES(?,?,?,?)", 
                u, ph, r, e.isEmpty() ? null : e);
        } catch (Exception e) { e.printStackTrace(); }
        return "redirect:/system/users";
    }

    @PostMapping("/system/users/reset-password")
    public String resetUserPassword(@RequestParam String id, @RequestParam String password) {
        try {
            DatabaseManager db = DatabaseManager.getInstance();
            String ph = db.hashPw(password);
            db.execute("UPDATE users SET password_hash=? WHERE id=?", ph, id);
        } catch (Exception e) { e.printStackTrace(); }
        return "redirect:/system/users";
    }

    @PostMapping("/system/users/toggle-status")
    public String toggleUserStatus(@RequestParam("id") String id, @RequestParam("status") String status) {
        try {
            DatabaseManager db = DatabaseManager.getInstance();
            String newStatus = "Active".equals(status) ? "Inactive" : "Active";
            db.execute("UPDATE users SET status=? WHERE id=?", newStatus, id);
        } catch (Exception e) { e.printStackTrace(); }
        return "redirect:/system/users";
    }

    @GetMapping("/system/settings")
    public String systemSettings(Model model) {
        model.addAttribute("appName", "BHSPL Attendance Management System");
        model.addAttribute("version", "2.0.42 (Enterprise)");
        model.addAttribute("runtime", "Java 17 (OpenJDK)");
        model.addAttribute("framework", "Spring Boot 3.x / Web Interface");
        model.addAttribute("db", "MySQL 8.0 (Connector/J)");
        model.addAttribute("admsStatus", com.bhspl.service.PushService.isRunning() ? "Active (Listening)" : "Inactive");
        model.addAttribute("admsPort", com.bhspl.service.PushService.getPort());
        return "system-settings";
    }

    @GetMapping("/system/backup")
    public String systemBackup(Model model) {
        try {
            Map<String, String> cfg = com.bhspl.core.Config.loadDbConfig();
            model.addAttribute("dbHost", cfg != null ? cfg.getOrDefault("host", "localhost") : "Unknown");
            model.addAttribute("dbPort", cfg != null ? cfg.getOrDefault("port", "3306") : "Unknown");
            model.addAttribute("dbName", cfg != null ? cfg.getOrDefault("database", "biotest") : "Unknown");
            model.addAttribute("dbUser", cfg != null ? cfg.getOrDefault("user", "root") : "Unknown");
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "system-backup";
    }

    @GetMapping("/system/about")
    public String systemAbout(Model model) {
        return "system-about";
    }

    @PostMapping("/system/process-logs")
    @ResponseBody
    public String processLogs() {
        try {
            DatabaseManager db = DatabaseManager.getInstance();
            // Ensure unique constraint for daily records
            // Trigger a live poll of devices first to get today's latest punches
            try { 
                com.bhspl.service.SyncService.performSync(); 
            } catch (Exception ignored) {}

            // Force total re-calculation for the last 60 days
            try {
                db.execute("DELETE FROM attendance WHERE punch_date >= DATE_SUB(CURDATE(), INTERVAL 60 DAY)");
                db.execute("UPDATE raw_logs SET synced=0 WHERE punch_time >= DATE_SUB(CURDATE(), INTERVAL 60 DAY)");
                db.commit();
            } catch (Exception ex) {
                return "Database Error during Reset: " + ex.getMessage();
            }
            
            // Run processing for all un-synced logs
            com.bhspl.service.SyncService.processRawLogs(msg -> System.out.println("Repair: " + msg));
            
            return "Repair Complete: All records for the last 60 days have been re-processed with FIRST-IN/LAST-OUT logic.";
        } catch (Exception e) {
            e.printStackTrace();
            return "Fatal Error: " + e.getMessage();
        }
    }

    @GetMapping("/system/debug")
    @ResponseBody
    public String debugInfo() {
        StringBuilder sb = new StringBuilder();
        try {
            DatabaseManager db = DatabaseManager.getInstance();
            Map<String, String> cfg = com.bhspl.core.Config.loadDbConfig();
            
            sb.append("--- DATABASE CONFIG ---\n");
            if (cfg != null) {
                sb.append("Host: ").append(cfg.get("host")).append("\n");
                sb.append("DB: ").append(cfg.get("database")).append("\n");
                sb.append("User: ").append(cfg.get("user")).append("\n");
            } else {
                sb.append("CONFIG FILE NOT FOUND OR NOT CONFIGURED!\n");
            }

            sb.append("\n--- TABLE COUNTS ---\n");
            sb.append("Employees: ").append(db.queryLong("SELECT COUNT(*) FROM employees")).append("\n");
            sb.append("Raw Logs: ").append(db.queryLong("SELECT COUNT(*) FROM raw_logs")).append("\n");
            sb.append("Attendance (Total): ").append(db.queryLong("SELECT COUNT(*) FROM attendance")).append("\n");
            sb.append("Attendance (May 2026): ").append(db.queryLong("SELECT COUNT(*) FROM attendance WHERE MONTH(punch_date)=5 AND YEAR(punch_date)=2026")).append("\n");
            
            sb.append("\n--- RAW LOGS ID SAMPLE ---\n");
            List<Map<String, Object>> idSample = db.query("SELECT DISTINCT emp_id FROM raw_logs WHERE emp_id != '0' LIMIT 5");
            for (Map<String, Object> id : idSample) {
                String val = id.get("emp_id").toString();
                long count = db.queryLong("SELECT COUNT(*) FROM employees WHERE emp_id = ?", val);
                long numericCount = db.queryLong("SELECT COUNT(*) FROM employees WHERE CAST(emp_id AS UNSIGNED) = CAST(? AS UNSIGNED)", val);
                sb.append("Log ID: [").append(val).append("] | Matches in Master: ").append(count).append(" | Numeric Match: ").append(numericCount).append("\n");
            }
            
            sb.append("\n--- REPORT QUERY DEBUG ---\n");
            String debugSql = "SELECT emp_id, emp_name, designation, department FROM employees WHERE 1=1";
            List<Map<String, Object>> debugEmps = db.query(debugSql);
            sb.append("Query: ").append(debugSql).append("\n");
            sb.append("Results Found: ").append(debugEmps.size()).append("\n");
            if (!debugEmps.isEmpty()) {
                sb.append("First Result: ").append(debugEmps.get(0)).append("\n");
            }

            sb.append("\n--- ATTENDANCE SAMPLE (May 2026) ---\n");
            List<Map<String, Object>> attSample = db.query("SELECT emp_id, punch_date, status, in_time, out_time, work_hours FROM attendance WHERE MONTH(punch_date)=5 AND YEAR(punch_date)=2026 LIMIT 5");
            if (attSample.isEmpty()) {
                sb.append("NO RECORDS FOUND FOR MAY 2026!\n");
            } else {
                for (Map<String, Object> a : attSample) {
                    sb.append(a.get("emp_id")).append(" | ").append(a.get("punch_date")).append(" | ").append(a.get("status"))
                      .append(" | IN: [").append(a.get("in_time")).append("] | OUT: [").append(a.get("out_time")).append("]\n");
                }
            }

            sb.append("\n--- SAMPLE DATA (Employees) ---\n");
            List<Map<String, Object>> emps = db.query("SELECT * FROM employees LIMIT 5");
            for (Map<String, Object> e : emps) {
                sb.append(e.get("emp_id")).append(" | ").append(e.get("emp_name")).append(" | ").append(e.get("status")).append("\n");
            }

        } catch (Exception e) {
            sb.append("\nERROR: ").append(e.getMessage());
            java.io.StringWriter sw = new java.io.StringWriter();
            e.printStackTrace(new java.io.PrintWriter(sw));
            sb.append("\n").append(sw.toString());
        }
        return "<pre>" + sb.toString() + "</pre>";
    }
    @GetMapping("/reports/monthly/export")
    public void exportMonthly(
            @RequestParam int month,
            @RequestParam int year,
            @RequestParam String dept,
            @RequestParam String type,
            @RequestParam String format,
            jakarta.servlet.http.HttpServletResponse response) throws Exception {
        
        DatabaseManager db = DatabaseManager.getInstance();
        java.time.YearMonth ym = java.time.YearMonth.of(year, month);
        int daysCount = ym.lengthOfMonth();
        
        String filename = "Attendance_" + month + "_" + year + ".csv";
        response.setContentType("text/csv");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
        
        java.io.PrintWriter writer = response.getWriter();
        
        // Header
        writer.print("ID,Name,Designation");
        for (int i = 1; i <= daysCount; i++) {
            writer.print("," + i + "/" + month + "/" + year);
        }
        writer.println();
        
        // Data
        String sql = "SELECT emp_id, emp_name, designation FROM employees WHERE status='Active'";
        if (dept != null && !"All".equals(dept)) sql += " AND department = '" + dept + "'";
        sql += " ORDER BY emp_name";
        
        List<Map<String, Object>> emps = db.query(sql);
        for (Map<String, Object> e : emps) {
            String eid = DatabaseManager.str(e, "emp_id");
            writer.print(eid + "," + DatabaseManager.str(e, "emp_name") + "," + DatabaseManager.str(e, "designation"));
            
            Map<Integer, String> attMap = new HashMap<>();
            List<Map<String, Object>> att = db.query("SELECT DAY(punch_date) as d, status, in_time, out_time, work_hours FROM attendance WHERE emp_id=? AND MONTH(punch_date)=? AND YEAR(punch_date)=?", eid, month, year);
            for (Map<String, Object> a : att) {
                if (type.equals("WH")) {
                    String inStr = DatabaseManager.str(a, "in_time");
                    String in = inStr.contains(" ") ? inStr.split(" ")[1] : (inStr.contains("T") ? inStr.split("T")[1] : "");
                    if (in.length() > 5) in = in.substring(0, 5);
                    double wh = DatabaseManager.dbl(a, "work_hours");
                    attMap.put(DatabaseManager.num(a, "d"), in.isEmpty() ? "P" : in + " (" + wh + ")");
                } else {
                    attMap.put(DatabaseManager.num(a, "d"), DatabaseManager.str(a, "status"));
                }
            }
            
            for (int i = 1; i <= daysCount; i++) {
                writer.print("," + attMap.getOrDefault(i, "A"));
            }
            writer.println();
        }
        writer.flush();
        writer.close();
    }

    @GetMapping("/system/sync")
    public String systemSync(@RequestParam(name="redirect", required=false) String redirect) {
        try {
            SyncService.performSync();
        } catch (Exception e) { e.printStackTrace(); }
        return "redirect:" + (redirect != null ? redirect : "/");
    }
}
