package com.bhspl;

import com.bhspl.db.ConfigManager;
import com.bhspl.db.DatabaseManager;
import java.util.*;

public class CheckRawLogsSchema {
    public static void main(String[] args) {
        Map<String, String> cfg = ConfigManager.load();
        DatabaseManager db = DatabaseManager.getInstance();
        try {
            db.connect(cfg.get("host"), cfg.get("port"), cfg.get("user"), cfg.get("password"), cfg.get("database"));
            
            System.out.println("Schema for raw_logs:");
            List<Map<String, Object>> cols = db.query("DESCRIBE raw_logs");
            for (Map<String, Object> c : cols) {
                System.out.println("Col: " + c.get("Field") + " | Type: " + c.get("Type"));
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
