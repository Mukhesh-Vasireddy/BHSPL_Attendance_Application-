package com.bhspl.debug;

import com.bhspl.db.DatabaseManager;
import com.bhspl.db.ConfigManager;
import java.util.*;

public class CheckData {
    public static void main(String[] args) throws Exception {
        Map<String, String> cfg = ConfigManager.load();
        DatabaseManager db = DatabaseManager.getInstance();
        db.connect(cfg.get("host"), cfg.get("port"), cfg.get("user"), cfg.get("password"), cfg.get("database"));

        System.out.println("--- SHIFTS ---");
        List<Map<String, Object>> shifts = db.fetchAll("SELECT * FROM shifts");
        for (Map<String, Object> s : shifts) {
            System.out.println(s);
        }

        System.out.println("\n--- RECENT RAW LOGS ---");
        List<Map<String, Object>> logs = db.fetchAll("SELECT * FROM raw_logs WHERE DATE(punch_time) >= DATE(SUBDATE(NOW(), INTERVAL 1 DAY)) ORDER BY punch_time DESC LIMIT 20");
        for (Map<String, Object> l : logs) {
            System.out.println(l);
        }

        System.out.println("\n--- RECENT ATTENDANCE ---");
        List<Map<String, Object>> att = db.fetchAll("SELECT * FROM attendance WHERE punch_date >= DATE(SUBDATE(NOW(), INTERVAL 1 DAY)) ORDER BY punch_date DESC, emp_id LIMIT 20");
        for (Map<String, Object> a : att) {
            System.out.println(a);
        }
    }
}
