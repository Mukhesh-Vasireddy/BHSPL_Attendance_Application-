package com.bhspl;

import com.bhspl.db.ConfigManager;
import com.bhspl.db.DatabaseManager;
import java.util.*;

public class CheckDevicesTable {
    public static void main(String[] args) throws Exception {
        Map<String, String> cfg = ConfigManager.load();
        DatabaseManager dm = DatabaseManager.getInstance();
        dm.connect(cfg.get("host"), cfg.get("port"), cfg.get("user"), cfg.get("password"), cfg.get("database"));
        
        System.out.println("--- Table structure (devices) ---");
        List<Map<String, Object>> cols = dm.query("DESCRIBE devices");
        for (Map<String, Object> c : cols) System.out.println(c);
        
        dm.close();
    }
}
