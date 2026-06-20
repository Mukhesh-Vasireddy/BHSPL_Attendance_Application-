
package com.bhspl.debug;
import com.bhspl.db.DatabaseManager;
import com.bhspl.db.ConfigManager;
import java.util.*;

public class ViewAttendance {
    public static void main(String[] args) throws Exception {
        Map<String, String> cfg = ConfigManager.load();
        DatabaseManager db = DatabaseManager.getInstance();
        db.connect(cfg.get("host"), cfg.get("port"), cfg.get("user"), cfg.get("password"), cfg.get("database"));

        System.out.println("=== FINAL ATTENDANCE SESSIONS FOR 14507 (APRIL 10) ===");
        List<Map<String, Object>> rows = db.fetchAll(
            "SELECT * FROM attendance WHERE emp_id='14507' AND punch_date='2026-04-10' ORDER BY in_time ASC"
        );
        for (Map<String, Object> r : rows) {
            System.out.println("  Session: IN " + r.get("in_time") + " | OUT " + r.get("out_time") + " | Status: " + r.get("status"));
        }

        db.close();
    }
}
