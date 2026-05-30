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
            
            System.out.println("Checking non-numeric emp_id formats in raw_logs...");
            List<Map<String, Object>> nonNumeric = db.query(
                "SELECT emp_id, COUNT(*) as cnt, MIN(punch_type) as min_p, MAX(punch_type) as max_p " +
                "FROM raw_logs WHERE emp_id REGEXP '[^0-9]' GROUP BY emp_id"
            );
            for (Map<String, Object> nn : nonNumeric) {
                System.out.println("EmpID: [" + nn.get("emp_id") + "] | Count: " + nn.get("cnt") + 
                                   " | MinPunchType: " + nn.get("min_p") + " | MaxPunchType: " + nn.get("max_p"));
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
