
package com.bhspl;
import com.bhspl.db.DatabaseManager;
import com.bhspl.db.ConfigManager;
import java.util.*;

public class CheckSync {
    public static void main(String[] args) throws Exception {
        Map<String, String> cfg = ConfigManager.load();
        DatabaseManager db = DatabaseManager.getInstance();
        db.connect(cfg.get("host"), cfg.get("port"), cfg.get("user"), cfg.get("password"), cfg.get("database"));

        System.out.println("=== SYNC VERIFICATION ===");
        List<Map<String, Object>> counts = db.fetchAll(
            "SELECT DATE(punch_time) as date, COUNT(*) as count FROM raw_logs " +
            "GROUP BY DATE(punch_time) ORDER BY date DESC LIMIT 15"
        );
        
        if (counts.isEmpty()) {
            System.out.println("ERROR: No logs found since April 10.");
        } else {
            System.out.println("Successfully fetched logs since April 10:");
            for (Map<String, Object> c : counts) {
                System.out.println("  " + c.get("date") + ": " + c.get("count") + " logs");
            }
        }
        db.close();
    }
}
