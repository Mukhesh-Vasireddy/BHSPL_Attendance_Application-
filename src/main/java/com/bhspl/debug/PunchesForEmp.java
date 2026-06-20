
package com.bhspl.debug;
import com.bhspl.db.DatabaseManager;
import com.bhspl.db.ConfigManager;
import java.util.*;

public class PunchesForEmp {
    public static void main(String[] args) throws Exception {
        Map<String, String> cfg = ConfigManager.load();
        DatabaseManager db = DatabaseManager.getInstance();
        db.connect(cfg.get("host"), cfg.get("port"), cfg.get("user"), cfg.get("password"), cfg.get("database"));

        String empId = "14507";
        System.out.println("=== PUNCHES IN DATABASE FOR " + empId + " ON APRIL 10 ===");
        List<Map<String, Object>> logs = db.fetchAll(
            "SELECT * FROM raw_logs WHERE emp_id=? AND DATE(punch_time) = '2026-04-10' ORDER BY punch_time ASC",
            empId
        );
        for (Map<String, Object> m : logs) {
            System.out.println("  Time: " + m.get("punch_time") + " | DeviceID: " + m.get("device_id"));
        }
        
        System.out.println("\n=== ATTENDANCE SESSIONS FOR " + empId + " (APRIL 10) ===");
        List<Map<String, Object>> att = db.fetchAll(
            "SELECT * FROM attendance WHERE emp_id=? AND punch_date = '2026-04-10' ORDER BY in_time ASC",
            empId
        );
        for (Map<String, Object> a : att) {
            System.out.println("  IN: " + a.get("in_time") + " | OUT: " + a.get("out_time"));
        }

        db.close();
    }
}
