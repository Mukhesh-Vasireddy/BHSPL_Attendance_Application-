
package com.bhspl.debug;

import com.bhspl.db.ConfigManager;
import com.bhspl.db.DatabaseManager;
import com.bhspl.service.SyncService;
import java.util.*;

public class SyncDebugger {
    public static void main(String[] args) throws Exception {
        Map<String, String> cfg = ConfigManager.load();
        if (cfg == null) {
            System.err.println("Config not found.");
            return;
        }
        DatabaseManager db = DatabaseManager.getInstance();
        db.connect(cfg.get("host"), cfg.get("port"), cfg.get("user"), cfg.get("password"), cfg.get("database"));

        System.out.println("=== SYNC DEBUGGER ===");

        // 1. Reset sync status for the last 7 days to force re-processing of missing
        // out-times
        System.out.println("Resetting 'synced' status for the last 7 days...");
        db.execute("UPDATE raw_logs SET synced=0 WHERE punch_time >= '2026-04-07'");

        // 2. Perform Sync (this now includes my high-latency and SQL fixes)
        System.out.println("Performing Sync...");
        SyncService.performSync();

        // 3. Manually trigger processing just in case performSync was async
        System.out.println("Manually triggering processRawLogs...");
        SyncService.processRawLogs(msg -> System.out.println("Processor: " + msg));

        // 4. Verify results for April 10
        System.out.println("\n=== VERIFICATION FOR APRIL 10 ===");
        List<Map<String, Object>> att = db.fetchAll(
                "SELECT a.emp_id, e.emp_name, a.punch_date, a.in_time, a.out_time " +
                        "FROM attendance a JOIN employees e ON a.emp_id = e.emp_id " +
                        "WHERE a.punch_date = '2026-04-10' ORDER BY a.emp_id");

        if (att.isEmpty()) {
            System.out.println("No attendance records found for April 10.");
        } else {
            System.out.format("%-10s | %-20s | %-20s | %-20s%n", "ID", "Name", "IN", "OUT");
            System.out.println("---------------------------------------------------------------------------");
            for (Map<String, Object> r : att) {
                System.out.format("%-10s | %-20s | %-20s | %-20s%n",
                        r.get("emp_id"), r.get("emp_name"), r.get("in_time"), r.get("out_time"));
            }
        }

        db.close();
        System.out.println("\n=== DEBUGGER FINISHED ===");
    }
}
