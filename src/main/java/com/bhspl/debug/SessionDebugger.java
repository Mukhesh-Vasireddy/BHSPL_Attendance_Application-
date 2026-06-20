
package com.bhspl.debug;

import com.bhspl.db.ConfigManager;
import com.bhspl.db.DatabaseManager;
import com.bhspl.service.SyncService;
import java.util.*;

public class SessionDebugger {
    public static void main(String[] args) throws Exception {
        Map<String, String> cfg = ConfigManager.load();
        DatabaseManager db = DatabaseManager.getInstance();
        db.connect(cfg.get("host"), cfg.get("port"), cfg.get("user"), cfg.get("password"), cfg.get("database"));

        System.out.println("=== MULTI-SESSION SYNC DEBUGGER ===");
        
        // 1. Force re-migration (run migrations again just in case)
        // db.runMigrations() is private, but execute() will work if we need.

        // 2. Reset sync status for the last 7 days to force re-calculation of sessions
        System.out.println("Resetting 'synced' status for April 07-13...");
        db.execute("UPDATE raw_logs SET synced=0 WHERE punch_time >= '2026-04-07'");
        
        // 3. Optional: Clear the attendance table for these dates to ensure clean re-build
        // System.out.println("Clearing old attendance records for April 10-13...");
        // db.execute("DELETE FROM attendance WHERE punch_date >= '2026-04-10'");

        // 4. Perform Sync (this now includes 30s timeouts and multi-session pairing)
        System.out.println("Performing Sync with 30s timeouts...");
        SyncService.performSync();
        
        // Wait for potential async tasks to catch up if executor is busy
        Thread.sleep(10000);

        // 5. Verify results for April 10 and April 11
        System.out.println("\n=== SESSIONS DETECTED (APRIL 10-13) ===");
        List<Map<String, Object>> att = db.fetchAll(
            "SELECT a.emp_id, e.emp_name, a.punch_date, a.in_time, a.out_time " +
            "FROM attendance a JOIN employees e ON a.emp_id = e.emp_id " +
            "WHERE a.punch_date >= '2026-04-10' ORDER BY a.punch_date DESC, a.emp_id ASC, a.in_time ASC"
        );
        
        if (att.isEmpty()) {
            System.out.println("No attendance records found yet.");
        } else {
            System.out.format("%-10s | %-15s | %-12s | %-20s | %-20s%n", "ID", "Name", "Date", "IN", "OUT");
            System.out.println("------------------------------------------------------------------------------------------");
            for (Map<String, Object> r : att) {
                System.out.format("%-10s | %-15s | %-12s | %-20s | %-20s%n", 
                    r.get("emp_id"), r.get("emp_name"), r.get("punch_date"), r.get("in_time"), r.get("out_time"));
            }
        }
        
        db.close();
        System.out.println("\n=== DEBUGGER FINISHED ===");
    }
}
