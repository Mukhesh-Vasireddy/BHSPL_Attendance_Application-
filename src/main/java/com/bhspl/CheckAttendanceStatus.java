package com.bhspl;

import com.bhspl.db.DatabaseManager;
import com.bhspl.db.ConfigManager;
import java.util.*;

public class CheckAttendanceStatus {
    public static void main(String[] args) throws Exception {
        Map<String, String> cfg = ConfigManager.load();
        DatabaseManager db = DatabaseManager.getInstance();
        db.connect(cfg.get("host"), cfg.get("port"), cfg.get("user"), cfg.get("password"), cfg.get("database"));

        System.out.println("Checking Status for Today (2026-04-14)...");
        
        long rawCount = (long) db.fetchOne("SELECT COUNT(*) AS c FROM raw_logs WHERE DATE(punch_time) = CURDATE()").get("c");
        long synced0 = (long) db.fetchOne("SELECT COUNT(*) AS c FROM raw_logs WHERE DATE(punch_time) = CURDATE() AND synced=0").get("c");
        long attCount = (long) db.fetchOne("SELECT COUNT(*) AS c FROM attendance WHERE punch_date = CURDATE()").get("c");
        
        System.out.println("Raw Logs Today: " + rawCount);
        System.out.println("Unsynced Logs Today: " + synced0);
        System.out.println("Attendance Rows Today: " + attCount);
        
        if (attCount > 0) {
            List<Map<String, Object>> rows = db.query("SELECT e.emp_name, a.status, a.in_time FROM attendance a JOIN employees e ON a.emp_id=e.emp_id WHERE a.punch_date=CURDATE() LIMIT 5");
            for (Map<String, Object> r : rows) {
                System.out.println("Row: " + r.get("emp_name") + " - " + r.get("status") + " @ " + r.get("in_time"));
            }
        }
        
        db.close();
    }
}
