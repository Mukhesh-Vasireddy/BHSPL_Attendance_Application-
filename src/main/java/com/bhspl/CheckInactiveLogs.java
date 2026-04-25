package com.bhspl;

import com.bhspl.db.ConfigManager;
import com.bhspl.db.DatabaseManager;
import java.util.*;

public class CheckInactiveLogs {
    public static void main(String[] args) {
        Map<String, String> cfg = ConfigManager.load();
        DatabaseManager db = DatabaseManager.getInstance();
        try {
            db.connect(cfg.get("host"), cfg.get("port"), cfg.get("user"), cfg.get("password"), cfg.get("database"));
            
            System.out.println("Checking for raw_logs with UIDs that are Inactive employees...");
            List<Map<String, Object>> logs = db.query("SELECT DISTINCT emp_id FROM raw_logs WHERE synced=0");
            List<Map<String, Object>> inactive = db.query("SELECT emp_id, emp_name, status FROM employees WHERE status != 'Active'");
            
            for (Map<String, Object> l : logs) {
                String uid = DatabaseManager.str(l, "emp_id");
                for (Map<String, Object> i : inactive) {
                    if (uid.equals(i.get("emp_id")) || uid.equals(i.get("device_enroll_id"))) {
                        System.out.println("Log UID '" + uid + "' matches Inactive Emp: " + i.get("emp_id") + " (" + i.get("emp_name") + ") Status: " + i.get("status"));
                    }
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
