package com.bhspl.service;

import com.bhspl.db.DatabaseManager;
import com.bhspl.util.ZkProtocol;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Background service to automatically fetch logs from all active biometric
 * devices.
 */
public class SyncService {

    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private static final java.util.concurrent.atomic.AtomicBoolean isSyncing = new java.util.concurrent.atomic.AtomicBoolean(
            false);
    private static boolean IsRunning = false;

    private static void logToFile(String msg) {
        try (java.io.PrintWriter pw = new java.io.PrintWriter(new java.io.FileWriter("sync_debug.txt", true))) {
            pw.println("[" + new java.util.Date() + "] " + msg);
        } catch (Exception ignored) {
        }
    }

    private static java.util.function.Consumer<String> statusListener;

    public static void setStatusListener(java.util.function.Consumer<String> listener) {
        statusListener = listener;
    }

    private static void broadcast(String msg) {
        if (statusListener != null)
            statusListener.accept(msg);
        System.out.println("SyncService: " + msg);
        logToFile("SyncService: " + msg);
    }

    public static boolean isRunning() {
        return IsRunning;
    }

    public static void start() {
        if (IsRunning)
            return;
        IsRunning = true;
        System.out.println("SyncService: Initializing background scheduler...");
        // Wait 5 seconds for initial run, then 10 seconds between runs
        scheduler.scheduleWithFixedDelay(SyncService::performSync, 5, 10, TimeUnit.SECONDS);
        broadcast("Background polling active (10s interval).");
    }

    public static void performSync() {
        if (!isSyncing.compareAndSet(false, true)) {
            System.out.println("SyncService: Sync already in progress, skipping this run.");
            return;
        }
        long start = System.currentTimeMillis();
        try {
            // Auto-refresh last 2 days to ensure status updates (e.g. IN becomes PRESENT when OUT arrives)
            try { 
                DatabaseManager.getInstance().execute("UPDATE raw_logs SET synced=0 WHERE punch_time >= DATE_SUB(NOW(), INTERVAL 2 DAY)"); 
            } catch (Exception ignored) {}

            broadcast("Checking for active devices...");
            List<Map<String, Object>> devices;
            try {
                devices = DatabaseManager.getInstance().query("SELECT * FROM devices WHERE status='Active'");
                System.out.println("SyncService: Found " + (devices != null ? devices.size() : 0) + " active devices.");
            } catch (Exception e) {
                System.err.println("SyncService: DB Error fetching devices: " + e.getMessage());
                logToFile("ERROR: DB Fetch devices failed: " + e.getMessage());
                return;
            }

            if (devices != null) {
                devices.parallelStream().forEach(d -> {
                    try {
                        syncDevice(d, 7);
                    } catch (Exception e) {
                        System.err
                                .println("SyncService: Failed to sync " + d.get("ip_address") + ": " + e.getMessage());
                        logToFile("ERROR: Sync failed for " + d.get("ip_address") + ": " + e.getMessage());
                    }
                });
            }

            System.out.println("SyncService: Starting raw log processing...");
            processRawLogs(null);
        } finally {
            isSyncing.set(false);
            long duration = (System.currentTimeMillis() - start) / 1000;
            broadcast("performSync completed in " + duration + "s");
        }
    }

    public static void syncDeviceById(int deviceId) throws Exception {
        Map<String, Object> dev = DatabaseManager.getInstance().fetchOne(
                "SELECT device_id, ip_address, port, device_name, comm_password FROM devices WHERE device_id=?",
                deviceId);
        if (dev != null) {
            syncDevice(dev, 90);
            processRawLogs(null);
        }
    }

    public static void forceUpdateToday(Runnable onComplete) {
        scheduler.submit(() -> {
            try {
                DatabaseManager.getInstance().execute(
                        "UPDATE raw_logs SET synced=0 WHERE DATE(punch_time) >= DATE(SUBDATE(NOW(), INTERVAL 3 DAY))");
            } catch (Exception ignored) {
            }

            performSync(); // This is now fully sequential

            if (onComplete != null) {
                javax.swing.SwingUtilities.invokeLater(onComplete);
            }
        });
    }

