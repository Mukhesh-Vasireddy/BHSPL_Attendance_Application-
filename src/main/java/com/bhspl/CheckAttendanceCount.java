package com.bhspl;

import com.bhspl.db.ConfigManager;
import com.bhspl.db.DatabaseManager;
import java.util.*;

public class CheckAttendanceCount {
    public static void main(String[] args) {
        Map<String, String> cfg = ConfigManager.load();
        DatabaseManager db = DatabaseManager.getInstance();
        try {
            db.connect(cfg.get("host"), cfg.get("port"), cfg.get("user"), cfg.get("password"), cfg.get("database"));
            
            System.out.println("Total records in attendance table: " + db.queryLong("SELECT COUNT(*) FROM attendance"));
            System.out.println("Latest 10 records by ID:");
            List<Map<String, Object>> records = db.query("SELECT * FROM attendance ORDER BY id DESC LIMIT 10");
            for (Map<String, Object> r : records) {
                System.out.println("ID=" + r.get("id") + " | Emp=" + r.get("emp_id") + " | Date=" + r.get("punch_date") + " | In=" + r.get("in_time"));
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
