
package com.bhspl;
import com.bhspl.db.DatabaseManager;
import com.bhspl.db.ConfigManager;
import java.util.*;

public class CheckDatabase {
    public static void main(String[] args) throws Exception {
        Map<String, String> cfg = ConfigManager.load();
        DatabaseManager db = DatabaseManager.getInstance();
        db.connect(cfg.get("host"), cfg.get("port"), cfg.get("user"), cfg.get("password"), cfg.get("database"));

        System.out.println("=== CHECKING TABLE INDICES ===");
        List<Map<String, Object>> indices = db.fetchAll("SHOW INDEX FROM attendance");
        for (Map<String, Object> idx : indices) {
            System.out.println("  Index: " + idx.get("Key_name") + " | Column: " + idx.get("Column_name"));
        }
        
        db.close();
    }
}
