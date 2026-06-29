package com.bhspl.web;

import com.bhspl.db.DatabaseManager;
import com.bhspl.service.SyncService;
import com.bhspl.util.CacheManager;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.http.ResponseEntity;
import jakarta.servlet.http.HttpSession;
import java.util.HashSet;
import java.util.Set;
import org.springframework.web.multipart.MultipartFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import java.util.UUID;

@Controller
public class WebController {

    @org.springframework.beans.factory.annotation.Autowired
    private com.bhspl.service.LeaveService leaveService;

    public static final java.util.Map<String, java.time.LocalDateTime> activeUsers = new java.util.concurrent.ConcurrentHashMap<>();

    private void updateActiveUser(HttpSession session) {
        if (session != null) {
            String username = (String) session.getAttribute("user");
            if (username != null) {
                activeUsers.put(username, java.time.LocalDateTime.now());
            }
        }
    }

    private java.util.List<java.util.Map<String, Object>> getActiveUsersList() {
        java.util.List<java.util.Map<String, Object>> list = new java.util.ArrayList<>();
        java.time.LocalDateTime cutoff = java.time.LocalDateTime.now().minusMinutes(15);
        activeUsers.entrySet().removeIf(e -> e.getValue().isBefore(cutoff));
        try {
            DatabaseManager db = DatabaseManager.getInstance();
            for (java.util.Map.Entry<String, java.time.LocalDateTime> entry : activeUsers.entrySet()) {
                String username = entry.getKey();
                java.time.LocalDateTime lastActive = entry.getValue();
                java.util.Map<String, Object> userDetails = db.queryOne("SELECT role FROM users WHERE username=?",
                        username);
                String role = userDetails != null ? (String) userDetails.get("role") : "Operator";
                java.util.Map<String, Object> m = new java.util.HashMap<>();
                m.put("username", username);
                m.put("role", role);
                java.time.format.DateTimeFormatter dtf = java.time.format.DateTimeFormatter.ofPattern("hh:mm a");
                m.put("lastActive", lastActive.format(dtf));
                list.add(m);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    private void logActivity(String username, String role, jakarta.servlet.http.HttpServletRequest request,
            String action, String moduleName, String description) {
        try {
            String ipAddress = "0.0.0.0";
            String userAgent = "Unknown";
            if (request != null) {
                ipAddress = request.getRemoteAddr();
                String xff = request.getHeader("X-Forwarded-For");
                if (xff != null && !xff.isEmpty()) {
                    ipAddress = xff.split(",")[0].trim();
                }
                userAgent = request.getHeader("User-Agent");
                if (userAgent != null && userAgent.length() > 255) {
                    userAgent = userAgent.substring(0, 255);
                }
            }
            java.time.format.DateTimeFormatter dtf = java.time.format.DateTimeFormatter
                    .ofPattern("yyyy-MM-dd HH:mm:ss");
            String currentTime = java.time.LocalDateTime.now().format(dtf);
            DatabaseManager.getInstance().execute(
                    "INSERT INTO activity_logs (username, role, action, module_name, description, ip_address, user_agent, created_at) VALUES (?,?,?,?,?,?,?,?)",
                    username != null ? username : "System/Anonymous",
                    role != null ? role : "None",
                    action, moduleName, description, ipAddress, userAgent, currentTime);
        } catch (Exception e) {
            System.err.println("Failed to log activity: " + e.getMessage());
        }
    }

    private void logActivity(HttpSession session, jakarta.servlet.http.HttpServletRequest request,
            String action, String moduleName, String description) {
        if (session != null) {
            String username = (String) session.getAttribute("user");
            String role = (String) session.getAttribute("role");
            logActivity(username, role, request, action, moduleName, description);
            updateActiveUser(session);
        } else {
            logActivity("System/Anonymous", "None", request, action, moduleName, description);
        }
    }

    private void logActivity(HttpSession session, String action, String moduleName, String description) {
        jakarta.servlet.http.HttpServletRequest request = null;
        try {
            org.springframework.web.context.request.RequestAttributes attribs = org.springframework.web.context.request.RequestContextHolder
                    .getRequestAttributes();
            if (attribs instanceof org.springframework.web.context.request.ServletRequestAttributes) {
                request = ((org.springframework.web.context.request.ServletRequestAttributes) attribs).getRequest();
            }
        } catch (Exception e) {
            // Ignore
        }
        logActivity(session, request, action, moduleName, description);
    }

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
            HttpSession session, Model model, jakarta.servlet.http.HttpServletRequest request) {
        try {
            DatabaseManager db = DatabaseManager.getInstance();
            Map<String, Object> user = db.queryOne("SELECT * FROM users WHERE username=?", username);

            if (user == null) {
                logActivity(username, "None", request, "Failed Login", "Authentication",
                        "Failed login attempt: User not found (" + username + ")");
                model.addAttribute("error", "Invalid username or password");
                return "login";
            }

            if (!"Active".equalsIgnoreCase(DatabaseManager.str(user, "status"))) {
                logActivity(username, DatabaseManager.str(user, "role"), request, "Failed Login", "Authentication",
                        "Failed login attempt: Account inactive");
                model.addAttribute("error", "Your account is currently inactive. Please contact administrator.");
                return "login";
            }

            String dbHash = DatabaseManager.str(user, "password_hash");
            boolean valid = false;
            if (dbHash.startsWith("$2a$")) {
                valid = org.mindrot.jbcrypt.BCrypt.checkpw(password, dbHash);
            } else {
                String shaHash = db.hashPw(password);
                if (shaHash.equals(dbHash)) {
                    valid = true;
                    dbHash = org.mindrot.jbcrypt.BCrypt.hashpw(password, org.mindrot.jbcrypt.BCrypt.gensalt());
                    db.execute("UPDATE users SET password_hash=? WHERE id=?", dbHash, user.get("id"));
                }
            }

            if (!valid) {
                logActivity(username, DatabaseManager.str(user, "role"), request, "Failed Login", "Authentication",
                        "Failed login attempt: Incorrect password");
                model.addAttribute("error", "Invalid username or password");
                return "login";
            }

            // Success
            session.setAttribute("user", user.get("username"));
            session.setAttribute("role", user.get("role"));
            session.setAttribute("passwordHash", dbHash); // Track for invalidation
            session.setAttribute("allowed_modules", DatabaseManager.str(user, "allowed_modules"));
            db.execute("UPDATE users SET last_login=NOW() WHERE id=?", user.get("id"));

            String loginToken = java.util.UUID.randomUUID().toString();
            session.setAttribute("loginToken", loginToken);

            logActivity(user.get("username").toString(), user.get("role").toString(), request, "Login",
                    "Authentication", "User login successful");

            return "redirect:/dashboard?loginToken=" + loginToken;

        } catch (Exception e) {
            e.printStackTrace();
            logActivity(username, "None", request, "Failed Login", "Authentication",
                    "Failed login attempt due to error: " + e.getMessage());
            model.addAttribute("error", "System Error: " + e.getMessage());
            return "login";
        }
    }

    @GetMapping("/logout")
    public String logout(HttpSession session, jakarta.servlet.http.HttpServletRequest request) {
        if (session != null && session.getAttribute("user") != null) {
            logActivity(session, request, "Logout", "Authentication", "User logged out");
            activeUsers.remove((String) session.getAttribute("user"));
        }
        session.invalidate();
        return "redirect:/login";
    }

    @GetMapping({ "/", "/dashboard" })
    public String index(Model model, @RequestParam(name = "search", required = false) String search,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "10") int pageSize,
            @RequestParam(name = "export", defaultValue = "false") boolean isExport,
            @RequestParam(name = "deviceId", required = false) Integer deviceId,
            HttpSession session) {
        if (isExport) {
            /* Dashboard uses fixed 10 but we'll override if needed */ }

        // Ensure ADMS/Push Service is running (Self-Healing) - Only if not manually
        // stopped
        if (!com.bhspl.service.PushService.isInternalRunning() && !com.bhspl.service.PushService.isUserStopped()) {
            com.bhspl.service.PushService.start();
        }

        List<Integer> allowedIds = getAllowedDeviceIds(session);
        if (isUserRestricted(session)) {
            model.addAttribute("noDevicesAssigned", true);
            model.addAttribute("devicesList", new ArrayList<>());
            model.addAttribute("recentLogs", new ArrayList<>());
            model.addAttribute("livePunches", new ArrayList<>());
            model.addAttribute("totalEmps", 0);
            model.addAttribute("presentCount", 0);
            model.addAttribute("absentCount", 0);
            model.addAttribute("leaveCount", 0);
            model.addAttribute("lateCount", 0);
            model.addAttribute("devicesOnline", 0);
            model.addAttribute("pendingLeaves", 0);
            model.addAttribute("weeklyOffCount", 0);
            model.addAttribute("weeklyPresentCounts",
                    new ArrayList<>(java.util.Arrays.asList(0L, 0L, 0L, 0L, 0L, 0L, 0L)));
            model.addAttribute("weekDays",
                    new ArrayList<>(java.util.Arrays.asList("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")));
            model.addAttribute("lastSyncTime", "Never");
            model.addAttribute("autoSyncActive", false);
            return "dashboard";
        }

        if (deviceId != null && !allowedIds.isEmpty() && !allowedIds.contains(deviceId)) {
            deviceId = null; // Ignore unauthorized device selection
        }

        String allowedIdsStr = "";
        if (!allowedIds.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (int idx = 0; idx < allowedIds.size(); idx++) {
                if (idx > 0)
                    sb.append(",");
                sb.append(allowedIds.get(idx));
            }
            allowedIdsStr = sb.toString();
        }

        try {
            DatabaseManager db = DatabaseManager.getInstance();
            if (isExport)
                pageSize = 50000;
            int offset = (page - 1) * pageSize;
            String todayStr = java.time.LocalDate.now().toString();

            // Stats & Analytics Caching
            String cacheKey = "dashboard_stats_" + (deviceId != null ? deviceId : "all");
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> stats = (java.util.Map<String, Object>) CacheManager.getInstance()
                    .get(cacheKey);
            if (stats == null || !stats.containsKey("weeklyPresentCounts")) {
                String dayName = java.time.LocalDate.now().getDayOfWeek()
                        .getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH);

                stats = new java.util.HashMap<>();
                long totalEmpsVal;
                if (deviceId != null) {
                    totalEmpsVal = db.queryLong(
                            "SELECT COUNT(DISTINCT e.emp_id) FROM employees e WHERE (e.device_id = ? OR e.emp_id IN (SELECT DISTINCT emp_id FROM raw_logs WHERE device_id = ?)) AND e.status = 'Active'",
                            deviceId, deviceId);
                } else if (!allowedIds.isEmpty()) {
                    totalEmpsVal = db.queryLong(
                            "SELECT COUNT(DISTINCT e.emp_id) FROM employees e WHERE (e.device_id IN (" + allowedIdsStr
                                    + ") OR e.emp_id IN (SELECT DISTINCT emp_id FROM raw_logs WHERE device_id IN (" + allowedIdsStr + "))) AND e.status = 'Active'");
                } else {
                    totalEmpsVal = db.queryLong("SELECT COUNT(*) FROM employees WHERE status='Active'");
                }

                long presentCountVal;
                if (deviceId != null) {
                    presentCountVal = db.queryLong(
                            "SELECT COUNT(DISTINCT r.emp_id) FROM raw_logs r JOIN employees e ON r.emp_id = e.emp_id WHERE r.punch_time >= ? AND e.status = 'Active' AND r.device_id = ?",
                            todayStr, deviceId);
                } else if (!allowedIds.isEmpty()) {
                    presentCountVal = db.queryLong(
                            "SELECT COUNT(DISTINCT r.emp_id) FROM raw_logs r JOIN employees e ON r.emp_id = e.emp_id WHERE r.punch_time >= ? AND e.status = 'Active' AND r.device_id IN ("
                                    + allowedIdsStr + ")",
                            todayStr);
                } else {
                    presentCountVal = db.queryLong(
                            "SELECT COUNT(DISTINCT r.emp_id) FROM raw_logs r JOIN employees e ON r.emp_id = e.emp_id WHERE r.punch_time >= ? AND e.status = 'Active'",
                            todayStr);
                }

                long leaveCountVal;
                if (deviceId != null) {
                    leaveCountVal = db.queryLong(
                            "SELECT COUNT(DISTINCT l.emp_id) FROM leaves l JOIN employees e ON l.emp_id = e.emp_id WHERE l.status='Approved' AND ? BETWEEN l.from_date AND l.to_date AND (e.device_id = ? OR e.emp_id IN (SELECT DISTINCT emp_id FROM raw_logs WHERE device_id = ?)) AND e.status='Active'",
                            todayStr, deviceId, deviceId);
                } else if (!allowedIds.isEmpty()) {
                    leaveCountVal = db.queryLong(
                            "SELECT COUNT(DISTINCT l.emp_id) FROM leaves l JOIN employees e ON l.emp_id = e.emp_id WHERE l.status='Approved' AND ? BETWEEN l.from_date AND l.to_date AND (e.device_id IN (" + allowedIdsStr
                                    + ") OR e.emp_id IN (SELECT DISTINCT emp_id FROM raw_logs WHERE device_id IN (" + allowedIdsStr + "))) AND e.status='Active'",
                            todayStr);
                } else {
                    leaveCountVal = db.queryLong(
                            "SELECT COUNT(DISTINCT emp_id) FROM leaves WHERE status='Approved' AND ? BETWEEN from_date AND to_date",
                            todayStr);
                }
                // totalLogs is equal to presentCount!
                long totalLogsVal = presentCountVal;

                long lateCountVal;
                if (deviceId != null) {
                    lateCountVal = db.queryLong(
                            "SELECT COUNT(DISTINCT emp_id) FROM attendance WHERE punch_date = ? AND status = 'Late' AND device_id = ?",
                            todayStr, deviceId);
                } else if (!allowedIds.isEmpty()) {
                    lateCountVal = db.queryLong(
                            "SELECT COUNT(DISTINCT emp_id) FROM attendance WHERE punch_date = ? AND status = 'Late' AND device_id IN ("
                                    + allowedIdsStr + ")",
                            todayStr);
                } else {
                    lateCountVal = db.queryLong(
                            "SELECT COUNT(DISTINCT emp_id) FROM attendance WHERE punch_date = ? AND status = 'Late'",
                            todayStr);
                }

                long devicesOnlineVal;
                if (deviceId != null) {
                    devicesOnlineVal = db.queryLong(
                            "SELECT COUNT(*) FROM devices WHERE status = 'Active' AND device_id = ?",
                            deviceId);
                } else if (!allowedIds.isEmpty()) {
                    devicesOnlineVal = db.queryLong(
                            "SELECT COUNT(*) FROM devices WHERE status = 'Active' AND device_id IN (" + allowedIdsStr
                                    + ")");
                } else {
                    devicesOnlineVal = db.queryLong("SELECT COUNT(*) FROM devices WHERE status = 'Active'");
                }

                long pendingLeavesVal;
                if (deviceId != null) {
                    pendingLeavesVal = db.queryLong(
                            "SELECT COUNT(DISTINCT l.id) FROM leaves l JOIN employees e ON l.emp_id = e.emp_id WHERE l.status='Pending' AND (e.device_id = ? OR e.emp_id IN (SELECT DISTINCT emp_id FROM raw_logs WHERE device_id = ?)) AND e.status='Active'",
                            deviceId, deviceId);
                } else if (!allowedIds.isEmpty()) {
                    pendingLeavesVal = db.queryLong(
                            "SELECT COUNT(DISTINCT l.id) FROM leaves l JOIN employees e ON l.emp_id = e.emp_id WHERE l.status='Pending' AND (e.device_id IN (" + allowedIdsStr
                                    + ") OR e.emp_id IN (SELECT DISTINCT emp_id FROM raw_logs WHERE device_id IN (" + allowedIdsStr + "))) AND e.status='Active'");
                } else {
                    pendingLeavesVal = db.queryLong("SELECT COUNT(*) FROM leaves WHERE status='Pending'");
                }
                long weeklyOffCountVal;
                if (deviceId != null) {
                    weeklyOffCountVal = db.queryLong(
                            "SELECT COUNT(DISTINCT e.emp_id) FROM employees e LEFT JOIN shifts s ON e.shift = s.shift_name WHERE (s.weekly_off1 = ? OR s.weekly_off2 = ?) AND (e.device_id = ? OR e.emp_id IN (SELECT DISTINCT emp_id FROM raw_logs WHERE device_id = ?)) AND e.status='Active'",
                            dayName, dayName, deviceId, deviceId);
                } else if (!allowedIds.isEmpty()) {
                    weeklyOffCountVal = db.queryLong(
                            "SELECT COUNT(DISTINCT e.emp_id) FROM employees e LEFT JOIN shifts s ON e.shift = s.shift_name WHERE (s.weekly_off1 = ? OR s.weekly_off2 = ?) AND (e.device_id IN (" + allowedIdsStr
                                    + ") OR e.emp_id IN (SELECT DISTINCT emp_id FROM raw_logs WHERE device_id IN (" + allowedIdsStr + "))) AND e.status='Active'",
                            dayName, dayName);
                } else {
                    weeklyOffCountVal = db.queryLong(
                            "SELECT COUNT(e.emp_id) FROM employees e LEFT JOIN shifts s ON e.shift = s.shift_name WHERE s.weekly_off1 = ? OR s.weekly_off2 = ?",
                            dayName, dayName);
                }
                stats.put("totalEmps", totalEmpsVal);
                stats.put("presentCount", presentCountVal);
                stats.put("leaveCount", leaveCountVal);
                stats.put("totalLogs", totalLogsVal);
                stats.put("lateCount", lateCountVal);
                stats.put("devicesOnline", devicesOnlineVal);
                stats.put("pendingLeaves", pendingLeavesVal);
                stats.put("weeklyOffCount", weeklyOffCountVal);

                // Compute Weekly Data inside the cache block
                List<String> weekDatesList = new java.util.ArrayList<>();
                List<String> weekDaysList = new java.util.ArrayList<>();
                java.time.format.DateTimeFormatter dayFormatter = java.time.format.DateTimeFormatter.ofPattern("EEE");

                for (int i = 6; i >= 0; i--) {
                    java.time.LocalDate d = java.time.LocalDate.now().minusDays(i);
                    weekDatesList.add(d.toString());
                    weekDaysList.add(d.format(dayFormatter));
                }

                List<Long> weeklyPresentCountsList = new java.util.ArrayList<>();
                if (!weekDatesList.isEmpty()) {
                    String startDate = weekDatesList.get(0);
                    String endDate = weekDatesList.get(weekDatesList.size() - 1);
                    List<Map<String, Object>> rows;
                    if (deviceId != null) {
                        rows = db.query(
                                "SELECT DATE(r.punch_time) as pdate, COUNT(DISTINCT r.emp_id) as pcount " +
                                        "FROM raw_logs r JOIN employees e ON r.emp_id = e.emp_id " +
                                        "WHERE r.punch_time >= ? AND r.punch_time < DATE_ADD(?, INTERVAL 1 DAY) AND e.status = 'Active' "
                                        +
                                        "AND r.device_id = ? " +
                                        "GROUP BY DATE(r.punch_time)",
                                startDate, endDate, deviceId);
                    } else if (!allowedIds.isEmpty()) {
                        rows = db.query(
                                "SELECT DATE(r.punch_time) as pdate, COUNT(DISTINCT r.emp_id) as pcount " +
                                        "FROM raw_logs r JOIN employees e ON r.emp_id = e.emp_id " +
                                        "WHERE r.punch_time >= ? AND r.punch_time < DATE_ADD(?, INTERVAL 1 DAY) AND e.status = 'Active' "
                                        +
                                        "AND r.device_id IN (" + allowedIdsStr + ") " +
                                        "GROUP BY DATE(r.punch_time)",
                                startDate, endDate);
                    } else {
                        rows = db.query(
                                "SELECT DATE(r.punch_time) as pdate, COUNT(DISTINCT r.emp_id) as pcount " +
                                        "FROM raw_logs r JOIN employees e ON r.emp_id = e.emp_id " +
                                        "WHERE r.punch_time >= ? AND r.punch_time < DATE_ADD(?, INTERVAL 1 DAY) AND e.status = 'Active' "
                                        +
                                        "GROUP BY DATE(r.punch_time)",
                                startDate, endDate);
                    }
                    Map<String, Long> countMap = new java.util.HashMap<>();
                    for (Map<String, Object> r : rows) {
                        Object pdateObj = r.get("pdate");
                        if (pdateObj != null) {
                            countMap.put(pdateObj.toString(), ((Number) r.get("pcount")).longValue());
                        }
                    }
                    for (String d : weekDatesList) {
                        weeklyPresentCountsList.add(countMap.getOrDefault(d, 0L));
                    }
                }
                stats.put("weeklyPresentCounts", weeklyPresentCountsList);
                stats.put("weekDates", weekDatesList);
                stats.put("weekDays", weekDaysList);

                CacheManager.getInstance().put(cacheKey, stats, 300000); // 5 minutes cache
            }

            long totalEmps = ((Number) stats.get("totalEmps")).longValue();
            long presentCount = ((Number) stats.get("presentCount")).longValue();
            long leaveCount = ((Number) stats.get("leaveCount")).longValue();
            long absentCount = totalEmps - presentCount - leaveCount;
            if (absentCount < 0)
                absentCount = 0;

            // Pagination info
            long totalLogs = ((Number) stats.get("totalLogs")).longValue();
            int totalPages = (int) Math.ceil((double) totalLogs / pageSize);
            if (totalPages == 0)
                totalPages = 1;

            // New Analytics
            long lateCount = ((Number) stats.get("lateCount")).longValue();
            long devicesOnline = ((Number) stats.get("devicesOnline")).longValue();
            long pendingLeaves = ((Number) stats.get("pendingLeaves")).longValue();
            long weeklyOffCount = ((Number) stats.get("weeklyOffCount")).longValue();

            // Recent Logs (Live Attendance Overview)
            String searchFilter = "";
            String orderBy = "MAX(r.punch_time) DESC";
            List<Object> params = new ArrayList<>();
            List<Object> orderParams = new ArrayList<>();

            if (search != null && !search.isEmpty()) {
                searchFilter = " AND (e.emp_name LIKE ? OR r.emp_id LIKE ?) ";
                params.add("%" + search + "%");
                params.add("%" + search + "%");

                orderBy = "CASE WHEN LOWER(e.emp_name) LIKE LOWER(?) THEN 0 WHEN LOWER(r.emp_id) LIKE LOWER(?) THEN 1 ELSE 2 END, "
                        + orderBy;
                orderParams.add(search + "%");
                orderParams.add(search + "%");
            }

            List<Object> allParams = new ArrayList<>(params);
            allParams.addAll(orderParams);

            String deviceFilterSql = "";
            if (deviceId != null) {
                deviceFilterSql = "AND r.device_id = ? ";
            } else if (!allowedIds.isEmpty()) {
                deviceFilterSql = "AND r.device_id IN (" + allowedIdsStr + ") ";
            }

            List<Object> recentLogsParams = new ArrayList<>();
            recentLogsParams.add(todayStr);
            recentLogsParams.add(todayStr);
            if (deviceId != null) {
                recentLogsParams.add(deviceId);
            }
            recentLogsParams.addAll(allParams);

            List<Map<String, Object>> recentLogs = db.query(
                    "SELECT r.emp_id, " +
                            "CASE WHEN e.emp_name IS NOT NULL THEN e.emp_name " +
                            "     WHEN r.emp_id = '0' OR r.emp_id = '0000' THEN 'System/Admin' " +
                            "     ELSE 'Unknown User' END as emp_name, " +
                            "COALESCE(d.device_name, 'Manual/No Device') as device_name, " +
                            "COALESCE(d.location, 'Not Assigned') as device_location, " +
                            "MIN(r.punch_time) as in_time, MAX(r.punch_time) as out_time, COUNT(*) as punches, " +
                            "s.start_time AS shift_start, s.grace_mins " +
                            "FROM raw_logs r " +
                            "LEFT JOIN employees e ON r.emp_id = e.emp_id " +
                            "LEFT JOIN devices d ON d.device_id = (SELECT device_id FROM raw_logs rl2 WHERE rl2.emp_id = r.emp_id AND rl2.punch_time >= ? ORDER BY rl2.punch_time DESC LIMIT 1) "
                            +
                            "LEFT JOIN shifts s ON e.shift = s.shift_name " +
                            "WHERE r.punch_time >= ? AND e.status = 'Active' "
                            + deviceFilterSql + searchFilter +
                            "GROUP BY r.emp_id, e.emp_name, d.device_name, d.location, s.start_time, s.grace_mins " +
                            "ORDER BY " + orderBy + " LIMIT " + pageSize + " OFFSET " + offset,
                    recentLogsParams.toArray());

            // Weekly Data mapping
            Map<String, Map<String, String>> weeklyData = new java.util.HashMap<>();
            @SuppressWarnings("unchecked")
            List<String> weekDates = (List<String>) stats.get("weekDates");
            @SuppressWarnings("unchecked")
            List<String> weekDays = (List<String>) stats.get("weekDays");
            @SuppressWarnings("unchecked")
            List<Long> weeklyPresentCounts = (List<Long>) stats.get("weeklyPresentCounts");

            model.addAttribute("weeklyPresentCounts", weeklyPresentCounts);

            // Fetch Latest Sync Time & Device Sync Metrics
            String lastSyncTime = db.queryString("SELECT DATE_FORMAT(MAX(last_sync), '%h:%i:%s %p') FROM devices");
            model.addAttribute("lastSyncTime", lastSyncTime != null ? lastSyncTime : "Never");
            model.addAttribute("autoSyncActive", com.bhspl.service.SyncService.isRunning());

            // Today's punch count (total raw logs received today)
            long todayPunches;
            if (deviceId != null) {
                todayPunches = db.queryLong(
                        "SELECT COUNT(*) FROM raw_logs WHERE punch_time >= ? AND device_id = ?", todayStr, deviceId);
            } else if (!allowedIds.isEmpty()) {
                todayPunches = db.queryLong(
                        "SELECT COUNT(*) FROM raw_logs WHERE punch_time >= ? AND device_id IN (" + allowedIdsStr + ")",
                        todayStr);
            } else {
                todayPunches = db.queryLong(
                        "SELECT COUNT(*) FROM raw_logs WHERE punch_time >= ?", todayStr);
            }
            model.addAttribute("todayPunches", todayPunches);

            // Today's synced logs (processed into attendance_logs or daily_attendance)
            long todaySyncedLogs;
            if (deviceId != null) {
                todaySyncedLogs = db.queryLong(
                        "SELECT COUNT(*) FROM raw_logs r " +
                                "JOIN employees e ON r.emp_id = e.emp_id " +
                                "WHERE r.punch_time >= ? " +
                                "AND e.status = 'Active' AND r.device_id = ?",
                        todayStr, deviceId);
            } else if (!allowedIds.isEmpty()) {
                todaySyncedLogs = db.queryLong(
                        "SELECT COUNT(*) FROM raw_logs r " +
                                "JOIN employees e ON r.emp_id = e.emp_id " +
                                "WHERE r.punch_time >= ? " +
                                "AND e.status = 'Active' AND r.device_id IN (" + allowedIdsStr + ")",
                        todayStr);
            } else {
                todaySyncedLogs = db.queryLong(
                        "SELECT COUNT(*) FROM raw_logs r " +
                                "JOIN employees e ON r.emp_id = e.emp_id " +
                                "WHERE r.punch_time >= ? " +
                                "AND e.status = 'Active'",
                        todayStr);
            }
            model.addAttribute("todaySyncedLogs", todaySyncedLogs);

            // Sync success rate
            double syncSuccessRate = (todayPunches > 0)
                    ? Math.round((todaySyncedLogs * 100.0 / todayPunches) * 10.0) / 10.0
                    : 100.0;
            model.addAttribute("syncSuccessRate", syncSuccessRate);

            if (!recentLogs.isEmpty()) {
                StringBuilder empIds = new StringBuilder();
                for (Map<String, Object> log : recentLogs) {
                    if (empIds.length() > 0)
                        empIds.append(",");
                    empIds.append("'").append(log.get("emp_id")).append("'");

                    // Dynamic Status Calculation for recent logs
                    Object inVal = log.get("in_time");
                    java.time.LocalDateTime inDateTime = null;
                    if (inVal != null) {
                        if (inVal instanceof java.sql.Timestamp) {
                            inDateTime = ((java.sql.Timestamp) inVal).toLocalDateTime();
                        } else if (inVal instanceof java.time.LocalDateTime) {
                            inDateTime = (java.time.LocalDateTime) inVal;
                        } else {
                            try {
                                inDateTime = java.time.LocalDateTime
                                        .parse(inVal.toString().replace(" ", "T").substring(0, 19));
                            } catch (Exception ignored) {
                            }
                        }
                    }

                    String statusVal = "On Time";
                    if (inDateTime != null) {
                        java.time.LocalTime shiftStart = java.time.LocalTime.of(9, 0);
                        int graceMins = 5;
                        Object shiftStartObj = log.get("shift_start");
                        Object graceMinsObj = log.get("grace_mins");

                        shiftStart = com.bhspl.util.AttendanceCalculator.parseLocalTime(shiftStartObj, shiftStart);
                        if (graceMinsObj != null) {
                            try {
                                graceMins = Integer.parseInt(graceMinsObj.toString());
                            } catch (Exception ignored) {
                            }
                        }

                        java.time.LocalTime firstInTime = inDateTime.toLocalTime();
                        java.time.LocalTime lateThreshold = shiftStart.plusMinutes(graceMins);
                        if (firstInTime.isAfter(lateThreshold)) {
                            statusVal = "Late";
                        } else {
                            statusVal = "On Time";
                        }
                    }
                    log.put("status", statusVal);
                }

                List<Map<String, Object>> weeklyLogs;
                if (deviceId != null) {
                    weeklyLogs = db.query(
                            "SELECT emp_id, DATE(punch_time) as pdate, COUNT(*) as punches " +
                                    "FROM raw_logs WHERE emp_id IN (" + empIds.toString() + ") " +
                                    "AND punch_time >= DATE_SUB(?, INTERVAL 6 DAY) AND punch_time < DATE_ADD(?, INTERVAL 1 DAY) "
                                    +
                                    "AND device_id = ? " +
                                    "GROUP BY emp_id, DATE(punch_time)",
                            todayStr, todayStr, deviceId);
                } else if (!allowedIds.isEmpty()) {
                    weeklyLogs = db.query(
                            "SELECT emp_id, DATE(punch_time) as pdate, COUNT(*) as punches " +
                                    "FROM raw_logs WHERE emp_id IN (" + empIds.toString() + ") " +
                                    "AND punch_time >= DATE_SUB(?, INTERVAL 6 DAY) AND punch_time < DATE_ADD(?, INTERVAL 1 DAY) "
                                    +
                                    "AND device_id IN (" + allowedIdsStr + ") " +
                                    "GROUP BY emp_id, DATE(punch_time)",
                            todayStr, todayStr);
                } else {
                    weeklyLogs = db.query(
                            "SELECT emp_id, DATE(punch_time) as pdate, COUNT(*) as punches " +
                                    "FROM raw_logs WHERE emp_id IN (" + empIds.toString() + ") " +
                                    "AND punch_time >= DATE_SUB(?, INTERVAL 6 DAY) AND punch_time < DATE_ADD(?, INTERVAL 1 DAY) "
                                    +
                                    "GROUP BY emp_id, DATE(punch_time)",
                            todayStr, todayStr);
                }

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

            // Employees with odd punch count today (missing OUT punch)
            long missingPunches;
            if (deviceId != null) {
                missingPunches = db.queryLong(
                        "SELECT COUNT(*) FROM (" +
                                "  SELECT r.emp_id FROM raw_logs r " +
                                "  JOIN employees e ON r.emp_id = e.emp_id " +
                                "  WHERE r.punch_time >= ? " +
                                "  AND e.status = 'Active' " +
                                "  AND r.device_id = ? " +
                                "  GROUP BY r.emp_id HAVING COUNT(*) % 2 = 1" +
                                ") t",
                        todayStr, deviceId);
            } else if (!allowedIds.isEmpty()) {
                missingPunches = db.queryLong(
                        "SELECT COUNT(*) FROM (" +
                                "  SELECT r.emp_id FROM raw_logs r " +
                                "  JOIN employees e ON r.emp_id = e.emp_id " +
                                "  WHERE r.punch_time >= ? " +
                                "  AND e.status = 'Active' " +
                                "  AND r.device_id IN (" + allowedIdsStr + ") " +
                                "  GROUP BY r.emp_id HAVING COUNT(*) % 2 = 1" +
                                ") t",
                        todayStr);
            } else {
                missingPunches = db.queryLong(
                        "SELECT COUNT(*) FROM (" +
                                "  SELECT r.emp_id FROM raw_logs r " +
                                "  JOIN employees e ON r.emp_id = e.emp_id " +
                                "  WHERE r.punch_time >= ? " +
                                "  AND e.status = 'Active' " +
                                "  GROUP BY r.emp_id HAVING COUNT(*) % 2 = 1" +
                                ") t",
                        todayStr);
            }
            model.addAttribute("missingPunches", missingPunches);

            // Recent Live Punches Activity (last 10 punches today) - Optimized to run point
            // queries in Java
            List<Map<String, Object>> livePunches;
            if (deviceId != null) {
                livePunches = db.query(
                        "SELECT r.emp_id, e.emp_name, r.punch_time, " +
                                "COALESCE(d.device_name, 'Manual/No Device') as device_name, " +
                                "COALESCE(d.location, 'Not Assigned') as device_location " +
                                "FROM raw_logs r LEFT JOIN employees e ON r.emp_id = e.emp_id " +
                                "LEFT JOIN devices d ON r.device_id = d.device_id " +
                                "WHERE r.punch_time >= ? AND e.status = 'Active' " +
                                "AND r.device_id = ? " +
                                "ORDER BY r.punch_time DESC LIMIT 10",
                        todayStr, deviceId);
            } else if (!allowedIds.isEmpty()) {
                livePunches = db.query(
                        "SELECT r.emp_id, e.emp_name, r.punch_time, " +
                                "COALESCE(d.device_name, 'Manual/No Device') as device_name, " +
                                "COALESCE(d.location, 'Not Assigned') as device_location " +
                                "FROM raw_logs r LEFT JOIN employees e ON r.emp_id = e.emp_id " +
                                "LEFT JOIN devices d ON r.device_id = d.device_id " +
                                "WHERE r.punch_time >= ? AND e.status = 'Active' " +
                                "AND r.device_id IN (" + allowedIdsStr + ") " +
                                "ORDER BY r.punch_time DESC LIMIT 10",
                        todayStr);
            } else {
                livePunches = db.query(
                        "SELECT r.emp_id, e.emp_name, r.punch_time, " +
                                "COALESCE(d.device_name, 'Manual/No Device') as device_name, " +
                                "COALESCE(d.location, 'Not Assigned') as device_location " +
                                "FROM raw_logs r LEFT JOIN employees e ON r.emp_id = e.emp_id " +
                                "LEFT JOIN devices d ON r.device_id = d.device_id " +
                                "WHERE r.punch_time >= ? AND e.status = 'Active' " +
                                "ORDER BY r.punch_time DESC LIMIT 10",
                        todayStr);
            }
            for (Map<String, Object> punch : livePunches) {
                String empId = (String) punch.get("emp_id");
                Object punchTime = punch.get("punch_time");
                long count = db.queryLong(
                        "SELECT COUNT(*) FROM raw_logs WHERE emp_id = ? AND punch_time >= ? AND punch_time < DATE_ADD(?, INTERVAL 1 DAY) AND punch_time <= ?",
                        empId, todayStr, todayStr, punchTime);
                punch.put("punch_num", count);
            }
            model.addAttribute("livePunches", livePunches);

            // Devices list and selected device ID for the dropdown
            List<Map<String, Object>> devices;
            if (!allowedIds.isEmpty()) {
                devices = db
                        .query("SELECT device_id, device_name FROM devices WHERE status = 'Active' AND device_id IN ("
                                + allowedIdsStr + ") ORDER BY device_name");
            } else {
                devices = db.query(
                        "SELECT device_id, device_name FROM devices WHERE status = 'Active' ORDER BY device_name");
            }
            model.addAttribute("devicesList", devices);
            model.addAttribute("selectedDeviceId", deviceId);

        } catch (Exception e) {
            e.printStackTrace();
            model.addAttribute("error", e.getMessage());
        }

        // Expose active users for Admin dashboard widgets
        if ("Admin".equalsIgnoreCase((String) session.getAttribute("role"))) {
            model.addAttribute("activeUsersList", getActiveUsersList());
            try {
                DatabaseManager db = DatabaseManager.getInstance();
                List<Map<String, Object>> recentActivities = db
                        .query("SELECT * FROM activity_logs ORDER BY created_at DESC LIMIT 5");
                model.addAttribute("recentActivities", recentActivities);
                long failedLogins = db.queryLong(
                        "SELECT COUNT(*) FROM activity_logs WHERE action='Failed Login' AND created_at >= DATE_SUB(NOW(), INTERVAL 24 HOUR)");
                model.addAttribute("failedLogins24h", failedLogins);
            } catch (Exception ex) {
                // ignore
            }
        }

        return "dashboard";
    }

    @PostMapping("/api/sync")
    @ResponseBody
    public Map<String, Object> sync(HttpSession session) {
        Map<String, Object> res = new HashMap<>();
        if (session.getAttribute("user") == null) {
            res.put("success", false);
            return res;
        }
        try {
            SyncService.performSync(true); // Force UDP Pull for manual sync
            com.bhspl.util.CacheManager.getInstance().clear(); // Invalidate all cache (including dashboard statistics)
            res.put("success", true);
            res.put("message", "Sync completed successfully.");
        } catch (Exception e) {
            res.put("success", false);
            res.put("message", e.getMessage());
        }
        return res;
    }

    @PostMapping("/api/devices/{deviceId}/sync")
    @ResponseBody
    public Map<String, Object> syncDeviceApi(@PathVariable("deviceId") int deviceId, HttpSession session) {
        Map<String, Object> res = new HashMap<>();
        if (session.getAttribute("user") == null) {
            res.put("success", false);
            res.put("message", "Unauthorized access. Please login.");
            return res;
        }
        try {
            com.bhspl.service.SyncService.syncDeviceById(deviceId);
            com.bhspl.util.CacheManager.getInstance().clear(); // Invalidate all cache (including dashboard statistics)
            res.put("success", true);
            res.put("message", "Device synced successfully.");
        } catch (Exception e) {
            res.put("success", false);
            res.put("message", e.getMessage());
        }
        return res;
    }

    @GetMapping("/api/system/status")
    @ResponseBody
    public Map<String, Object> systemStatus(HttpSession session) {
        Map<String, Object> res = new HashMap<>();
        if (session.getAttribute("user") == null) {
            res.put("success", false);
            return res;
        }
        res.put("success", true);
        res.put("autoSyncActive", com.bhspl.service.SyncService.isRunning());
        res.put("pushServiceActive", com.bhspl.service.PushService.isRunning());
        return res;
    }

    @GetMapping("/employees")
    public String employees(Model model,
            @RequestParam(name = "search", required = false) String search,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "10") int pageSize,
            @RequestParam(name = "deviceId", required = false) Integer deviceId,
            HttpSession session) {
        if (session.getAttribute("user") == null)
            return "redirect:/login";

        List<Integer> allowedIds = getAllowedDeviceIds(session);
        if (isUserRestricted(session)) {
            model.addAttribute("noDevicesAssigned", true);
            model.addAttribute("employees", new ArrayList<>());
            model.addAttribute("selSearch", search);
            model.addAttribute("currentPage", 1);
            model.addAttribute("totalPages", 1);
            model.addAttribute("pageSize", pageSize);
            model.addAttribute("totalItems", 0);
            model.addAttribute("depts", new ArrayList<>());
            model.addAttribute("activeDevices", new ArrayList<>());
            model.addAttribute("shifts", new ArrayList<>());
            model.addAttribute("devicesList", new ArrayList<>());
            model.addAttribute("selectedDeviceId", deviceId);
            return "employees";
        }

        if (deviceId != null && !allowedIds.isEmpty() && !allowedIds.contains(deviceId)) {
            deviceId = null;
        }

        String allowedIdsStr = "";
        if (!allowedIds.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (int idx = 0; idx < allowedIds.size(); idx++) {
                if (idx > 0)
                    sb.append(",");
                sb.append(allowedIds.get(idx));
            }
            allowedIdsStr = sb.toString();
        }

        try {
            DatabaseManager db = DatabaseManager.getInstance();
            int offset = (page - 1) * pageSize;

            String where = " WHERE 1=1";
            if (!allowedIds.isEmpty()) {
                where += " AND (emp_id IN (SELECT DISTINCT emp_id FROM raw_logs WHERE device_id IN (" + allowedIdsStr
                        + ")) OR device_id IN (" + allowedIdsStr + ") OR device_id = 0 OR device_id IS NULL)";
            }

            String orderBy = "emp_name";
            List<Object> params = new ArrayList<>();
            List<Object> orderParams = new ArrayList<>();

            if (deviceId != null) {
                where += " AND (emp_id IN (SELECT DISTINCT emp_id FROM raw_logs WHERE device_id = ?) OR device_id = ?)";
                params.add(deviceId);
                params.add(deviceId);
            }

            if (search != null && !search.isEmpty()) {
                where += " AND (emp_name LIKE ? OR emp_id LIKE ?)";
                params.add("%" + search + "%");
                params.add("%" + search + "%");

                orderBy = "CASE WHEN LOWER(emp_name) LIKE LOWER(?) THEN 0 WHEN LOWER(emp_id) LIKE LOWER(?) THEN 1 ELSE 2 END, "
                        + orderBy;
                orderParams.add(search + "%");
                orderParams.add(search + "%");
            }

            long total = db.queryLong("SELECT COUNT(*) FROM employees" + where, params.toArray());
            int totalPages = (int) Math.ceil((double) total / pageSize);
            if (totalPages == 0)
                totalPages = 1;

            String sql = "SELECT * FROM employees" + where + " ORDER BY " + orderBy + " LIMIT " + pageSize + " OFFSET "
                    + offset;

            List<Object> allParams = new ArrayList<>(params);
            allParams.addAll(orderParams);

            List<Map<String, Object>> employees = db.query(sql, allParams.toArray());
            model.addAttribute("employees", employees);
            model.addAttribute("selSearch", search);
            model.addAttribute("currentPage", page);
            model.addAttribute("totalPages", totalPages);
            model.addAttribute("pageSize", pageSize);
            model.addAttribute("totalItems", total);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> cachedDepts = (List<Map<String, Object>>) CacheManager.getInstance()
                    .get("list_departments");
            if (cachedDepts == null) {
                cachedDepts = db.query("SELECT dept_name FROM departments ORDER BY dept_name");
                CacheManager.getInstance().put("list_departments", cachedDepts, 3600000); // 1 hour
            }
            model.addAttribute("depts", cachedDepts);

            // Fetch active biometric devices and shifts for the Device Import feature
            List<Map<String, Object>> activeDevices;
            if (!allowedIds.isEmpty()) {
                activeDevices = db.query(
                        "SELECT device_id, device_name, serial_number FROM devices WHERE status='Active' AND device_id IN ("
                                + allowedIdsStr + ")");
            } else {
                activeDevices = db
                        .query("SELECT device_id, device_name, serial_number FROM devices WHERE status='Active'");
            }
            model.addAttribute("activeDevices", activeDevices);

            List<Map<String, Object>> shiftsList = db
                    .query("SELECT shift_name FROM shifts WHERE status='Active' ORDER BY shift_name");
            model.addAttribute("shifts", shiftsList);

            // Devices list and selected device ID for the filter dropdown
            List<Map<String, Object>> devices;
            if (!allowedIds.isEmpty()) {
                devices = db
                        .query("SELECT device_id, device_name FROM devices WHERE status = 'Active' AND device_id IN ("
                                + allowedIdsStr + ") ORDER BY device_name");
            } else {
                devices = db.query(
                        "SELECT device_id, device_name FROM devices WHERE status = 'Active' ORDER BY device_name");
            }
            model.addAttribute("devicesList", devices);
            model.addAttribute("selectedDeviceId", deviceId);
        } catch (Exception e) {
            model.addAttribute("error", e.getMessage());
        }
        return "employees";
    }

