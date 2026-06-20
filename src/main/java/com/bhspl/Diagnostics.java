package com.bhspl;

import com.bhspl.db.DatabaseManager;
import java.util.*;

public class Diagnostics {
    public static void main(String[] args) throws Exception {
        Map<String, String> config = com.bhspl.core.Config.loadDbConfig();
        DatabaseManager db = DatabaseManager.getInstance();
        db.connect(config.get("host"), config.get("port"), config.get("user"), config.get("password"), config.get("database"));

        System.out.println("=== DEVICE STATUS ===");
        List<Map<String, Object>> devices = db.fetchAll("SELECT device_id, device_name, last_sync, last_error FROM devices");
        for (Map<String, Object> d : devices) {
            System.out.println(d);
        }

        System.out.println("\n=== LATEST RAW LOGS ===");
        List<Map<String, Object>> logs = db.fetchAll("SELECT id, emp_id, punch_time, created_at FROM raw_logs ORDER BY id DESC LIMIT 10");
        for (Map<String, Object> l : logs) {
            System.out.println(l);
        }
        
        System.out.println("\n=== LOG COUNT PER DAY (Last 7 Days) ===");
        List<Map<String, Object>> counts = db.fetchAll("SELECT DATE(punch_time) as date, COUNT(*) as count FROM raw_logs WHERE punch_time >= DATE(SUBDATE(NOW(), INTERVAL 7 DAY)) GROUP BY DATE(punch_time) ORDER BY date DESC");
        for (Map<String, Object> c : counts) {
            System.out.println(c);
        }
    }
}
