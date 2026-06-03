package com.bhspl.debug;

import com.bhspl.db.DatabaseManager;
import java.util.Map;

public class MigrateDB {
    public static void main(String[] args) {
        try {
            Map<String, String> cfg = com.bhspl.core.Config.loadDbConfig();
            DatabaseManager db = DatabaseManager.getInstance();
            db.connect(cfg.get("host"), cfg.get("port"), cfg.get("user"), cfg.get("password"), cfg.get("database"));
            
            System.out.println("Adding exceptions column to attendance table...");
            db.execute("ALTER TABLE attendance ADD COLUMN exceptions VARCHAR(255) DEFAULT ''");
            System.out.println("Migration complete.");
            System.exit(0);
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("Duplicate column name")) {
                System.out.println("Column already exists. Migration complete.");
                System.exit(0);
            } else {
                e.printStackTrace();
                System.exit(1);
            }
        }
    }
}