    public static void forceUpdateToday() {
        forceUpdateToday(null);
    }

    private static void syncDevice(Map<String, Object> dev, int days) {
        int id = (int) dev.get("device_id");
        String name = (String) dev.get("device_name");
        String ip = (String) dev.get("ip_address");
        int port = (int) dev.get("port");
        int pwd = (int) dev.get("comm_password");

        broadcast("Syncing " + name + " (" + ip + ")...");
        ZkProtocol zk = new ZkProtocol(ip, port, 4000);
        zk.setPassword(pwd);
        if (zk.connect()) {
            try {
                List<Map<String, Object>> records = zk.getAttendanceRecords();
                if (records == null || records.isEmpty())
                    records = zk.fetchTailOnly(2000);

                if (records != null) {
                    DatabaseManager dbInstance = DatabaseManager.getInstance();
                    try {
                        List<Object[]> paramsList = new ArrayList<>();
                        for (Map<String, Object> rec : records) {
                            String uid = (String) rec.get("uid");
                            Object punchTimeObj = rec.get("punch_time");
                            String time = (punchTimeObj instanceof LocalDateTime)
                                    ? ((LocalDateTime) punchTimeObj).toString().replace("T", " ")
                                    : punchTimeObj.toString();
                            int pType = (rec.get("punch_type") != null ? (int) rec.get("punch_type") : 0);
                            paramsList.add(new Object[] { id, uid, time, pType });
                        }
                        int newLogs = dbInstance.executeBatch(
                                "INSERT IGNORE INTO raw_logs (device_id, emp_id, punch_time, punch_type, synced) VALUES (?,?,?,?,0)",
                                paramsList);
                        broadcast(name + ": Sync complete. Found: " + records.size() + ", New: " + newLogs);
                    } catch (Exception e) {
                        broadcast(name + ": Database Error: " + e.getMessage());
                    }
                    DatabaseManager.getInstance().execute(
                            "UPDATE devices SET last_sync=NOW(), last_error=NULL, status='Active' WHERE device_id=?",
                            id);
                }
            } catch (Exception e) {
                broadcast(name + ": Error: " + e.getMessage());
            } finally {
                zk.disconnect();
            }
        } else {
            broadcast(name + ": Connection Failed.");
        }
    }

