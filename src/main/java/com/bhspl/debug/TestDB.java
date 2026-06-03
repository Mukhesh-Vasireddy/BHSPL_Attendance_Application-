package com.bhspl.debug;

import com.bhspl.db.DatabaseManager;
import java.util.Map;

public class TestDB {
    public static void main(String[] args) {
        try {
            Map<String, String> cfg = com.bhspl.core.Config.loadDbConfig();
            DatabaseManager db = DatabaseManager.getInstance();
            db.connect(cfg.get("host"), cfg.get("port"), cfg.get("user"), cfg.get("password"), cfg.get("database"));
            
            System.out.println("Grouping exceptions by date...");
            java.util.List<Map<String, Object>> res = db.query("SELECT punch_date, COUNT(*) as c FROM attendance WHERE exceptions != '' AND exceptions IS NOT NULL GROUP BY punch_date ORDER BY punch_date DESC LIMIT 10");
            for(Map<String, Object> r : res) {
                System.out.println(r.get("punch_date") + " -> " + r.get("c"));
            }
            System.exit(0);
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
}
