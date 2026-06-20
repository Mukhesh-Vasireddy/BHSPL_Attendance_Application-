
package com.bhspl.debug;
import com.bhspl.db.DatabaseManager;
import com.bhspl.db.ConfigManager;
import java.util.*;

public class FixDatabase {
    public static void main(String[] args) throws Exception {
        Map<String, String> cfg = ConfigManager.load();
        DatabaseManager db = DatabaseManager.getInstance();
        db.connect(cfg.get("host"), cfg.get("port"), cfg.get("user"), cfg.get("password"), cfg.get("database"));

        System.out.println("=== CLEANING DATABASE CONSTRAINTS ===");
        
        try {
            db.execute("SET FOREIGN_KEY_CHECKS = 0");
            db.execute("ALTER TABLE attendance DROP INDEX uq_emp_date");
            db.execute("SET FOREIGN_KEY_CHECKS = 1");
            System.out.println("  Forced Drop uq_emp_date");
        } catch (Exception e) {
            System.out.println("  uq_emp_date drop error: " + e.getMessage());
        }

        try {
            db.execute("ALTER TABLE attendance ADD UNIQUE INDEX uq_emp_in_time (emp_id, in_time)");
            System.out.println("  Added uq_emp_in_time");
        } catch (Exception e) {
            System.out.println("  uq_emp_in_time already exists or error: " + e.getMessage());
        }

        System.out.println("=== CLEARING CORRUPT ATTENDANCE (APRIL 10) ===");
        db.execute("DELETE FROM attendance WHERE punch_date = '2026-04-10'");
        System.out.println("  Deleted April 10 records for recalculation.");

        db.close();
    }
}
