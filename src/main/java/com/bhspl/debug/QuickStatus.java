
package com.bhspl.debug;
import com.bhspl.db.DatabaseManager;
import com.bhspl.db.ConfigManager;
import java.util.*;

public class QuickStatus {
    public static void main(String[] args) throws Exception {
        Map<String, String> cfg = ConfigManager.load();
        DatabaseManager db = DatabaseManager.getInstance();
        db.connect(cfg.get("host"), cfg.get("port"), cfg.get("user"), cfg.get("password"), cfg.get("database"));

        System.out.println("=== SYSTEM HEALTH CHECK ===");
        
        System.out.println("\n1. DEVICE STATUS:");
        List<Map<String, Object>> devices = db.query("SELECT device_id, device_name, ip_address, last_sync, last_error FROM devices");
        for (Map<String, Object> d : devices) {
            System.out.format("ID: %s | Name: %-15s | IP: %-15s | Last Sync: %s | Error: %s%n",
                d.get("device_id"), d.get("device_name"), d.get("ip_address"), d.get("last_sync"), d.get("last_error"));
        }

        System.out.println("\n2. RAW LOGS (TODAY - April 13):");
        Map<String, Object> rawCount = db.fetchOne("SELECT count(*) as cnt FROM raw_logs WHERE punch_time >= '2026-04-13'");
        System.out.println("Total raw logs today: " + rawCount.get("cnt"));

        System.out.println("\n3. ATTENDANCE RECORDS (TODAY - April 13):");
        Map<String, Object> attCount = db.fetchOne("SELECT count(*) as cnt FROM attendance WHERE punch_date = '2026-04-13'");
        System.out.println("Total attendance records today: " + attCount.get("cnt"));

        System.out.println("\n4. UNPROCESSED LOGS (synced=0):");
        Map<String, Object> unprocCount = db.fetchOne("SELECT count(*) as cnt FROM raw_logs WHERE synced=0");
        System.out.println("Total unprocessed logs: " + unprocCount.get("cnt"));

        db.close();
    }
}
