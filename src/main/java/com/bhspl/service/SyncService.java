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

                if (records != null && !records.isEmpty()) {
                    DatabaseManager dbInstance = DatabaseManager.getInstance();
                    try {
                        // 1. Sort records by uid and punch_time ascending
                        records = new ArrayList<>(records); // ensure mutable
                        records.sort((r1, r2) -> {
                            String u1 = (String) r1.get("uid");
                            String u2 = (String) r2.get("uid");
                            int comp = u1.compareTo(u2);
                            if (comp != 0) return comp;
                            LocalDateTime t1 = (LocalDateTime) r1.get("punch_time");
                            LocalDateTime t2 = (LocalDateTime) r2.get("punch_time");
                            return t1.compareTo(t2);
                        });

                        // 2. Perform rolling 5-minute duplicate swiping filtering in-memory
                        List<Map<String, Object>> filteredRecords = new ArrayList<>();
                        Map<String, LocalDateTime> lastPunches = new HashMap<>();
                        Map<String, Integer> lastTypes = new HashMap<>();
                        for (Map<String, Object> rec : records) {
                            String uid = (String) rec.get("uid");
                            LocalDateTime time = (LocalDateTime) rec.get("punch_time");
                            int pType = rec.get("punch_type") != null ? (int) rec.get("punch_type") : 0;
                            if (lastPunches.containsKey(uid)) {
                                LocalDateTime lastTime = lastPunches.get(uid);
                                int lastType = lastTypes.containsKey(uid) ? lastTypes.get(uid) : 0;
                                long diffSeconds = java.time.Duration.between(lastTime, time).abs().getSeconds();
                                if (diffSeconds < 60 && lastType == pType) { // 1 minute = 60 seconds, identical type
                                    continue;
                                }
                            }
                            filteredRecords.add(rec);
                            lastPunches.put(uid, time);
                            lastTypes.put(uid, pType);
                        }
                        records = filteredRecords;

                        // 3. Query existing logs in raw_logs to filter database duplicates within 5 minutes
                        LocalDateTime minTime = null;
                        LocalDateTime maxTime = null;
                        for (Map<String, Object> rec : records) {
                            LocalDateTime time = (LocalDateTime) rec.get("punch_time");
                            if (minTime == null || time.isBefore(minTime)) minTime = time;
                            if (maxTime == null || time.isAfter(maxTime)) maxTime = time;
                        }

                        if (minTime != null && maxTime != null) {
                            String startRange = minTime.minusMinutes(1).toString().replace("T", " ");
                            String endRange = maxTime.plusMinutes(1).toString().replace("T", " ");
                            
                            List<Map<String, Object>> existingList = dbInstance.query(
                                "SELECT emp_id, punch_time, punch_type FROM raw_logs WHERE punch_time >= ? AND punch_time <= ?",
                                startRange, endRange
                            );

                            Map<String, List<Map<String, Object>>> existingPunches = new HashMap<>();
                            for (Map<String, Object> ex : existingList) {
                                String empId = DatabaseManager.str(ex, "emp_id");
                                Object pt = ex.get("punch_time");
                                int exType = ex.get("punch_type") != null ? (int) ex.get("punch_type") : 0;
                                LocalDateTime exTime = null;
                                if (pt instanceof LocalDateTime) {
                                    exTime = (LocalDateTime) pt;
                                } else if (pt instanceof java.sql.Timestamp) {
                                    exTime = ((java.sql.Timestamp) pt).toLocalDateTime();
                                } else if (pt != null) {
                                    try {
                                        exTime = LocalDateTime.parse(pt.toString().replace(" ", "T").split("\\.")[0]);
                                    } catch (Exception ignored) {}
                                }
                                if (exTime != null) {
                                    Map<String, Object> punchInfo = new HashMap<>();
                                    punchInfo.put("time", exTime);
                                    punchInfo.put("type", exType);
                                    existingPunches.computeIfAbsent(empId, k -> new ArrayList<>()).add(punchInfo);
                                }
                            }

                            List<Map<String, Object>> finalRecords = new ArrayList<>();
                            for (Map<String, Object> rec : records) {
                                String uid = (String) rec.get("uid");
                                LocalDateTime time = (LocalDateTime) rec.get("punch_time");
                                int pType = rec.get("punch_type") != null ? (int) rec.get("punch_type") : 0;
                                List<Map<String, Object>> dbPunches = existingPunches.get(uid);
                                boolean isDbDuplicate = false;
                                if (dbPunches != null) {
                                    for (Map<String, Object> dbPunch : dbPunches) {
                                        LocalDateTime dbTime = (LocalDateTime) dbPunch.get("time");
                                        int dbType = (int) dbPunch.get("type");
                                        long diffSeconds = java.time.Duration.between(dbTime, time).abs().getSeconds();
                                        if (diffSeconds < 60 && dbType == pType) {
                                            isDbDuplicate = true;
                                            break;
                                        }
                                    }
                                }
                                if (!isDbDuplicate) {
                                    finalRecords.add(rec);
                                }
                            }
                            records = finalRecords;
                        }

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
                            "SELECT punch_time, punch_type FROM raw_logs WHERE " + filter
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
                    List<Map<String, Object>> list = matched.stream().map(m -> {
                        Object o = m.get("punch_time");
                        Object ptObj = m.get("punch_type");
                        int type = ptObj != null ? (int) ptObj : 0;
                        try {
                            LocalDateTime ldt;
                            if (o instanceof LocalDateTime) ldt = (LocalDateTime) o;
                            else if (o instanceof java.sql.Timestamp) ldt = ((java.sql.Timestamp) o).toLocalDateTime();
                            else ldt = LocalDateTime.parse(o.toString().replace(" ", "T").split("\\.")[0]);
                            
                            if (!isOvernight && !ldt.toLocalDate().toString().equals(dStr)) return null;
                            
                            Map<String, Object> pMap = new HashMap<>();
                            pMap.put("time", ldt);
                            pMap.put("type", type);
                            return pMap;
                        } catch (Exception e) { return null; }
                    }).filter(Objects::nonNull).sorted((p1, p2) -> {
                        return ((LocalDateTime) p1.get("time")).compareTo((LocalDateTime) p2.get("time"));
                    }).collect(Collectors.toList());

                    if (list.isEmpty()) continue;

                    com.bhspl.util.AttendanceCalculator.Metrics met = new com.bhspl.util.AttendanceCalculator.Metrics();
                    com.bhspl.util.AttendanceCalculator.calculateFromPunches(list, shift, met);
                    
                    if (met.firstIn == null) continue;

                    db.execute(
                            "INSERT INTO attendance (emp_id, punch_date, in_time, out_time, status, work_hours, overtime, late_mins, early_mins, punch_type, exceptions) "
                                    + "VALUES (?,?,?,?,?,?,?,?,?, 'Device', ?) ON DUPLICATE KEY UPDATE in_time=VALUES(in_time), out_time=VALUES(out_time), "
                                    + "work_hours=VALUES(work_hours), overtime=VALUES(overtime), late_mins=VALUES(late_mins), early_mins=VALUES(early_mins), status=VALUES(status), exceptions=VALUES(exceptions)",
                            empId, date, java.sql.Timestamp.valueOf(met.firstIn),
                            (met.lastOut != null ? java.sql.Timestamp.valueOf(met.lastOut) : null),
                            met.status, met.workHours, met.overtime, met.lateMins, met.earlyMins, met.exceptions);
                } catch (Exception e) {
                    if (logConsumer != null)
                        logConsumer.accept("Error: " + e.getMessage());
                }
            }
            if (!newRawIds.isEmpty()) {
                String idList = newRawIds.stream().map(String::valueOf).collect(Collectors.joining(","));
                db.execute("UPDATE raw_logs SET synced=1 WHERE id IN (" + idList + ")");
                try {
                    com.bhspl.util.CacheManager.getInstance().invalidate("dashboard_stats");
                } catch (Exception ignored) {}
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