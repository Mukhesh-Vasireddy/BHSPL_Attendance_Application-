package com.bhspl.web;

import com.bhspl.db.DatabaseManager;
import com.bhspl.service.SyncService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.ResponseBody;
import jakarta.servlet.http.HttpSession;

@Controller
public class WebController {

    @GetMapping("/login")
    public String loginPage(HttpSession session) {
        try {
            DatabaseManager db = DatabaseManager.getInstance();
            if (db.queryLong("SELECT COUNT(*) FROM users") == 0) {
                db.execute("INSERT INTO users (username, password_hash, role, status) VALUES (?,?,?,?)",
                        "admin", db.hashPw("admin123"), "Admin", "Active");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (session.getAttribute("user") != null)
            return "redirect:/dashboard";
        return "login";
    }

    @PostMapping("/login")
    public String loginAction(@RequestParam("username") String username, @RequestParam("password") String password,
            HttpSession session, Model model) {
        try {
            DatabaseManager db = DatabaseManager.getInstance();
            String hash = db.hashPw(password);
            Map<String, Object> user = db.queryOne(
                    "SELECT * FROM users WHERE username=? AND password_hash=? AND status='Active'", username, hash);

            if (user != null) {
                session.setAttribute("user", user.get("username"));
                session.setAttribute("role", user.get("role"));
                db.execute("UPDATE users SET last_login=NOW() WHERE id=?", user.get("id"));
                return "redirect:/dashboard";
            } else {
                model.addAttribute("error", "Invalid username or password");
                return "login";
            }
        } catch (Exception e) {
            e.printStackTrace();
            model.addAttribute("error", "System Error: " + e.getMessage());
            return "login";
        }
    }

    @GetMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/login";
    }

    @GetMapping({ "/", "/dashboard" })
    public String index(Model model, @RequestParam(name = "search", required = false) String search,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "10") int pageSize,
            @RequestParam(name = "export", defaultValue = "false") boolean isExport,
            HttpSession session) {
        if (isExport) {
            /* Dashboard uses fixed 10 but we'll override if needed */ }

        // Ensure ADMS/Push Service is running (Self-Healing) - Only if not manually
        // stopped
        if (!com.bhspl.service.PushService.isInternalRunning() && !com.bhspl.service.PushService.isUserStopped()) {
            com.bhspl.service.PushService.start();
        }

        try {
            DatabaseManager.getInstance()
                    .execute("UPDATE raw_logs SET synced=0 WHERE punch_time >= DATE_SUB(NOW(), INTERVAL 1 DAY)");
        } catch (Exception ignored) {
        }

        try {
            DatabaseManager db = DatabaseManager.getInstance();
            if (isExport) pageSize = 50000;
            int offset = (page - 1) * pageSize;

            // Stats
            long totalEmps = db.queryLong("SELECT COUNT(*) FROM employees WHERE status='Active'");
            long presentCount = db
                    .queryLong(
                            "SELECT COUNT(DISTINCT r.emp_id) FROM raw_logs r JOIN employees e ON r.emp_id = e.emp_id WHERE r.punch_time >= CURDATE() AND r.punch_time < DATE_ADD(CURDATE(), INTERVAL 1 DAY) AND e.status = 'Active'");
            long leaveCount = db.queryLong(
                    "SELECT COUNT(*) FROM leaves WHERE status='Approved' AND CURDATE() BETWEEN from_date AND to_date");
            long absentCount = totalEmps - presentCount - leaveCount;
            if (absentCount < 0)
                absentCount = 0;

            // Pagination info
            long totalLogs = db.queryLong(
                    "SELECT COUNT(*) FROM (SELECT r.emp_id FROM raw_logs r JOIN employees e ON r.emp_id = e.emp_id WHERE r.punch_time >= CURDATE() AND r.punch_time < DATE_ADD(CURDATE(), INTERVAL 1 DAY) AND e.status = 'Active' GROUP BY r.emp_id) as t");
            int totalPages = (int) Math.ceil((double) totalLogs / pageSize);
            if (totalPages == 0)
                totalPages = 1;

            // New Analytics
            long lateCount = db
                    .queryLong("SELECT COUNT(*) FROM attendance WHERE punch_date = CURDATE() AND status = 'Late'");
            long devicesOnline = db.queryLong("SELECT COUNT(*) FROM devices WHERE status = 'Active'");
            long pendingLeaves = db.queryLong("SELECT COUNT(*) FROM leaves WHERE status = 'Pending'");
            long weeklyOffCount = db.queryLong(
                    "SELECT COUNT(e.emp_id) FROM employees e LEFT JOIN shifts s ON e.shift = s.shift_name WHERE s.weekly_off1 = DAYNAME(CURDATE()) OR s.weekly_off2 = DAYNAME(CURDATE())");

            // Recent Logs (Live Attendance Overview)
            String searchFilter = "";
            String orderBy = "MAX(r.punch_time) DESC";
            if (search != null && !search.isEmpty()) {
                searchFilter = " AND (e.emp_name LIKE '%" + search + "%' OR r.emp_id LIKE '%" + search + "%') ";
                orderBy = "CASE WHEN LOWER(e.emp_name) LIKE LOWER('" + search
                        + "%') THEN 0 WHEN LOWER(r.emp_id) LIKE LOWER('" + search + "%') THEN 1 ELSE 2 END, " + orderBy;
            }

            List<Map<String, Object>> recentLogs = db.query(
                    "SELECT r.emp_id, " +
                            "CASE WHEN e.emp_name IS NOT NULL THEN e.emp_name " +
                            "     WHEN r.emp_id = '0' OR r.emp_id = '0000' THEN 'System/Admin' " +
                            "     ELSE 'Unknown User' END as emp_name, " +
                            "MIN(r.punch_time) as in_time, MAX(r.punch_time) as out_time, COUNT(*) as punches " +
                            "FROM raw_logs r " +
                            "LEFT JOIN employees e ON r.emp_id = e.emp_id " +
                            "WHERE r.punch_time >= CURDATE() AND r.punch_time < DATE_ADD(CURDATE(), INTERVAL 1 DAY) AND e.status = 'Active' "
                            + searchFilter +
                            "GROUP BY r.emp_id, e.emp_name " +
                            "ORDER BY " + orderBy + " LIMIT " + pageSize + " OFFSET " + offset);

            // Weekly Data mapping
            Map<String, Map<String, String>> weeklyData = new java.util.HashMap<>();
            List<String> weekDates = new java.util.ArrayList<>();
            List<String> weekDays = new java.util.ArrayList<>();
            java.time.format.DateTimeFormatter dayFormatter = java.time.format.DateTimeFormatter.ofPattern("EEE");

            for (int i = 6; i >= 0; i--) {
                java.time.LocalDate d = java.time.LocalDate.now().minusDays(i);
                weekDates.add(d.toString());
                weekDays.add(d.format(dayFormatter));
            }

            // Calculate Total Presents for the Bar Chart using range-scans (fully utilizes
            // index on punch_time)
            List<Long> weeklyPresentCounts = new java.util.ArrayList<>();
            for (String d : weekDates) {
                long presentOnDay = db.queryLong(
                        "SELECT COUNT(DISTINCT r.emp_id) FROM raw_logs r JOIN employees e ON r.emp_id = e.emp_id WHERE r.punch_time >= ? AND r.punch_time < DATE_ADD(?, INTERVAL 1 DAY) AND e.status = 'Active'",
                        d, d);
                weeklyPresentCounts.add(presentOnDay);
            }
            model.addAttribute("weeklyPresentCounts", weeklyPresentCounts);

            // Fetch Latest Sync Time
            String lastSync = db.queryString("SELECT MAX(last_sync) FROM devices");
            model.addAttribute("lastSync", lastSync != null ? lastSync : "Never");
            model.addAttribute("autoSyncActive", com.bhspl.service.SyncService.isRunning());

            if (!recentLogs.isEmpty()) {
                StringBuilder empIds = new StringBuilder();
                for (Map<String, Object> log : recentLogs) {
                    if (empIds.length() > 0)
                        empIds.append(",");
                    empIds.append("'").append(log.get("emp_id")).append("'");
                }

                List<Map<String, Object>> weeklyLogs = db.query(
                        "SELECT emp_id, DATE(punch_time) as pdate, COUNT(*) as punches " +
                                "FROM raw_logs WHERE emp_id IN (" + empIds.toString() + ") " +
                                "AND punch_time >= CURDATE() - INTERVAL 6 DAY AND punch_time < DATE_ADD(CURDATE(), INTERVAL 1 DAY) "
                                +
                                "GROUP BY emp_id, DATE(punch_time)");

                for (Map<String, Object> wl : weeklyLogs) {
                    String eId = (String) wl.get("emp_id");
                    String pDate = wl.get("pdate").toString();
                    long punches = (Long) wl.get("punches");

                    weeklyData.putIfAbsent(eId, new java.util.HashMap<>());
                    weeklyData.get(eId).put(pDate, punches > 0 ? "P" : "A");
                }
            }

            model.addAttribute("totalEmps", totalEmps);
            model.addAttribute("presentCount", presentCount);
            model.addAttribute("absentCount", absentCount);
            model.addAttribute("leaveCount", leaveCount);

            // New Analytics attributes
            model.addAttribute("lateCount", lateCount);
            model.addAttribute("devicesOnline", devicesOnline);
            model.addAttribute("pendingLeaves", pendingLeaves);
            model.addAttribute("weeklyOffCount", weeklyOffCount);

            model.addAttribute("recentLogs", recentLogs);
            model.addAttribute("selSearch", search);
            model.addAttribute("currentPage", page);
            model.addAttribute("totalPages", totalPages);
            model.addAttribute("pageSize", pageSize);
            model.addAttribute("totalItems", totalLogs);
            model.addAttribute("weekDates", weekDates);
            model.addAttribute("weekDays", weekDays);
            model.addAttribute("weeklyData", weeklyData);

            // Recent Live Punches Activity (last 10 punches today)
            List<Map<String, Object>> livePunches = db.query(
                    "SELECT r.emp_id, e.emp_name, r.punch_time, " +
                            "(SELECT COUNT(*) FROM raw_logs r2 WHERE r2.emp_id = r.emp_id AND r2.punch_time >= CURDATE() AND r2.punch_time < DATE_ADD(CURDATE(), INTERVAL 1 DAY) AND r2.punch_time <= r.punch_time) as punch_num "
                            +
                            "FROM raw_logs r LEFT JOIN employees e ON r.emp_id = e.emp_id " +
                            "WHERE r.punch_time >= CURDATE() AND r.punch_time < DATE_ADD(CURDATE(), INTERVAL 1 DAY) AND e.status = 'Active' ORDER BY r.punch_time DESC LIMIT 10");
            model.addAttribute("livePunches", livePunches);

        } catch (Exception e) {
            e.printStackTrace();
            model.addAttribute("error", e.getMessage());
        }
        return "dashboard";
    }

