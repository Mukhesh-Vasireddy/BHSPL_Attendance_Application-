package com.bhspl;

import com.bhspl.db.ConfigManager;
import com.bhspl.db.DatabaseManager;
import java.util.*;

public class DebugLogs {
    public static void main(String[] args) throws Exception {
        Map<String, String> cfg = ConfigManager.load();
        DatabaseManager dm = DatabaseManager.INSTANCE;
        dm.connect(cfg.get("host"), cfg.get("port"), cfg.get("user"), cfg.get("password"), cfg.get("database"));
        
        System.out.println("Processing raw logs (Fix Verification)...");
        com.bhspl.service.SyncService.processRawLogs(msg -> System.out.println("DEBUG: " + msg));

        System.out.println("--- Raw Logs for Yesterday (04-10) and Today (04-11) ---");
        List<Map<String, Object>> logs = dm.query(
            "SELECT * FROM raw_logs WHERE DATE(punch_time) IN ('2026-04-10', '2026-04-11') ORDER BY emp_id, punch_time"
        );
        for (Map<String, Object> log : logs) {
            System.out.println(log);
        }
        
        System.out.println("\n--- Attendance for Yesterday (04-10) and Today (04-11) ---");
        List<Map<String, Object>> att = dm.query(
            "SELECT * FROM attendance WHERE punch_date IN ('2026-04-10', '2026-04-11') ORDER BY emp_id, punch_date"
        );
        for (Map<String, Object> a : att) {
            System.out.println(a);
        }
        
        System.out.println("\n--- Devices Status ---");
        List<Map<String, Object>> devs = dm.query("SELECT * FROM devices");
        for (Map<String, Object> d : devs) {
            System.out.println(d);
        }
        
        dm.close();
    }
}