    @PostMapping("/employees/save")
    public String saveEmployee(@RequestParam Map<String, String> params, HttpSession session,
            jakarta.servlet.http.HttpServletRequest request) {
        try {
            DatabaseManager db = DatabaseManager.getInstance();
            String isEdit = params.get("is_edit");
            String empId = params.get("emp_id");
            String name = params.get("emp_name");
            String dept = params.get("department");
            String desig = params.get("designation");
            String shift = params.get("shift");
            String status = params.get("status");
            String deviceIdStr = params.get("device_id");
            int deviceId = 0;
            if (deviceIdStr != null && !deviceIdStr.isEmpty()) {
                try {
                    deviceId = Integer.parseInt(deviceIdStr);
                } catch (NumberFormatException ignored) {}
            }

            if (empId == null || !empId.matches("\\d+")) {
                System.err.println("WebController: Invalid non-numeric Employee ID rejected: " + empId);
                return "redirect:/employees";
            }

            if ("false".equals(isEdit)) {
                db.execute(
                        "INSERT INTO employees (emp_id, emp_name, department, designation, shift, status, device_id) VALUES (?,?,?,?,?,?,?)",
                        empId, name, dept, desig, shift, status, deviceId);
                logActivity(session, request, "Create Employee", "Employee Directory",
                        "Created employee " + name + " (ID: " + empId + ", Device ID: " + deviceId + ")");
            } else {
                db.execute(
                        "UPDATE employees SET emp_name=?, department=?, designation=?, shift=?, status=?, device_id=? WHERE emp_id=?",
                        name, dept, desig, shift, status, deviceId, empId);
                logActivity(session, request, "Edit Employee", "Employee Directory",
                        "Updated employee " + name + " (ID: " + empId + ", Device ID: " + deviceId + ")");
            }
            CacheManager.getInstance().clear();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "redirect:/employees";
    }

    @PostMapping("/employees/photo/upload")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> uploadPhoto(@RequestParam("photo") MultipartFile file,
            @RequestParam("empId") String empId) {
        Map<String, Object> response = new HashMap<>();
        try {
            if (file.isEmpty() || empId == null || empId.isEmpty()) {
                response.put("success", false);
                response.put("message", "Invalid file or employee ID.");
                return ResponseEntity.badRequest().body(response);
            }

            Path uploadDir = Paths.get("uploads", "photos");
            if (!Files.exists(uploadDir)) {
                Files.createDirectories(uploadDir);
            }

            String origName = file.getOriginalFilename();
            if (origName == null || !origName.contains(".")) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Invalid file"));
            }
            String fileExt = origName.substring(origName.lastIndexOf(".")).toLowerCase();
            if (!fileExt.equals(".jpg") && !fileExt.equals(".jpeg") && !fileExt.equals(".png")) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Only JPG and PNG files are allowed"));
            }
            String fileName = empId + "_" + UUID.randomUUID().toString().substring(0, 8) + fileExt;
            Path targetLocation = uploadDir.resolve(fileName).normalize();

            Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);

            DatabaseManager db = DatabaseManager.getInstance();
            // Ensure photo_path column exists
            try {
                db.execute("ALTER TABLE employees ADD COLUMN photo_path VARCHAR(255)");
            } catch (Exception ignored) {
            } // Column might already exist

            db.execute("UPDATE employees SET photo_path=? WHERE emp_id=?", targetLocation.toString().replace("\\", "/"),
                    empId);