    @GetMapping("/api/sync")
    @ResponseBody
    public Map<String, Object> sync(HttpSession session) {
        Map<String, Object> res = new HashMap<>();
        if (session.getAttribute("user") == null) {
            res.put("success", false);
            return res;
        }
        try {
            SyncService.performSync();
            res.put("success", true);
            res.put("message", "Sync completed successfully.");
        } catch (Exception e) {
            res.put("success", false);
            res.put("message", e.getMessage());
        }
        return res;
    }

    @GetMapping("/employees")
    public String employees(Model model,
            @RequestParam(name = "search", required = false) String search,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "10") int pageSize,
            HttpSession session) {
        if (session.getAttribute("user") == null)
            return "redirect:/login";
        try {
            DatabaseManager db = DatabaseManager.getInstance();
            int offset = (page - 1) * pageSize;

            String where = " WHERE 1=1";
            String orderBy = "emp_name";
            if (search != null && !search.isEmpty()) {
                where += " AND (emp_name LIKE '%" + search + "%' OR emp_id LIKE '%" + search + "%')";
                orderBy = "CASE WHEN LOWER(emp_name) LIKE LOWER('" + search
                        + "%') THEN 0 WHEN LOWER(emp_id) LIKE LOWER('" + search + "%') THEN 1 ELSE 2 END, " + orderBy;
            }

            long total = db.queryLong("SELECT COUNT(*) FROM employees" + where);
            int totalPages = (int) Math.ceil((double) total / pageSize);
            if (totalPages == 0)
                totalPages = 1;

            String sql = "SELECT * FROM employees" + where + " ORDER BY " + orderBy + " LIMIT " + pageSize + " OFFSET "
                    + offset;

            List<Map<String, Object>> employees = db.query(sql);
            model.addAttribute("employees", employees);
            model.addAttribute("selSearch", search);
            model.addAttribute("currentPage", page);
            model.addAttribute("totalPages", totalPages);
            model.addAttribute("pageSize", pageSize);
            model.addAttribute("totalItems", total);
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
                db.execute(
                        "INSERT INTO employees (emp_id, emp_name, department, designation, shift, status) VALUES (?,?,?,?,?,?)",
                        empId, name, dept, desig, shift, status);
            } else {
                db.execute(
                        "UPDATE employees SET emp_name=?, department=?, designation=?, shift=?, status=? WHERE emp_id=?",
                        name, dept, desig, shift, status, empId);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "redirect:/employees";
    }

    @GetMapping("/employees/delete/{id}")
    public String deleteEmployee(@PathVariable("id") String id) {
        try {
            DatabaseManager.getInstance().execute("DELETE FROM employees WHERE emp_id=?", id);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "redirect:/employees";
    }

    @GetMapping("/attendance")
    public String attendance(Model model,
            @RequestParam(name = "date", required = false) String date,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "10") int pageSize,
            HttpSession session) {
        if (session.getAttribute("user") == null)
            return "redirect:/login";
        try {
            DatabaseManager db = DatabaseManager.getInstance();
            int offset = (page - 1) * pageSize;
            String filterDate = (date != null) ? date : java.time.LocalDate.now().toString();

            String where = " WHERE e.status='Active'";
            if (status != null && !status.isEmpty() && !"All".equals(status)) {
                if ("Absent".equalsIgnoreCase(status)) {
                    where += " AND (a.status IS NULL OR a.status = 'A' OR a.status = 'Absent')";
                } else if ("Late".equalsIgnoreCase(status)) {
                    where += " AND a.status = 'Late'";
                } else if ("Present".equalsIgnoreCase(status)) {
                    where += " AND (a.status = 'P' OR a.status = 'Present' OR a.status = 'Late' OR a.status = 'Early')";
                }
            }

            long total = db.queryLong(
                    "SELECT COUNT(*) FROM employees e LEFT JOIN attendance a ON e.emp_id = a.emp_id AND a.punch_date = ?"
                            + where,
                    filterDate);
            int totalPages = (int) Math.ceil((double) total / pageSize);
            if (totalPages == 0)
                totalPages = 1;

            String sql = "SELECT e.emp_id, e.emp_name, e.shift, a.in_time, a.out_time, a.work_hours, a.status " +
                    "FROM employees e " +
                    "LEFT JOIN attendance a ON e.emp_id = a.emp_id AND a.punch_date = ? " +
                    where + " ORDER BY a.in_time DESC, e.emp_name ASC LIMIT " + pageSize + " OFFSET " + offset;

            List<Map<String, Object>> data = db.query(sql, filterDate);

            model.addAttribute("attendance", data);
            model.addAttribute("selDate", filterDate);
            model.addAttribute("selStatus", (status != null) ? status : "All");
            model.addAttribute("currentPage", page);
            model.addAttribute("totalPages", totalPages);
            model.addAttribute("pageSize", pageSize);
            model.addAttribute("totalItems", total);
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
            @RequestParam(name = "date", required = false) String date,
            @RequestParam(name = "dept", required = false) String dept,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "10") int pageSize,
            @RequestParam(name = "export", defaultValue = "false") boolean isExport) {
        if (isExport) {
            pageSize = 50000;
            page = 1;
        }
        List<Map<String, Object>> data = new java.util.ArrayList<>();
        try {
            DatabaseManager db = DatabaseManager.getInstance();
            int offset = (page - 1) * pageSize;
            String filterDate = (date != null) ? date : java.time.LocalDate.now().toString();

            String where = " WHERE 1=1";
            if (dept != null && !"All".equals(dept))
                where += " AND e.department = '" + dept + "'";

            long total = db.queryLong(
                    "SELECT COUNT(*) FROM employees e LEFT JOIN attendance a ON e.emp_id = a.emp_id AND a.punch_date = ?"
                            + where,
                    filterDate);
            int totalPages = (int) Math.ceil((double) total / pageSize);
            if (totalPages == 0)
                totalPages = 1;

            String sql = "SELECT e.emp_id, e.emp_name, e.department, a.status, a.in_time as punch_in, a.out_time as punch_out, a.work_hours "
                    + "FROM employees e LEFT JOIN attendance a ON e.emp_id = a.emp_id AND a.punch_date = ?" + where
                    + " ORDER BY e.emp_name ASC LIMIT " + pageSize + " OFFSET " + offset;

            data = db.query(sql, filterDate);
            model.addAttribute("selDate", filterDate);
            model.addAttribute("selDept", (dept != null) ? dept : "All");
            model.addAttribute("currentPage", page);
            model.addAttribute("totalPages", totalPages);
            model.addAttribute("pageSize", pageSize);
            model.addAttribute("totalItems", total);
            model.addAttribute("depts", db.query("SELECT dept_name FROM departments ORDER BY dept_name"));
        } catch (Exception e) {
            e.printStackTrace();
        }
        model.addAttribute("data", data);
        return "reports-daily";
    }

    @GetMapping("/reports/monthly")
    public String reportsMonthly(Model model,
            @RequestParam(name = "month", required = false) String month,
            @RequestParam(name = "year", required = false) String year,
            @RequestParam(name = "dept", required = false) String dept,
            @RequestParam(name = "type", required = false) String type,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "10") int pageSize,
            @RequestParam(name = "export", defaultValue = "false") boolean isExport) {
        if (isExport) {
            pageSize = 50000;
            page = 1;
        }
        List<Map<String, Object>> matrix = new java.util.ArrayList<>();
        try {
            DatabaseManager db = DatabaseManager.getInstance();
            int curM = (month != null) ? Integer.parseInt(month) : 5;
            int curY = (year != null) ? Integer.parseInt(year) : 2026;
            String reportType = (type != null) ? type : "PA";

            java.time.YearMonth yearMonth = java.time.YearMonth.of(curY, curM);
            int daysInMonth = yearMonth.lengthOfMonth();
            List<Integer> daysList = new java.util.ArrayList<>();
            for (int i = 1; i <= daysInMonth; i++)
                daysList.add(i);

            // Fetch holidays
            List<Map<String, Object>> holidays = db.query(
                    "SELECT holiday_date, holiday_name FROM holidays WHERE MONTH(holiday_date) = ? AND YEAR(holiday_date) = ?",
                    curM, curY);
            Map<Integer, String> holidayMap = new HashMap<>();
            for (Map<String, Object> h : holidays) {
                java.sql.Date d = (java.sql.Date) h.get("holiday_date");
                holidayMap.put(d.toLocalDate().getDayOfMonth(), (String) h.get("holiday_name"));
            }

            String countSql = "SELECT COUNT(*) FROM employees WHERE 1=1";
            if (dept != null && !"All".equals(dept))
                countSql += " AND department = '" + dept + "'";
            long total = db.queryLong(countSql);
            int totalPages = (int) Math.ceil((double) total / pageSize);
            if (totalPages == 0)
                totalPages = 1;
            int offset = (page - 1) * pageSize;

            String empSql = "SELECT emp_id, emp_name, designation, department FROM employees WHERE 1=1";
            if (dept != null && !"All".equals(dept))
                empSql += " AND department = '" + dept + "'";
            empSql += " ORDER BY emp_name ASC LIMIT " + pageSize + " OFFSET " + offset;
            List<Map<String, Object>> employees = db.query(empSql);

            model.addAttribute("currentPage", page);
            model.addAttribute("totalPages", totalPages);
            model.addAttribute("pageSize", pageSize);
            model.addAttribute("totalItems", total);

            for (Map<String, Object> emp : employees) {
                String eid = (String) emp.get("emp_id");
                Map<String, Object> row = new HashMap<>(emp);
                Map<Integer, Map<String, Object>> attendanceMap = new HashMap<>();

                List<Map<String, Object>> att = db.query(
                        "SELECT DAY(punch_date) as d, status, in_time, out_time, work_hours FROM attendance WHERE emp_id=? AND MONTH(punch_date)=? AND YEAR(punch_date)=?",
                        eid, curM, curY);
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
                                if (inStr.contains(" "))
                                    in = inStr.split(" ")[1];
                                else if (inStr.contains("T"))
                                    in = inStr.split("T")[1];
                                if (in.length() > 5)
                                    in = in.substring(0, 5);

                                String out = "";
                                if (outStr.contains(" "))
                                    out = outStr.split(" ")[1];
                                else if (outStr.contains("T"))
                                    out = outStr.split("T")[1];
                                if (out.length() > 5)
                                    out = out.substring(0, 5);

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
            model.addAttribute("data", matrix);
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
        return "reports-monthly";
    }

    @GetMapping("/reports/leave")
    public String reportsLeave(Model model,
            @RequestParam(name = "from", required = false) String from,
            @RequestParam(name = "to", required = false) String to,
            @RequestParam(name = "dept", required = false) String dept,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "10") int pageSize,
            @RequestParam(name = "export", defaultValue = "false") boolean isExport) {
        if (isExport) {
            pageSize = 50000;
            page = 1;
        }
        List<Map<String, Object>> data = new java.util.ArrayList<>();
        try {
            DatabaseManager db = DatabaseManager.getInstance();
            int offset = (page - 1) * pageSize;
            String f = (from != null) ? from : java.time.LocalDate.now().withDayOfMonth(1).toString();
            String t = (to != null) ? to : java.time.LocalDate.now().toString();

            String where = " WHERE l.from_date BETWEEN ? AND ? ";
            if (dept != null && !"All".equals(dept))
                where += " AND e.department = '" + dept + "'";

            long total = db.queryLong("SELECT COUNT(*) FROM leaves l JOIN employees e ON l.emp_id = e.emp_id" + where,
                    f, t);
            int totalPages = (int) Math.ceil((double) total / pageSize);
            if (totalPages == 0)
                totalPages = 1;

            String sql = "SELECT l.*, e.emp_name, e.department FROM leaves l " +
                    "JOIN employees e ON l.emp_id = e.emp_id " + where +
                    " ORDER BY e.emp_name ASC, l.from_date DESC LIMIT " + pageSize + " OFFSET " + offset;

            data = db.query(sql, f, t);
            model.addAttribute("selFrom", f);
            model.addAttribute("selTo", t);
            model.addAttribute("selDept", (dept != null) ? dept : "All");
            model.addAttribute("depts", db.query("SELECT dept_name FROM departments ORDER BY dept_name"));
            model.addAttribute("currentPage", page);
            model.addAttribute("totalPages", totalPages);
            model.addAttribute("pageSize", pageSize);
            model.addAttribute("totalItems", total);
        } catch (Exception e) {
            e.printStackTrace();
        }
        model.addAttribute("data", data);
        return "reports-leave";
    }

    @GetMapping("/raw-logs")
    public String rawLogs(Model model,
            @RequestParam(name = "from", required = false) String from,
            @RequestParam(name = "to", required = false) String to,
            @RequestParam(name = "dept", required = false) String dept,
            @RequestParam(name = "emp", required = false) String emp,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "10") int pageSize,
            @RequestParam(name = "export", defaultValue = "false") boolean isExport) {
        if (isExport) {
            pageSize = 50000;
            page = 1;
        }
        return handleRawLogs(model, from, to, dept, emp, "raw-logs", page, pageSize);
    }

    @GetMapping("/raw-logs/report")
    public String rawLogsReport(Model model,
            @RequestParam(name = "from", required = false) String from,
            @RequestParam(name = "to", required = false) String to,
            @RequestParam(name = "dept", required = false) String dept,
            @RequestParam(name = "emp", required = false) String emp,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "10") int pageSize,
            @RequestParam(name = "export", defaultValue = "false") boolean isExport) {
        if (isExport) {
            pageSize = 50000;
            page = 1;
        }
        return handleRawLogs(model, from, to, dept, emp, "report-raw-logs", page, pageSize);
    }

    private String handleRawLogs(Model model, String from, String to, String dept, String emp, String viewName,
            int page, int pageSize) {
        try {
            DatabaseManager db = DatabaseManager.getInstance();
            String f = (from != null && !from.isEmpty()) ? from : java.time.LocalDate.now().toString();
            String t = (to != null && !to.isEmpty()) ? to : java.time.LocalDate.now().toString();
            String d = (dept != null && !dept.trim().isEmpty()) ? dept.trim() : "All";
            String eId = (emp != null && !emp.trim().isEmpty()) ? emp.trim() : "All";

            java.time.LocalDate startDate = java.time.LocalDate.parse(f);
            java.time.LocalDate endDate = java.time.LocalDate.parse(t);

            model.addAttribute("depts", db.query("SELECT dept_name FROM departments ORDER BY dept_name"));
            model.addAttribute("employees",
                    db.query("SELECT emp_id, emp_name FROM employees WHERE status='Active' ORDER BY emp_name"));

            String empBaseSql = "SELECT emp_id, emp_name, department, shift, device_enroll_id FROM employees WHERE status='Active'";
            if (!"All".equals(d))
                empBaseSql += " AND department = '" + d + "'";
            if (!"All".equals(eId))
                empBaseSql += " AND emp_id = '" + eId + "'";
            empBaseSql += " ORDER BY emp_name ASC";

            List<Map<String, Object>> activeEmps = db.query(empBaseSql);

            if (!"All".equals(eId) && !activeEmps.isEmpty()) {
                model.addAttribute("selEmpName", activeEmps.get(0).get("emp_name"));
            }

            List<Map<String, Object>> rawLogs = db.query(
                    "SELECT * FROM raw_logs WHERE DATE(punch_time) BETWEEN ? AND ? ORDER BY punch_time ASC", f, t);
            List<Map<String, Object>> allSessions = new ArrayList<>();
            java.time.format.DateTimeFormatter timeFmt = java.time.format.DateTimeFormatter.ofPattern("hh:mm a");

            Map<String, String> enrollMap = new HashMap<>();
            for (Map<String, Object> e : activeEmps) {
                String sid = DatabaseManager.str(e, "emp_id");
                String eid = DatabaseManager.str(e, "device_enroll_id");
                enrollMap.put(sid, sid);
                if (!eid.isEmpty())
                    enrollMap.put(eid, sid);
                try {
                    enrollMap.put(String.valueOf(Long.parseLong(sid)), sid);
                    if (!eid.isEmpty())
                        enrollMap.put(String.valueOf(Long.parseLong(eid)), sid);
                } catch (Exception ignored) {
                }
            }

            Map<String, List<Map<String, Object>>> logMap = new HashMap<>();
            for (Map<String, Object> log : rawLogs) {
                String fullTime = log.get("punch_time").toString().replace("T", " ");
                String date = fullTime.split(" ")[0];
                String rawEid = log.get("emp_id").toString().trim();
                String matchedSid = enrollMap.get(rawEid);
                if (matchedSid == null) {
                    try {
                        matchedSid = enrollMap.get(String.valueOf(Long.parseLong(rawEid)));
                    } catch (Exception ignored) {
                    }
                }
                if (matchedSid != null) {
                    String key = matchedSid + "_" + date;
                    logMap.computeIfAbsent(key, k -> new ArrayList<>()).add(log);
                }
            }

            for (Map<String, Object> employee : activeEmps) {
                String sid = (String) employee.get("emp_id");
                for (java.time.LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
                    String dateStr = date.toString();
                    String key = sid + "_" + dateStr;
                    List<Map<String, Object>> dayLogs = logMap.get(key);

                    if (dayLogs == null || dayLogs.isEmpty()) {
                        Map<String, Object> row = new HashMap<>(employee);
                        row.put("date", dateStr);
                        row.put("in_time", "—");
                        row.put("out_time", "—");
                        row.put("punch_count", 0);
                        row.put("status", "Absent");
                        allSessions.add(row);
                    } else {
                        int totalPunches = dayLogs.size();
                        for (int i = 0; i < dayLogs.size(); i += 2) {
                            Map<String, Object> session = new HashMap<>(employee);
                            session.put("date", dateStr);
                            session.put("punch_count", totalPunches);
                            session.put("status", "Present");
                            java.time.LocalDateTime inDt = java.time.LocalDateTime
                                    .parse(dayLogs.get(i).get("punch_time").toString().replace(" ", "T"));
                            session.put("in_time", inDt.format(timeFmt));
                            if (i + 1 < dayLogs.size()) {
                                java.time.LocalDateTime outDt = java.time.LocalDateTime
                                        .parse(dayLogs.get(i + 1).get("punch_time").toString().replace(" ", "T"));
                                session.put("out_time", outDt.format(timeFmt));
                            } else {
                                session.put("out_time", "Wait...");
                            }
                            allSessions.add(session);
                        }
                    }
                }
            }

            int total = allSessions.size();
            int totalPages = (int) Math.ceil((double) total / pageSize);
            if (totalPages == 0)
                totalPages = 1;

            int startIdx = (page - 1) * pageSize;
            int endIdx = Math.min(startIdx + pageSize, total);

            List<Map<String, Object>> pagedSessions = (startIdx < total) ? allSessions.subList(startIdx, endIdx)
                    : new ArrayList<>();

            model.addAttribute("sessions", pagedSessions);
            model.addAttribute("currentPage", page);
            model.addAttribute("totalPages", totalPages);
            model.addAttribute("pageSize", pageSize);
            model.addAttribute("totalItems", total);
            model.addAttribute("selFrom", f);
            model.addAttribute("selTo", t);
            model.addAttribute("selDept", d);
            model.addAttribute("selEmp", eId);
        } catch (Exception ex) {
            ex.printStackTrace();
            model.addAttribute("error", ex.getMessage());
        }
        return viewName;
    }

    @GetMapping("/devices")
    public String devices(Model model, HttpSession session,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "10") int pageSize) {
        if (session.getAttribute("user") == null)
            return "redirect:/login";
        try {
            DatabaseManager db = DatabaseManager.getInstance();
            int offset = (page - 1) * pageSize;

            long total = db.queryLong("SELECT COUNT(*) FROM devices");
            int totalPages = (int) Math.ceil((double) total / pageSize);
            if (totalPages == 0)
                totalPages = 1;

            List<Map<String, Object>> devices = db
                    .query("SELECT * FROM devices ORDER BY device_name LIMIT " + pageSize + " OFFSET " + offset);
            model.addAttribute("devices", devices);
            model.addAttribute("currentPage", page);
            model.addAttribute("totalPages", totalPages);
            model.addAttribute("pageSize", pageSize);
            model.addAttribute("totalItems", total);
        } catch (Exception e) {
            model.addAttribute("error", e.getMessage());
        }
        return "devices";
    }

    @PostMapping("/devices/save")
    public String saveDevice(@RequestParam Map<String, String> params) {
        try {
            DatabaseManager db = DatabaseManager.getInstance();
            String id = params.get("device_id");
            String name = params.get("device_name");
            String ip = params.get("ip_address");
            String port = params.get("port");
            String sn = params.get("serial_number");
            String loc = params.get("location");
            String status = params.get("status");

            if (id == null || id.isEmpty() || "0".equals(id)) {
                db.execute(
                        "INSERT INTO devices (device_name, ip_address, port, serial_number, location, status) VALUES (?,?,?,?,?,?)",
                        name, ip, port, sn, loc, status);
            } else {
                db.execute(
                        "UPDATE devices SET device_name=?, ip_address=?, port=?, serial_number=?, location=?, status=? WHERE device_id=?",
                        name, ip, port, sn, loc, status, id);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "redirect:/devices";
    }

    @PostMapping("/devices/delete")
    public String deleteDevice(@RequestParam("device_id") String id) {
        try {
            DatabaseManager.getInstance().execute("DELETE FROM devices WHERE device_id=?", id);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "redirect:/devices";
    }

    @GetMapping("/leave")
    public String leave(Model model) {
        return "redirect:/leave/requests";
    }

    @GetMapping("/leave/manager")
    public String leaveManager(Model model,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "10") int pageSize) {
        List<Map<String, Object>> leaves = new java.util.ArrayList<>();
        List<Map<String, Object>> types = new java.util.ArrayList<>();
        try {
            DatabaseManager db = DatabaseManager.getInstance();
            int offset = (page - 1) * pageSize;

            long total = db.queryLong("SELECT COUNT(*) FROM leaves");
            int totalPages = (int) Math.ceil((double) total / pageSize);
            if (totalPages == 0)
                totalPages = 1;

            leaves = db.query(
                    "SELECT l.*, e.emp_name FROM leaves l JOIN employees e ON l.emp_id=e.emp_id ORDER BY l.applied_on DESC LIMIT "
                            + pageSize + " OFFSET " + offset);
            types = db.query("SELECT leave_type FROM leave_policy WHERE status='Active'");

            model.addAttribute("currentPage", page);
            model.addAttribute("totalPages", totalPages);
            model.addAttribute("pageSize", pageSize);
            model.addAttribute("totalItems", total);
        } catch (Exception e) {
            model.addAttribute("error", e.getMessage());
        }
        model.addAttribute("leaves", leaves);
        model.addAttribute("leaveTypes", types);
        return "leave";
    }

    @GetMapping("/leave/requests")
    public String leaveRequests(Model model,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "10") int pageSize) {
        List<Map<String, Object>> leaves = new java.util.ArrayList<>();
        int pendingCount = 0;
        int approvedToday = 0;
        try {
            DatabaseManager db = DatabaseManager.getInstance();
            int offset = (page - 1) * pageSize;

            long total = db.queryLong("SELECT COUNT(*) FROM leaves WHERE status='Pending'");
            int totalPages = (int) Math.ceil((double) total / pageSize);
            if (totalPages == 0)
                totalPages = 1;

            leaves = db.query(
                    "SELECT l.*, e.emp_name FROM leaves l JOIN employees e ON l.emp_id=e.emp_id WHERE l.status='Pending' ORDER BY l.applied_on ASC LIMIT "
                            + pageSize + " OFFSET " + offset);

            pendingCount = (int) total;

            List<Map<String, Object>> aStats = db.query(
                    "SELECT COUNT(*) as cnt FROM leaves WHERE status='Approved' AND DATE(applied_on) = CURDATE()");
            if (!aStats.isEmpty())
                approvedToday = ((Number) aStats.get(0).get("cnt")).intValue();

            model.addAttribute("currentPage", page);
            model.addAttribute("totalPages", totalPages);
            model.addAttribute("pageSize", pageSize);
            model.addAttribute("totalItems", total);
        } catch (Exception e) {
            model.addAttribute("error", e.getMessage());
        }
        model.addAttribute("leaves", leaves);
        model.addAttribute("pendingCount", pendingCount);
        model.addAttribute("approvedToday", approvedToday);
        return "leave-requests";
    }

    @PostMapping("/leave/requests/process")
    public String processLeave(@RequestParam("id") String id, @RequestParam("status") String status,
            @RequestParam(value = "comment", required = false) String comment) {
        try {
            DatabaseManager db = DatabaseManager.getInstance();
            // Try to add column if it doesn't exist (compatible with older MySQL)
            try {
                db.execute("ALTER TABLE leaves ADD reject_reason TEXT");
            } catch (Exception ex) {
            }

            db.execute("UPDATE leaves SET status=?, reject_reason=? WHERE id=?", status, comment, id);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "redirect:/leave/requests";
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
                db.execute(
                        "INSERT INTO leaves (emp_id, leave_type, from_date, to_date, days, reason, status, applied_on) VALUES (?,?,?,?,?,?,?,NOW())",
                        empId, type, from, to, days, reason, status);
            } else {
                db.execute(
                        "UPDATE leaves SET emp_id=?, leave_type=?, from_date=?, to_date=?, days=?, reason=?, status=? WHERE id=?",
                        empId, type, from, to, days, reason, status, id);
            }

            // Desktop App Logic: Sync with attendance if approved
            if ("Approved".equals(status)) {
                java.time.LocalDate start = java.time.LocalDate.parse(from);
                java.time.LocalDate end = java.time.LocalDate.parse(to);
                for (java.time.LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
                    db.execute(
                            "INSERT INTO attendance (emp_id, punch_date, status, remarks) VALUES (?,?,?,?) ON DUPLICATE KEY UPDATE status=?, remarks=?",
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
                        db.execute(
                                "INSERT INTO attendance (emp_id, punch_date, status, remarks) VALUES (?,?,?,?) ON DUPLICATE KEY UPDATE status=?, remarks=?",
                                empId, d.toString(), type, "Leave Approved", type, "Leave Approved");
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "redirect:/leave";
    }

    @PostMapping("/leave/delete")
    public String deleteLeave(@RequestParam("id") String id) {
        try {
            System.out.println("Deleting leave request ID: " + id);
            DatabaseManager db = DatabaseManager.getInstance();
            db.execute("DELETE FROM leaves WHERE id=?", id);
        } catch (Exception e) {
            System.err.println("Error deleting leave: " + e.getMessage());
            e.printStackTrace();
        }
        return "redirect:/leave/manager";
    }

    // Leave Sub-menus

    @GetMapping("/leave/od")
    public String leaveOD(Model model,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "10") int pageSize) {
        List<Map<String, Object>> depts = new java.util.ArrayList<>();
        List<Map<String, Object>> ods = new java.util.ArrayList<>();
        List<Map<String, Object>> employees = new java.util.ArrayList<>();
        try {
            DatabaseManager db = DatabaseManager.getInstance();
            int offset = (page - 1) * pageSize;

            depts = db.query("SELECT dept_name FROM departments WHERE status='Active'");
            employees = db.query("SELECT emp_id, emp_name FROM employees WHERE status='Active'");

            long total = db.queryLong("SELECT COUNT(*) FROM od_requests");
            int totalPages = (int) Math.ceil((double) total / pageSize);
            if (totalPages == 0)
                totalPages = 1;

            ods = db.query(
                    "SELECT o.*, e.emp_name, e.department FROM od_requests o JOIN employees e ON o.emp_id = e.emp_id ORDER BY o.applied_on DESC LIMIT "
                            + pageSize + " OFFSET " + offset);

            model.addAttribute("currentPage", page);
            model.addAttribute("totalPages", totalPages);
            model.addAttribute("pageSize", pageSize);
            model.addAttribute("totalItems", total);
        } catch (Exception e) {
            model.addAttribute("error", e.getMessage());
        }
        model.addAttribute("depts", depts);
        model.addAttribute("ods", ods);
        model.addAttribute("employees", employees);
        return "leave-od";
    }

    @PostMapping("/leave/od/apply")
    public String applyOD(
            @RequestParam(required = false) String id,
            @RequestParam("emp_id") String empId,
            @RequestParam("od_from") String odFrom,
            @RequestParam("od_to") String odTo,
            @RequestParam("od_days") double odDays,
            @RequestParam("od_type") String odType,
            @RequestParam("location") String location) {
        try {
            System.out.println("Processing OD Application: emp=" + empId + ", from=" + odFrom + ", to=" + odTo
                    + ", days=" + odDays + ", type=" + odType + ", loc=" + location + ", id=" + id);
            DatabaseManager db = DatabaseManager.getInstance();
            if (id != null && !id.trim().isEmpty()) {
                db.execute(
                        "UPDATE od_requests SET emp_id=?, od_from=?, od_to=?, od_days=?, od_type=?, location=? WHERE id=?",
                        empId, odFrom, odTo, odDays, odType, location, id);
                System.out.println("OD Request updated successfully: ID " + id);
            } else {
                db.execute(
                        "INSERT INTO od_requests (emp_id, od_from, od_to, od_days, od_type, location, status, applied_on) VALUES (?, ?, ?, ?, ?, ?, 'Pending', CURDATE())",
                        empId, odFrom, odTo, odDays, odType, location);
                System.out.println("New OD Request inserted successfully for " + empId);
            }
        } catch (Exception e) {
            System.err.println("Error processing OD application: " + e.getMessage());
            e.printStackTrace();
        }
        return "redirect:/leave/od";
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
                            "SELECT s.work_hours FROM employees e JOIN shifts s ON e.shift=s.shift_name WHERE e.emp_id=?",
                            eid);
                    double wh = (emp != null) ? Double.parseDouble(emp.get("work_hours").toString()) : 8.0;

                    for (java.time.LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
                        db.execute("INSERT INTO attendance (emp_id, punch_date, status, work_hours, remarks) " +
                                "VALUES (?,?,?,?,'OD Approved') ON DUPLICATE KEY UPDATE status=?, work_hours=?, remarks='OD Approved'",
                                eid, d.toString(), "OD", wh, "OD", wh);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "redirect:/leave/od";
    }

    @PostMapping("/leave/od/delete")
    public String deleteOD(@RequestParam("id") String id) {
        try {
            System.out.println("Deleting OD request ID: " + id);
            DatabaseManager db = DatabaseManager.getInstance();
            db.execute("DELETE FROM od_requests WHERE id=?", id);
        } catch (Exception e) {
            System.err.println("Error deleting OD: " + e.getMessage());
            e.printStackTrace();
        }
        return "redirect:/leave/od";
    }

    @GetMapping("/leave/policy")
    public String leavePolicy(Model model,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "10") int pageSize) {
        List<Map<String, Object>> policies = new java.util.ArrayList<>();
        try {
            DatabaseManager db = DatabaseManager.getInstance();
            int offset = (page - 1) * pageSize;

            long total = db.queryLong("SELECT COUNT(*) FROM leave_policy");
            int totalPages = (int) Math.ceil((double) total / pageSize);
            if (totalPages == 0)
                totalPages = 1;

            policies = db.query("SELECT * FROM leave_policy ORDER BY id LIMIT " + pageSize + " OFFSET " + offset);
            System.out.println("Fetched " + policies.size() + " leave policies.");
            model.addAttribute("currentPage", page);
            model.addAttribute("totalPages", totalPages);
            model.addAttribute("pageSize", pageSize);
            model.addAttribute("totalItems", total);
        } catch (Exception e) {
            e.printStackTrace();
            model.addAttribute("error", e.getMessage());
        }
        model.addAttribute("policies", policies);
        return "leave-policy";
    }

    @PostMapping("/leave/policy/save")
    public String savePolicy(@RequestParam Map<String, String> params) {
        try {
            DatabaseManager db = DatabaseManager.getInstance();
            System.out.println("Leave Policy Save Params: " + params);

            String id = params.get("id");
            String leaveType = params.get("leave_type");
            String credit = params.get("credit_method");
            String gender = params.get("applicable_gender");
            String status = params.get("status");
            String desc = params.getOrDefault("description", "");

            // Safe parsing helper
            double days = 0;
            try {
                String val = params.get("days_per_year");
                if (val != null && !val.isEmpty())
                    days = Double.parseDouble(val);
            } catch (Exception e) {
            }

            double maxCarry = 0;
            try {
                String val = params.get("max_carry");
                if (val != null && !val.isEmpty())
                    maxCarry = Double.parseDouble(val);
            } catch (Exception e) {
            }

            int expire = 0;
            try {
                String val = params.get("expire_months");
                if (val != null && !val.isEmpty())
                    expire = Integer.parseInt(val);
            } catch (Exception e) {
            }

            int minSvc = 0;
            try {
                String val = params.get("min_service_days");
                if (val != null && !val.isEmpty())
                    minSvc = Integer.parseInt(val);
            } catch (Exception e) {
            }

            // Checkboxes
            int carry = params.containsKey("carry_forward") ? 1 : 0;
            int encash = params.containsKey("encashable") ? 1 : 0;
            int proRata = params.containsKey("pro_rata") ? 1 : 0;

            System.out.println("Saving policy: " + leaveType + " (ID: " + id + ")");

            if (id != null && !id.trim().isEmpty()) {
                db.execute(
                        "UPDATE leave_policy SET leave_type=?, days_per_year=?, credit_method=?, carry_forward=?, max_carry=?, expire_months=?, encashable=?, pro_rata=?, applicable_gender=?, status=?, min_service_days=?, description=? WHERE id=?",
                        leaveType, days, credit, carry, maxCarry, expire, encash, proRata, gender, status, minSvc, desc,
                        id);
            } else {
                db.execute(
                        "INSERT INTO leave_policy (leave_type, days_per_year, credit_method, carry_forward, max_carry, expire_months, encashable, pro_rata, applicable_gender, status, min_service_days, description) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                        leaveType, days, credit, carry, maxCarry, expire, encash, proRata, gender, status, minSvc,
                        desc);
            }
        } catch (Exception e) {
            System.err.println("Error saving leave policy: " + e.getMessage());
            e.printStackTrace();
        }
        return "redirect:/leave/policy";
    }

    @PostMapping("/leave/policy/delete")
    public String deletePolicy(@RequestParam("id") String id) {
        try {
            DatabaseManager db = DatabaseManager.getInstance();
            db.execute("DELETE FROM leave_policy WHERE id=?", id);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "redirect:/leave/policy";
    }

    @PostMapping("/leave/balance/credit")
    public String creditLeaves(@RequestParam("type") String type, @RequestParam("amount") double amount) {
        try {
            DatabaseManager db = DatabaseManager.getInstance();
            int curY = java.time.LocalDate.now().getYear();
            List<Map<String, Object>> emps = db.query("SELECT emp_id FROM employees WHERE status='Active'");
            for (Map<String, Object> e : emps) {
                String empId = e.get("emp_id").toString();
                int rows = db.execute(
                        "UPDATE leave_balance SET credited = credited + ?, closing_bal = closing_bal + ? WHERE emp_id=? AND leave_type=? AND year=?",
                        amount, amount, empId, type, String.valueOf(curY));
                if (rows == 0) {
                    db.execute(
                            "INSERT INTO leave_balance (emp_id, leave_type, year, opening_bal, credited, used, lapsed, closing_bal) VALUES (?, ?, ?, 0, ?, 0, 0, ?)",
                            empId, type, String.valueOf(curY), amount, amount);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "redirect:/leave/balance";
    }

    @PostMapping("/leave/balance/adjust")
    public String adjustBalance(@RequestParam("empId") String empId, @RequestParam("type") String type,
            @RequestParam("year") String year, @RequestParam("newBalance") double newBalance) {
        try {
            DatabaseManager db = DatabaseManager.getInstance();
            db.execute("UPDATE leave_balance SET closing_bal = ? WHERE emp_id=? AND leave_type=? AND year=?",
                    newBalance, empId, type, year);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "redirect:/leave/balance";
    }

    @GetMapping("/leave/balance")
    public String leaveBalance(Model model,
            @RequestParam(name = "year", required = false) String year,
            @RequestParam(name = "dept", required = false) String dept,
            @RequestParam(name = "type", required = false) String type,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "10") int pageSize,
            @RequestParam(name = "export", defaultValue = "false") boolean isExport) {
        if (isExport) {
            pageSize = 50000;
            page = 1;
        }
        try {
            DatabaseManager db = DatabaseManager.getInstance();
            int offset = (page - 1) * pageSize;
            int curY = java.time.LocalDate.now().getYear();
            String filterYear = (year != null) ? year : String.valueOf(curY);

            List<Map<String, Object>> depts = db
                    .query("SELECT dept_name FROM departments WHERE status='Active' ORDER BY dept_name");
            List<Map<String, Object>> types = db
                    .query("SELECT leave_type FROM leave_policy WHERE status='Active' ORDER BY leave_type");

            StringBuilder where = new StringBuilder(" WHERE b.year = ?");
            if (dept != null && !"All".equals(dept))
                where.append(" AND e.department = '").append(dept).append("'");
            if (type != null && !"All".equals(type))
                where.append(" AND b.leave_type = '").append(type).append("'");

            long total = db.queryLong(
                    "SELECT COUNT(*) FROM leave_balance b JOIN employees e ON b.emp_id = e.emp_id" + where.toString(),
                    filterYear);
            int totalPages = (int) Math.ceil((double) total / pageSize);
            if (totalPages == 0)
                totalPages = 1;

            String sql = "SELECT b.*, e.emp_name, e.department FROM leave_balance b " +
                    "JOIN employees e ON b.emp_id = e.emp_id " + where.toString() +
                    " ORDER BY e.department, e.emp_id, b.leave_type LIMIT " + pageSize + " OFFSET " + offset;

            List<Map<String, Object>> balances = db.query(sql, filterYear);

            // Summary Stats (Always calculate on full set or pre-calculate if needed, but
            // for now we keep it simple)
            // Actually, stats should be on the FILTERED set, not just the page.
            List<Map<String, Object>> allBalances = db
                    .query("SELECT b.*, e.emp_id FROM leave_balance b JOIN employees e ON b.emp_id = e.emp_id"
                            + where.toString(), filterYear);

            double tBal = 0, tUsed = 0, tLapsed = 0;
            java.util.Set<String> uniqueEmps = new java.util.HashSet<>();
            if (allBalances != null) {
                for (Map<String, Object> r : allBalances) {
                    tBal += DatabaseManager.dbl(r, "closing_bal");
                    tUsed += DatabaseManager.dbl(r, "used");
                    tLapsed += DatabaseManager.dbl(r, "lapsed");
                    uniqueEmps.add(DatabaseManager.str(r, "emp_id"));
                }
            }
            long totalActive = db.queryLong("SELECT COUNT(*) FROM employees WHERE status='Active'");

            model.addAttribute("balances", balances);
            model.addAttribute("depts", depts);
            model.addAttribute("types", types);
            model.addAttribute("selYear", filterYear);
            model.addAttribute("selDept", (dept != null) ? dept : "All");
            model.addAttribute("selType", (type != null) ? type : "All");
            model.addAttribute("currentPage", page);
            model.addAttribute("totalPages", totalPages);
            model.addAttribute("pageSize", pageSize);
            model.addAttribute("totalItems", total);
            model.addAttribute("stats", Map.of(
                    "empCount", uniqueEmps.size() + " / " + totalActive,
                    "totalBal", String.format("%.1f", tBal),
                    "totalUsed", String.format("%.1f", tUsed),
                    "totalLapsed", String.format("%.1f", tLapsed)));
        } catch (Exception e) {
            model.addAttribute("error", e.getMessage());
        }
        return "leave-balance";
    }

    @GetMapping("/leave/holidays")
    public String leaveHolidays(Model model,
            @RequestParam(name = "type", required = false) String type,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "10") int pageSize) {
        List<Map<String, Object>> holidays = new java.util.ArrayList<>();
        try {
            DatabaseManager db = DatabaseManager.getInstance();
            int offset = (page - 1) * pageSize;

            String where = "";
            if (type != null && !"All".equals(type)) {
                where = " WHERE holiday_type = '" + type + "'";
            }

            long total = db.queryLong("SELECT COUNT(*) FROM holidays" + where);
            int totalPages = (int) Math.ceil((double) total / pageSize);
            if (totalPages == 0)
                totalPages = 1;

            String sql = "SELECT * FROM holidays" + where + " ORDER BY holiday_date LIMIT " + pageSize + " OFFSET "
                    + offset;
            holidays = db.query(sql);

            model.addAttribute("selType", (type != null) ? type : "All");
            model.addAttribute("currentPage", page);
            model.addAttribute("totalPages", totalPages);
            model.addAttribute("pageSize", pageSize);
            model.addAttribute("totalItems", total);
        } catch (Exception e) {
            model.addAttribute("error", e.getMessage());
        }
        model.addAttribute("holidays", holidays);
        return "holiday";
    }

    @PostMapping("/leave/holidays/save")
    public String saveHoliday(@RequestParam(required = false) String id,
            @RequestParam String holiday_date,
            @RequestParam String holiday_name,
            @RequestParam String holiday_type) {
        try {
            DatabaseManager db = DatabaseManager.getInstance();
            if (id != null && !id.trim().isEmpty()) {
                db.execute("UPDATE holidays SET holiday_date=?, holiday_name=?, holiday_type=? WHERE id=?",
                        holiday_date, holiday_name, holiday_type, id);
            } else {
                db.execute("INSERT INTO holidays (holiday_date, holiday_name, holiday_type) VALUES (?,?,?)",
                        holiday_date, holiday_name, holiday_type);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "redirect:/leave/holidays";
    }

    @PostMapping("/leave/holidays/delete")
    public String deleteHoliday(@RequestParam("id") String id) {
        try {
            System.out.println("Deleting holiday ID: " + id);
            DatabaseManager db = DatabaseManager.getInstance();
            db.execute("DELETE FROM holidays WHERE id=?", id);
        } catch (Exception e) {
            System.err.println("Error deleting holiday: " + e.getMessage());
            e.printStackTrace();
        }
        return "redirect:/leave/holidays";
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
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "redirect:/leave/holidays";
    }

    @GetMapping("/masters")
    public String masters(Model model) {
        return "masters";
    }

    @GetMapping("/system")
    public String system(Model model) {
        boolean isRunning = com.bhspl.service.PushService.isRunning();
        boolean isInternal = com.bhspl.service.PushService.isInternalRunning();
        String statusText = isInternal ? "Active (Listening)" : (isRunning ? "Active (Handled by Worker)" : "Inactive");

        model.addAttribute("admsStatus", statusText);
        model.addAttribute("admsPort", com.bhspl.service.PushService.getPort());
        model.addAttribute("admsError", com.bhspl.service.PushService.getLastError());
        return "system";
    }

    @GetMapping("/masters/departments")
    public String mastersDepts(Model model, 
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "10") int pageSize) {
        List<Map<String, Object>> depts = new java.util.ArrayList<>();
        try {
            DatabaseManager db = DatabaseManager.getInstance();
            int offset = (page - 1) * pageSize;

            long total = db.queryLong("SELECT COUNT(*) FROM departments");
            int totalPages = (int) Math.ceil((double) total / pageSize);
            if (totalPages == 0)
                totalPages = 1;

            depts = db.query("SELECT * FROM departments ORDER BY dept_name LIMIT " + pageSize + " OFFSET " + offset);
            model.addAttribute("currentPage", page);
            model.addAttribute("totalPages", totalPages);
            model.addAttribute("pageSize", pageSize);
            model.addAttribute("totalItems", total);
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
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "redirect:/masters/departments";
    }

    @PostMapping("/masters/departments/delete")
    public String deleteDept(@RequestParam String id) {
        try {
            DatabaseManager db = DatabaseManager.getInstance();
            db.execute("DELETE FROM departments WHERE id=?", id);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "redirect:/masters/departments";
    }

    @GetMapping("/masters/designations")
    public String mastersDesig(Model model,
            @RequestParam(name = "search", required = false) String search,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "10") int pageSize) {
        List<Map<String, Object>> desigs = new java.util.ArrayList<>();
        try {
            DatabaseManager db = DatabaseManager.getInstance();
            int offset = (page - 1) * pageSize;

            String where = "";
            String orderBy = "level_order ASC, desig_name ASC";
            if (search != null && !search.isEmpty()) {
                where = " WHERE desig_name LIKE '%" + search + "%'";
                orderBy = "CASE WHEN LOWER(desig_name) LIKE LOWER('" + search + "%') THEN 0 ELSE 1 END, " + orderBy;
            }

            long total = db.queryLong("SELECT COUNT(*) FROM designations" + where);
            int totalPages = (int) Math.ceil((double) total / pageSize);
            if (totalPages == 0)
                totalPages = 1;

            String sql = "SELECT * FROM designations" + where + " ORDER BY " + orderBy + " LIMIT "
                    + pageSize + " OFFSET " + offset;
            desigs = db.query(sql);

            model.addAttribute("currentPage", page);
            model.addAttribute("totalPages", totalPages);
            model.addAttribute("pageSize", pageSize);
            model.addAttribute("totalItems", total);
            model.addAttribute("selSearch", search);
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
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "redirect:/masters/designations";
    }

    @PostMapping("/masters/designations/delete")
    public String deleteDesig(@RequestParam String id) {
        try {
            DatabaseManager db = DatabaseManager.getInstance();
            db.execute("DELETE FROM designations WHERE id=?", id);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "redirect:/masters/designations";
    }

    @GetMapping("/masters/shifts")
    public String mastersShifts(Model model, 
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "10") int pageSize) {
        List<Map<String, Object>> shifts = new java.util.ArrayList<>();
        try {
            DatabaseManager db = DatabaseManager.getInstance();
            int offset = (page - 1) * pageSize;

            long total = db.queryLong("SELECT COUNT(*) FROM shifts");
            int totalPages = (int) Math.ceil((double) total / pageSize);
            if (totalPages == 0)
                totalPages = 1;

            shifts = db.query("SELECT * FROM shifts ORDER BY shift_name LIMIT " + pageSize + " OFFSET " + offset);
            model.addAttribute("currentPage", page);
            model.addAttribute("totalPages", totalPages);
            model.addAttribute("pageSize", pageSize);
            model.addAttribute("totalItems", total);
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
                db.execute(
                        "INSERT INTO shifts (shift_name, start_time, end_time, break_mins, grace_mins, weekly_off1, weekly_off2) VALUES (?,?,?,?,?,?,?)",
                        name, start, end, breakM, grace, off1, off2);
            } else {
                db.execute(
                        "UPDATE shifts SET shift_name=?, start_time=?, end_time=?, break_mins=?, grace_mins=?, weekly_off1=?, weekly_off2=? WHERE id=?",
                        name, start, end, breakM, grace, off1, off2, id);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "redirect:/masters/shifts";
    }

    @PostMapping("/masters/shifts/delete")
    public String deleteShift(@RequestParam String id) {
        try {
            DatabaseManager db = DatabaseManager.getInstance();
            db.execute("DELETE FROM shifts WHERE id=?", id);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "redirect:/masters/shifts";
    }

    @GetMapping("/masters/weekly-off")
    public String mastersWeeklyOff(Model model,
            @RequestParam(name = "dept", required = false) String dept,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "10") int pageSize) {
        List<Map<String, Object>> depts = new java.util.ArrayList<>();
        List<Map<String, Object>> offs = new java.util.ArrayList<>();
        List<Map<String, Object>> employees = new java.util.ArrayList<>();
        try {
            DatabaseManager db = DatabaseManager.getInstance();
            int offset = (page - 1) * pageSize;

            depts = db.query("SELECT dept_name FROM departments ORDER BY dept_name");

            String where = "";
            if (dept != null && !"All".equals(dept))
                where = " WHERE e.department = '" + dept + "'";

            long total = db
                    .queryLong("SELECT COUNT(*) FROM weekly_offs w JOIN employees e ON w.emp_id = e.emp_id" + where);
            int totalPages = (int) Math.ceil((double) total / pageSize);
            if (totalPages == 0)
                totalPages = 1;

            String sql = "SELECT w.*, e.emp_name, e.department as dept_name " +
                    "FROM weekly_offs w " +
                    "JOIN employees e ON w.emp_id = e.emp_id" + where + " LIMIT " + pageSize + " OFFSET " + offset;

            offs = db.query(sql);
            employees = db.query("SELECT emp_id, emp_name FROM employees ORDER BY emp_name");

            model.addAttribute("currentPage", page);
            model.addAttribute("totalPages", totalPages);
            model.addAttribute("pageSize", pageSize);
            model.addAttribute("totalItems", total);
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
            db.execute(
                    "INSERT INTO weekly_offs (emp_id, off_day1, off_day2, effective_from, effective_to, remarks) VALUES (?,?,?,?,?,?)",
                    params.get("empId"), params.get("day1"), params.get("day2"), params.get("from"), params.get("to"),
                    params.get("remarks"));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "redirect:/masters/weekly-off";
    }

    @PostMapping("/masters/weekly-off/delete")
    public String deleteOff(@RequestParam String id) {
        try {
            DatabaseManager db = DatabaseManager.getInstance();
            db.execute("DELETE FROM weekly_offs WHERE id=?", id);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "redirect:/masters/weekly-off";
    }

    // System Sub-menus
    @GetMapping("/system/users")
    public String systemUsers(Model model, 
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "10") int pageSize) {
        List<Map<String, Object>> users = new java.util.ArrayList<>();
        try {
            DatabaseManager db = DatabaseManager.getInstance();
            int offset = (page - 1) * pageSize;

            long total = db.queryLong("SELECT COUNT(*) FROM users");
            int totalPages = (int) Math.ceil((double) total / pageSize);
            if (totalPages == 0)
                totalPages = 1;

            users = db.query("SELECT id, username, role, emp_id, status, last_login FROM users ORDER BY username LIMIT "
                    + pageSize + " OFFSET " + offset);
            model.addAttribute("currentPage", page);
            model.addAttribute("totalPages", totalPages);
            model.addAttribute("pageSize", pageSize);
            model.addAttribute("totalItems", total);
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
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "redirect:/system/users";
    }

    @PostMapping("/system/users/reset-password")
    public String resetUserPassword(@RequestParam("id") String id, @RequestParam("password") String password) {
        try {
            DatabaseManager db = DatabaseManager.getInstance();
            String ph = db.hashPw(password);
            db.execute("UPDATE users SET password_hash=? WHERE id=?", ph, id);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "redirect:/system/users";
    }

    @PostMapping("/system/users/toggle-status")
    public String toggleUserStatus(@RequestParam("id") String id, @RequestParam("status") String status) {
        try {
            DatabaseManager db = DatabaseManager.getInstance();
            String newStatus = "Active".equals(status) ? "Inactive" : "Active";
            db.execute("UPDATE users SET status=? WHERE id=?", newStatus, id);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "redirect:/system/users";
    }

    @PostMapping("/system/users/delete")
    public String deleteUser(@RequestParam String id) {
        try {
            DatabaseManager db = DatabaseManager.getInstance();
            db.execute("DELETE FROM users WHERE id=?", id);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "redirect:/system/users";
    }

    @GetMapping("/system/settings")
    public String systemSettings(Model model) {
        // Ensure ADMS/Push Service is running - Only if not manually stopped
        if (!com.bhspl.service.PushService.isInternalRunning() && !com.bhspl.service.PushService.isUserStopped()) {
            com.bhspl.service.PushService.start();
        }

        model.addAttribute("appName", "BHSPL Attendance Management System");
        model.addAttribute("version", "2.0.42 (Enterprise)");
        model.addAttribute("runtime", "Java 17 (OpenJDK)");
        model.addAttribute("framework", "Spring Boot 3.x / Web Interface");
        model.addAttribute("db", "MySQL 8.0 (Connector/J)");
        boolean isInternal = com.bhspl.service.PushService.isInternalRunning();
        boolean isExternal = com.bhspl.service.PushService.isRunning();
        String statusText = isInternal ? "Active (Listening)" : (isExternal ? "Occupied (External)" : "Inactive");

        model.addAttribute("admsStatus", statusText);
        model.addAttribute("admsPort", com.bhspl.service.PushService.getPort());
        model.addAttribute("admsError", com.bhspl.service.PushService.getLastError());
        return "system-settings";
    }

    @PostMapping("/system/settings/start-adms")
    @GetMapping("/system/settings/start-adms")
    public String startAdms(Model model) {
        try {
            com.bhspl.service.PushService.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "redirect:/system/settings";
    }

    @PostMapping("/system/settings/stop-adms")
    @GetMapping("/system/settings/stop-adms")
    public String stopAdms() {
        try {
            com.bhspl.service.PushService.stop();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "redirect:/system/settings";
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
            } catch (Exception ignored) {
            }

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
            sb.append("Attendance (May 2026): ")
                    .append(db.queryLong(
                            "SELECT COUNT(*) FROM attendance WHERE MONTH(punch_date)=5 AND YEAR(punch_date)=2026"))
                    .append("\n");

            sb.append("\n--- RAW LOGS ID SAMPLE ---\n");
            List<Map<String, Object>> idSample = db
                    .query("SELECT DISTINCT emp_id FROM raw_logs WHERE emp_id != '0' LIMIT 5");
            for (Map<String, Object> id : idSample) {
                String val = id.get("emp_id").toString();
                long count = db.queryLong("SELECT COUNT(*) FROM employees WHERE emp_id = ?", val);
                long numericCount = db.queryLong(
                        "SELECT COUNT(*) FROM employees WHERE CAST(emp_id AS UNSIGNED) = CAST(? AS UNSIGNED)", val);
                sb.append("Log ID: [").append(val).append("] | Matches in Master: ").append(count)
                        .append(" | Numeric Match: ").append(numericCount).append("\n");
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
            List<Map<String, Object>> attSample = db.query(
                    "SELECT emp_id, punch_date, status, in_time, out_time, work_hours FROM attendance WHERE MONTH(punch_date)=5 AND YEAR(punch_date)=2026 LIMIT 5");
            if (attSample.isEmpty()) {
                sb.append("NO RECORDS FOUND FOR MAY 2026!\n");
            } else {
                for (Map<String, Object> a : attSample) {
                    sb.append(a.get("emp_id")).append(" | ").append(a.get("punch_date")).append(" | ")
                            .append(a.get("status"))
                            .append(" | IN: [").append(a.get("in_time")).append("] | OUT: [").append(a.get("out_time"))
                            .append("]\n");
                }
            }

            sb.append("\n--- SAMPLE DATA (Employees) ---\n");
            List<Map<String, Object>> emps = db.query("SELECT * FROM employees LIMIT 5");
            for (Map<String, Object> e : emps) {
                sb.append(e.get("emp_id")).append(" | ").append(e.get("emp_name")).append(" | ").append(e.get("status"))
                        .append("\n");
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
            @RequestParam("month") int month,
            @RequestParam("year") int year,
            @RequestParam("dept") String dept,
            @RequestParam("type") String type,
            @RequestParam("format") String format,
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
        if (dept != null && !"All".equals(dept))
            sql += " AND department = '" + dept + "'";
        sql += " ORDER BY emp_name";

        List<Map<String, Object>> emps = db.query(sql);
        for (Map<String, Object> e : emps) {
            String eid = DatabaseManager.str(e, "emp_id");
            writer.print(eid + "," + DatabaseManager.str(e, "emp_name") + "," + DatabaseManager.str(e, "designation"));

            Map<Integer, String> attMap = new HashMap<>();
            List<Map<String, Object>> att = db.query(
                    "SELECT DAY(punch_date) as d, status, in_time, out_time, work_hours FROM attendance WHERE emp_id=? AND MONTH(punch_date)=? AND YEAR(punch_date)=?",
                    eid, month, year);
            for (Map<String, Object> a : att) {
                if (type.equals("WH")) {
                    String inStr = DatabaseManager.str(a, "in_time");
                    String in = inStr.contains(" ") ? inStr.split(" ")[1]
                            : (inStr.contains("T") ? inStr.split("T")[1] : "");
                    if (in.length() > 5)
                        in = in.substring(0, 5);
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
    public String systemSync(@RequestParam(name = "redirect", required = false) String redirect) {
        try {
            SyncService.performSync();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "redirect:" + (redirect != null ? redirect : "/");
    }

    @GetMapping("/logo-reveal")
    public String logoReveal() {
        return "logo-reveal";
    }
}
