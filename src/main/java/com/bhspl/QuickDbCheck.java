package com.bhspl;

import com.bhspl.db.ConfigManager;
import com.bhspl.db.DatabaseManager;
import java.util.*;

public class QuickDbCheck {
    public static void main(String[] args) {
        Map<String, String> cfg = ConfigManager.load();
        if (cfg == null) {
            System.err.println("No config found.");
            return;
        }

        DatabaseManager db = DatabaseManager.getInstance();
        try {
            db.connect(cfg.get("host"), cfg.get("port"), cfg.get("user"), cfg.get("password"), cfg.get("database"));
            
            System.out.println("Checking employees...");
            List<Map<String, Object>> emps = db.query("SELECT emp_id, emp_name, device_enroll_id FROM employees");
            for (Map<String, Object> e : emps) {
                System.out.println("Emp: " + e.get("emp_id") + " | Name: " + e.get("emp_name") + " | EnrollID: " + e.get("device_enroll_id"));
            }

            System.out.println("\nChecking recent raw_logs...");
            List<Map<String, Object>> logs = db.query("SELECT * FROM raw_logs ORDER BY id DESC LIMIT 10");
            for (Map<String, Object> l : logs) {
                System.out.println("Log: ID=" + l.get("id") + " | EmpID=" + l.get("emp_id") + " | Time=" + l.get("punch_time") + " | Synced=" + l.get("synced"));
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
