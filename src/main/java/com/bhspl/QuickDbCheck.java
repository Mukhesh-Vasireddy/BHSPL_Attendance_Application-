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
            
            System.out.println("Checking punch_type distribution...");
            List<Map<String, Object>> types = db.query("SELECT punch_type, COUNT(*) as cnt FROM raw_logs GROUP BY punch_type");
            for (Map<String, Object> t : types) {
                System.out.println("PunchType: " + t.get("punch_type") + " | Count: " + t.get("cnt"));
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
