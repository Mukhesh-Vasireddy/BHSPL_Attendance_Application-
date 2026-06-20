package com.bhspl;

import com.bhspl.db.DatabaseManager;
import com.bhspl.core.Config;
import java.util.*;
import java.time.LocalDate;

public class FinalCheck {
    public static void main(String[] args) throws Exception {
        Map<String, String> config = Config.loadDbConfig();
        DatabaseManager db = DatabaseManager.getInstance();
        
        if (config == null) {
            System.err.println("Config not found!");
            return;
        }

        db.connect(
            config.get("host"), 
            config.get("port"), 
            config.get("user"), 
            config.get("password"), 
            config.get("database")
        );

        String today = LocalDate.now().toString();
        System.out.println("Checking data for: " + today);

        System.out.println("\n--- Devices Status ---");
        List<Map<String, Object>> devs = db.query("SELECT device_id, device_name, ip_address, last_sync, last_error, status FROM devices");
        for (Map<String, Object> d : devs) {
            System.out.println(d);
        }

        System.out.println("\n--- Raw Logs count for today ---");
        Map<String, Object> rc = db.fetchOne("SELECT COUNT(*) as c FROM raw_logs WHERE DATE(punch_time) = ?", today);
        System.out.println("Raw logs today: " + rc.get("c"));

        System.out.println("\n--- Attendance records for today ---");
        List<Map<String, Object>> att = db.query("SELECT emp_id, punch_date, in_time, out_time, status FROM attendance WHERE punch_date = ?", today);
        for (Map<String, Object> a : att) {
            System.out.println(a);
        }

        System.out.println("\n--- Unprocessed logs count ---");
        Map<String, Object> uc = db.fetchOne("SELECT COUNT(*) as c FROM raw_logs WHERE synced = 0");
        System.out.println("Unprocessed logs: " + uc.get("c"));

        db.close();
    }
}