    public static void processRawLogs(java.util.function.Consumer<String> logConsumer) {
        DatabaseManager db = DatabaseManager.getInstance();
        try {
            db.setAutoCommit(false);
            List<Map<String, Object>> raw = db
                    .query("SELECT * FROM raw_logs WHERE synced=0 ORDER BY emp_id, punch_time");
            if (raw.isEmpty()) {
                if (logConsumer != null)
                    logConsumer.accept("All logs already synced.");
                return;
            }

            if (logConsumer != null)
                logConsumer.accept("Processing " + raw.size() + " new logs...");

            List<Map<String, Object>> employees = db
                    .query("SELECT emp_id, emp_name, shift, device_enroll_id FROM employees WHERE status='Active'");
            Map<String, String> enrollMap = new HashMap<>();
            Map<String, String> reverseEnrollMap = new HashMap<>();
            for (Map<String, Object> e : employees) {
                String sid = DatabaseManager.str(e, "emp_id");
                String eid = DatabaseManager.str(e, "device_enroll_id");
                
                enrollMap.put(sid, sid);
                reverseEnrollMap.put(sid, eid.isEmpty() ? sid : eid);
                if (!eid.isEmpty()) enrollMap.put(eid, sid);
                
                try {
                    String normSid = String.valueOf(Long.parseLong(sid));
                    enrollMap.put(normSid, sid);
                    if (!eid.isEmpty()) {
                        String normEid = String.valueOf(Long.parseLong(eid));
                        enrollMap.put(normEid, sid);
                    }
                } catch (Exception ignored) {}
            }

            List<Map<String, Object>> shifts = db.query("SELECT * FROM shifts WHERE status='Active'");
            Map<String, Map<String, Object>> shiftMap = new HashMap<>();
            for (Map<String, Object> s : shifts) {
                shiftMap.put(DatabaseManager.str(s, "shift_name"), s);
            }

            Map<String, Map<String, Object>> empToShiftMap = new HashMap<>();
            for (Map<String, Object> e : employees) {
                String sn = (String) e.get("shift");
                if (shiftMap.containsKey(sn))
                    empToShiftMap.put((String) e.get("emp_id"), shiftMap.get(sn));
            }

            Set<String> affectedDays = new HashSet<>();
            List<Integer> newRawIds = new ArrayList<>();
            List<String> unknownUids = new ArrayList<>();
            
            try (java.io.PrintWriter pw = new java.io.PrintWriter(new java.io.FileWriter("sync_debug.txt", true))) {
                pw.println("\n--- Processing " + raw.size() + " raw logs at " + new java.util.Date());
                for (Map<String, Object> r : raw) {
                    String uid = DatabaseManager.str(r, "emp_id");
                    String eid = enrollMap.get(uid);
                    
                    // Try normalized match
                    if (eid == null) {
                        try {
                            String norm = String.valueOf(Long.parseLong(uid));
                            eid = enrollMap.get(norm);
                        } catch (Exception ignored) {}
                    }

                    newRawIds.add(DatabaseManager.num(r, "id"));
                    if (eid != null) {
                        Object p = r.get("punch_time");
                        LocalDate d = null;
                        try {
                            if (p instanceof LocalDateTime) {
                                d = ((LocalDateTime) p).toLocalDate();
                            } else if (p instanceof java.sql.Timestamp) {
                                d = ((java.sql.Timestamp) p).toLocalDateTime().toLocalDate();
                            } else {
                                String s = p.toString().replace("T", " ").split(" ")[0];
                                d = LocalDate.parse(s);
                            }
                        } catch (Exception ex) {
                            pw.println("Date parse failed for " + p);
                            continue;
                        }
                        if (d != null) affectedDays.add(eid + "|" + d);
                    } else {
                        pw.println("UNMATCHED UID from log entry: '" + uid + "'");
                        if (!unknownUids.contains(uid)) unknownUids.add(uid);
                    }
                }
                pw.println("Identified " + affectedDays.size() + " distinct day combos to process.");
            } catch (Exception e) {
                e.printStackTrace();
            }

            for (String dayKey : affectedDays) {
                try {
                    String[] parts = dayKey.split("\\|");
                    String empId = parts[0];
                    String date = parts[1];
                    String enrollId = reverseEnrollMap.get(empId);

                    String filter = "emp_id=?";
                    List<Object> params = new ArrayList<>();
                    params.add(empId);
                    if (enrollId != null && !enrollId.equals(empId)) {
                        filter = "(emp_id=? OR emp_id=?)";
                        params.add(enrollId);
                    }

                    String startRange = date + " 00:00:00";
                    String endRange = LocalDate.parse(date).plusDays(1).toString() + " 10:00:00";

                    if (logConsumer != null)
                        logConsumer.accept("Processing " + empId + " for " + date);

                    List<Object> sqlParams = new ArrayList<>();
                    if (params.size() > 1) {
                        sqlParams.add(params.get(0));
                        sqlParams.add(params.get(1));
                    } else {
                        sqlParams.add(params.get(0));
                    }
                    sqlParams.add(startRange);
                    sqlParams.add(endRange);

                    List<Map<String, Object>> matched = db.fetchAll(
                            "SELECT punch_time FROM raw_logs WHERE " + filter
                                    + " AND (punch_time >= ? AND punch_time < ?) ORDER BY punch_time ASC",
                            sqlParams.toArray());
                    if (matched.isEmpty())
                        continue;

                    Map<String, Object> shift = empToShiftMap.get(empId);
                    boolean overnightFlag = false;
                    if (shift != null) {
                        try {
                            Object st = shift.get("start_time");
                            Object et = shift.get("end_time");
                            if (st != null && et != null) {
                                LocalTime sTime;
                                if (st instanceof java.sql.Time) sTime = ((java.sql.Time) st).toLocalTime();
                                else sTime = LocalTime.parse(st.toString().substring(0, 5));

                                LocalTime eTime;
                                if (et instanceof java.sql.Time) eTime = ((java.sql.Time) et).toLocalTime();
                                else eTime = LocalTime.parse(et.toString().substring(0, 5));

                                if (eTime.isBefore(sTime)) overnightFlag = true;
                            }
                        } catch (Exception ignored) {}
                    }

                    final String dStr = date;
                    final boolean isOvernight = overnightFlag;
                    List<LocalDateTime> list = matched.stream().map(m -> {
                        Object o = m.get("punch_time");
                        try {
                            LocalDateTime ldt;
                            if (o instanceof LocalDateTime) ldt = (LocalDateTime) o;
                            else if (o instanceof java.sql.Timestamp) ldt = ((java.sql.Timestamp) o).toLocalDateTime();
                            else ldt = LocalDateTime.parse(o.toString().replace(" ", "T").split("\\.")[0]);
                            
                            if (!isOvernight && !ldt.toLocalDate().toString().equals(dStr)) return null;
                            return ldt;
                        } catch (Exception e) { return null; }
                    }).filter(Objects::nonNull).sorted().collect(Collectors.toList());

                    if (list.isEmpty()) continue;

                    // SIMPLE FIRST-IN / LAST-OUT LOGIC
                    LocalDateTime firstIn = list.get(0);
                    LocalDateTime lastOut = (list.size() > 1) ? list.get(list.size() - 1) : null;
                    
                    // Sanity check: if there's only one punch, it's just an IN.
                    // If the "last" punch is within 1 minute of the "first", ignore it as a double-swipe.
                    if (lastOut != null && Duration.between(firstIn, lastOut).toMinutes() < 1) {
                        lastOut = null;
                    }
                    
                    double totalDuration = 0;
                    if (lastOut != null) {
                        totalDuration = Duration.between(firstIn, lastOut).toMinutes() / 60.0;
                    }

                    // Saving the daily record
                    com.bhspl.util.AttendanceCalculator.Metrics met = com.bhspl.util.AttendanceCalculator.calculate(firstIn, lastOut, shift);
                    
                    double otThreshold = 9.0;
                    if (shift != null) {
                        otThreshold = DatabaseManager.dbl(shift, "overtime_after");
                        if (otThreshold <= 0) otThreshold = DatabaseManager.dbl(shift, "work_hours");
                        if (otThreshold <= 0) otThreshold = 9.0;
                    }
                    double totalOT = Math.max(0, totalDuration - otThreshold);

                    db.execute(
                            "INSERT INTO attendance (emp_id, punch_date, in_time, out_time, status, work_hours, overtime, late_mins, early_mins, punch_type) "
                                    + "VALUES (?,?,?,?,?,?,?,?,?, 'Device') ON DUPLICATE KEY UPDATE in_time=VALUES(in_time), out_time=VALUES(out_time), "
                                    + "work_hours=VALUES(work_hours), overtime=VALUES(overtime), late_mins=VALUES(late_mins), early_mins=VALUES(early_mins), status=VALUES(status)",
                            empId, date, java.sql.Timestamp.valueOf(firstIn),
                            (lastOut != null ? java.sql.Timestamp.valueOf(lastOut) : null),
                            met.status, totalDuration, totalOT, met.lateMins, met.earlyMins);
                } catch (Exception e) {
                    if (logConsumer != null)
                        logConsumer.accept("Error: " + e.getMessage());
                }
            }
            if (!newRawIds.isEmpty()) {
                String idList = newRawIds.stream().map(String::valueOf).collect(Collectors.joining(","));
                db.execute("UPDATE raw_logs SET synced=1 WHERE id IN (" + idList + ")");
            }
            db.commit(); // CRITICAL: Save everything to DB
            if (logConsumer != null)
                logConsumer.accept("Sync complete. " + affectedDays.size() + " days processed.");
        } catch (Exception e) {
            e.printStackTrace();
            if (logConsumer != null)
                logConsumer.accept("Fatal Error: " + e.getMessage());
            db.rollback();
        } finally {
            try {
                db.setAutoCommit(true);
            } catch (Exception ignored) {
            }
        }
    }

}