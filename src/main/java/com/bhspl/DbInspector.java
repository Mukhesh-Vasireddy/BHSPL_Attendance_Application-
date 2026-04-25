package com.bhspl;
import com.bhspl.db.ConfigManager;
import com.bhspl.db.DatabaseManager;
import java.util.List;
import java.util.Map;

public class DbInspector {
    public static void main(String[] args) throws Exception {
        Map<String, String> cfg = ConfigManager.load();
        DatabaseManager.getInstance().connect(
            cfg.get("host"), cfg.get("port"), cfg.get("user"), cfg.get("password"), cfg.get("database")
        );
        DatabaseManager db = DatabaseManager.getInstance();

        System.out.println("=== EMPLOYEE SAMPLE ===");
        List<Map<String, Object>> emps = db.query("SELECT emp_id, emp_name, device_enroll_id FROM employees LIMIT 5");
        for (Map<String, Object> e : emps) System.out.println(e);

        System.out.println("\n=== UNPROCESSED RAW LOG SAMPLE ===");
        List<Map<String, Object>> raws = db.query("SELECT * FROM raw_logs WHERE synced=0 LIMIT 10");
        for (Map<String, Object> r : raws) System.out.println(r);
        
        System.out.println("\n=== SYNCED RAW LOGS TODAY? ===");
        List<Map<String, Object>> todayRaw = db.query("SELECT COUNT(*) as c FROM raw_logs WHERE DATE(punch_time) = CURRENT_DATE");
        System.out.println("Raw logs today (any sync status): " + (todayRaw.isEmpty() ? "0" : todayRaw.get(0).get("c")));
    }
}
