package com.bhspl.debug;

import com.bhspl.db.DatabaseManager;

public class ReprocessLogs {
    public static void main(String[] args) {
        try {
            DatabaseManager db = DatabaseManager.getInstance();
            System.out.println("Resetting synced flag for recent raw logs...");
            db.execute("UPDATE raw_logs SET synced=0 WHERE DATE(punch_time) >= DATE(SUBDATE(NOW(), INTERVAL 30 DAY))");
            System.out.println("Processing raw logs...");
            com.bhspl.service.SyncService.processRawLogs(msg -> System.out.println(msg));
            System.out.println("Reprocessing complete.");
            System.exit(0);
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
}
