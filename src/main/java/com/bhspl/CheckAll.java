package com.bhspl;

import java.sql.*;
import java.util.*;
import com.bhspl.db.*;

public class CheckAll {
    public static void main(String[] args) throws Exception {
        Map<String, String> cfg = ConfigManager.load();
        if (cfg == null) {
            System.err.println("CRITICAL ERROR: Application is not configured. Please run the UI first.");
            return;
        }

        System.out.println("--- BHSPL Attendance System - DIAGNOSTIC CHECK ---");
        System.out.println("Checking database connectivity...");
        
        DatabaseManager db = DatabaseManager.getInstance();
        boolean connected = db.connect(
            cfg.get("host"),
            cfg.get("port"),
            cfg.get("user"),
            cfg.get("password"),
            cfg.get("database")
        );

        if (connected) {
            System.out.println("SUCCESS: Connected to database: " + cfg.get("database"));
            
            System.out.println("\nChecking configuration integrity...");
            List<Map<String, Object>> counts = db.query("SELECT (SELECT COUNT(*) FROM employees) AS ec, (SELECT COUNT(*) FROM devices) AS dc");
            if (!counts.isEmpty()) {
                System.out.println("Employees found: " + counts.get(0).get("ec"));
                System.out.println("Devices found:   " + counts.get(0).get("dc"));
            }

            System.out.println("\nChecking for unprocessed logs...");
            Map<String, Object> uc = db.fetchOne("SELECT COUNT(*) as c FROM raw_logs WHERE synced = 0");
            System.out.println("Unprocessed logs in queue: " + uc.get("c"));

            System.out.println("\nMost Recent 5 Raw Logs:");
            List<Map<String, Object>> logs = db.query("SELECT * FROM raw_logs ORDER BY punch_time DESC LIMIT 5");
            for (Map<String, Object> log : logs) {
                System.out.println(log);
            }
            
            db.close();
            System.out.println("\nAll core systems appear operational.");
        } else {
            System.err.println("FAILED: Could not connect to the database. Check your network or credentials.");
        }
    }
}
