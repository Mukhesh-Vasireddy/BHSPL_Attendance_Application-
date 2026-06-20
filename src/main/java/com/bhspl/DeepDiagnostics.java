
package com.bhspl;
import com.bhspl.db.DatabaseManager;
import com.bhspl.db.ConfigManager;
import java.util.*;

public class DeepDiagnostics {
    public static void main(String[] args) throws Exception {
        Map<String, String> cfg = ConfigManager.load();
        DatabaseManager db = DatabaseManager.getInstance();
        db.connect(cfg.get("host"), cfg.get("port"), cfg.get("user"), cfg.get("password"), cfg.get("database"));

        System.out.println("=== RAW LOGS SINCE APRIL 10 ===");
        List<Map<String, Object>> logs = db.fetchAll(
            "SELECT id, emp_id, punch_time FROM raw_logs WHERE punch_time >= '2026-04-10' ORDER BY punch_time DESC LIMIT 20"
        );
        for (Map<String, Object> l : logs) {
            System.out.println(l);
        }

        System.out.println("\n=== ATTENDANCE RECORDS SINCE APRIL 10 ===");
        List<Map<String, Object>> att = db.fetchAll(
            "SELECT emp_id, punch_date, in_time, out_time FROM attendance WHERE punch_date >= '2026-04-10' ORDER BY punch_date DESC LIMIT 20"
        );
        for (Map<String, Object> a : att) {
            System.out.println(a);
        }
        
        System.out.println("\n=== EMPLOYEE PUNCH COUNTS FOR APRIL 10 ===");
        List<Map<String, Object>> counts = db.fetchAll(
            "SELECT emp_id, COUNT(*) as punch_count FROM raw_logs WHERE DATE(punch_time) = '2026-04-10' GROUP BY emp_id"
        );
        for (Map<String, Object> c : counts) {
            System.out.println(c);
        }

        db.close();
    }
}
