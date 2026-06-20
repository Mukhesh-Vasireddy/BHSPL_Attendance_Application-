package com.bhspl.debug;

import com.bhspl.db.ConfigManager;
import com.bhspl.db.DatabaseManager;
import com.bhspl.service.SyncService;
import java.time.LocalDate;
import java.util.*;

public class ForceUpdateToday {
    public static void main(String[] args) throws Exception {
        Map<String, String> cfg = ConfigManager.load();
        if (cfg == null) {
            System.out.println("No config found.");
            return;
        }
        DatabaseManager dm = DatabaseManager.INSTANCE;
        dm.connect(cfg.get("host"), cfg.get("port"), cfg.get("user"), cfg.get("password"), cfg.get("database"));
        
        System.out.println("Checking status for today: " + LocalDate.now());
        
        // 1. Force reset synced status for today's logs to ensure they are re-processed
        int resetCount = dm.execute("UPDATE raw_logs SET synced=0 WHERE DATE(punch_time) = CURDATE()");
        System.out.println("Reset synced=0 for " + resetCount + " logs today.");
        
        // 2. Perform sync (this will pull new logs from devices and call processRawLogs)
        System.out.println("Triggering SyncService.performSync()...");
        SyncService.performSync();
        
        System.out.println("Sync and Update completed.");
        
        // 3. Verify attendance for today
        List<Map<String, Object>> attendance = dm.fetchAll("SELECT * FROM attendance WHERE punch_date = CURDATE()");
        System.out.println("--- Today's Attendance ---");
        for (Map<String, Object> a : attendance) {
            System.out.println(a);
        }
        
        dm.close();
    }
}
