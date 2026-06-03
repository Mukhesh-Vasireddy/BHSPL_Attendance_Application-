package com.bhspl.debug;

import com.bhspl.db.DatabaseManager;
import java.util.*;

public class TestTypes {
    public static void main(String[] args) {
        try {
            Map<String, String> cfg = com.bhspl.core.Config.loadDbConfig();
            DatabaseManager db = DatabaseManager.getInstance();
            db.connect(cfg.get("host"), cfg.get("port"), cfg.get("user"), cfg.get("password"), cfg.get("database"));
            
            List<Map<String, Object>> logs = db.query(
                    "SELECT punch_type, COUNT(*) as c FROM raw_logs WHERE DATE(punch_time) = '2026-06-03' GROUP BY punch_type");
            
            for (Map<String, Object> log : logs) {
                System.out.println("Type: " + log.get("punch_type") + " Count: " + log.get("c"));
            }
            System.exit(0);
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
}
