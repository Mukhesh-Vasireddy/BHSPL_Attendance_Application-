package com.bhspl.scratch;

import com.bhspl.db.DatabaseManager;
import java.util.List;
import java.util.Map;

public class CheckADMSData {
    public static void main(String[] args) {
        try {
            DatabaseManager db = DatabaseManager.getInstance();
            // Connect using default credentials found in common configs
            db.connect("localhost", "3306", "root", "root", "bhspl_attendance");
            
            System.out.println("--- Checking for ADMS Activity ---");
            
            // Check recent logs
            List<Map<String, Object>> logs = db.query("SELECT * FROM raw_logs ORDER BY punch_time DESC LIMIT 5");
            if (logs.isEmpty()) {
                System.out.println("No logs found in raw_logs table.");
            } else {
                System.out.println("Most recent raw logs:");
                for (Map<String, Object> log : logs) {
                    System.out.println("Device ID: " + log.get("device_id") + 
                                       ", Emp ID: " + log.get("emp_id") + 
                                       ", Time: " + log.get("punch_time"));
                }
            }
            
            // Check device status
            List<Map<String, Object>> devices = db.query("SELECT device_name, serial_number, status, last_sync FROM devices");
            System.out.println("\n--- Device Status ---");
            for (Map<String, Object> dev : devices) {
                System.out.println("Name: " + dev.get("device_name") + 
                                   ", SN: " + dev.get("serial_number") + 
                                   ", Status: " + dev.get("status") + 
                                   ", Last Sync: " + dev.get("last_sync"));
            }
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
