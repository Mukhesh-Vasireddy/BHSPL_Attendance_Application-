package com.bhspl.debug;

import com.bhspl.db.DatabaseManager;
import java.util.Map;

public class ReprocessLogs2 {
    public static void main(String[] args) {
        try {
            Map<String, String> cfg = com.bhspl.core.Config.loadDbConfig();
            DatabaseManager db = DatabaseManager.getInstance();
            db.connect(cfg.get("host"), cfg.get("port"), cfg.get("user"), cfg.get("password"), cfg.get("database"));
            
            System.out.println("Resetting synced status for last 30 days...");
            db.execute("UPDATE raw_logs SET synced=0 WHERE punch_time >= DATE_SUB(NOW(), INTERVAL 30 DAY)");
            System.out.println("Reprocessing logs...");
            com.bhspl.service.SyncService.processRawLogs(msg -> System.out.println(msg));
            System.out.println("Done processing logs!");
            
            java.util.List<Map<String, Object>> res = db.query("SELECT COUNT(*) as c FROM attendance WHERE punch_date='2026-06-02'");
            System.out.println("Attendance records for 2026-06-02: " + res.get(0).get("c"));
            System.exit(0);
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
}
