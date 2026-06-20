package com.bhspl.debug;

import com.bhspl.db.ConfigManager;
import com.bhspl.db.DatabaseManager;
import com.bhspl.service.SyncService;
import java.util.List;
import java.util.Map;

public class MainSync {
    public static void main(String[] args) {
        try {
            System.out.println("=== INITIALIZING DATABASE ===");
            Map<String, String> cfg = ConfigManager.load();
            if (cfg == null) {
                System.err.println("Database configuration not found in LOCALAPPDATA.");
                return;
            }
            
            System.out.println("Config loaded. Database name/path: " + cfg.get("database"));
            
            DatabaseManager db = DatabaseManager.getInstance();
            db.connect(
                cfg.get("host"),
                cfg.get("port"),
                cfg.get("user"),
                cfg.get("password"),
                cfg.get("database")
            );
            
            // Check raw counts directly
            List<Map<String, Object>> rows = db.query("SELECT COUNT(*) as c FROM raw_logs WHERE synced=0");
            Object count = (rows != null && !rows.isEmpty()) ? rows.get(0).get("c") : "0";
            System.out.println("Current Unprocessed logs (synced=0) in DB: " + count);

            System.out.println("\n=== STARTING DEVICE SYNC ===");
            SyncService.performSync(); 
            System.out.println("=== SYNC COMPLETED ===\n");

            System.out.println("=== PROCESSING RAW LOGS INTO ATTENDANCE ===");
            SyncService.processRawLogs(msg -> System.out.println("  > " + msg));
            System.out.println("=== PROCESSING COMPLETED ===\n");

            // Final verification
            rows = db.query("SELECT COUNT(*) as c FROM raw_logs WHERE synced=0");
            count = (rows != null && !rows.isEmpty()) ? rows.get(0).get("c") : "0";
            System.out.println("Final Unprocessed count: " + count);
            
        } catch (Exception e) {
            System.err.println("MainSync execution failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
