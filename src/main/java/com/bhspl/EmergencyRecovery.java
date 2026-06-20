package com.bhspl;

import com.bhspl.db.DatabaseManager;
import com.bhspl.db.ConfigManager;
import com.bhspl.util.ZkProtocol;
import com.bhspl.service.SyncService;

import java.util.*;

/**
 * Emergency script to recover missing data since April 10th.
 * It uses a robust tail-fetch strategy to jump to the most recent logs.
 */
public class EmergencyRecovery {

    public static void main(String[] args) {
        System.out.println("=== EMERGENCY DATA RECOVERY STARTING ===");
        System.out.println("Target: Recover all logs since 2026-04-10 00:00:00");
        
        try {
            Map<String, String> cfg = ConfigManager.load();
            DatabaseManager db = DatabaseManager.getInstance();
            db.connect(cfg.get("host"), cfg.get("port"), cfg.get("user"), cfg.get("password"), cfg.get("database"));

            List<Map<String, Object>> devices = db.fetchAll(
                "SELECT device_id, ip_address, port, device_name, comm_password FROM devices WHERE status='Active'"
            );

            if (devices.isEmpty()) {
                System.err.println("EmergencyRecovery: No active devices found in database.");
                return;
            }

            for (Map<String, Object> dev : devices) {
                recoverDevice(dev);
            }

            System.out.println("\nEmergencyRecovery: All devices polled. Triggering attendance re-calculation...");
            
            // Critical: Reset synced flag since Jan 1st to ensure full historical re-calculation
            db.execute("UPDATE raw_logs SET synced=0 WHERE DATE(punch_time) >= '2026-01-01'");
            
            // Run the processing logic
            SyncService.processRawLogs(msg -> System.out.println("SyncService [Recovery]: " + msg));

            System.out.println("\n=== EMERGENCY DATA RECOVERY COMPLETE ===");
            System.out.println("Please check your Dashboard and Raw Punch Logs panels now.");

        } catch (Exception e) {
            System.err.println("EmergencyRecovery CRITICAL ERROR: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void recoverDevice(Map<String, Object> dev) {
        int id = (int) dev.get("device_id");
        String name = (String) dev.get("device_name");
        String ip = (String) dev.get("ip_address");
        int port = (int) dev.get("port");
        int pwd = (int) dev.get("comm_password");

        System.out.println("\n--- Recovering " + name + " (" + ip + ") ---");
        
        // Use a very high timeout (10s) and large buffer (3000 logs)
        ZkProtocol zk = new ZkProtocol(ip, port, 10000); 
        zk.setPassword(pwd);

        if (zk.connect()) {
            try {
                // Fetch the last 10,000 logs (covers several months)
                System.out.println("EmergencyRecovery: Fetching last 10,000 logs from tail...");
                List<Map<String, Object>> logs = zk.fetchTailOnly(10000);
                
                System.out.println("EmergencyRecovery: Found " + logs.size() + " logs in tail buffer.");
                int inserted = 0;
                String filterDate = "2026-01-01 00:00:00";

                DatabaseManager.getInstance().setAutoCommit(false);
                try {
                    for (Map<String, Object> log : logs) {
                        String uid = (String) log.get("uid");
                        Object ptObj = log.get("punch_time");
                        String time = (ptObj instanceof java.time.LocalDateTime) 
                                ? ((java.time.LocalDateTime)ptObj).toString().replace("T", " ") 
                                : ptObj.toString();
                        int type = log.get("punch_type") != null ? (int) log.get("punch_type") : 0;

                        if (time.compareTo(filterDate) >= 0) {
                            int aff = DatabaseManager.getInstance().execute(
                                "INSERT IGNORE INTO raw_logs (device_id, emp_id, punch_time, punch_type, synced) VALUES (?,?,?,?,0)",
                                id, uid, time, type
                            );
                            if (aff > 0) inserted++;
                        }
                    }
                    DatabaseManager.getInstance().commit();
                } catch (Exception loopEx) {
                    DatabaseManager.getInstance().rollback();
                    throw loopEx;
                } finally {
                    DatabaseManager.getInstance().setAutoCommit(true);
                }
                System.out.println("EmergencyRecovery: Successfully restored " + inserted + " NEW logs since April 10.");
                
            } catch (Exception e) {
                System.err.println("EmergencyRecovery: Failed for " + name + ": " + e.getMessage());
            } finally {
                zk.disconnect();
            }
        } else {
            System.err.println("EmergencyRecovery: Could not connect to " + name);
        }
    }
}
