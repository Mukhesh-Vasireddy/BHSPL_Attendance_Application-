package com.bhspl;

import com.bhspl.core.Config;
import com.bhspl.db.DatabaseManager;
import com.bhspl.service.SyncService;
import java.util.Map;
import java.util.List;

public class DebugSync {
    public static void main(String[] args) {
        try {
            System.out.println("--- Starting Sync Debugger ---");
            Map<String, String> config = Config.loadDbConfig();
            if (config != null) {
                DatabaseManager db = DatabaseManager.getInstance();
                db.connect(config.get("host"), config.get("port"), config.get("user"), config.get("password"), config.get("database"));
                System.out.println("Connected to local database.");

                // Check unsynced logs count
                List<Map<String, Object>> unsynced = db.fetchAll("SELECT id, emp_id, punch_time FROM raw_logs WHERE synced = 0");
                System.out.println("Found " + unsynced.size() + " unsynced logs before process.");
                for (int i = 0; i < Math.min(5, unsynced.size()); i++) {
                    System.out.println(" Unsynced sample: " + unsynced.get(i).get("emp_id") + " at " + unsynced.get(i).get("punch_time"));
                }

                SyncService.setStatusListener(msg -> System.out.println("[SERVICE LOG] " + msg));
                
                System.out.println("\n--- Triggering performSync ---");
                SyncService.performSync();
                System.out.println("--- performSync Completed ---\n");

                List<Map<String, Object>> remaining = db.fetchAll("SELECT id, emp_id, punch_time FROM raw_logs WHERE synced = 0");
                System.out.println("Found " + remaining.size() + " unsynced logs AFTER process.");
                
                List<Map<String, Object>> attn = db.fetchAll("SELECT * FROM attendance WHERE punch_date = DATE(NOW())");
                System.out.println("Attendance records for today: " + attn.size());
            } else {
                System.out.println("No database configuration found. Please run the setup first.");
            }
            System.exit(0);
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
}