            response.put("success", true);
            response.put("photoUrl", "/employees/photo/" + empId + "?t=" + System.currentTimeMillis());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            e.printStackTrace();
            response.put("success", false);
            response.put("message", "Internal server error: " + e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    @GetMapping("/employees/photo/{empId}")
    @ResponseBody
    public ResponseEntity<Resource> getEmployeePhoto(@PathVariable("empId") String empId) {
        try {
            DatabaseManager db = DatabaseManager.getInstance();
            List<Map<String, Object>> emps = db.query("SELECT photo_path FROM employees WHERE emp_id=?", empId);
            if (!emps.isEmpty() && emps.get(0).get("photo_path") != null) {
                String photoPathStr = (String) emps.get(0).get("photo_path");
                Path photoPath = Paths.get(photoPathStr).normalize();
                if (!photoPath.toAbsolutePath().startsWith(Paths.get("uploads/photos").toAbsolutePath())) {
                    return ResponseEntity.notFound().build();
                }
                if (Files.exists(photoPath)) {
                    Resource resource = new UrlResource(photoPath.toUri());
                    String contentType = Files.probeContentType(photoPath);
                    if (contentType == null)
                        contentType = "image/jpeg";

                    return ResponseEntity.ok()
                            .contentType(MediaType.parseMediaType(contentType))
                            .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate")
                            .body(resource);
                }
            }
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/employees/delete/{id}")
    public String deleteEmployee(@PathVariable("id") String id, HttpSession session,
            jakarta.servlet.http.HttpServletRequest request) {
        try {
            DatabaseManager db = DatabaseManager.getInstance();
            Map<String, Object> emp = db.queryOne("SELECT emp_name FROM employees WHERE emp_id=?", id);
            String empName = emp != null ? (String) emp.get("emp_name") : "Unknown";
            db.execute("DELETE FROM employees WHERE emp_id=?", id);
            CacheManager.getInstance().clear();
            logActivity(session, request, "Delete Employee", "Employee Directory",
                    "Deleted employee " + empName + " (ID: " + id + ")");
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "redirect:/employees";
    }

    @GetMapping("/employees/profile/{id}")
    public String employeeProfile(@PathVariable("id") String empId,
            @RequestParam(name = "month", required = false) Integer month,
            @RequestParam(name = "year", required = false) Integer year,
            Model model, HttpSession session) {
        if (session.getAttribute("user") == null)
            return "redirect:/login";

        try {
            DatabaseManager db = DatabaseManager.getInstance();

            List<Integer> allowedIds = getAllowedDeviceIds(session);
            if (isUserRestricted(session)) {
                return "redirect:/employees";
            }
            String allowedIdsStr = "";
            if (!allowedIds.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (int idx = 0; idx < allowedIds.size(); idx++) {
                    if (idx > 0)
                        sb.append(",");
                    sb.append(allowedIds.get(idx));
                }
                allowedIdsStr = sb.toString();

                boolean isAllowed = db.queryLong(
                        "SELECT COUNT(*) FROM employees WHERE emp_id=? AND (emp_id IN (SELECT DISTINCT emp_id FROM raw_logs WHERE device_id IN ("
                                + allowedIdsStr + ")) OR device_id IN (" + allowedIdsStr + "))",
                        empId) > 0;
                if (!isAllowed) {
                    return "redirect:/employees";
                }
            }

            // 1. Employee Details
            Map<String, Object> emp = db.queryOne("SELECT * FROM employees WHERE emp_id=?", empId);
            if (emp == null)
                return "redirect:/employees";
            model.addAttribute("emp", emp);

            // Default to current month/year if not provided
            java.time.LocalDate now = java.time.LocalDate.now();
            int selectedMonth = (month != null) ? month : now.getMonthValue();
            int selectedYear = (year != null) ? year : now.getYear();
            model.addAttribute("selectedMonth", selectedMonth);
            model.addAttribute("selectedYear", selectedYear);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> cachedDepts = (List<Map<String, Object>>) CacheManager.getInstance()
                    .get("list_departments");
            if (cachedDepts == null) {
                cachedDepts = db.query("SELECT dept_name FROM departments ORDER BY dept_name");
                CacheManager.getInstance().put("list_departments", cachedDepts, 3600000); // 1 hour
            }
            model.addAttribute("depts", cachedDepts);

            List<Map<String, Object>> shiftsList = db
                    .query("SELECT shift_name FROM shifts WHERE status='Active' ORDER BY shift_name");
            model.addAttribute("shifts", shiftsList);

            // 2. Attendance Summary & Monthly Stats
            List<Map<String, Object>> attendanceRecords;
            if (!allowedIds.isEmpty()) {
                attendanceRecords = db.query(
                        "SELECT * FROM attendance WHERE emp_id=? AND YEAR(punch_date) = ? AND MONTH(punch_date) = ? AND device_id IN ("
                                + allowedIdsStr + ")",
                        empId, selectedYear, selectedMonth);
            } else {
                attendanceRecords = db.query(
                        "SELECT * FROM attendance WHERE emp_id=? AND YEAR(punch_date) = ? AND MONTH(punch_date) = ?",
                        empId, selectedYear, selectedMonth);
            }

            int presentDays = 0, absentDays = 0, lateDays = 0, leaveDays = 0, weeklyOffs = 0, holidays = 0;
            double totalWorkedHours = 0.0;
            int daysWithHours = 0;

            Map<String, List<Map<String, Object>>> recordsByDate = new HashMap<>();
            for (Map<String, Object> a : attendanceRecords) {
                Object pdObj = a.get("punch_date");
                if (pdObj != null) {
                    recordsByDate.computeIfAbsent(pdObj.toString(), k -> new ArrayList<>()).add(a);
                }
            }

            for (Map.Entry<String, List<Map<String, Object>>> entry : recordsByDate.entrySet()) {
                List<Map<String, Object>> dayRecords = entry.getValue();
                boolean hasLate = false;
                boolean hasPresent = false;
                boolean hasLeave = false;
                boolean hasWeeklyOff = false;
                boolean hasHoliday = false;
                boolean hasAbsent = false;
                double dayWorkHours = 0.0;
                boolean dayHasHours = false;

                for (Map<String, Object> a : dayRecords) {
                    String status = DatabaseManager.str(a, "status");
                    if (status == null)
                        status = "";

                    if (status.equalsIgnoreCase("Present") || status.equalsIgnoreCase("P")
                            || status.equalsIgnoreCase("Early")) {
                        hasPresent = true;
                    } else if (status.equalsIgnoreCase("Absent") || status.equalsIgnoreCase("A")) {
                        hasAbsent = true;
                    } else if (status.equalsIgnoreCase("Late")) {
                        hasLate = true;
                    } else if (status.equalsIgnoreCase("Leave") || status.equalsIgnoreCase("L")
                            || status.equalsIgnoreCase("Half-Day")) {
                        hasLeave = true;
                    } else if (status.equalsIgnoreCase("Weekly Off") || status.equalsIgnoreCase("WO")) {
                        hasWeeklyOff = true;
                    } else if (status.equalsIgnoreCase("Holiday") || status.equalsIgnoreCase("H")
                            || status.equalsIgnoreCase("PH")) {
                        hasHoliday = true;
                    }

                    Object wh = a.get("work_hours");
                    if (wh != null) {
                        try {
                            double h = Double.parseDouble(wh.toString());
                            dayWorkHours += h;
                            if (h > 0)
                                dayHasHours = true;
                        } catch (Exception e) {
                        }
                    }
                }

                if (hasLate) {
                    lateDays++;
                } else if (hasPresent) {
                    presentDays++;
                } else if (hasLeave) {
                    leaveDays++;
                } else if (hasWeeklyOff) {
                    weeklyOffs++;
                } else if (hasHoliday) {
                    holidays++;
                } else if (hasAbsent) {
                    absentDays++;
                }

                totalWorkedHours += dayWorkHours;
                if (dayHasHours) {
                    daysWithHours++;
                }
            }

            double avgWorkingHours = (daysWithHours > 0) ? (totalWorkedHours / daysWithHours) : 0.0;

            // Calculate Weekly Offs based on Shift
            int computedWeeklyOffs = 0;
            String shiftName = (String) emp.get("shift");
            Map<String, Object> shiftInfo = db
                    .queryOne("SELECT weekly_off1, weekly_off2 FROM shifts WHERE shift_name=?", shiftName);
            Map<String, Object> customWo = db.queryOne(
                    "SELECT off_day1, off_day2 FROM weekly_offs WHERE emp_id=? ORDER BY id DESC LIMIT 1", empId);
            String wo1 = customWo != null ? DatabaseManager.str(customWo, "off_day1")
                    : (shiftInfo != null ? DatabaseManager.str(shiftInfo, "weekly_off1") : "Sunday");
            String wo2 = customWo != null ? DatabaseManager.str(customWo, "off_day2")
                    : (shiftInfo != null ? DatabaseManager.str(shiftInfo, "weekly_off2") : "None");

            java.time.YearMonth ym = java.time.YearMonth.of(selectedYear, selectedMonth);
            for (int i = 1; i <= ym.lengthOfMonth(); i++) {
                String dayName = ym.atDay(i).getDayOfWeek().getDisplayName(java.time.format.TextStyle.FULL,
                        java.util.Locale.ENGLISH);
                if (dayName.equalsIgnoreCase(wo1) || dayName.equalsIgnoreCase(wo2)) {
                    computedWeeklyOffs++;
                }
            }
            weeklyOffs = computedWeeklyOffs;

            // Calculate Holidays
            holidays = (int) db.queryLong(
                    "SELECT COUNT(*) FROM holidays WHERE MONTH(holiday_date) = ? AND YEAR(holiday_date) = ?",
                    selectedMonth, selectedYear);

            // Calculate Leaves
            long computedLeaveDays = db.queryLong(
                    "SELECT COALESCE(SUM(days), 0) FROM leaves WHERE emp_id=? AND status='Approved' AND MONTH(from_date) = ? AND YEAR(from_date) = ?",
                    empId, selectedMonth, selectedYear);
            leaveDays = (int) computedLeaveDays;

            model.addAttribute("presentDays", presentDays); // Exclude Late from Present count for UI mutually exclusive
                                                            // cards
            model.addAttribute("absentDays", absentDays);
            model.addAttribute("lateDays", lateDays);
            model.addAttribute("leaveDays", leaveDays);
            model.addAttribute("weeklyOffs", weeklyOffs);
            model.addAttribute("holidays", holidays);

            model.addAttribute("totalWorkingDays", presentDays + lateDays);
            model.addAttribute("totalWorkedHours", String.format("%.2f", totalWorkedHours));
            model.addAttribute("avgWorkingHours", String.format("%.2f", avgWorkingHours));
            model.addAttribute("netWorkingHours", String.format("%.2f", totalWorkedHours));

            // 3. Recent Punch Logs (Last 10)
            List<Map<String, Object>> recentPunches;
            if (!allowedIds.isEmpty()) {
                recentPunches = db.query(
                        "SELECT r.punch_time, r.punch_type, " +
                                "COALESCE(d.device_name, 'Manual/No Device') as device_name, " +
                                "COALESCE(d.location, 'Not Assigned') as device_location " +
                                "FROM raw_logs r " +
                                "LEFT JOIN devices d ON r.device_id = d.device_id " +
                                "WHERE r.emp_id=? AND r.device_id IN (" + allowedIdsStr
                                + ") ORDER BY r.punch_time DESC LIMIT 10",
                        empId);
            } else {
                recentPunches = db.query(
                        "SELECT r.punch_time, r.punch_type, " +
                                "COALESCE(d.device_name, 'Manual/No Device') as device_name, " +
                                "COALESCE(d.location, 'Not Assigned') as device_location " +
                                "FROM raw_logs r " +
                                "LEFT JOIN devices d ON r.device_id = d.device_id " +
                                "WHERE r.emp_id=? ORDER BY r.punch_time DESC LIMIT 10",
                        empId);
            }

            java.time.format.DateTimeFormatter timeFmt = java.time.format.DateTimeFormatter.ofPattern("hh:mm a");
            java.time.format.DateTimeFormatter dateFmt = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd");
            for (Map<String, Object> p : recentPunches) {
                Object pt = p.get("punch_time");
                if (pt != null) {
                    java.time.LocalDateTime ldt = null;
                    if (pt instanceof java.sql.Timestamp)
                        ldt = ((java.sql.Timestamp) pt).toLocalDateTime();
                    else if (pt instanceof java.time.LocalDateTime)
                        ldt = (java.time.LocalDateTime) pt;
                    else
                        try {
                            ldt = java.time.LocalDateTime.parse(pt.toString().replace(" ", "T").substring(0, 19));
                        } catch (Exception e) {
                        }

                    if (ldt != null) {
                        p.put("formatted_date", ldt.format(dateFmt));
                        p.put("formatted_time", ldt.format(timeFmt));
                    }
                }
            }
            model.addAttribute("recentPunches", recentPunches);

            // 4. Leave Summary (Yearly based on selectedYear)
            List<Map<String, Object>> leaveBalances = db.query(
                    "SELECT * FROM leave_balance WHERE emp_id=? AND year=?", empId, selectedYear);
            double availableLeave = 0.0, usedLeave = 0.0;
            for (Map<String, Object> lb : leaveBalances) {
                availableLeave += DatabaseManager.dbl(lb, "closing_bal");
                usedLeave += DatabaseManager.dbl(lb, "used");
            }
            long pendingLeaves = db.queryLong("SELECT COUNT(*) FROM leaves WHERE emp_id=? AND status='Pending'", empId);
            long approvedLeaves = db.queryLong(
                    "SELECT COUNT(*) FROM leaves WHERE emp_id=? AND status='Approved' AND YEAR(from_date)=?", empId,
                    selectedYear);

            model.addAttribute("availableLeave", availableLeave);
            model.addAttribute("usedLeave", usedLeave);
            model.addAttribute("pendingLeaves", pendingLeaves);
            model.addAttribute("approvedLeaves", approvedLeaves);

        } catch (Exception e) {
            e.printStackTrace();
            model.addAttribute("error", e.getMessage());
        }

        return "employee-profile";
    }

    @GetMapping("/api/employees/import-device-preview")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> importDevicePreview(
            @RequestParam("deviceId") int deviceId,
            @RequestParam(value = "fromDate", required = false) String fromDateStr,
            @RequestParam(value = "toDate", required = false) String toDateStr,
            HttpSession session) {

        Map<String, Object> response = new HashMap<>();
        if (session.getAttribute("user") == null) {
            response.put("status", "error");
            response.put("message", "Unauthorized. Please log in.");
            return ResponseEntity.status(401).body(response);
        }

        try {
            DatabaseManager db = DatabaseManager.getInstance();

            // Fetch device details
            Map<String, Object> dev = db.fetchOne(
                    "SELECT * FROM devices WHERE device_id=? AND status='Active'", deviceId);
            if (dev == null) {
                response.put("status", "error");
                response.put("message", "Active device not found or offline.");
                return ResponseEntity.badRequest().body(response);
            }

            String ip = (String) dev.get("ip_address");
            int port = (int) dev.get("port");
            int pwd = (int) dev.get("comm_password");

            // Connect to device and fetch users
            com.bhspl.util.ZkProtocol zk = new com.bhspl.util.ZkProtocol(ip, port, 4000);
            zk.setPassword(pwd);

            if (!zk.connect()) {
                response.put("status", "error");
                response.put("message",
                        "Failed to connect to device at " + ip + ":" + port + ". Please ensure it is online.");
                return ResponseEntity.status(504).body(response);
            }

            List<Map<String, String>> deviceUsers;
            List<Map<String, Object>> attendanceRecords = null;
            try {
                deviceUsers = zk.getUsers();
                if ((fromDateStr != null && !fromDateStr.trim().isEmpty()) ||
                        (toDateStr != null && !toDateStr.trim().isEmpty())) {
                    attendanceRecords = zk.getAttendanceRecords();
                }
            } finally {
                zk.disconnect();
            }

            if (deviceUsers == null) {
                deviceUsers = new ArrayList<>();
            }

            if (attendanceRecords != null) {
                java.time.LocalDate fromDate = null;
                java.time.LocalDate toDate = null;
                if (fromDateStr != null && !fromDateStr.trim().isEmpty()) {
                    try {
                        fromDate = java.time.LocalDate.parse(fromDateStr.trim());
                    } catch (Exception ignored) {
                    }
                }
                if (toDateStr != null && !toDateStr.trim().isEmpty()) {
                    try {
                        toDate = java.time.LocalDate.parse(toDateStr.trim());
                    } catch (Exception ignored) {
                    }
                }

                Set<String> activeUids = new HashSet<>();
                for (Map<String, Object> rec : attendanceRecords) {
                    String uid = DatabaseManager.str(rec, "uid").trim();
                    Object pt = rec.get("punch_time");
                    java.time.LocalDateTime punchTime = null;
                    if (pt instanceof java.time.LocalDateTime) {
                        punchTime = (java.time.LocalDateTime) pt;
                    } else if (pt instanceof java.sql.Timestamp) {
                        punchTime = ((java.sql.Timestamp) pt).toLocalDateTime();
                    } else if (pt != null) {
                        try {
                            punchTime = java.time.LocalDateTime.parse(pt.toString().replace(" ", "T").substring(0, 19));
                        } catch (Exception ignored) {
                        }
                    }

                    if (punchTime != null) {
                        java.time.LocalDate punchDate = punchTime.toLocalDate();
                        boolean matchesFrom = (fromDate == null || !punchDate.isBefore(fromDate));
                        boolean matchesTo = (toDate == null || !punchDate.isAfter(toDate));
                        if (matchesFrom && matchesTo) {
                            activeUids.add(uid);
                        }
                    }
                }

                List<Map<String, String>> filteredUsers = new ArrayList<>();
                for (Map<String, String> u : deviceUsers) {
                    String userId = u.get("user_id").trim();
                    if (activeUids.contains(userId)) {
                        filteredUsers.add(u);
                    } else {
                        try {
                            String normUser = String.valueOf(Long.parseLong(userId));
                            boolean matched = false;
                            for (String au : activeUids) {
                                try {
                                    if (String.valueOf(Long.parseLong(au)).equals(normUser)) {
                                        matched = true;
                                        break;
                                    }
                                } catch (Exception ignored) {
                                }
                            }
                            if (matched) {
                                filteredUsers.add(u);
                            }
                        } catch (Exception ignored) {
                        }
                    }
                }
                deviceUsers = filteredUsers;
            }

            // Fetch existing database employees
            List<Map<String, Object>> employees = db.query(
                    "SELECT emp_id, device_enroll_id FROM employees");

            Set<String> existingCodes = new HashSet<>();
            for (Map<String, Object> emp : employees) {
                String empId = DatabaseManager.str(emp, "emp_id").trim();
                String enrollId = DatabaseManager.str(emp, "device_enroll_id").trim();
                if (!empId.isEmpty()) {
                    existingCodes.add(empId);
                    try {
                        existingCodes.add(String.valueOf(Long.parseLong(empId)));
                    } catch (Exception ignored) {
                    }
                }
                if (!enrollId.isEmpty()) {
                    existingCodes.add(enrollId);
                    try {
                        existingCodes.add(String.valueOf(Long.parseLong(enrollId)));
                    } catch (Exception ignored) {
                    }
                }
            }

            // Filter new/unmatched users
            List<Map<String, String>> newUsers = new ArrayList<>();
            for (Map<String, String> u : deviceUsers) {
                String userId = u.get("user_id").trim();
                if (userId.isEmpty() || "0".equals(userId)) {
                    continue; // Skip invalid or system IDs
                }

                // Check standard match and normalized match
                boolean exists = existingCodes.contains(userId);
                if (!exists) {
                    try {
                        String norm = String.valueOf(Long.parseLong(userId));
                        exists = existingCodes.contains(norm);
                    } catch (Exception ignored) {
                    }
                }

                if (!exists) {
                    newUsers.add(u);
                }
            }

            response.put("status", "success");
            response.put("newUsers", newUsers);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            e.printStackTrace();
            response.put("status", "error");
            response.put("message", "System Error: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    @PostMapping("/api/employees/import-device-save")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> importDeviceSave(
            @RequestBody List<Map<String, String>> employeesToImport,
            HttpSession session,
            jakarta.servlet.http.HttpServletRequest request) {

        Map<String, Object> response = new HashMap<>();
        if (session.getAttribute("user") == null) {
            response.put("status", "error");
            response.put("message", "Unauthorized. Please log in.");
            return ResponseEntity.status(401).body(response);
        }

        if (employeesToImport == null || employeesToImport.isEmpty()) {
            response.put("status", "error");
            response.put("message", "No employees selected for import.");
            return ResponseEntity.badRequest().body(response);
        }

        try {
            DatabaseManager db = DatabaseManager.getInstance();

            // Validate all inputs first (Pre-validation check)
            for (Map<String, String> emp : employeesToImport) {
                String empId = emp.get("emp_id");
                String name = emp.get("emp_name");

                if (empId == null || empId.trim().isEmpty() || !empId.matches("[a-zA-Z0-9_.-]+")) {
                    response.put("status", "error");
                    response.put("message",
                            "Invalid or missing Employee ID: " + empId
                                    + ". Employee Code must contain only alphanumeric characters.");
                    return ResponseEntity.badRequest().body(response);
                }
                if (name == null || name.trim().isEmpty()) {
                    response.put("status", "error");
                    response.put("message", "Employee Name cannot be empty.");
                    return ResponseEntity.badRequest().body(response);
                }

                // Removed strict duplicate pre-validation to allow skipping existing employees
                // instead of failing the batch
            }

            // Execute transactional insertions
            int importedCount = 0;
            db.setAutoCommit(false);
            try {
                for (Map<String, String> emp : employeesToImport) {
                    String empId = emp.get("emp_id").trim();

                    // Skip if employee already exists in DB
                    long count = db.queryLong("SELECT COUNT(*) FROM employees WHERE emp_id=?", empId);
                    if (count > 0)
                        continue;

                    String name = emp.get("emp_name").trim();
                    String dept = emp.get("department");
                    if (dept != null)
                        dept = dept.trim();
                    String desig = emp.get("designation");
                    if (desig != null)
                        desig = desig.trim();
                    String shift = emp.get("shift");
                    if (shift != null)
                        shift = shift.trim();
                    String status = emp.get("status");
                    if (status != null)
                        status = status.trim();
                    String enrollId = emp.get("device_enroll_id");
                    if (enrollId != null)
                        enrollId = enrollId.trim();

                    db.execute(
                            "INSERT INTO employees (emp_id, emp_name, department, designation, shift, status, device_enroll_id) "
                                    +
                                    "VALUES (?, ?, ?, ?, ?, ?, ?)",
                            empId, name, dept, desig, shift, status, enrollId);
                    importedCount++;
                }
                db.commit();
                CacheManager.getInstance().clear(); // Invalidate all cached stats and dashboard caches
                logActivity(session, request, "Import Employees", "Employee Directory",
                        "Imported " + importedCount + " employees from device");
            } catch (Exception e) {
                db.rollback();
                throw e;
            } finally {
                db.setAutoCommit(true);
            }

            response.put("status", "success");
            response.put("message", "Successfully imported " + importedCount + " new employee(s).");
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            e.printStackTrace();
            response.put("status", "error");
            response.put("message", "System Error: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    @GetMapping("/attendance")
    public String attendance(Model model,
            @RequestParam(name = "date", required = false) String date,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "search", required = false) String search,
            @RequestParam(name = "deviceId", required = false) Integer reqDeviceId,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "10") int pageSize,
            HttpSession session) {
        if (session.getAttribute("user") == null)
            return "redirect:/login";

        List<Integer> allowedIds = getAllowedDeviceIds(session);
        if (isUserRestricted(session)) {
            model.addAttribute("noDevicesAssigned", true);
            model.addAttribute("attendance", new ArrayList<>());
            model.addAttribute("selDate", date != null ? date : java.time.LocalDate.now().toString());
            model.addAttribute("selStatus", status != null ? status : "All");
            model.addAttribute("selSearch", search != null ? search : "");
            model.addAttribute("currentPage", 1);
            model.addAttribute("totalPages", 1);
            model.addAttribute("pageSize", pageSize);
            model.addAttribute("totalItems", 0);
            model.addAttribute("devicesList", new ArrayList<>());
            model.addAttribute("selectedDeviceId", reqDeviceId);
            return "attendance";
        }

        if (reqDeviceId != null && !allowedIds.isEmpty() && !allowedIds.contains(reqDeviceId)) {
            reqDeviceId = null;
        }

        String allowedIdsStr = "";
        if (!allowedIds.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (int idx = 0; idx < allowedIds.size(); idx++) {
                if (idx > 0)
                    sb.append(",");
                sb.append(allowedIds.get(idx));
            }
            allowedIdsStr = sb.toString();
        }

        try {
            DatabaseManager db = DatabaseManager.getInstance();
            int offset = (page - 1) * pageSize;
            String filterDate = (date != null) ? date : java.time.LocalDate.now().toString();

            String deviceJoinFilter = "";
            if (reqDeviceId != null) {
                deviceJoinFilter = " AND a.device_id = " + reqDeviceId;
            } else if (!allowedIds.isEmpty()) {
                deviceJoinFilter = " AND a.device_id IN (" + allowedIdsStr + ")";
            }

            String employeeDeviceFilter = "";
            if (reqDeviceId != null) {
                employeeDeviceFilter = " AND (e.device_id = " + reqDeviceId + " OR e.emp_id IN (SELECT DISTINCT emp_id FROM raw_logs WHERE device_id = "
                        + reqDeviceId + ")) ";
            } else if (!allowedIds.isEmpty()) {
                employeeDeviceFilter = " AND (e.emp_id IN (SELECT DISTINCT emp_id FROM raw_logs WHERE device_id IN ("
                        + allowedIdsStr + ")) OR e.device_id IN (" + allowedIdsStr + ")) ";
            }

            String baseSql = "SELECT e.emp_id, e.emp_name, e.shift, " +
                    "MIN(a.in_time) AS in_time, " +
                    "MAX(a.out_time) AS out_time, " +
                    "SUM(a.work_hours) AS work_hours, " +
                    "s.start_time AS shift_start, s.grace_mins, " +
                    "MAX(a.late_mins) AS late_mins, " +
                    "GROUP_CONCAT(DISTINCT COALESCE(d.device_name, 'Manual/No Device') SEPARATOR ', ') AS device_name, "
                    +
                    "GROUP_CONCAT(DISTINCT COALESCE(d.location, 'Not Assigned') SEPARATOR ', ') AS device_location, " +
                    "(SELECT COUNT(*) FROM raw_logs r WHERE (r.emp_id = e.emp_id OR r.emp_id = e.device_enroll_id) AND DATE(r.punch_time) = ?"
                    + (reqDeviceId != null ? " AND r.device_id = " + reqDeviceId
                            : (!allowedIds.isEmpty() ? " AND r.device_id IN (" + allowedIdsStr + ")" : ""))
                    + ") AS punches_count, " +
                    "COALESCE( " +
                    "  (CASE " +
                    "    WHEN SUM(CASE WHEN a.status='Late' THEN 1 ELSE 0 END) > 0 THEN 'Late' " +
                    "    WHEN SUM(CASE WHEN a.status='Present' OR a.status='On Time' OR a.status='P' THEN 1 ELSE 0 END) > 0 THEN 'On Time' "
                    +
                    "    WHEN MAX(a.status) IS NOT NULL THEN MAX(a.status) " +
                    "  END), " +
                    "  (SELECT UPPER(leave_type) FROM leaves l WHERE l.emp_id = e.emp_id AND l.status='Approved' AND ? BETWEEN l.from_date AND l.to_date LIMIT 1), "
                    +
                    "  (SELECT UPPER(holiday_name) FROM holidays h WHERE h.holiday_date = ? LIMIT 1), " +
                    "  (SELECT UPPER(CASE WHEN DAYNAME(?) = off_day1 THEN off_day1 ELSE off_day2 END) FROM weekly_offs w "
                    +
                    "   WHERE w.emp_id = e.emp_id AND (? >= w.effective_from) AND (w.effective_to IS NULL OR ? <= w.effective_to) "
                    +
                    "   AND (DAYNAME(?) = w.off_day1 OR DAYNAME(?) = w.off_day2) LIMIT 1), " +
                    "  (CASE WHEN DAYNAME(?) = s.weekly_off1 THEN UPPER(s.weekly_off1) WHEN DAYNAME(?) = s.weekly_off2 THEN UPPER(s.weekly_off2) END), "
                    +
                    "  'Absent' " +
                    ") AS status " +
                    "FROM employees e " +
                    "LEFT JOIN attendance a ON e.emp_id = a.emp_id AND a.punch_date = ? " + deviceJoinFilter + " " +
                    "LEFT JOIN devices d ON a.device_id = d.device_id " +
                    "LEFT JOIN shifts s ON e.shift = s.shift_name " +
                    "WHERE e.status='Active' " + employeeDeviceFilter +
                    "GROUP BY e.emp_id, e.emp_name, e.shift, s.start_time, s.grace_mins";

            String filterWhere = "";
            List<Object> filterParams = new ArrayList<>();
            List<String> conditions = new ArrayList<>();
            if (status != null && !status.isEmpty() && !"All".equals(status)) {
                if ("Absent".equalsIgnoreCase(status)) {
                    conditions.add("status IN ('Absent', 'A')");
                } else if ("Late".equalsIgnoreCase(status)) {
                    conditions.add("status = 'Late'");
                } else if ("Present".equalsIgnoreCase(status)) {
                    conditions.add("status IN ('Present', 'On Time', 'P', 'Late', 'Early')");
                }
            }
            if (search != null && !search.isEmpty()) {
                conditions.add("(emp_name LIKE ? OR emp_id LIKE ?)");
                filterParams.add("%" + search + "%");
                filterParams.add("%" + search + "%");
            }
            if (!conditions.isEmpty()) {
                filterWhere = " WHERE " + String.join(" AND ", conditions);
            }

            List<Object> countParams = new ArrayList<>();
            for (int i = 0; i < 11; i++) {
                countParams.add(filterDate);
            }
            countParams.addAll(filterParams);

            long total = db.queryLong("SELECT COUNT(*) FROM (" + baseSql + ") t" + filterWhere, countParams.toArray());
            int totalPages = (int) Math.ceil((double) total / pageSize);
            if (totalPages == 0)
                totalPages = 1;

            String sql = "SELECT * FROM (" + baseSql + ") t" + filterWhere
                    + " ORDER BY in_time DESC, emp_name ASC LIMIT " + pageSize + " OFFSET " + offset;

            List<Object> queryParams = new ArrayList<>();
            for (int i = 0; i < 11; i++) {
                queryParams.add(filterDate);
            }
            queryParams.addAll(filterParams);

            List<Map<String, Object>> data = db.query(sql, queryParams.toArray());

            java.time.format.DateTimeFormatter timeFmt = java.time.format.DateTimeFormatter.ofPattern("hh:mm a");
            for (Map<String, Object> a : data) {
                Object inVal = a.get("in_time");
                java.time.LocalDateTime inDateTime = null;
                if (inVal != null) {
                    if (inVal instanceof java.sql.Timestamp) {
                        inDateTime = ((java.sql.Timestamp) inVal).toLocalDateTime();
                    } else if (inVal instanceof java.time.LocalDateTime) {
                        inDateTime = (java.time.LocalDateTime) inVal;
                    } else {
                        try {
                            inDateTime = java.time.LocalDateTime
                                    .parse(inVal.toString().replace(" ", "T").substring(0, 19));
                        } catch (Exception ignored) {
                        }
                    }
                    if (inDateTime != null) {
                        a.put("in_time_formatted", inDateTime.format(timeFmt));
                    }
                }

                Object outVal = a.get("out_time");
                if (outVal != null && !outVal.equals(inVal)) {
                    java.time.LocalDateTime outDateTime = null;
                    if (outVal instanceof java.sql.Timestamp) {
                        outDateTime = ((java.sql.Timestamp) outVal).toLocalDateTime();
                    } else if (outVal instanceof java.time.LocalDateTime) {
                        outDateTime = (java.time.LocalDateTime) outVal;
                    } else {
                        try {
                            outDateTime = java.time.LocalDateTime
                                    .parse(outVal.toString().replace(" ", "T").substring(0, 19));
                        } catch (Exception ignored) {
                        }
                    }
                    if (outDateTime != null) {
                        a.put("out_time_formatted", outDateTime.format(timeFmt));
                    }
                }

                // Dynamic Status Calculation
                String statusVal = DatabaseManager.str(a, "status");
                if (inDateTime != null) {
                    java.time.LocalTime shiftStart = java.time.LocalTime.of(9, 0);
                    int graceMins = 5;
                    Object shiftStartObj = a.get("shift_start");
                    Object graceMinsObj = a.get("grace_mins");

                    shiftStart = com.bhspl.util.AttendanceCalculator.parseLocalTime(shiftStartObj, shiftStart);
                    if (graceMinsObj != null) {
                        try {
                            graceMins = Integer.parseInt(graceMinsObj.toString());
                        } catch (Exception ignored) {
                        }
                    }

                    java.time.LocalTime firstInTime = inDateTime.toLocalTime();
                    java.time.LocalTime lateThreshold = shiftStart.plusMinutes(graceMins);
                    if (firstInTime.isAfter(lateThreshold)) {
                        statusVal = "Late";
                        long diffMins = java.time.Duration.between(shiftStart, firstInTime).toMinutes();
                        a.put("late_mins", (int) diffMins);
                        a.put("late_by", diffMins + " mins");
                    } else {
                        statusVal = "On Time";
                        a.put("late_by", "—");
                    }
                } else {
                    a.put("late_by", "—");
                }
                a.put("status", statusVal);
            }

            model.addAttribute("attendance", data);
            model.addAttribute("selDate", filterDate);
            model.addAttribute("selStatus", (status != null) ? status : "All");
            model.addAttribute("selSearch", (search != null) ? search : "");
            model.addAttribute("currentPage", page);
            model.addAttribute("totalPages", totalPages);
            model.addAttribute("pageSize", pageSize);
            model.addAttribute("totalItems", total);
            List<Map<String, Object>> devices;
            if (!allowedIds.isEmpty()) {
                devices = db
                        .query("SELECT device_id, device_name FROM devices WHERE status = 'Active' AND device_id IN ("
                                + allowedIdsStr + ") ORDER BY device_name");
            } else {
                devices = db.query(
                        "SELECT device_id, device_name FROM devices WHERE status = 'Active' ORDER BY device_name");
            }
            model.addAttribute("devicesList", devices);
            model.addAttribute("selectedDeviceId", reqDeviceId);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "attendance";
    }

    @GetMapping("/reports")
    public String reports() {
        return "redirect:/reports/daily";
    }

    private static java.time.LocalDateTime parseDateTime(Object val) {
        if (val == null)
            return null;
        if (val instanceof java.time.LocalDateTime)
            return (java.time.LocalDateTime) val;
        if (val instanceof java.sql.Timestamp)
            return ((java.sql.Timestamp) val).toLocalDateTime();
        try {
            String s = val.toString().trim().replace(" ", "T");
            if (s.contains(".")) {
                s = s.split("\\.")[0];
            }
            if (s.length() >= 16) {
                if (s.length() == 16) {
                    s = s + ":00";
                }
                return java.time.LocalDateTime.parse(s.substring(0, 19));
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    @GetMapping("/reports/exceptions")
    public String reportsExceptions(Model model,
            @RequestParam(name = "date", required = false) String date,
            @RequestParam(name = "dept", required = false) String dept,
            @RequestParam(name = "search", required = false) String search,
            @RequestParam(name = "deviceId", required = false) Integer reqDeviceId,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "10") int pageSize,
            HttpSession session) {

        if (session.getAttribute("user") == null)
            return "redirect:/login";

        List<Integer> allowedIds = getAllowedDeviceIds(session);
        if (isUserRestricted(session)) {
            model.addAttribute("noDevicesAssigned", true);
            model.addAttribute("depts", new ArrayList<>());
            model.addAttribute("data", new ArrayList<>());
            model.addAttribute("currentPage", 1);
            model.addAttribute("totalPages", 1);
            model.addAttribute("selDate", date != null ? date : java.time.LocalDate.now().toString());
            model.addAttribute("selDept", dept != null ? dept : "All");
            model.addAttribute("devicesList", new ArrayList<>());
            model.addAttribute("selectedDeviceId", reqDeviceId);
            return "reports-exceptions";
        }

        if (reqDeviceId != null && !allowedIds.isEmpty() && !allowedIds.contains(reqDeviceId)) {
            reqDeviceId = null;
        }

        String allowedIdsStr = "";
        if (!allowedIds.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (int idx = 0; idx < allowedIds.size(); idx++) {
                if (idx > 0)
                    sb.append(",");
                sb.append(allowedIds.get(idx));
            }
            allowedIdsStr = sb.toString();
        }

        List<Map<String, Object>> data = new java.util.ArrayList<>();
        try {
            DatabaseManager db = DatabaseManager.getInstance();
            int offset = (page - 1) * pageSize;
            String filterDate = (date != null) ? date : java.time.LocalDate.now().toString();

            String where = " WHERE a.exceptions IS NOT NULL AND a.exceptions != ''";
            List<Object> params = new ArrayList<>();
            if (reqDeviceId != null) {
                where += " AND a.device_id = ?";
                params.add(reqDeviceId);
            } else if (!allowedIds.isEmpty()) {
                where += " AND a.device_id IN (" + allowedIdsStr + ")";
            }
            if (dept != null && !"All".equals(dept)) {
                where += " AND e.department = ?";
                params.add(dept);
            }
            if (filterDate != null && !filterDate.isEmpty()) {
                where += " AND a.punch_date = ?";
                params.add(filterDate);
            }
            if (search != null && !search.trim().isEmpty()) {
                String s = search.trim();
                where += " AND (e.emp_name LIKE ? OR e.emp_id LIKE ?)";
                params.add("%" + s + "%");
                params.add("%" + s + "%");
                model.addAttribute("selSearch", s);
            } else {
                model.addAttribute("selSearch", "");
            }

            String sql = "SELECT a.*, e.emp_name, e.department, e.shift, "
                    + "COALESCE(d.device_name, CASE WHEN a.in_time IS NOT NULL THEN 'Manual/No Device' ELSE '-' END) AS device_name, "
                    + "COALESCE(d.location, CASE WHEN a.in_time IS NOT NULL THEN 'Not Assigned' ELSE '-' END) AS device_location "
                    + "FROM attendance a "
                    + "JOIN employees e ON a.emp_id = e.emp_id "
                    + "LEFT JOIN devices d ON a.device_id = d.device_id "
                    + where + " ORDER BY a.punch_date DESC LIMIT " + pageSize + " OFFSET " + offset;

            data = db.query(sql, params.toArray());

            long totalRecords = db.queryLong(
                    "SELECT COUNT(*) FROM attendance a JOIN employees e ON a.emp_id = e.emp_id " + where,
                    params.toArray());
            int totalPages = (int) Math.ceil((double) totalRecords / pageSize);
            if (totalPages == 0) totalPages = 1;

            List<Map<String, Object>> depts = db.query("SELECT dept_name FROM departments WHERE status='Active'");
            model.addAttribute("depts", depts);
            model.addAttribute("data", data);
            model.addAttribute("currentPage", page);
            model.addAttribute("totalPages", totalPages);
            model.addAttribute("selDate", filterDate);
            model.addAttribute("selDept", dept != null ? dept : "All");
            List<Map<String, Object>> devices;
            if (!allowedIds.isEmpty()) {
                devices = db
                        .query("SELECT device_id, device_name FROM devices WHERE status = 'Active' AND device_id IN ("
                                + allowedIdsStr + ") ORDER BY device_name");
            } else {
                devices = db.query(
                        "SELECT device_id, device_name FROM devices WHERE status = 'Active' ORDER BY device_name");
            }
            model.addAttribute("devicesList", devices);
            model.addAttribute("selectedDeviceId", reqDeviceId);

        } catch (Exception e) {
            e.printStackTrace();
        }
        return "reports-exceptions";
    }

    @GetMapping("/reports/daily")
    public String reportsDaily(Model model,
            @RequestParam(name = "from", required = false) String from,
            @RequestParam(name = "to", required = false) String to,
            @RequestParam(name = "date", required = false) String date,
            @RequestParam(name = "dept", required = false) String dept,
            @RequestParam(name = "search", required = false) String search,
            @RequestParam(name = "deviceId", required = false) Integer reqDeviceId,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "10") int pageSize,
            @RequestParam(name = "export", defaultValue = "false") boolean isExport,
            HttpSession session) {

        if (session.getAttribute("user") == null)
            return "redirect:/login";

        List<Integer> allowedIds = getAllowedDeviceIds(session);
        if (isUserRestricted(session)) {
            model.addAttribute("noDevicesAssigned", true);
            model.addAttribute("selFrom", from != null ? from : java.time.LocalDate.now().toString());
            model.addAttribute("selTo", to != null ? to : java.time.LocalDate.now().toString());
            model.addAttribute("selDate", from != null ? from : java.time.LocalDate.now().toString());
            model.addAttribute("selDept", dept != null ? dept : "All");
            model.addAttribute("currentPage", 1);
            model.addAttribute("totalPages", 1);
            model.addAttribute("pageSize", pageSize);
            model.addAttribute("totalItems", 0);
            model.addAttribute("depts", new ArrayList<>());
            model.addAttribute("devicesList", new ArrayList<>());
            model.addAttribute("selectedDeviceId", reqDeviceId);
            model.addAttribute("data", new ArrayList<>());
            return "reports-daily";
        }

        if (reqDeviceId != null && !allowedIds.isEmpty() && !allowedIds.contains(reqDeviceId)) {
            reqDeviceId = null;
        }

        String allowedIdsStr = "";
        if (!allowedIds.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (int idx = 0; idx < allowedIds.size(); idx++) {
                if (idx > 0)
                    sb.append(",");
                sb.append(allowedIds.get(idx));
            }
            allowedIdsStr = sb.toString();
        }

        if (isExport) {
            logActivity(session, "Export Report", "Reports", "Exported Daily Report");
            pageSize = 50000;
            page = 1;
        }
        List<Map<String, Object>> data = new java.util.ArrayList<>();
        try {
            DatabaseManager db = DatabaseManager.getInstance();

            String todayStr = java.time.LocalDate.now().toString();
            String filterFrom = (from != null && !from.isEmpty()) ? from
                    : ((date != null && !date.isEmpty()) ? date : todayStr);
            String filterTo = (to != null && !to.isEmpty()) ? to
                    : ((date != null && !date.isEmpty()) ? date : todayStr);

            java.time.LocalDate startDate = java.time.LocalDate.parse(filterFrom);
            java.time.LocalDate endDate = java.time.LocalDate.parse(filterTo);
            if (startDate.isAfter(endDate)) {
                java.time.LocalDate temp = startDate;
                startDate = endDate;
                endDate = temp;
                filterFrom = startDate.toString();
                filterTo = endDate.toString();
            }

            // Query active employees matching dept, device and search
            String empSql = "SELECT e.emp_id, e.emp_name, e.department FROM employees e WHERE 1=1";
            List<Object> empParams = new ArrayList<>();
            if (reqDeviceId != null) {
                empSql += " AND (e.device_id = ? OR e.emp_id IN (SELECT DISTINCT emp_id FROM raw_logs WHERE device_id = ?))";
                empParams.add(reqDeviceId);
                empParams.add(reqDeviceId);
            } else if (!allowedIds.isEmpty()) {
                empSql += " AND (e.emp_id IN (SELECT DISTINCT emp_id FROM raw_logs WHERE device_id IN (" + allowedIdsStr
                        + ")) OR e.device_id IN (" + allowedIdsStr + "))";
            }
            if (dept != null && !"All".equals(dept)) {
                empSql += " AND e.department = ?";
                empParams.add(dept);
            }
            if (search != null && !search.trim().isEmpty()) {
                String s = search.trim();
                empSql += " AND (e.emp_name LIKE ? OR e.emp_id LIKE ?)";
                empParams.add("%" + s + "%");
                empParams.add("%" + s + "%");
                model.addAttribute("selSearch", s);
            } else {
                model.addAttribute("selSearch", "");
            }
            empSql += " ORDER BY e.emp_name ASC";
            List<Map<String, Object>> matchingEmployees = db.query(empSql, empParams.toArray());

            // Generate list of dates in the range
            List<java.time.LocalDate> dateList = new ArrayList<>();
            java.time.LocalDate curr = startDate;
            while (!curr.isAfter(endDate)) {
                dateList.add(curr);
                curr = curr.plusDays(1);
            }

            // Query attendance records for the range, joining devices to get device name
            String attSql = "SELECT a.emp_id, a.device_id, a.punch_date, a.status, a.in_time as punch_in, a.out_time as punch_out, a.work_hours, "
                    +
                    "COALESCE(d.device_name, CASE WHEN a.in_time IS NOT NULL THEN 'Manual/No Device' ELSE '-' END) AS device_name, "
                    +
                    "COALESCE(d.location, CASE WHEN a.in_time IS NOT NULL THEN 'Not Assigned' ELSE '-' END) AS device_location "
                    +
                    "FROM attendance a " +
                    "LEFT JOIN devices d ON a.device_id = d.device_id " +
                    "WHERE a.punch_date >= ? AND a.punch_date <= ?";
            List<Object> attParams = new ArrayList<>();
            attParams.add(startDate.toString());
            attParams.add(endDate.toString());
            if (reqDeviceId != null) {
                attSql += " AND a.device_id = ?";
                attParams.add(reqDeviceId);
            } else if (!allowedIds.isEmpty()) {
                attSql += " AND a.device_id IN (" + allowedIdsStr + ")";
            }
            List<Map<String, Object>> attList = db.query(attSql, attParams.toArray());

            Map<String, Map<String, List<Map<String, Object>>>> attMap = new HashMap<>();
            for (Map<String, Object> att : attList) {
                String eid = (String) att.get("emp_id");
                Object pd = att.get("punch_date");
                String dateStr = pd != null ? pd.toString() : "";
                if (!dateStr.isEmpty()) {
                    attMap.computeIfAbsent(eid, k -> new HashMap<>())
                            .computeIfAbsent(dateStr, k -> new ArrayList<>())
                            .add(att);
                }
            }

            // Build full list of employee, date, and device combinations in memory
            List<Map<String, Object>> fullList = new ArrayList<>();
            for (Map<String, Object> emp : matchingEmployees) {
                String eid = (String) emp.get("emp_id");
                for (java.time.LocalDate d : dateList) {
                    String dStr = d.toString();
                    List<Map<String, Object>> atts = null;
                    if (attMap.containsKey(eid)) {
                        atts = attMap.get(eid).get(dStr);
                    }
                    if (atts != null && !atts.isEmpty()) {
                        for (Map<String, Object> att : atts) {
                            Map<String, Object> item = new HashMap<>();
                            item.put("emp_id", emp.get("emp_id"));
                            item.put("emp_name", emp.get("emp_name"));
                            item.put("department", emp.get("department"));
                            item.put("date", dStr);
                            item.put("device_id", att.get("device_id") != null ? (int) att.get("device_id") : 0);
                            item.put("device_name", att.get("device_name"));
                            item.put("device_location", att.get("device_location"));
                            item.put("status", att.get("status"));
                            item.put("punch_in", att.get("punch_in"));
                            item.put("punch_out", att.get("punch_out"));
                            item.put("work_hours", att.get("work_hours"));
                            fullList.add(item);
                        }
                    } else {
                        Map<String, Object> item = new HashMap<>();
                        item.put("emp_id", emp.get("emp_id"));
                        item.put("emp_name", emp.get("emp_name"));
                        item.put("department", emp.get("department"));
                        item.put("date", dStr);
                        item.put("device_id", 0);
                        item.put("device_name", "-");
                        item.put("device_location", "-");
                        item.put("status", "Absent");
                        item.put("punch_in", null);
                        item.put("punch_out", null);
                        item.put("work_hours", 0.0);
                        fullList.add(item);
                    }
                }
            }

            long total = fullList.size();
            int totalPages = (int) Math.ceil((double) total / pageSize);
            if (totalPages == 0)
                totalPages = 1;
            int offset = (page - 1) * pageSize;

            List<Map<String, Object>> pageCombos = new ArrayList<>();
            for (int i = offset; i < Math.min(offset + pageSize, fullList.size()); i++) {
                pageCombos.add(fullList.get(i));
            }
            data = pageCombos;

            // Build employee biometric enrollment map
            Map<String, String> enrollMap = new HashMap<>();
            try {
                List<Map<String, Object>> activeEmpsList = db
                        .query("SELECT emp_id, device_enroll_id FROM employees WHERE status='Active'");
                for (Map<String, Object> e : activeEmpsList) {
                    String sid = DatabaseManager.str(e, "emp_id");
                    String eid = DatabaseManager.str(e, "device_enroll_id");
                    enrollMap.put(sid, sid);
                    if (eid != null && !eid.isEmpty()) {
                        enrollMap.put(eid, sid);
                    }
                    try {
                        enrollMap.put(String.valueOf(Long.parseLong(sid)), sid);
                        if (eid != null && !eid.isEmpty()) {
                            enrollMap.put(String.valueOf(Long.parseLong(eid)), sid);
                        }
                    } catch (Exception ignored) {
                    }
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }

            // Fetch day raw logs for Break Time and Net Hours calculations across ALL
            // devices
            // Option B: punches combined per employee+date regardless of device
            Map<String, Map<String, List<Map<String, Object>>>> empPunches = new HashMap<>();
            try {
                Set<String> pageDates = new HashSet<>();
                for (Map<String, Object> combo : data) {
                    pageDates.add((String) combo.get("date"));
                }
                List<Map<String, Object>> dayRawLogs = new ArrayList<>();
                if (!pageDates.isEmpty()) {
                    StringBuilder inClause = new StringBuilder();
                    List<Object> rawParams = new ArrayList<>();
                    for (String dStr : pageDates) {
                        if (inClause.length() > 0)
                            inClause.append(",");
                        inClause.append("?");
                        rawParams.add(dStr);
                    }
                    String rawLogsSql = "SELECT emp_id, device_id, punch_time, punch_type FROM raw_logs WHERE DATE(punch_time) IN ("
                            + inClause + ")";
                    if (reqDeviceId != null) {
                        rawLogsSql += " AND device_id = ?";
                        rawParams.add(reqDeviceId);
                    } else if (!allowedIds.isEmpty()) {
                        rawLogsSql += " AND device_id IN (" + allowedIdsStr + ")";
                    }
                    rawLogsSql += " ORDER BY punch_time ASC";
                    dayRawLogs = db.query(rawLogsSql, rawParams.toArray());
                }
                for (Map<String, Object> log : dayRawLogs) {
                    Object eidObj = log.get("emp_id");
                    if (eidObj == null)
                        continue;
                    String rawEid = eidObj.toString().trim();
                    if (rawEid.isEmpty())
                        continue;

                    String matchedSid = enrollMap.get(rawEid);
                    if (matchedSid == null) {
                        try {
                            matchedSid = enrollMap.get(String.valueOf(Long.parseLong(rawEid)));
                        } catch (Exception ignored) {
                        }
                    }
                    if (matchedSid != null) {
                        Object pt = log.get("punch_time");
                        int type = log.get("punch_type") != null ? (int) log.get("punch_type") : 0;
                        java.time.LocalDateTime ldt = null;
                        if (pt instanceof java.time.LocalDateTime) {
                            ldt = (java.time.LocalDateTime) pt;
                        } else if (pt instanceof java.sql.Timestamp) {
                            ldt = ((java.sql.Timestamp) pt).toLocalDateTime();
                        } else if (pt != null) {
                            try {
                                ldt = java.time.LocalDateTime.parse(pt.toString().replace(" ", "T").split("\\.")[0]);
                            } catch (Exception ignored) {
                            }
                        }
                        if (ldt != null) {
                            Map<String, Object> pMap = new HashMap<>();
                            pMap.put("time", ldt);
                            pMap.put("type", type);
                            String punchDateStr = ldt.toLocalDate().toString();
                            // Group by emp+date only (Option B — no device splitting)
                            empPunches.computeIfAbsent(matchedSid, k -> new HashMap<>())
                                    .computeIfAbsent(punchDateStr, k -> new ArrayList<>())
                                    .add(pMap);
                        }
                    }
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }

            java.time.format.DateTimeFormatter timeFmt = java.time.format.DateTimeFormatter.ofPattern("hh:mm a");
            for (Map<String, Object> r : data) {
                String empId = r.get("emp_id").toString();
                String dStr = r.get("date").toString();
                // Option B: Look up all punches for this employee on this date (across all
                // devices)
                List<Map<String, Object>> punches = null;
                if (empPunches.containsKey(empId) && empPunches.get(empId).containsKey(dStr)) {
                    punches = empPunches.get(empId).get(dStr);
                }

                double breakHours = 0;
                double productiveHours = 0;

                if (punches != null && !punches.isEmpty()) {
                    com.bhspl.util.AttendanceCalculator.Metrics met = new com.bhspl.util.AttendanceCalculator.Metrics();
                    com.bhspl.util.AttendanceCalculator.calculateFromPunches(punches, null, met);

                    breakHours = met.breakHours;
                    productiveHours = met.workHours;

                    if (met.firstIn != null)
                        r.put("punch_in", met.firstIn);
                    if (met.lastOut != null)
                        r.put("punch_out", met.lastOut);

                    r.put("work_hours", String.format(java.util.Locale.US, "%.1f", met.duration));
                    r.put("break_time", com.bhspl.util.AttendanceCalculator.formatDuration(met.breakHours));
                    r.put("net_working_hours", com.bhspl.util.AttendanceCalculator.formatDuration(met.workHours));

                    java.time.format.DateTimeFormatter dtFmt = java.time.format.DateTimeFormatter
                            .ofPattern("hh:mm:ss a");

                    // Format Break Details
                    StringBuilder breakDetails = new StringBuilder();
                    if (met.breakIntervals != null && !met.breakIntervals.isEmpty()) {
                        int index = 1;
                        for (Map<String, Object> interval : met.breakIntervals) {
                            java.time.LocalDateTime start = (java.time.LocalDateTime) interval.get("start");
                            java.time.LocalDateTime end = (java.time.LocalDateTime) interval.get("end");
                            long durSecs = (long) interval.get("duration");
                            String durStr = String.format("%02d:%02d:%02d", durSecs / 3600, (durSecs % 3600) / 60,
                                    durSecs % 60);
                            breakDetails.append("Break ").append(index++).append(": ")
                                    .append(start.format(dtFmt)).append(" → ")
                                    .append(end.format(dtFmt)).append(" = ").append(durStr).append("|");
                        }
                        breakDetails.append("Total Break Time: ")
                                .append(com.bhspl.util.AttendanceCalculator.formatDuration(breakHours));
                    } else {
                        breakDetails.append("No break records found.");
                    }
                    r.put("break_details", breakDetails.toString());

                    // Format Work Details
                    StringBuilder workDetails = new StringBuilder();
                    if (met.firstIn != null && met.lastOut != null) {
                        workDetails.append("First IN: ").append(met.firstIn.format(dtFmt)).append("|");
                        workDetails.append("Last OUT: ").append(met.lastOut.format(dtFmt)).append("|");
                        workDetails.append("Total Duration: ")
                                .append(com.bhspl.util.AttendanceCalculator.formatDuration(met.duration));
                    } else {
                        workDetails.append("Incomplete punch records.");
                    }
                    r.put("work_details", workDetails.toString());

                } else {
                    // Fallback to attendance record punch_in and punch_out if raw logs are not
                    // present or insufficient
                    java.time.LocalDateTime firstIn = parseDateTime(r.get("punch_in"));
                    java.time.LocalDateTime lastOut = parseDateTime(r.get("punch_out"));

                    if (firstIn != null && lastOut != null) {
                        long totalMins = java.time.Duration.between(firstIn, lastOut).toMinutes();
                        if (totalMins > 0) {
                            productiveHours = Math.max(0, totalMins) / 60.0;
                        }
                    } else {
                        // Fallback to work_hours if datetimes are not present or invalid
                        productiveHours = DatabaseManager.dbl(r, "work_hours");
                    }
                }

                String formattedNetWorkingHours = com.bhspl.util.AttendanceCalculator.formatDuration(productiveHours);
                System.out.println("DEBUG: EmpId: " + empId + " | punches size: "
                        + (punches != null ? punches.size() : "null") + " | breakHours: " + breakHours
                        + " | productiveHours: " + productiveHours + " | netWorkingHours: " + formattedNetWorkingHours);
                String formattedBreakTime = com.bhspl.util.AttendanceCalculator.formatDuration(breakHours);
                r.put("break_time", formattedBreakTime);
                r.put("productive_time", formattedNetWorkingHours);
                r.put("net_working_hours", formattedNetWorkingHours);

                if (r.get("break_details") == null)
                    r.put("break_details", "No detailed records available.");
                if (r.get("work_details") == null)
                    r.put("work_details", "No detailed records available.");

                // Format Net Details
                String totalDur = "00:00:00";
                if (r.get("work_details") != null && r.get("work_details").toString().contains("Total Duration: ")) {
                    String wd = r.get("work_details").toString();
                    totalDur = wd.substring(wd.indexOf("Total Duration: ") + 16);
                }
                StringBuilder netDetails = new StringBuilder();
                netDetails.append("Total Duration: ").append(totalDur).append("|");
                netDetails.append("Break Time: ").append(formattedBreakTime).append("|");
                netDetails.append("Net Working Hours: ").append(formattedNetWorkingHours);
                r.put("net_details", netDetails.toString());

                Object inVal = r.get("punch_in");
                if (inVal != null) {
                    if (inVal instanceof java.sql.Timestamp) {
                        r.put("punch_in", ((java.sql.Timestamp) inVal).toLocalDateTime().format(timeFmt));
                    } else if (inVal instanceof java.time.LocalDateTime) {
                        r.put("punch_in", ((java.time.LocalDateTime) inVal).format(timeFmt));
                    } else {
                        try {
                            java.time.LocalDateTime dt = java.time.LocalDateTime
                                    .parse(inVal.toString().replace(" ", "T").substring(0, 19));
                            r.put("punch_in", dt.format(timeFmt));
                        } catch (Exception e) {
                            r.put("punch_in", inVal.toString());
                        }
                    }
                }

                Object outVal = r.get("punch_out");
                if (outVal != null) {
                    if (outVal instanceof java.sql.Timestamp) {
                        r.put("punch_out", ((java.sql.Timestamp) outVal).toLocalDateTime().format(timeFmt));
                    } else if (outVal instanceof java.time.LocalDateTime) {
                        r.put("punch_out", ((java.time.LocalDateTime) outVal).format(timeFmt));
                    } else {
                        try {
                            java.time.LocalDateTime dt = java.time.LocalDateTime
                                    .parse(outVal.toString().replace(" ", "T").substring(0, 19));
                            r.put("punch_out", dt.format(timeFmt));
                        } catch (Exception e) {
                            r.put("punch_out", outVal.toString());
                        }
                    }
                }
            }

            model.addAttribute("selFrom", filterFrom);
            model.addAttribute("selTo", filterTo);
            model.addAttribute("selDate", filterFrom);
            model.addAttribute("selDept", (dept != null) ? dept : "All");
            model.addAttribute("currentPage", page);
            model.addAttribute("totalPages", totalPages);
            model.addAttribute("pageSize", pageSize);
            model.addAttribute("totalItems", total);
            model.addAttribute("depts", db.query("SELECT dept_name FROM departments ORDER BY dept_name"));
            List<Map<String, Object>> devices;
            if (!allowedIds.isEmpty()) {
                devices = db
                        .query("SELECT device_id, device_name FROM devices WHERE status = 'Active' AND device_id IN ("
                                + allowedIdsStr + ") ORDER BY device_name");
            } else {
                devices = db.query(
                        "SELECT device_id, device_name FROM devices WHERE status = 'Active' ORDER BY device_name");
            }
            model.addAttribute("devicesList", devices);
            model.addAttribute("selectedDeviceId", reqDeviceId);
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
            @RequestParam(name = "empSearch", required = false) String empSearch,
            @RequestParam(name = "deviceId", required = false) Integer reqDeviceId,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "10") int pageSize,
            @RequestParam(name = "export", defaultValue = "false") boolean isExport,
            HttpSession session) {

        if (session.getAttribute("user") == null)
            return "redirect:/login";

        List<Integer> allowedIds = getAllowedDeviceIds(session);
        if (isUserRestricted(session)) {
            model.addAttribute("noDevicesAssigned", true);
            String selM = String.format("%02d", java.time.LocalDate.now().getMonthValue());
            try { if (month != null) selM = String.format("%02d", Integer.parseInt(month)); } catch(Exception ignored) {}
            model.addAttribute("selMonth", selM);
            model.addAttribute("selYear", year != null ? year : String.valueOf(java.time.LocalDate.now().getYear()));
            model.addAttribute("selDept", dept != null ? dept : "All");
            model.addAttribute("selType", type != null ? type : "PA");
            model.addAttribute("currentPage", 1);
            model.addAttribute("totalPages", 1);
            model.addAttribute("pageSize", pageSize);
            model.addAttribute("totalItems", 0);
            model.addAttribute("depts", new ArrayList<>());
            model.addAttribute("devicesList", new ArrayList<>());
            model.addAttribute("selectedDeviceId", reqDeviceId);
            model.addAttribute("data", new ArrayList<>());
            model.addAttribute("days", new ArrayList<>());
            return "reports-monthly";
        }

        if (reqDeviceId != null && !allowedIds.isEmpty() && !allowedIds.contains(reqDeviceId)) {
            reqDeviceId = null;
        }

        String allowedIdsStr = "";
        if (!allowedIds.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (int idx = 0; idx < allowedIds.size(); idx++) {
                if (idx > 0)
                    sb.append(",");
                sb.append(allowedIds.get(idx));
            }
            allowedIdsStr = sb.toString();
        }

        if (isExport) {
            logActivity(session, "Export Report", "Reports", "Exported Monthly Report");
            pageSize = 50000;
            page = 1;
        }
        List<Map<String, Object>> matrix = new java.util.ArrayList<>();
        try {
            DatabaseManager db = DatabaseManager.getInstance();
            java.time.LocalDate today = java.time.LocalDate.now();
            int curM = today.getMonthValue();
            int curY = today.getYear();
            try {
                if (month != null) curM = Integer.parseInt(month);
                if (year != null) curY = Integer.parseInt(year);
            } catch (Exception ignored) {}
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
            List<Object> params = new ArrayList<>();
            if (reqDeviceId != null) {
                countSql += " AND (device_id = ? OR emp_id IN (SELECT DISTINCT emp_id FROM raw_logs WHERE device_id = ?))";
                params.add(reqDeviceId);
                params.add(reqDeviceId);
            } else if (!allowedIds.isEmpty()) {
                countSql += " AND (emp_id IN (SELECT DISTINCT emp_id FROM raw_logs WHERE device_id IN (" + allowedIdsStr
                        + ")) OR device_id IN (" + allowedIdsStr + "))";
            }
            if (dept != null && !"All".equals(dept)) {
                countSql += " AND department = ?";
                params.add(dept);
            }
            if (empSearch != null && !empSearch.trim().isEmpty()) {
                String safeSearch = empSearch.trim();
                countSql += " AND (emp_name LIKE ? OR emp_id LIKE ?)";
                params.add("%" + safeSearch + "%");
                params.add("%" + safeSearch + "%");
            }
            long total = db.queryLong(countSql, params.toArray());
            int totalPages = (int) Math.ceil((double) total / pageSize);
            if (totalPages == 0)
                totalPages = 1;
            int offset = (page - 1) * pageSize;

            String empSql = "SELECT emp_id, emp_name, designation, department FROM employees WHERE 1=1";
            List<Object> empParams = new ArrayList<>();
            if (reqDeviceId != null) {
                empSql += " AND (device_id = ? OR emp_id IN (SELECT DISTINCT emp_id FROM raw_logs WHERE device_id = ?))";
                empParams.add(reqDeviceId);
                empParams.add(reqDeviceId);
            } else if (!allowedIds.isEmpty()) {
                empSql += " AND (emp_id IN (SELECT DISTINCT emp_id FROM raw_logs WHERE device_id IN (" + allowedIdsStr
                        + ")) OR device_id IN (" + allowedIdsStr + "))";
            }
            if (dept != null && !"All".equals(dept)) {
                empSql += " AND department = ?";
                empParams.add(dept);
            }
            if (empSearch != null && !empSearch.trim().isEmpty()) {
                String safeSearch = empSearch.trim();
                empSql += " AND (emp_name LIKE ? OR emp_id LIKE ?)";
                empParams.add("%" + safeSearch + "%");
                empParams.add("%" + safeSearch + "%");
            }
            empSql += " ORDER BY emp_name ASC LIMIT " + pageSize + " OFFSET " + offset;
            List<Map<String, Object>> employees = db.query(empSql, empParams.toArray());

            model.addAttribute("currentPage", page);
            model.addAttribute("totalPages", totalPages);
            model.addAttribute("pageSize", pageSize);
            model.addAttribute("totalItems", total);
            model.addAttribute("selEmpSearch", empSearch);

            List<Map<String, Object>> allDevices = new ArrayList<>();
            try {
                if (!allowedIds.isEmpty()) {
                    allDevices = db.query("SELECT device_id, device_name, location FROM devices WHERE device_id IN ("
                            + allowedIdsStr + ")");
                } else {
                    allDevices = db.query("SELECT device_id, device_name, location FROM devices");
                }
            } catch (Exception ignored) {
            }
            Map<Integer, String> deviceNamesMap = new HashMap<>();
            Map<Integer, String> deviceLocationsMap = new HashMap<>();
            for (Map<String, Object> dev : allDevices) {
                int devId = (int) dev.get("device_id");
                deviceNamesMap.put(devId, (String) dev.get("device_name"));
                String loc = (String) dev.get("location");
                deviceLocationsMap.put(devId, loc != null && !loc.trim().isEmpty() ? loc : "Not Assigned");
            }

            for (Map<String, Object> emp : employees) {
                String eid = (String) emp.get("emp_id");
                List<Integer> deviceIds = new ArrayList<>();
                if (reqDeviceId != null) {
                    deviceIds.add(reqDeviceId);
                } else {
                    List<Map<String, Object>> deviceListDb = new ArrayList<>();
                    try {
                        if (!allowedIds.isEmpty()) {
                            deviceListDb = db.query(
                                    "SELECT DISTINCT device_id FROM attendance WHERE emp_id=? AND device_id IN ("
                                            + allowedIdsStr + ") AND MONTH(punch_date)=? AND YEAR(punch_date)=?",
                                    eid, curM, curY);
                        } else {
                            deviceListDb = db.query(
                                    "SELECT DISTINCT device_id FROM attendance WHERE emp_id=? AND MONTH(punch_date)=? AND YEAR(punch_date)=?",
                                    eid, curM, curY);
                        }
                    } catch (Exception ignored) {
                    }
                    for (Map<String, Object> dMap : deviceListDb) {
                        deviceIds.add(dMap.get("device_id") != null ? (int) dMap.get("device_id") : 0);
                    }
                    if (deviceIds.isEmpty()) {
                        deviceIds.add(0);
                    }
                }

                for (int deviceId : deviceIds) {
                    String deviceName = deviceNamesMap.getOrDefault(deviceId,
                            deviceId == 0 ? "Manual/No Device" : "Unknown Device");
                    String deviceLocation = deviceLocationsMap.getOrDefault(deviceId,
                            deviceId == 0 ? "Not Assigned" : "Unknown Location");
                    Map<String, Object> row = new HashMap<>(emp);
                    row.put("device_id", deviceId);
                    row.put("device_name", deviceName);
                    row.put("device_location", deviceLocation);
                    Map<Integer, Map<String, Object>> attendanceMap = new HashMap<>();

                    List<Map<String, Object>> att = db.query(
                            "SELECT DAY(punch_date) as d, status, in_time, out_time, work_hours FROM attendance WHERE emp_id=? AND device_id=? AND MONTH(punch_date)=? AND YEAR(punch_date)=?",
                            eid, deviceId, curM, curY);
                    for (Map<String, Object> a : att) {
                        int dayNum = DatabaseManager.num(a, "d");
                        attendanceMap.put(dayNum, a);
                    }

                    List<Map<String, Object>> dayStatuses = new ArrayList<>();
                    for (int d = 1; d <= daysInMonth; d++) {
                        java.time.LocalDate date = java.time.LocalDate.of(curY, curM, d);
                        Map<String, Object> cellData = new HashMap<>();
                        cellData.put("date", date.toString());

                        if (holidayMap.containsKey(d)) {
                            cellData.put("status", holidayMap.get(d).toUpperCase());
                        } else if (date.getDayOfWeek() == java.time.DayOfWeek.SUNDAY) {
                            cellData.put("status", "SUN");
                        } else {
                            Map<String, Object> data = attendanceMap.get(d);
                            if (data == null) {
                                cellData.put("status", "A");
                            } else {
                                if ("WH".equals(reportType)) {
                                    String inStr = DatabaseManager.str(data, "in_time");
                                    String outStr = DatabaseManager.str(data, "out_time");
                                    double wh = DatabaseManager.dbl(data, "work_hours");

                                    String in = "";
                                    java.time.LocalDateTime inDateTime = null;
                                    if (!inStr.isEmpty()) {
                                        try {
                                            inDateTime = java.time.LocalDateTime
                                                    .parse(inStr.replace(" ", "T").split("\\.")[0]);
                                            in = String.format("%02d:%02d", inDateTime.getHour(),
                                                    inDateTime.getMinute());
                                        } catch (Exception e) {
                                        }
                                    }

                                    String out = "";
                                    java.time.LocalDateTime outDateTime = null;
                                    if (!outStr.isEmpty()) {
                                        try {
                                            outDateTime = java.time.LocalDateTime
                                                    .parse(outStr.replace(" ", "T").split("\\.")[0]);
                                            out = String.format("%02d:%02d", outDateTime.getHour(),
                                                    outDateTime.getMinute());
                                        } catch (Exception e) {
                                        }
                                    }

                                    if (in.isEmpty()) {
                                        cellData.put("status", "P");
                                    } else {
                                        double totalHours = 0;
                                        if (inDateTime != null && outDateTime != null) {
                                            totalHours = java.time.Duration.between(inDateTime, outDateTime).toMinutes()
                                                    / 60.0;
                                        }
                                        double breakHours = totalHours - wh;
                                        if (breakHours < 0)
                                            breakHours = 0;

                                        cellData.put("status", "WH");
                                        cellData.put("inTime", in);
                                        cellData.put("outTime", out.isEmpty() ? "--:--" : out);
                                        cellData.put("netHours",
                                                com.bhspl.util.AttendanceCalculator.formatDuration(wh));
                                        cellData.put("totalHours",
                                                com.bhspl.util.AttendanceCalculator.formatDuration(totalHours));
                                        cellData.put("breakHours",
                                                com.bhspl.util.AttendanceCalculator.formatDuration(breakHours));
                                    }
                                } else {
                                    String s = DatabaseManager.str(data, "status");
                                    String inTime = DatabaseManager.str(data, "in_time");

                                    if ("Present".equals(s) || "Late".equals(s) || "Early".equals(s)
                                            || !inTime.isEmpty()) {
                                        cellData.put("status", "P");
                                    } else if (s.isEmpty()) {
                                        cellData.put("status", "A");
                                    } else {
                                        cellData.put("status", s.substring(0, 1));
                                    }
                                }
                            }
                        }
                        dayStatuses.add(cellData);
                    }
                    row.put("days", dayStatuses);
                    matrix.add(row);
                }
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
            List<Map<String, Object>> devices;
            if (!allowedIds.isEmpty()) {
                devices = db
                        .query("SELECT device_id, device_name FROM devices WHERE status = 'Active' AND device_id IN ("
                                + allowedIdsStr + ") ORDER BY device_name");
            } else {
                devices = db.query(
                        "SELECT device_id, device_name FROM devices WHERE status = 'Active' ORDER BY device_name");
            }
            model.addAttribute("devicesList", devices);
            model.addAttribute("selectedDeviceId", reqDeviceId);
        } catch (Exception e) {
            System.err.println("CRITICAL ERROR in reportsMonthly: " + e.getMessage());
            e.printStackTrace();
        }
        return "reports-monthly";
    }

    @GetMapping("/api/reports/daily-punches")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getDailyPunches(
            @RequestParam("empId") String empId,
            @RequestParam("date") String date,
            @RequestParam(name = "deviceId", required = false) Integer deviceId,
            HttpSession session) {
        Map<String, Object> response = new HashMap<>();
        if (session.getAttribute("user") == null) {
            response.put("status", "error");
            response.put("message", "Unauthorized");
            return ResponseEntity.status(401).body(response);
        }

        List<Integer> allowedIds = getAllowedDeviceIds(session);
        if (isUserRestricted(session) && allowedIds.isEmpty()) {
            response.put("status", "error");
            response.put("message", "No devices assigned");
            return ResponseEntity.status(403).body(response);
        }

        if (deviceId != null && !allowedIds.isEmpty() && !allowedIds.contains(deviceId)) {
            deviceId = null; // Ignore unauthorized device filter
        }

        String allowedIdsStr = "";
        if (!allowedIds.isEmpty()) {
            allowedIdsStr = allowedIds.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
        }

        try {
            DatabaseManager db = DatabaseManager.getInstance();
            // Get employee shift
            Map<String, Object> emp = db.fetchOne("SELECT shift FROM employees WHERE emp_id=?", empId);
            Map<String, Object> shift = null;
            boolean isOvernight = false;

            if (emp != null && emp.get("shift") != null) {
                shift = db.fetchOne("SELECT * FROM shifts WHERE shift_name=?", emp.get("shift"));
                if (shift != null) {
                    try {
                        Object st = shift.get("start_time");
                        Object et = shift.get("end_time");
                        if (st != null && et != null) {
                            java.time.LocalTime sTime = java.time.LocalTime.parse(st.toString().substring(0, 5));
                            java.time.LocalTime eTime = java.time.LocalTime.parse(et.toString().substring(0, 5));
                            if (eTime.isBefore(sTime))
                                isOvernight = true;
                        }
                    } catch (Exception ignored) {
                    }
                }
            }

            // Fetch raw logs
            String startRange = date + " 00:00:00";
            String endRange = java.time.LocalDate.parse(date).plusDays(1).toString() + " 10:00:00"; // include next
                                                                                                    // morning for night
                                                                                                    // shifts

            List<Map<String, Object>> rawLogs;
            String deviceFilter = "";
            List<Object> params = new ArrayList<>();
            params.add(empId);
            
            if (deviceId != null) {
                deviceFilter = " AND r.device_id=? ";
                params.add(deviceId);
            } else if (!allowedIds.isEmpty()) {
                deviceFilter = " AND r.device_id IN (" + allowedIdsStr + ") ";
            }
            
            params.add(startRange);
            params.add(endRange);

            rawLogs = db.query(
                    "SELECT r.punch_time, r.punch_type, d.device_name " +
                            "FROM raw_logs r LEFT JOIN devices d ON r.device_id = d.device_id " +
                            "WHERE r.emp_id=?" + deviceFilter + " AND r.punch_time >= ? AND r.punch_time < ? " +
                            "ORDER BY r.punch_time ASC",
                    params.toArray());

            List<Map<String, Object>> calcInput = new ArrayList<>();

            for (Map<String, Object> log : rawLogs) {
                Object pt = log.get("punch_time");
                int type = log.get("punch_type") != null ? (int) log.get("punch_type") : 0;

                java.time.LocalDateTime ldt = null;
                if (pt instanceof java.time.LocalDateTime)
                    ldt = (java.time.LocalDateTime) pt;
                else if (pt instanceof java.sql.Timestamp)
                    ldt = ((java.sql.Timestamp) pt).toLocalDateTime();
                else {
                    try {
                        ldt = java.time.LocalDateTime.parse(pt.toString().replace(" ", "T").split("\\.")[0]);
                    } catch (Exception e) {
                    }
                }

                if (ldt != null) {
                    // Filter out next morning if not overnight shift
                    if (!isOvernight && !ldt.toLocalDate().toString().equals(date))
                        continue;

                    Map<String, Object> p = new HashMap<>();
                    p.put("time", ldt);
                    p.put("type", type);
                    p.put("deviceName", log.get("device_name") != null ? log.get("device_name") : "Unknown Device");
                    calcInput.add(p);
                }
            }

            // Sort to ensure chronological order
            calcInput.sort((p1, p2) -> ((java.time.LocalDateTime) p1.get("time"))
                    .compareTo((java.time.LocalDateTime) p2.get("time")));

            // Filter duplicates exactly like AttendanceCalculator
            List<Map<String, Object>> filtered = new ArrayList<>();
            java.time.LocalDateTime lastTime = null;
            int lastType = -1;
            for (Map<String, Object> p : calcInput) {
                java.time.LocalDateTime t = (java.time.LocalDateTime) p.get("time");
                int type = (int) p.get("type");
                if (lastTime != null) {
                    long diff = Math.abs(java.time.Duration.between(lastTime, t).getSeconds());
                    if (diff < 60 && type == lastType)
                        continue; // skip duplicate within 60s
                }
                filtered.add(p);
                lastTime = t;
                lastType = type;
            }

            java.time.format.DateTimeFormatter timeFmt = java.time.format.DateTimeFormatter.ofPattern("hh:mm a");
            List<Map<String, Object>> formattedPunches = new ArrayList<>();

            for (int i = 0; i < filtered.size(); i++) {
                Map<String, Object> p = filtered.get(i);
                java.time.LocalDateTime ldt = (java.time.LocalDateTime) p.get("time");

                // AttendanceCalculator assumes even index = IN, odd index = OUT
                int calculatedType = (i % 2 == 0) ? 1 : 2;

                String timeStr = ldt.format(timeFmt);
                if (!ldt.toLocalDate().toString().equals(date)) {
                    timeStr += " (+1)";
                }

                Map<String, Object> fp = new HashMap<>();
                fp.put("time", ldt);
                fp.put("type", calculatedType);
                fp.put("deviceName", p.get("deviceName"));
                fp.put("formattedTime", timeStr);
                formattedPunches.add(fp);
            }

            com.bhspl.util.AttendanceCalculator.Metrics metrics = new com.bhspl.util.AttendanceCalculator.Metrics();
            if (!filtered.isEmpty()) {
                com.bhspl.util.AttendanceCalculator.calculateFromPunches(filtered, shift, metrics);
            }

            // Format break intervals
            List<Map<String, Object>> breakIntervals = new ArrayList<>();
            for (Map<String, Object> b : metrics.breakIntervals) {
                java.time.LocalDateTime start = (java.time.LocalDateTime) b.get("start");
                java.time.LocalDateTime end = (java.time.LocalDateTime) b.get("end");
                long durMins = (long) b.get("duration");

                String startStr = start.format(timeFmt);
                if (!start.toLocalDate().toString().equals(date))
                    startStr += " (+1)";

                String endStr = end.format(timeFmt);
                if (!end.toLocalDate().toString().equals(date))
                    endStr += " (+1)";

                Map<String, Object> bMap = new HashMap<>();
                bMap.put("start", startStr);
                bMap.put("end", endStr);
                bMap.put("duration", durMins + " mins");
                breakIntervals.add(bMap);
            }

            response.put("status", "success");
            response.put("punches", formattedPunches);
            response.put("breaks", breakIntervals);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            e.printStackTrace();
            response.put("status", "error");
            response.put("message", e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    @GetMapping("/reports/leave")
    public String reportsLeave(Model model,
            @RequestParam(name = "from", required = false) String from,
            @RequestParam(name = "to", required = false) String to,
            @RequestParam(name = "dept", required = false) String dept,
            @RequestParam(name = "deviceId", required = false) Integer reqDeviceId,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "10") int pageSize,
            @RequestParam(name = "export", defaultValue = "false") boolean isExport,
            HttpSession session) {

        if (session.getAttribute("user") == null)
            return "redirect:/login";

        List<Integer> allowedIds = getAllowedDeviceIds(session);
        if (isUserRestricted(session)) {
            model.addAttribute("noDevicesAssigned", true);
            model.addAttribute("selFrom", from != null ? from : java.time.LocalDate.now().withDayOfMonth(1).toString());
            model.addAttribute("selTo", to != null ? to : java.time.LocalDate.now().toString());
            model.addAttribute("selDept", dept != null ? dept : "All");
            model.addAttribute("depts", new ArrayList<>());
            model.addAttribute("currentPage", 1);
            model.addAttribute("totalPages", 1);
            model.addAttribute("pageSize", pageSize);
            model.addAttribute("totalItems", 0);
            model.addAttribute("devicesList", new ArrayList<>());
            model.addAttribute("selectedDeviceId", reqDeviceId);
            model.addAttribute("data", new ArrayList<>());
            return "reports-leave";
        }

        if (reqDeviceId != null && !allowedIds.isEmpty() && !allowedIds.contains(reqDeviceId)) {
            reqDeviceId = null;
        }

        String allowedIdsStr = "";
        if (!allowedIds.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (int idx = 0; idx < allowedIds.size(); idx++) {
                if (idx > 0)
                    sb.append(",");
                sb.append(allowedIds.get(idx));
            }
            allowedIdsStr = sb.toString();
        }

        if (isExport) {
            logActivity(session, "Export Report", "Reports", "Exported Leave Report");
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
            List<Object> params = new ArrayList<>();
            params.add(f);
            params.add(t);

            if (reqDeviceId != null) {
                where += " AND (e.device_id = ? OR e.emp_id IN (SELECT DISTINCT emp_id FROM raw_logs WHERE device_id = ?))";
                params.add(reqDeviceId);
                params.add(reqDeviceId);
            } else if (!allowedIds.isEmpty()) {
                where += " AND (e.emp_id IN (SELECT DISTINCT emp_id FROM raw_logs WHERE device_id IN (" + allowedIdsStr
                        + ")) OR e.device_id IN (" + allowedIdsStr + "))";
            }
            if (dept != null && !"All".equals(dept)) {
                where += " AND e.department = ?";
                params.add(dept);
            }

            long total = db.queryLong("SELECT COUNT(*) FROM leaves l JOIN employees e ON l.emp_id = e.emp_id" + where,
                    params.toArray());
            int totalPages = (int) Math.ceil((double) total / pageSize);
            if (totalPages == 0)
                totalPages = 1;

            String sql = "SELECT l.*, e.emp_name, e.department FROM leaves l " +
                    "JOIN employees e ON l.emp_id = e.emp_id " + where +
                    " ORDER BY e.emp_name ASC, l.from_date DESC LIMIT " + pageSize + " OFFSET " + offset;

            data = db.query(sql, params.toArray());
            model.addAttribute("selFrom", f);
            model.addAttribute("selTo", t);
            model.addAttribute("selDept", (dept != null) ? dept : "All");
            model.addAttribute("depts", db.query("SELECT dept_name FROM departments ORDER BY dept_name"));
            model.addAttribute("currentPage", page);
            model.addAttribute("totalPages", totalPages);
            model.addAttribute("pageSize", pageSize);
            model.addAttribute("totalItems", total);
            List<Map<String, Object>> devices;
            if (!allowedIds.isEmpty()) {
                devices = db
                        .query("SELECT device_id, device_name FROM devices WHERE status = 'Active' AND device_id IN ("
                                + allowedIdsStr + ") ORDER BY device_name");
            } else {
                devices = db.query(
                        "SELECT device_id, device_name FROM devices WHERE status = 'Active' ORDER BY device_name");
            }
            model.addAttribute("devicesList", devices);
            model.addAttribute("selectedDeviceId", reqDeviceId);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "reports-leave";
    }

    @GetMapping("/raw-logs")
    public String rawLogs(Model model,
            @RequestParam(name = "from", required = false) String from,
            @RequestParam(name = "to", required = false) String to,
            @RequestParam(name = "dept", required = false) String dept,
            @RequestParam(name = "emp", required = false) String emp,
            @RequestParam(name = "device", required = false) String device,
            @RequestParam(name = "search", required = false) String search,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "10") int pageSize,
            @RequestParam(name = "export", defaultValue = "false") boolean isExport,
            HttpSession session) {
        if (isExport) {
            pageSize = 50000;
            page = 1;
        }
        return handleRawLogs(model, from, to, dept, emp, device, search, "raw-logs", page, pageSize, session);
    }

    @GetMapping("/raw-logs/report")
    public String rawLogsReport(Model model,
            @RequestParam(name = "from", required = false) String from,
            @RequestParam(name = "to", required = false) String to,
            @RequestParam(name = "dept", required = false) String dept,
            @RequestParam(name = "emp", required = false) String emp,
            @RequestParam(name = "device", required = false) String device,
            @RequestParam(name = "search", required = false) String search,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "10") int pageSize,
            @RequestParam(name = "export", defaultValue = "false") boolean isExport,
            HttpSession session) {
        if (isExport) {
            logActivity(session, "Export Report", "Reports", "Exported Raw Logs Report");
            pageSize = 50000;
            page = 1;
        }
        return handleRawLogs(model, from, to, dept, emp, device, search, "report-raw-logs", page, pageSize, session);
    }

    @PostMapping("/api/raw-logs/generate")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> generateRawLogs(
            @RequestParam("from") String fromDateStr,
            @RequestParam("to") String toDateStr,
            @RequestParam(name = "emp", required = false) String emp,
            @RequestParam(name = "search", required = false) String search,
            HttpSession session) {

        Map<String, Object> response = new HashMap<>();
        if (session.getAttribute("user") == null) {
            response.put("success", false);
            response.put("message", "Unauthorized");
            return ResponseEntity.status(401).body(response);
        }

        try {
            System.out.println("[API] generateRawLogs: from=" + fromDateStr + ", to=" + toDateStr + ", emp=" + emp
                    + ", search=" + search);
            DatabaseManager db = DatabaseManager.getInstance();
            java.time.LocalDate start = java.time.LocalDate.parse(fromDateStr);
            java.time.LocalDate end = java.time.LocalDate.parse(toDateStr);

            if (start.isAfter(end)) {
                response.put("success", false);
                response.put("message", "From date cannot be after To date");
                return ResponseEntity.badRequest().body(response);
            }

            // Get active employees with optional filter
            String empSql = "SELECT emp_id, emp_name FROM employees WHERE status='Active'";
            List<Object> queryParams = new ArrayList<>();
            if (emp != null && !emp.trim().isEmpty() && !"All".equalsIgnoreCase(emp.trim())) {
                empSql += " AND emp_id = ?";
                queryParams.add(emp.trim());
            } else if (search != null && !search.trim().isEmpty()) {
                empSql += " AND (emp_name LIKE ? OR emp_id = ?)";
                queryParams.add("%" + search.trim() + "%");
                queryParams.add(search.trim());
            }
            List<Map<String, Object>> employees = db.query(empSql, queryParams.toArray());

            // Get active devices
            List<Map<String, Object>> devices = db.query("SELECT device_id FROM devices WHERE status='Active'");
            int defaultDevId = devices.isEmpty() ? 0 : ((Number) devices.get(0).get("device_id")).intValue();

            int count = 0;
            db.setAutoCommit(false);
            try {
                java.time.format.DateTimeFormatter dtf = java.time.format.DateTimeFormatter
                        .ofPattern("yyyy-MM-dd HH:mm:ss");
                java.util.Random rand = new java.util.Random();

                for (java.time.LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
                    String dayName = date.getDayOfWeek().toString();
                    if ("SUNDAY".equalsIgnoreCase(dayName)) {
                        continue;
                    }

                    for (Map<String, Object> employee : employees) {
                        String empId = (String) employee.get("emp_id");

                        // 10% chance employee is absent on this day
                        if (rand.nextInt(100) < 10) {
                            continue;
                        }

                        // Generate IN punch: ~08:45 to ~09:30
                        int inMinOffset = rand.nextInt(45) - 15; // -15 to +30 mins from 9:00 AM
                        java.time.LocalDateTime inTime = date.atTime(9, 0).plusMinutes(inMinOffset)
                                .plusSeconds(rand.nextInt(60));

                        db.execute(
                                "INSERT INTO raw_logs (device_id, emp_id, punch_time, punch_type, synced) VALUES (?,?,?,?,0)",
                                defaultDevId, empId, inTime.format(dtf), 0);
                        count++;

                        // 5% chance employee forgets to check out (missing OUT punch)
                        if (rand.nextInt(100) < 5) {
                            continue;
                        }

                        // Generate OUT punch: ~17:30 to ~18:30 (5:30 PM to 6:30 PM)
                        int outMinOffset = rand.nextInt(60) - 30; // -30 to +30 mins from 6:00 PM
                        java.time.LocalDateTime outTime = date.atTime(18, 0).plusMinutes(outMinOffset)
                                .plusSeconds(rand.nextInt(60));

                        db.execute(
                                "INSERT INTO raw_logs (device_id, emp_id, punch_time, punch_type, synced) VALUES (?,?,?,?,0)",
                                defaultDevId, empId, outTime.format(dtf), 1);
                        count++;
                    }
                }
                db.commit();
            } catch (Exception e) {
                db.rollback();
                throw e;
            } finally {
                db.setAutoCommit(true);
            }

            // Trigger sync to process the newly generated raw logs
            try {
                com.bhspl.service.SyncService.processRawLogs(msg -> {
                });
            } catch (Exception ignored) {
            }

            CacheManager.getInstance().clear();

            response.put("success", true);
            response.put("count", count);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            e.printStackTrace();
            response.put("success", false);
            response.put("message", "Error: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    @PostMapping("/api/raw-logs/add")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> addRawLog(
            @RequestBody Map<String, String> payload,
            HttpSession session,
            jakarta.servlet.http.HttpServletRequest request) {

        Map<String, Object> response = new HashMap<>();
        if (session.getAttribute("user") == null) {
            response.put("success", false);
            response.put("message", "Unauthorized");
            return ResponseEntity.status(401).body(response);
        }

        try {
            String empId = payload.get("empId");
            String deviceIdStr = payload.get("deviceId");
            String punchTimeStr = payload.get("punchTime");
            String punchTypeStr = payload.get("punchType");

            if (empId == null || punchTimeStr == null || empId.trim().isEmpty() || punchTimeStr.trim().isEmpty()) {
                response.put("success", false);
                response.put("message", "Required fields are missing.");
                return ResponseEntity.badRequest().body(response);
            }

            int deviceId = deviceIdStr != null ? Integer.parseInt(deviceIdStr) : 0;
            int punchType = punchTypeStr != null ? Integer.parseInt(punchTypeStr) : 0;

            String formattedTime = punchTimeStr.replace("T", " ");
            if (formattedTime.length() == 16) {
                formattedTime += ":00";
            }

            DatabaseManager db = DatabaseManager.getInstance();
            db.execute("INSERT INTO raw_logs (device_id, emp_id, punch_time, punch_type, synced) VALUES (?,?,?,?,0)",
                    deviceId, empId.trim(), formattedTime, punchType);

            try {
                com.bhspl.service.SyncService.processRawLogs(msg -> {
                });
            } catch (Exception ignored) {
            }

            CacheManager.getInstance().clear();
            logActivity(session, request, "Manual Punch", "Attendance Management",
                    "Added manual punch for employee " + empId.trim() + " at " + formattedTime);

            response.put("success", true);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            e.printStackTrace();
            response.put("success", false);
            response.put("message", "Error: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    @PostMapping("/api/raw-logs/clear")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> clearRawLogs(
            @RequestParam("from") String fromDateStr,
            @RequestParam("to") String toDateStr,
            HttpSession session,
            jakarta.servlet.http.HttpServletRequest request) {

        Map<String, Object> response = new HashMap<>();
        if (session.getAttribute("user") == null) {
            response.put("success", false);
            response.put("message", "Unauthorized");
            return ResponseEntity.status(401).body(response);
        }

        try {
            DatabaseManager db = DatabaseManager.getInstance();
            java.time.LocalDate start = java.time.LocalDate.parse(fromDateStr);
            java.time.LocalDate end = java.time.LocalDate.parse(toDateStr);

            if (start.isAfter(end)) {
                response.put("success", false);
                response.put("message", "From date cannot be after To date");
                return ResponseEntity.badRequest().body(response);
            }

            db.setAutoCommit(false);
            try {
                // Delete from raw_logs
                db.execute("DELETE FROM raw_logs WHERE DATE(punch_time) BETWEEN ? AND ?", fromDateStr, toDateStr);

                // Delete from attendance
                db.execute("DELETE FROM attendance WHERE punch_date BETWEEN ? AND ?", fromDateStr, toDateStr);

                db.commit();
                logActivity(session, request, "Clear Raw Logs", "Attendance Management",
                        "Cleared raw logs and attendance between " + fromDateStr + " and " + toDateStr);
            } catch (Exception e) {
                db.rollback();
                throw e;
            } finally {
                db.setAutoCommit(true);
            }

            CacheManager.getInstance().clear();

            response.put("success", true);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            e.printStackTrace();
            response.put("success", false);
            response.put("message", "Error: " + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    private String handleRawLogs(Model model, String from, String to, String dept, String emp, String device,
            String search,
            String viewName,
            int page, int pageSize,
            HttpSession session) {
        try {
            DatabaseManager db = DatabaseManager.getInstance();
            String f = (from != null && !from.isEmpty()) ? from : java.time.LocalDate.now().toString();
            String t = (to != null && !to.isEmpty()) ? to : java.time.LocalDate.now().toString();
            String d = (dept != null && !dept.trim().isEmpty()) ? dept.trim() : "All";
            String eId = (emp != null && !emp.trim().isEmpty()) ? emp.trim() : "All";
            String s = (search != null) ? search.trim() : "";

            java.time.LocalDate startDate = java.time.LocalDate.parse(f);
            java.time.LocalDate endDate = java.time.LocalDate.parse(t);

            List<Integer> allowedIds = getAllowedDeviceIds(session);
            if (isUserRestricted(session)) {
                model.addAttribute("noDevicesAssigned", true);
                model.addAttribute("depts", new ArrayList<>());
                model.addAttribute("employees", new ArrayList<>());
                model.addAttribute("devices", new ArrayList<>());
                model.addAttribute("sessions", new ArrayList<>());
                model.addAttribute("currentPage", 1);
                model.addAttribute("totalPages", 1);
                model.addAttribute("pageSize", pageSize);
                model.addAttribute("totalItems", 0);
                model.addAttribute("selFrom", f);
                model.addAttribute("selTo", t);
                model.addAttribute("selDept", d);
                model.addAttribute("selEmp", eId);
                model.addAttribute("selDevice", device != null ? device : "All");
                model.addAttribute("selSearch", s);
                return viewName;
            }

            String allowedIdsStr = "";
            if (!allowedIds.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (int idx = 0; idx < allowedIds.size(); idx++) {
                    if (idx > 0)
                        sb.append(",");
                    sb.append(allowedIds.get(idx));
                }
                allowedIdsStr = sb.toString();
            }

            model.addAttribute("depts", db.query("SELECT dept_name FROM departments ORDER BY dept_name"));
            if (!allowedIds.isEmpty()) {
                model.addAttribute("employees",
                        db.query(
                                "SELECT emp_id, emp_name FROM employees WHERE status='Active' AND (emp_id IN (SELECT DISTINCT emp_id FROM raw_logs WHERE device_id IN ("
                                        + allowedIdsStr + ")) OR device_id IN (" + allowedIdsStr
                                        + ")) ORDER BY emp_name"));
            } else {
                model.addAttribute("employees",
                        db.query("SELECT emp_id, emp_name FROM employees WHERE status='Active' ORDER BY emp_name"));
            }

            String empBaseSql = "SELECT emp_id, emp_name, department, shift, device_enroll_id FROM employees WHERE status='Active'";
            if (!allowedIds.isEmpty()) {
                empBaseSql += " AND (emp_id IN (SELECT DISTINCT emp_id FROM raw_logs WHERE device_id IN ("
                        + allowedIdsStr + ")) OR device_id IN (" + allowedIdsStr + "))";
            }
            List<Object> params = new ArrayList<>();
            if (!"All".equals(d)) {
                empBaseSql += " AND department = ?";
                params.add(d);
            }
            if (!"All".equals(eId)) {
                empBaseSql += " AND emp_id = ?";
                params.add(eId);
            }
            empBaseSql += " ORDER BY emp_name ASC";

            List<Map<String, Object>> activeEmps = db.query(empBaseSql, params.toArray());

            if (!"All".equals(eId) && !activeEmps.isEmpty()) {
                model.addAttribute("selEmpName", activeEmps.get(0).get("emp_name"));
            }

            // Load device name + location map once (performance: single query)
            List<Map<String, Object>> deviceRows;
            if (!allowedIds.isEmpty()) {
                deviceRows = db.query(
                        "SELECT device_id, device_name, COALESCE(location, 'Not Assigned') as location FROM devices WHERE device_id IN ("
                                + allowedIdsStr + ")");
            } else {
                deviceRows = db.query(
                        "SELECT device_id, device_name, COALESCE(location, 'Not Assigned') as location FROM devices");
            }
            model.addAttribute("devices", deviceRows);

            Map<Integer, String> deviceNameMap = new HashMap<>();
            Map<Integer, String> deviceLocationMap = new HashMap<>();
            for (Map<String, Object> dRow : deviceRows) {
                Object didObj = dRow.get("device_id");
                if (didObj == null)
                    continue;
                int did = (didObj instanceof Number) ? ((Number) didObj).intValue()
                        : Integer.parseInt(didObj.toString());
                String name = (String) dRow.get("device_name");
                String loc = (String) dRow.get("location");
                deviceNameMap.put(did, name);
                deviceLocationMap.put(did, loc);

                String displayName = name;
                if (loc != null && !loc.trim().isEmpty() && !"Not Assigned".equalsIgnoreCase(loc.trim())) {
                    displayName += " (" + loc + ")";
                }
                dRow.put("display_name", displayName);
            }

            List<Map<String, Object>> rawLogs;
            if (!allowedIds.isEmpty()) {
                rawLogs = db.query(
                        "SELECT * FROM raw_logs WHERE DATE(punch_time) BETWEEN ? AND ? AND device_id IN ("
                                + allowedIdsStr + ") ORDER BY punch_time ASC",
                        f, t);
            } else {
                rawLogs = db.query(
                        "SELECT * FROM raw_logs WHERE DATE(punch_time) BETWEEN ? AND ? ORDER BY punch_time ASC", f, t);
            }
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
                        if (!"All".equals(eId)) {
                            Map<String, Object> row = new HashMap<>(employee);
                            row.put("date", dateStr);
                            row.put("in_time", "—");
                            row.put("out_time", "—");
                            row.put("punch_count", 0);
                            row.put("status", "Absent");
                            row.put("device_name", "—");
                            row.put("device_location", "—");
                            row.put("out_device_name", null);
                            row.put("out_device_location", null);
                            allSessions.add(row);
                        }
                    } else {
                        int totalPunches = dayLogs.size();
                        for (int i = 0; i < dayLogs.size(); i += 2) {
                            Map<String, Object> logSession = new HashMap<>(employee);
                            logSession.put("date", dateStr);
                            logSession.put("punch_count", totalPunches);
                            logSession.put("status", "Present");
                            logSession.put("out_device_name", null);
                            logSession.put("out_device_location", null);

                            // IN punch — device details
                            Map<String, Object> inLog = dayLogs.get(i);
                            java.time.LocalDateTime inDt = java.time.LocalDateTime
                                    .parse(inLog.get("punch_time").toString().replace(" ", "T"));
                            logSession.put("in_time", inDt.format(timeFmt));

                            Object inDevIdObj = inLog.get("device_id");
                            int inDevId = (inDevIdObj instanceof Integer) ? (int) inDevIdObj
                                    : (inDevIdObj != null ? Integer.parseInt(inDevIdObj.toString()) : 0);
                            logSession.put("device_name", deviceNameMap.getOrDefault(inDevId, "Manual/No Device"));
                            logSession.put("device_location", deviceLocationMap.getOrDefault(inDevId, "Not Assigned"));

                            // OUT punch — note the device if different from IN device
                            if (i + 1 < dayLogs.size()) {
                                Map<String, Object> outLog = dayLogs.get(i + 1);
                                java.time.LocalDateTime outDt = java.time.LocalDateTime
                                        .parse(outLog.get("punch_time").toString().replace(" ", "T"));
                                logSession.put("out_time", outDt.format(timeFmt));

                                Object outDevIdObj = outLog.get("device_id");
                                int outDevId = (outDevIdObj instanceof Integer) ? (int) outDevIdObj
                                        : (outDevIdObj != null ? Integer.parseInt(outDevIdObj.toString()) : 0);
                                if (outDevId != inDevId) {
                                    String outDevName = deviceNameMap.getOrDefault(outDevId, "Manual/No Device");
                                    String outDevLoc = deviceLocationMap.getOrDefault(outDevId, "Not Assigned");
                                    // Append OUT device info for audit visibility
                                    logSession.put("out_device_name", outDevName);
                                    logSession.put("out_device_location", outDevLoc);
                                }
                            } else {
                                logSession.put("out_time", "Wait...");
                            }
                            allSessions.add(logSession);
                        }
                    }
                }
            }

            // In-Memory Search and Filtering
            List<Map<String, Object>> filteredSessions = new ArrayList<>();
            String searchLower = s.toLowerCase();
            String deviceFilter = (device != null && !device.trim().isEmpty()) ? device.trim() : "All";

            for (Map<String, Object> sess : allSessions) {
                // Apply device filter
                if (!"All".equals(deviceFilter)) {
                    String devName = (String) sess.get("device_name");
                    String devLoc = (String) sess.get("device_location");
                    String outDevName = (String) sess.get("out_device_name");
                    String outDevLoc = (String) sess.get("out_device_location");

                    boolean matchesIn = (devName != null && devName.equalsIgnoreCase(deviceFilter)) ||
                            (devLoc != null && devLoc.equalsIgnoreCase(deviceFilter));
                    boolean matchesOut = (outDevName != null && outDevName.equalsIgnoreCase(deviceFilter)) ||
                            (outDevLoc != null && outDevLoc.equalsIgnoreCase(deviceFilter));

                    if (!matchesIn && !matchesOut) {
                        continue;
                    }
                }

                // Apply search text filter
                if (!searchLower.isEmpty()) {
                    String empId = String.valueOf(sess.get("emp_id")).toLowerCase();
                    String empName = String.valueOf(sess.get("emp_name")).toLowerCase();
                    String department = String.valueOf(sess.get("department")).toLowerCase();
                    String devName = String.valueOf(sess.get("device_name")).toLowerCase();
                    String devLoc = String.valueOf(sess.get("device_location")).toLowerCase();
                    String outDevName = sess.containsKey("out_device_name")
                            ? String.valueOf(sess.get("out_device_name")).toLowerCase()
                            : "";
                    String outDevLoc = sess.containsKey("out_device_location")
                            ? String.valueOf(sess.get("out_device_location")).toLowerCase()
                            : "";
                    String status = String.valueOf(sess.get("status")).toLowerCase();

                    boolean matches = empId.contains(searchLower) ||
                            empName.contains(searchLower) ||
                            department.contains(searchLower) ||
                            devName.contains(searchLower) ||
                            devLoc.contains(searchLower) ||
                            outDevName.contains(searchLower) ||
                            outDevLoc.contains(searchLower) ||
                            status.contains(searchLower);
                    if (!matches) {
                        continue;
                    }
                }

                filteredSessions.add(sess);
            }

            int total = filteredSessions.size();
            int totalPages = (int) Math.ceil((double) total / pageSize);
            if (totalPages == 0)
                totalPages = 1;

            int startIdx = (page - 1) * pageSize;
            int endIdx = Math.min(startIdx + pageSize, total);

            List<Map<String, Object>> pagedSessions = (startIdx < total) ? filteredSessions.subList(startIdx, endIdx)
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
            model.addAttribute("selDevice", deviceFilter);
            model.addAttribute("selSearch", s);
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

        List<Integer> allowedIds = getAllowedDeviceIds(session);
        if (isUserRestricted(session)) {
            model.addAttribute("noDevicesAssigned", true);
            model.addAttribute("devices", new ArrayList<>());
            model.addAttribute("currentPage", 1);
            model.addAttribute("totalPages", 1);
            model.addAttribute("pageSize", pageSize);
            model.addAttribute("totalItems", 0);
            return "devices";
        }

        String allowedIdsStr = "";
        if (!allowedIds.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (int idx = 0; idx < allowedIds.size(); idx++) {
                if (idx > 0)
                    sb.append(",");
                sb.append(allowedIds.get(idx));
            }
            allowedIdsStr = sb.toString();
        }

        try {
            DatabaseManager db = DatabaseManager.getInstance();
            int offset = (page - 1) * pageSize;

            long total;
            List<Map<String, Object>> devices;
            if (!allowedIds.isEmpty()) {
                total = db.queryLong("SELECT COUNT(*) FROM devices WHERE device_id IN (" + allowedIdsStr + ")");
                devices = db.query("SELECT * FROM devices WHERE device_id IN (" + allowedIdsStr
                        + ") ORDER BY device_name LIMIT " + pageSize + " OFFSET " + offset);
            } else {
                total = db.queryLong("SELECT COUNT(*) FROM devices");
                devices = db
                        .query("SELECT * FROM devices ORDER BY device_name LIMIT " + pageSize + " OFFSET " + offset);
            }
            int totalPages = (int) Math.ceil((double) total / pageSize);
            if (totalPages == 0)
                totalPages = 1;

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
    public String saveDevice(@RequestParam Map<String, String> params,
            org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes, HttpSession session,
            jakarta.servlet.http.HttpServletRequest request) {
        try {
            DatabaseManager db = DatabaseManager.getInstance();
            String id = params.get("device_id");
            String name = params.get("device_name");
            String ip = params.get("ip_address");
            String port = params.get("port");
            String sn = params.get("serial_number");
            String loc = params.get("location");
            String status = params.get("status");

            if (name == null || name.trim().isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Device Name is a mandatory field.");
                return "redirect:/devices";
            }
            if (loc == null || loc.trim().isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Device Location is a mandatory field.");
                return "redirect:/devices";
            }
            if (ip == null || ip.trim().isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "IP Address is a mandatory field.");
                return "redirect:/devices";
            }
            if (sn == null || sn.trim().isEmpty()) {
                redirectAttributes.addFlashAttribute("error", "Serial Number is a mandatory field.");
                return "redirect:/devices";
            }
            if (!isValidIPv4(ip)) {
                redirectAttributes.addFlashAttribute("error",
                        "IP Address must be a valid IPv4 address (e.g. 192.168.1.201).");
                return "redirect:/devices";
            }

            if (id == null || id.isEmpty() || "0".equals(id)) {
                db.execute(
                        "INSERT INTO devices (device_name, ip_address, port, serial_number, location, status) VALUES (?,?,?,?,?,?)",
                        name, ip, port, sn, loc, status);
                logActivity(session, request, "Create Device", "Device Management",
                        "Created biometric device " + name + " (IP: " + ip + ", S/N: " + sn + ")");
            } else {
                db.execute(
                        "UPDATE devices SET device_name=?, ip_address=?, port=?, serial_number=?, location=?, status=? WHERE device_id=?",
                        name, ip, port, sn, loc, status, id);
                logActivity(session, request, "Edit Device", "Device Management",
                        "Updated biometric device " + name + " (IP: " + ip + ", S/N: " + sn + ")");
            }
        } catch (Exception e) {
            e.printStackTrace();
            redirectAttributes.addFlashAttribute("error", "Error saving device: " + e.getMessage());
        }
        return "redirect:/devices";
    }

    @PostMapping("/devices/delete")
    public String deleteDevice(@RequestParam("device_id") String id, HttpSession session,
            jakarta.servlet.http.HttpServletRequest request) {
        try {
            DatabaseManager db = DatabaseManager.getInstance();
            Map<String, Object> dev = db.queryOne("SELECT device_name FROM devices WHERE device_id=?", id);
            String devName = dev != null ? (String) dev.get("device_name") : "Unknown";
            db.execute("DELETE FROM devices WHERE device_id=?", id);
            logActivity(session, request, "Delete Device", "Device Management",
                    "Deleted biometric device " + devName + " (ID: " + id + ")");
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
            @RequestParam(value = "comment", required = false) String comment, HttpSession session) {
        try {
            DatabaseManager db = DatabaseManager.getInstance();

            // Fetch old request first to verify status and prevent duplicate actions
            Map<String, Object> req = db.queryOne("SELECT * FROM leaves WHERE id=?", id);
            if (req == null) {
                return "redirect:/leave/requests";
            }

            String oldStatus = DatabaseManager.str(req, "status");
            if (status.equals(oldStatus)) {
                System.out.println("processLeave: Request " + id + " is already " + status + ". Skipping processing.");
                return "redirect:/leave/requests";
            }

            // Try to add column if it doesn't exist (compatible with older MySQL)
            try {
                db.execute("ALTER TABLE leaves ADD reject_reason TEXT");
            } catch (Exception ex) {
            }

            db.execute("UPDATE leaves SET status=?, reject_reason=? WHERE id=?", status, comment, id);

            // Deduct or reverse leave balance on approval/rejection
            String empId = req.get("emp_id").toString();
            String type = req.get("leave_type").toString();
            double days = DatabaseManager.dbl(req, "days");
            int yr = java.time.LocalDate.parse(req.get("from_date").toString()).getYear();

            if ("Approved".equals(status) && !"Approved".equals(oldStatus)) {
                leaveService.deductBalance(empId, type, yr, days, id, "Deducted upon approval of request ID: " + id);
            } else if (("Rejected".equals(status) || "Pending".equals(status)) && "Approved".equals(oldStatus)) {
                leaveService.reverseDeduction(empId, type, yr, days, id);
            }

            logActivity(session, "Process Leave", "Leave Management",
                    "Processed leave request ID " + id + " to status " + status);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "redirect:/leave/requests";
    }

    @PostMapping("/leave/save")
    public String saveLeave(@RequestParam Map<String, String> params, HttpSession session) {
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

            double daysVal = 0;
            try { if (days != null && !days.isEmpty()) daysVal = Double.parseDouble(days); } catch (Exception ignored) {}
            int yr = java.time.LocalDate.parse(from).getYear();

            if (id == null || id.isEmpty()) {
                db.execute(
                        "INSERT INTO leaves (emp_id, leave_type, from_date, to_date, days, reason, status, applied_on) VALUES (?,?,?,?,?,?,?,NOW())",
                        empId, type, from, to, days, reason, status);

                // Get the newly generated ID if approved
                if ("Approved".equals(status)) {
                    Map<String, Object> newReq = db.queryOne(
                            "SELECT id FROM leaves WHERE emp_id=? AND leave_type=? AND from_date=? ORDER BY id DESC LIMIT 1",
                            empId, type, from);
                    if (newReq != null) {
                        String newId = newReq.get("id").toString();
                        leaveService.deductBalance(empId, type, yr, daysVal, newId,
                                "Deducted upon direct creation of approved leave");
                    }
                }
            } else {
                // Fetch old status to check for reversal
                Map<String, Object> oldReq = db.queryOne("SELECT status FROM leaves WHERE id=?", id);
                String oldStatus = (oldReq != null) ? DatabaseManager.str(oldReq, "status") : "";

                db.execute(
                        "UPDATE leaves SET emp_id=?, leave_type=?, from_date=?, to_date=?, days=?, reason=?, status=? WHERE id=?",
                        empId, type, from, to, days, reason, status, id);

                if ("Approved".equals(status) && !"Approved".equals(oldStatus)) {
                    leaveService.deductBalance(empId, type, yr, daysVal, id, "Deducted upon approved status update");
                } else if (!"Approved".equals(status) && "Approved".equals(oldStatus)) {
                    leaveService.reverseDeduction(empId, type, yr, daysVal, id);
                }
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
            logActivity(session, "Save Leave", "Leave Management", "Saved/Updated leave for employee " + empId);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "redirect:/leave";
    }

    @PostMapping("/leave/update-status")
    public String updateLeaveStatus(@RequestParam("id") String id, @RequestParam("status") String status,
            HttpSession session) {
        try {
            DatabaseManager db = DatabaseManager.getInstance();

            // Fetch old status
            Map<String, Object> oldReq = db.queryOne("SELECT * FROM leaves WHERE id=?", id);
            if (oldReq != null) {
                String oldStatus = DatabaseManager.str(oldReq, "status");
                String empId = oldReq.get("emp_id").toString();
                String type = oldReq.get("leave_type").toString();
                double days = DatabaseManager.dbl(oldReq, "days");
                int yr = java.time.LocalDate.parse(oldReq.get("from_date").toString()).getYear();

                db.execute("UPDATE leaves SET status=? WHERE id=?", status, id);

                if ("Approved".equals(status) && !"Approved".equals(oldStatus)) {
                    leaveService.deductBalance(empId, type, yr, days, id, "Deducted upon update to approved");
                } else if (!"Approved".equals(status) && "Approved".equals(oldStatus)) {
                    leaveService.reverseDeduction(empId, type, yr, days, id);
                }
            }

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
            logActivity(session, "Update Leave Status", "Leave Management",
                    "Updated leave ID " + id + " to status " + status);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "redirect:/leave";
    }

    @PostMapping("/leave/delete")
    public String deleteLeave(@RequestParam("id") String id, HttpSession session) {
        try {
            System.out.println("Deleting leave request ID: " + id);
            DatabaseManager db = DatabaseManager.getInstance();

            // Reverse leave balance deduction if approved before deleting
            Map<String, Object> oldReq = db.queryOne("SELECT * FROM leaves WHERE id=?", id);
            if (oldReq != null) {
                String oldStatus = DatabaseManager.str(oldReq, "status");
                if ("Approved".equals(oldStatus)) {
                    String empId = oldReq.get("emp_id").toString();
                    String type = oldReq.get("leave_type").toString();
                    double days = DatabaseManager.dbl(oldReq, "days");
                    int yr = java.time.LocalDate.parse(oldReq.get("from_date").toString()).getYear();
                    leaveService.reverseDeduction(empId, type, yr, days, id);
                }
            }

            db.execute("DELETE FROM leaves WHERE id=?", id);
            logActivity(session, "Delete Leave", "Leave Management", "Deleted leave ID " + id);
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
            @RequestParam("location") String location, HttpSession session) {
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
            logActivity(session, "Apply OD", "Leave Management",
                    "Applied OD for employee " + empId + " from " + odFrom + " to " + odTo);
        } catch (Exception e) {
            System.err.println("Error processing OD application: " + e.getMessage());
            e.printStackTrace();
        }
        return "redirect:/leave/od";
    }

    @PostMapping("/leave/od/update-status")
    public String updateODStatus(@RequestParam("id") String id, @RequestParam("status") String status,
            HttpSession session) {
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
            logActivity(session, "Update OD Status", "Leave Management",
                    "Updated OD ID " + id + " to status " + status);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "redirect:/leave/od";
    }

    @PostMapping("/leave/od/delete")
    public String deleteOD(@RequestParam("id") String id, HttpSession session) {
        try {
            System.out.println("Deleting OD request ID: " + id);
            DatabaseManager db = DatabaseManager.getInstance();
            db.execute("DELETE FROM od_requests WHERE id=?", id);
            logActivity(session, "Delete OD", "Leave Management", "Deleted OD ID " + id);
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
    public String creditLeaves(
            @RequestParam(value = "dept", defaultValue = "All") String dept,
            @RequestParam(value = "empId", defaultValue = "All") String empId,
            @RequestParam("type") String type,
            @RequestParam("amount") double amount,
            @RequestParam(value = "remarks", defaultValue = "Batch Credit") String remarks) {
        try {
            leaveService.creditLeavesBulk(dept, empId, type, amount, remarks);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "redirect:/leave/balance";
    }

    @PostMapping("/leave/balance/yearend")
    public String processYearEnd(
            @RequestParam("sourceYear") int sourceYear,
            @RequestParam("targetYear") int targetYear) {
        try {
            leaveService.processYearEnd(sourceYear, targetYear);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "redirect:/leave/balance";
    }

    @GetMapping("/api/leave/balance/history")
    @ResponseBody
    public List<Map<String, Object>> getLeaveHistory(
            @RequestParam("empId") String empId,
            @RequestParam("type") String type,
            @RequestParam("year") int year) {
        try {
            DatabaseManager db = DatabaseManager.getInstance();
            return db.query(
                    "SELECT *, DATE_FORMAT(transaction_date, '%Y-%m-%d %h:%i %p') as formatted_date FROM leave_transactions WHERE emp_id=? AND leave_type=? AND year=? ORDER BY transaction_date DESC",
                    empId, type, year);
        } catch (Exception e) {
            e.printStackTrace();
            return List.of();
        }
    }

    @PostMapping("/leave/balance/adjust")
    public String adjustBalance(@RequestParam("empId") String empId, @RequestParam("type") String type,
            @RequestParam("year") String year, @RequestParam("newBalance") double newBalance) {
        try {
            DatabaseManager db = DatabaseManager.getInstance();
            // Fetch old balance first
            Map<String, Object> oldRow = db.queryOne(
                    "SELECT closing_bal FROM leave_balance WHERE emp_id=? AND leave_type=? AND year=?", empId, type,
                    year);
            double oldClosing = (oldRow != null) ? DatabaseManager.dbl(oldRow, "closing_bal") : 0.0;
            double diff = newBalance - oldClosing;

            db.execute("UPDATE leave_balance SET closing_bal = ? WHERE emp_id=? AND leave_type=? AND year=?",
                    newBalance, empId, type, year);

            // Record transaction
            db.execute(
                    "INSERT INTO leave_transactions (emp_id, leave_type, year, transaction_type, amount, reference_id, remarks) VALUES (?, ?, ?, 'Adjustment', ?, 'Manual Adjustment', ?)",
                    empId, type, year, "Adjustment", diff, "Manual adjustment by Admin");
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
            @RequestParam(name = "export", defaultValue = "false") boolean isExport, HttpSession session) {
        if (isExport) {
            logActivity(session, "Export Report", "Reports", "Exported Leave Balance Report");
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
            List<Object> params = new ArrayList<>();
            params.add(filterYear);

            if (dept != null && !"All".equals(dept)) {
                where.append(" AND e.department = ?");
                params.add(dept);
            }
            if (type != null && !"All".equals(type)) {
                where.append(" AND b.leave_type = ?");
                params.add(type);
            }

            long total = db.queryLong(
                    "SELECT COUNT(*) FROM leave_balance b JOIN employees e ON b.emp_id = e.emp_id" + where.toString(),
                    params.toArray());
            int totalPages = (int) Math.ceil((double) total / pageSize);
            if (totalPages == 0)
                totalPages = 1;

            String sql = "SELECT b.*, e.emp_name, e.department FROM leave_balance b " +
                    "JOIN employees e ON b.emp_id = e.emp_id " + where.toString() +
                    " ORDER BY e.department, e.emp_id, b.leave_type LIMIT " + pageSize + " OFFSET " + offset;

            List<Map<String, Object>> balances = db.query(sql, params.toArray());

            // Summary Stats (Always calculate on full set or pre-calculate if needed, but
            // for now we keep it simple)
            // Actually, stats should be on the FILTERED set, not just the page.
            List<Map<String, Object>> allBalances = db
                    .query("SELECT b.*, e.emp_id FROM leave_balance b JOIN employees e ON b.emp_id = e.emp_id"
                            + where.toString(), params.toArray());

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

            // Fetch active employees & charts for bulk credits & analytics
            List<Map<String, Object>> employees = db
                    .query("SELECT emp_id, emp_name FROM employees WHERE status='Active' ORDER BY emp_name");
            List<Map<String, Object>> deptChart = db.query(
                    "SELECT e.department, SUM(b.used) as used_days FROM leave_balance b JOIN employees e ON b.emp_id = e.emp_id WHERE b.year = ? GROUP BY e.department ORDER BY used_days DESC",
                    filterYear);
            List<Map<String, Object>> typeChart = db.query(
                    "SELECT b.leave_type, SUM(b.used) as used_days FROM leave_balance b WHERE b.year = ? GROUP BY b.leave_type ORDER BY used_days DESC",
                    filterYear);

            model.addAttribute("balances", balances);
            model.addAttribute("depts", depts);
            model.addAttribute("types", types);
            model.addAttribute("employees", employees);
            model.addAttribute("deptChart", deptChart);
            model.addAttribute("typeChart", typeChart);
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
            List<Object> params = new ArrayList<>();
            if (type != null && !"All".equals(type)) {
                where = " WHERE holiday_type = ?";
                params.add(type);
            }

            long total = db.queryLong("SELECT COUNT(*) FROM holidays" + where, params.toArray());
            int totalPages = (int) Math.ceil((double) total / pageSize);
            if (totalPages == 0)
                totalPages = 1;

            String sql = "SELECT * FROM holidays" + where + " ORDER BY holiday_date LIMIT " + pageSize + " OFFSET "
                    + offset;
            holidays = db.query(sql, params.toArray());

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
            List<Object> params = new ArrayList<>();
            List<Object> orderParams = new ArrayList<>();

            if (search != null && !search.isEmpty()) {
                where = " WHERE desig_name LIKE ?";
                params.add("%" + search + "%");
                orderBy = "CASE WHEN LOWER(desig_name) LIKE LOWER(?) THEN 0 ELSE 1 END, " + orderBy;
                orderParams.add(search + "%");
            }

            long total = db.queryLong("SELECT COUNT(*) FROM designations" + where, params.toArray());
            int totalPages = (int) Math.ceil((double) total / pageSize);
            if (totalPages == 0)
                totalPages = 1;

            String sql = "SELECT * FROM designations" + where + " ORDER BY " + orderBy + " LIMIT "
                    + pageSize + " OFFSET " + offset;

            List<Object> allParams = new ArrayList<>(params);
            allParams.addAll(orderParams);
            desigs = db.query(sql, allParams.toArray());

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
            List<Object> params = new ArrayList<>();
            if (dept != null && !"All".equals(dept)) {
                where = " WHERE e.department = ?";
                params.add(dept);
            }

            long total = db
                    .queryLong("SELECT COUNT(*) FROM weekly_offs w JOIN employees e ON w.emp_id = e.emp_id" + where,
                            params.toArray());
            int totalPages = (int) Math.ceil((double) total / pageSize);
            if (totalPages == 0)
                totalPages = 1;

            String sql = "SELECT w.*, e.emp_name, e.department as dept_name " +
                    "FROM weekly_offs w " +
                    "JOIN employees e ON w.emp_id = e.emp_id" + where + " LIMIT " + pageSize + " OFFSET " + offset;

            offs = db.query(sql, params.toArray());
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
            @RequestParam(name = "size", defaultValue = "10") int pageSize,
            HttpSession session) {
        String sessionRole = (String) session.getAttribute("role");
        String sessionUser = (String) session.getAttribute("user");
        // Allow normal users to view their own profile

        List<Integer> allowedIds = getAllowedDeviceIds(session);
        String allowedIdsStr = "";
        if (!allowedIds.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (int idx = 0; idx < allowedIds.size(); idx++) {
                if (idx > 0)
                    sb.append(",");
                sb.append(allowedIds.get(idx));
            }
            allowedIdsStr = sb.toString();
        }

        List<Map<String, Object>> users = new java.util.ArrayList<>();
        try {
            DatabaseManager db = DatabaseManager.getInstance();
            int offset = (page - 1) * pageSize;
            String where = " WHERE 1=1";
            List<Object> params = new ArrayList<>();
            if (!"Admin".equalsIgnoreCase(sessionRole)) {
                where += " AND username = ?";
                params.add(sessionUser);
            }

            long total = db.queryLong("SELECT COUNT(*) FROM users" + where, params.toArray());
            int totalPages = (int) Math.ceil((double) total / pageSize);
            if (totalPages == 0)
                totalPages = 1;

            users = db.query(
                    "SELECT id, username, role, emp_id, status, last_login, allowed_devices, allowed_modules FROM users"
                            + where + " ORDER BY username LIMIT " + pageSize + " OFFSET " + offset, params.toArray());

            List<Map<String, Object>> activeDevices;
            if (!allowedIds.isEmpty()) {
                activeDevices = db
                        .query("SELECT device_id, device_name FROM devices WHERE status='Active' AND device_id IN ("
                                + allowedIdsStr + ") ORDER BY device_name");
            } else {
                activeDevices = db
                        .query("SELECT device_id, device_name FROM devices WHERE status='Active' ORDER BY device_name");
            }
            model.addAttribute("activeDevices", activeDevices);
            model.addAttribute("allModules", java.util.Arrays.asList(
                    "Dashboard", "Employee Directory", "Attendance Management",
                    "Leave & Time Off", "Reporting & Analytics", "System Administration",
                    "Hardware Control"));

            List<Map<String, Object>> existingRoles = db
                    .query("SELECT DISTINCT role FROM users WHERE role IS NOT NULL AND role != '' ORDER BY role");
            model.addAttribute("existingRoles", existingRoles);

            model.addAttribute("users", users);
            model.addAttribute("currentPage", page);
            model.addAttribute("totalPages", totalPages);
            model.addAttribute("pageSize", pageSize);
            model.addAttribute("totalItems", total);
        } catch (Exception e) {
            model.addAttribute("error", e.getMessage());
        }
        model.addAttribute("users", users);
        model.addAttribute("allModules",
                java.util.Arrays.asList("Dashboard", "Employee Directory", "Attendance Management",
                        "Attendance Reports", "Activity Logs", "Device Management", "Leave Management",
                        "Administration", "Settings"));
        return "system-users";
    }

    @PostMapping("/system/users/save")
    public String saveUser(@RequestParam Map<String, String> params,
            @RequestParam(name = "allowedDevices", required = false) List<String> allowedDevices,
            @RequestParam(name = "allowedModules", required = false) List<String> allowedModules,
            HttpSession session) {
        String sessionRole = (String) session.getAttribute("role");
        String sessionUser = (String) session.getAttribute("user");
        String u = params.get("username");
        String id = params.get("id");
        if (!"Admin".equalsIgnoreCase(sessionRole)) {
            try {
                DatabaseManager db = DatabaseManager.getInstance();
                long count = db.queryLong("SELECT COUNT(*) FROM users WHERE id=? AND username=?", id, sessionUser);
                if (count == 0) {
                    return "redirect:/dashboard";
                }
            } catch (Exception e) {
                return "redirect:/dashboard";
            }
        }

        List<Integer> allowedIds = getAllowedDeviceIds(session);
        String allowedIdsStr = "";
        if (!allowedIds.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (int idx = 0; idx < allowedIds.size(); idx++) {
                if (idx > 0)
                    sb.append(",");
                sb.append(allowedIds.get(idx));
            }
            allowedIdsStr = sb.toString();
        }

        try {
            DatabaseManager db = DatabaseManager.getInstance();
            String p = params.get("password");
            String r = params.get("role");
            String e = params.get("empId");

            if (e != null && !e.isEmpty() && !allowedIds.isEmpty()) {
                boolean isEmpAllowed = db.queryLong(
                        "SELECT COUNT(*) FROM employees WHERE emp_id=? AND (emp_id IN (SELECT DISTINCT emp_id FROM raw_logs WHERE device_id IN ("
                                + allowedIdsStr + ")) OR device_id IN (" + allowedIdsStr + "))",
                        e) > 0;
                if (!isEmpAllowed) {
                    return "redirect:/system/users?error=Invalid+Employee+ID";
                }
            }

            String allowedDevicesStr = null;
            if (allowedDevices != null && !allowedDevices.isEmpty()) {
                allowedDevicesStr = String.join(",", allowedDevices);
            }

            String allowedModulesStr = null;
            if (allowedModules != null && !allowedModules.isEmpty()) {
                allowedModulesStr = String.join(",", allowedModules);
            }

            if (id == null || id.isEmpty()) {
                // INSERT
                String ph = db.hashPw(p);
                db.execute(
                        "INSERT INTO users (username, password_hash, role, emp_id, allowed_devices, allowed_modules) VALUES(?,?,?,?,?,?)",
                        u, ph, r, e.isEmpty() ? null : e, allowedDevicesStr, allowedModulesStr);
                logActivity(session, "Create User", "System Users", "Created user " + u);
            } else {
                // UPDATE
                if (!"Admin".equalsIgnoreCase(sessionRole)) {
                    if (u != null && !u.isEmpty()) {
                        db.execute("UPDATE users SET username=? WHERE id=?", u, id);
                        session.setAttribute("user", u);
                        logActivity(session, "Edit User", "System Users", "Updated profile username to " + u);
                    }
                    if (p != null && !p.isEmpty()) {
                        String ph = db.hashPw(p);
                        db.execute("UPDATE users SET password_hash=? WHERE id=?", ph, id);
                        logActivity(session, "Edit User", "System Users", "Updated password for own account");
                    }
                } else {
                    if (p == null || p.isEmpty()) {
                        db.execute(
                                "UPDATE users SET username=?, role=?, emp_id=?, allowed_devices=?, allowed_modules=? WHERE id=?",
                                u, r, e.isEmpty() ? null : e, allowedDevicesStr, allowedModulesStr, id);
                        logActivity(session, "Edit User", "System Users",
                                "Updated user " + u + " without password change");
                    } else {
                        String ph = db.hashPw(p);
                        db.execute(
                                "UPDATE users SET username=?, password_hash=?, role=?, emp_id=?, allowed_devices=?, allowed_modules=? WHERE id=?",
                                u, ph, r, e.isEmpty() ? null : e, allowedDevicesStr, allowedModulesStr, id);
                        logActivity(session, "Edit User", "System Users",
                                "Updated user " + u + " with password change");
                    }
                }
            }
            com.bhspl.util.CacheManager.getInstance().clear();
        } catch (Exception e) {
            e.printStackTrace();
            return "redirect:/system/users?error=Update+Failed";
        }
        return "redirect:/system/users?success=User+updated+successfully";
    }

    @PostMapping("/system/users/reset-password")
    public String resetUserPassword(@RequestParam("id") String id, @RequestParam("password") String password,
            HttpSession session) {
        String sessionRole = (String) session.getAttribute("role");
        String sessionUser = (String) session.getAttribute("user");
        try {
            DatabaseManager db = DatabaseManager.getInstance();
            Map<String, Object> user = db.queryOne("SELECT username FROM users WHERE id=?", id);
            if (user != null) {
                String targetUsername = DatabaseManager.str(user, "username");
                if (!"Admin".equalsIgnoreCase(sessionRole) && !sessionUser.equals(targetUsername)) {
                    return "redirect:/dashboard";
                }
                String ph = db.hashPw(password);
                db.execute("UPDATE users SET password_hash=? WHERE id=?", ph, id);
                logActivity(session, "Reset Password", "System Users", "Reset password for user " + targetUsername);
                com.bhspl.util.CacheManager.getInstance().clear();
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "redirect:/system/users?error=Password+Reset+Failed";
        }
        return "redirect:/system/users?success=Password+reset+successfully";
    }

    @PostMapping("/system/users/toggle-status")
    public String toggleUserStatus(@RequestParam("id") String id, @RequestParam("status") String status,
            HttpSession session) {
        String sessionRole = (String) session.getAttribute("role");
        if (!"Admin".equalsIgnoreCase(sessionRole)) {
            return "redirect:/dashboard"; // Only admins can toggle status
        }
        try {
            DatabaseManager db = DatabaseManager.getInstance();
            String newStatus = "Active".equals(status) ? "Inactive" : "Active";
            db.execute("UPDATE users SET status=? WHERE id=?", newStatus, id);
            logActivity(session, "Toggle Status", "System Users",
                    "Toggled status for user ID " + id + " to " + newStatus);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "redirect:/system/users";
    }

    @PostMapping("/system/users/delete")
    public String deleteUser(@RequestParam String id, HttpSession session) {
        String sessionRole = (String) session.getAttribute("role");
        if (!"Admin".equalsIgnoreCase(sessionRole)) {
            return "redirect:/dashboard"; // Only admins can delete users
        }
        try {
            DatabaseManager db = DatabaseManager.getInstance();
            db.execute("DELETE FROM users WHERE id=?", id);
            logActivity(session, "Delete User", "System Users", "Deleted user ID " + id);
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
        model.addAttribute("udpPullEnabled", com.bhspl.service.SyncService.isUdpPullEnabled());
        return "system-settings";
    }

    @PostMapping("/system/settings/start-adms")
    public String startAdms(Model model, HttpSession session) {
        try {
            com.bhspl.service.PushService.start();
            logActivity(session, "Start Server", "System Settings", "Started ADMS/Push Service");
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "redirect:/system/settings";
    }

    @PostMapping("/system/settings/stop-adms")
    public String stopAdms(HttpSession session) {
        try {
            com.bhspl.service.PushService.stop();
            logActivity(session, "Stop Server", "System Settings", "Stopped ADMS/Push Service");
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "redirect:/system/settings";
    }

    @PostMapping("/system/settings/save-sync")
    public String saveSyncSettings(@RequestParam("udpPullEnabled") String udpPullEnabled, HttpSession session) {
        try {
            com.bhspl.db.ConfigManager.setProperty("udp_pull_enabled", udpPullEnabled);
            logActivity(session, "Update Settings", "System Settings", "Updated UDP Pull setting to " + udpPullEnabled);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "redirect:/system/settings";
    }

    @GetMapping("/system/activity-logs")
    public String activityLogs(Model model,
            @RequestParam(name = "from", required = false) String from,
            @RequestParam(name = "to", required = false) String to,
            @RequestParam(name = "search", required = false) String search,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "20") int pageSize,
            HttpSession session) {

        String sessionRole = (String) session.getAttribute("role");
        if (!"Admin".equalsIgnoreCase(sessionRole)) {
            return "redirect:/dashboard";
        }

        try {
            DatabaseManager db = DatabaseManager.getInstance();
            int offset = (page - 1) * pageSize;

            String where = " WHERE 1=1";
            List<Object> params = new ArrayList<>();
            if (from != null && !from.isEmpty()) {
                where += " AND DATE(created_at) >= ?";
                params.add(from);
            }
            if (to != null && !to.isEmpty()) {
                where += " AND DATE(created_at) <= ?";
                params.add(to);
            }
            if (search != null && !search.isEmpty()) {
                where += " AND (username LIKE ? OR action LIKE ? OR module_name LIKE ? OR description LIKE ?)";
                String likeSearch = "%" + search + "%";
                params.add(likeSearch);
                params.add(likeSearch);
                params.add(likeSearch);
                params.add(likeSearch);
            }

            long total = db.queryLong("SELECT COUNT(*) FROM activity_logs" + where, params.toArray());
            int totalPages = (int) Math.ceil((double) total / pageSize);
            if (totalPages == 0)
                totalPages = 1;

            List<Map<String, Object>> logs = db.query("SELECT * FROM activity_logs" + where
                    + " ORDER BY created_at DESC LIMIT " + pageSize + " OFFSET " + offset, params.toArray());

            model.addAttribute("logs", logs);
            model.addAttribute("currentPage", page);
            model.addAttribute("totalPages", totalPages);
            model.addAttribute("pageSize", pageSize);
            model.addAttribute("totalItems", total);
            model.addAttribute("from", from);
            model.addAttribute("to", to);
            model.addAttribute("search", search);

            // Add Active Users Widget Data
            model.addAttribute("activeUsers", getActiveUsersList());
        } catch (Exception e) {
            e.printStackTrace();
            model.addAttribute("error", e.getMessage());
        }
        return "system-activity-logs";
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
            jakarta.servlet.http.HttpServletResponse response,
            HttpSession session) throws Exception {

        logActivity(session, "Export Report", "Reports", "Exported Monthly Report CSV");
        List<Integer> allowedIds = getAllowedDeviceIds(session);
        if (isUserRestricted(session)) {
            response.setContentType("text/csv");
            response.setHeader("Content-Disposition",
                    "attachment; filename=\"Attendance_" + month + "_" + year + ".csv\"");
            java.io.PrintWriter writer = response.getWriter();
            writer.println("No devices assigned to your account.");
            writer.flush();
            writer.close();
            return;
        }

        String allowedIdsStr = "";
        if (!allowedIds.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (int idx = 0; idx < allowedIds.size(); idx++) {
                if (idx > 0)
                    sb.append(",");
                sb.append(allowedIds.get(idx));
            }
            allowedIdsStr = sb.toString();
        }

        DatabaseManager db = DatabaseManager.getInstance();
        java.time.YearMonth ym = java.time.YearMonth.of(year, month);
        int daysCount = ym.lengthOfMonth();

        String filename = "Attendance_" + month + "_" + year + ".csv";
        response.setContentType("text/csv");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");

        java.io.PrintWriter writer = response.getWriter();

        // Header
        writer.print("ID,Name,Device,Device Location,Designation");
        for (int i = 1; i <= daysCount; i++) {
            writer.print("," + i + "/" + month + "/" + year);
        }
        writer.println();

        // Data
        List<Object> params = new ArrayList<>();
        String sql = "SELECT emp_id, emp_name, designation FROM employees WHERE status='Active'";
        if (!allowedIds.isEmpty()) {
            sql += " AND (emp_id IN (SELECT DISTINCT emp_id FROM raw_logs WHERE device_id IN (" + allowedIdsStr
                    + ")) OR device_id IN (" + allowedIdsStr + "))";
        }
        if (dept != null && !"All".equals(dept)) {
            sql += " AND department = ?";
            params.add(dept);
        }
        sql += " ORDER BY emp_name";

        List<Map<String, Object>> allDevices;
        if (!allowedIds.isEmpty()) {
            allDevices = db.query(
                    "SELECT device_id, device_name, location FROM devices WHERE device_id IN (" + allowedIdsStr + ")");
        } else {
            allDevices = db.query("SELECT device_id, device_name, location FROM devices");
        }
        Map<Integer, String> deviceNamesMap = new HashMap<>();
        Map<Integer, String> deviceLocationsMap = new HashMap<>();
        for (Map<String, Object> dev : allDevices) {
            int devId = (int) dev.get("device_id");
            deviceNamesMap.put(devId, (String) dev.get("device_name"));
            String loc = (String) dev.get("location");
            deviceLocationsMap.put(devId, loc != null && !loc.trim().isEmpty() ? loc : "Not Assigned");
        }

        List<Map<String, Object>> emps = db.query(sql, params.toArray());
        for (Map<String, Object> e : emps) {
            String eid = DatabaseManager.str(e, "emp_id");

            List<Map<String, Object>> deviceListDb;
            if (!allowedIds.isEmpty()) {
                deviceListDb = db.query(
                        "SELECT DISTINCT device_id FROM attendance WHERE emp_id=? AND device_id IN (" + allowedIdsStr
                                + ") AND MONTH(punch_date)=? AND YEAR(punch_date)=?",
                        eid, month, year);
            } else {
                deviceListDb = db.query(
                        "SELECT DISTINCT device_id FROM attendance WHERE emp_id=? AND MONTH(punch_date)=? AND YEAR(punch_date)=?",
                        eid, month, year);
            }
            List<Integer> deviceIds = new ArrayList<>();
            for (Map<String, Object> dMap : deviceListDb) {
                deviceIds.add(dMap.get("device_id") != null ? (int) dMap.get("device_id") : 0);
            }
            if (deviceIds.isEmpty()) {
                deviceIds.add(0);
            }

            for (int deviceId : deviceIds) {
                String deviceName = deviceNamesMap.getOrDefault(deviceId,
                        deviceId == 0 ? "Manual/No Device" : "Unknown Device");
                String deviceLocation = deviceLocationsMap.getOrDefault(deviceId,
                        deviceId == 0 ? "Not Assigned" : "Unknown Location");
                writer.print(eid + "," + DatabaseManager.str(e, "emp_name") + "," + deviceName + "," + deviceLocation
                        + "," + DatabaseManager.str(e, "designation"));

                Map<Integer, String> attMap = new HashMap<>();
                List<Map<String, Object>> att = db.query(
                        "SELECT DAY(punch_date) as d, status, in_time, out_time, work_hours FROM attendance WHERE emp_id=? AND device_id=? AND MONTH(punch_date)=? AND YEAR(punch_date)=?",
                        eid, deviceId, month, year);
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
        }
        writer.flush();
        writer.close();
    }

    @PostMapping("/system/sync")
    public String systemSync(@RequestParam(name = "redirect", required = false) String redirect) {
        try {
            SyncService.performSync(true);
            CacheManager.getInstance().clear();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "redirect:" + (redirect != null ? redirect : "/");
    }

    @GetMapping("/logo-reveal")
    public String logoReveal() {
        return "logo-reveal";
    }

    private List<Integer> getAllowedDeviceIds(HttpSession session) {
        List<Integer> deviceIds = new ArrayList<>();
        String username = (String) session.getAttribute("user");
        if (username == null)
            return deviceIds;
        try {
            DatabaseManager db = DatabaseManager.getInstance();
            Map<String, Object> user = db.queryOne("SELECT role, allowed_devices FROM users WHERE username=?",
                    username);
            if (user != null) {
                String role = (String) user.get("role");
                String allowedStr = (String) user.get("allowed_devices");
                if (allowedStr != null && !allowedStr.trim().isEmpty()) {
                    for (String idStr : allowedStr.split(",")) {
                        try {
                            deviceIds.add(Integer.parseInt(idStr.trim()));
                        } catch (NumberFormatException ignored) {
                        }
                    }
                } else if ("Admin".equalsIgnoreCase(role)) {
                    // Admins see all devices if none are specifically selected
                    List<Map<String, Object>> allDevices = db
                            .query("SELECT device_id FROM devices WHERE status='Active'");
                    for (Map<String, Object> dev : allDevices) {
                        deviceIds.add(((Number) dev.get("device_id")).intValue());
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return deviceIds;
    }

    private boolean isUserRestricted(HttpSession session) {
        String username = (String) session.getAttribute("user");
        if (username == null)
            return true;
        try {
            DatabaseManager db = DatabaseManager.getInstance();
            Map<String, Object> user = db.queryOne("SELECT role, allowed_devices FROM users WHERE username=?",
                    username);
            if (user != null) {
                String role = (String) user.get("role");
                String allowedStr = (String) user.get("allowed_devices");
                if ("Admin".equalsIgnoreCase(role)) {
                    return false;
                }
                return allowedStr == null || allowedStr.trim().isEmpty();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return true;
    }

    private boolean isValidIPv4(String ip) {
        if (ip == null || ip.trim().isEmpty())
            return false;
        String[] parts = ip.trim().split("\\.");
        if (parts.length != 4)
            return false;
        for (String part : parts) {
            try {
                int num = Integer.parseInt(part);
                if (num < 0 || num > 255)
                    return false;
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return true;
    }
}
