package com.bhspl.syncagent;

import com.bhspl.db.ConfigManager;
import com.bhspl.db.DatabaseManager;
import com.bhspl.service.SyncService;
import com.bhspl.service.PushService;
import com.bhspl.service.CloudSyncService;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * Headless background synchronization agent that connects to biometric devices and 
 * pushes attendance logs to the cloud.
 */
public class SyncAgent {
    public static void main(String[] args) {
        System.out.println("=================================================");
        System.out.println("  BHSPL Attendance Sync Agent (Background Daemon) ");
        System.out.println("=================================================");

        // Enforce Java AWT Headless environment
        System.setProperty("java.awt.headless", "true");

        Map<String, String> cfg = ConfigManager.load();
        if (cfg == null) {
            System.err.println("CRITICAL ERROR: Database connection is not configured. Please use the desktop UI to run initial database configuration first.");
            System.exit(1);
        }

        try {
            DatabaseManager db = DatabaseManager.getInstance();
            boolean connected = db.connect(
                cfg.get("host"),
                cfg.get("port"),
                cfg.get("user"),
                cfg.get("password"),
                cfg.get("database")
            );

            if (connected) {
                System.out.println("SyncAgent: Connected to database: " + cfg.get("database"));

                // Start ADMS HTTP Push Listener
                PushService.start();

                // Start local device pulling background scheduler (UDP polling if active)
                SyncService.start();

                // Start background cloud sync service
                CloudSyncService.start();

                System.out.println("SyncAgent: Synchronization agent is now running.");
                System.out.println("Press Ctrl+C to terminate the daemon.");

                while (true) {
                    try {
                        Thread.sleep(60000); // 1-minute heartbeat
                        String timeStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                        System.out.println("[SyncAgent Heartbeat] Daemon is active. Time: " + timeStr);
                    } catch (InterruptedException e) {
                        System.out.println("SyncAgent: Interrupted, stopping background services...");
                        PushService.stop();
                        CloudSyncService.stop();
                        break;
                    }
                }
            } else {
                System.err.println("CRITICAL ERROR: Failed to connect to local database.");
                System.exit(1);
            }
        } catch (Exception e) {
            System.err.println("CRITICAL ERROR: Exception during daemon boot: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
