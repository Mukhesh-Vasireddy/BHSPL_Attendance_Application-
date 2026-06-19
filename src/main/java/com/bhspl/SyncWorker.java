package com.bhspl;

import com.bhspl.db.ConfigManager;
import com.bhspl.db.DatabaseManager;
import com.bhspl.service.SyncService;
import java.util.Map;

/**
 * Headless background worker that runs the attendance synchronization service
 * without any UI. This can be run persistently on a server or desktop.
 */
public class SyncWorker {
    public static void main(String[] args) {
        System.out.println("Starting BHSPL Attendance Sync Worker (Headless)...");

        Map<String, String> cfg = ConfigManager.load();
        if (cfg == null) {
            System.err.println("CRITICAL ERROR: Database is not configured. Please run the main application and set up the DB connection first.");
            System.exit(1);
        }

        try {
            writeStatus("Starting...");
            
            DatabaseManager db = DatabaseManager.getInstance();
            boolean connected = db.connect(
                cfg.get("host"),
                cfg.get("port"),
                cfg.get("user"),
                cfg.get("password"),
                cfg.get("database")
            );

            if (connected) {
                System.out.println("Successfully connected to database: " + cfg.get("database"));
                writeStatus("Database Connected. Initializing Sync...");
                
                // Start background sync service (Pull Mode)
                SyncService.start();
                
                // Start ADMS listener (Push Mode)
                com.bhspl.service.PushService.start();
                
                System.out.println("SyncWorker is now running in the background. Press Ctrl+C to stop.");
                writeStatus("Active. Syncing devices...");

                while (true) {
                    try {
                        Thread.sleep(60000); // 1 minute heartbeat
                        String timeStr = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                        writeStatus("Active. Heartbeat at: " + timeStr);
                    } catch (InterruptedException e) {
                        System.out.println("SyncWorker interrupted, shutting down...");
                        break;
                    }
                }
            } else {
                System.err.println("CRITICAL ERROR: Could not connect to the database.");
                System.exit(1);
            }
        } catch (Exception e) {
            try (java.io.FileWriter fw = new java.io.FileWriter("sync_error.txt", true)) {
                fw.write("CRITICAL ERROR [" + java.time.LocalDateTime.now() + "]: " + e.getMessage() + "\n");
                java.io.PrintWriter pw = new java.io.PrintWriter(fw);
                e.printStackTrace(pw);
                fw.write("\n---\n");
            } catch (java.io.IOException ioe) {
                System.err.println("Could not write error log: " + ioe.getMessage());
            }
            System.err.println("CRITICAL ERROR during SyncWorker initialization: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void writeStatus(String msg) {
        System.out.println("SyncWorker: " + msg);
        try (java.io.FileWriter fw = new java.io.FileWriter("sync_status.txt")) {
            fw.write(msg);
        } catch (java.io.IOException e) {
            System.err.println("SyncWorker: could not write status file: " + e.getMessage());
        }
    }
}
