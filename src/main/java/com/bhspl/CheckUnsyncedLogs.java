package com.bhspl;

import com.bhspl.db.ConfigManager;
import com.bhspl.db.DatabaseManager;
import java.util.*;

public class CheckUnsyncedLogs {
    public static void main(String[] args) {
        Map<String, String> cfg = ConfigManager.load();
        DatabaseManager db = DatabaseManager.getInstance();
        try {
            db.connect(cfg.get("host"), cfg.get("port"), cfg.get("user"), cfg.get("password"), cfg.get("database"));
            
            List<Map<String, Object>> logs = db.query("SELECT * FROM raw_logs WHERE synced=0");
            System.out.println("Found " + logs.size() + " unsynced logs.");
            for (int i = 0; i < Math.min(20, logs.size()); i++) {
                Map<String, Object> l = logs.get(i);
                System.out.println("Log: ID=" + l.get("id") + " | EmpID=" + l.get("emp_id") + " | Time=" + l.get("punch_time"));
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
