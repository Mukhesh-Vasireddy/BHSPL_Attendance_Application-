
package com.bhspl;
import com.bhspl.db.DatabaseManager;
import com.bhspl.db.ConfigManager;
import java.util.*;

public class DataIntegrityCheck {
    public static void main(String[] args) throws Exception {
        Map<String, String> cfg = ConfigManager.load();
        DatabaseManager db = DatabaseManager.getInstance();
        db.connect(cfg.get("host"), cfg.get("port"), cfg.get("user"), cfg.get("password"), cfg.get("database"));

        System.out.println("=== CHECKING FOR DATA ON APRIL 11 ===");
        List<Map<String, Object>> logs = db.fetchAll(
            "SELECT count(*) as count FROM raw_logs WHERE DATE(punch_time) = '2026-04-11'"
        );
        System.out.println("Raw logs for April 11: " + logs.get(0).get("count"));

        List<Map<String, Object>> att = db.fetchAll(
            "SELECT count(*) as count FROM attendance WHERE punch_date = '2026-04-11'"
        );
        System.out.println("Attendance records for April 11: " + att.get(0).get("count"));
        
        System.out.println("\n=== CHECKING MULTIPLE PUNCHES FOR APRIL 10 ===");
        List<Map<String, Object>> multi = db.fetchAll(
            "SELECT emp_id, COUNT(*) as c FROM raw_logs WHERE DATE(punch_time) = '2026-04-10' GROUP BY emp_id HAVING c > 1 LIMIT 5"
        );
        for (Map<String, Object> m : multi) {
            System.out.println("Emp: " + m.get("emp_id") + " has " + m.get("c") + " punches.");
        }

        db.close();
    }
}
