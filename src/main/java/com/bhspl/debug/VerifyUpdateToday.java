package com.bhspl.debug;

import com.bhspl.db.ConfigManager;
import com.bhspl.db.DatabaseManager;
import com.bhspl.service.SyncService;
import java.time.LocalDate;
import java.util.*;

public class VerifyUpdateToday {
    public static void main(String[] args) throws Exception {
        Map<String, String> cfg = ConfigManager.load();
        if (cfg == null) {
            System.out.println("No config found.");
            return;
        }
        DatabaseManager dm = DatabaseManager.INSTANCE;
        dm.connect(cfg.get("host"), cfg.get("port"), cfg.get("user"), cfg.get("password"), cfg.get("database"));
        
        System.out.println("Verifying force update for today: " + LocalDate.now());
        
        // Trigger the newly implemented forceUpdateToday
        System.out.println("Calling SyncService.forceUpdateToday()...");
        SyncService.forceUpdateToday();
        
        // Since forceUpdateToday uses a scheduler, we wait a bit or call processRawLogs manually for verification
        Thread.sleep(5000); // Wait for sync task to start
        
        System.out.println("Manually calling processRawLogs for immediate verification...");
        SyncService.processRawLogs(msg -> System.out.println("Logger: " + msg));
        
        // Verify attendance for today
        List<Map<String, Object>> attendance = dm.fetchAll("SELECT * FROM attendance WHERE punch_date = CURDATE()");
        System.out.println("--- Today's Attendance after update ---");
        if (attendance.isEmpty()) {
            System.out.println("No attendance records found for today yet.");
        } else {
            for (Map<String, Object> a : attendance) {
                System.out.println(a);
            }
        }
        
        dm.close();
    }
}
