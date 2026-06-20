package com.bhspl;

import com.bhspl.db.ConfigManager;
import com.bhspl.db.DatabaseManager;
import java.util.*;

public class CheckVeryRecentLogs {
    public static void main(String[] args) {
        Map<String, String> cfg = ConfigManager.load();
        DatabaseManager db = DatabaseManager.getInstance();
        try {
            db.connect(cfg.get("host"), cfg.get("port"), cfg.get("user"), cfg.get("password"), cfg.get("database"));
            
            System.out.println("Checking for raw_logs from today after 12:00...");
            List<Map<String, Object>> logs = db.query("SELECT * FROM raw_logs WHERE punch_time > '2026-04-23 12:00:00'");
            System.out.println("Found " + logs.size() + " very recent logs.");
            for (Map<String, Object> l : logs) {
                System.out.println("Log: ID=" + l.get("id") + " | EmpID=" + l.get("emp_id") + " | Time=" + l.get("punch_time"));
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
