package com.bhspl.util;

import com.bhspl.db.ConfigManager;
import com.bhspl.db.DatabaseManager;
import java.util.List;
import java.util.Map;

public class CleanupUsersTask {
    public static void main(String[] args) {
        try {
            System.out.println("Starting User Cleanup...");
            Map<String, String> cfg = ConfigManager.load();
            if (cfg == null) {
                System.err.println("Database not configured.");
                return;
            }

            DatabaseManager db = DatabaseManager.INSTANCE;
            db.connect(cfg.get("host"), cfg.get("port"), cfg.get("user"), cfg.get("password"), cfg.get("database"));

            List<Map<String, Object>> users = db.fetchAll("SELECT username, role, status FROM users");
            System.out.println("Current registered users:");
            for (Map<String, Object> u : users) {
                System.out.println(" - " + u.get("username") + " (" + u.get("role") + ")");
            }

            System.out.println("\nDeleting all users EXCEPT 'admin'...");
            db.execute("DELETE FROM users WHERE username != 'admin'");

            long finalCnt = db.queryLong("SELECT COUNT(*) FROM users");

            System.out.println("Cleanup completed. Users remaining: " + finalCnt);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
