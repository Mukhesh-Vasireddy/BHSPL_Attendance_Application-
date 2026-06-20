package com.bhspl;

import com.bhspl.db.ConfigManager;
import com.bhspl.db.DatabaseManager;
import java.util.*;

public class DbInspect {
    public static void main(String[] args) throws Exception {
        Map<String, String> cfg = ConfigManager.load();
        if (cfg == null) {
            System.out.println("No config found.");
            return;
        }
        DatabaseManager dm = DatabaseManager.INSTANCE;
        dm.connect(cfg.get("host"), cfg.get("port"), cfg.get("user"), cfg.get("password"), cfg.get("database"));
        
        System.out.println("--- Table structure (attendance) ---");
        List<Map<String, Object>> cols = dm.query("DESCRIBE attendance");
        for (Map<String, Object> c : cols) System.out.println(c);
        
        System.out.println("--- Triggers ---");
        List<Map<String, Object>> triggers = dm.query("SHOW TRIGGERS");
        for (Map<String, Object> t : triggers) System.out.println(t);
        
        System.out.println("--- Sample Data ---");
        List<Map<String, Object>> data = dm.query("SELECT * FROM attendance ORDER BY id DESC LIMIT 5");
        for (Map<String, Object> d : data) System.out.println(d);
        
        dm.close();
    }
}
