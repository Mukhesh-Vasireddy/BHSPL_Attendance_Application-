package com.bhspl;

import com.bhspl.db.ConfigManager;
import com.bhspl.db.DatabaseManager;
import java.util.*;

public class CheckUnmatchedUids {
    public static void main(String[] args) {
        Map<String, String> cfg = ConfigManager.load();
        DatabaseManager db = DatabaseManager.getInstance();
        try {
            db.connect(cfg.get("host"), cfg.get("port"), cfg.get("user"), cfg.get("password"), cfg.get("database"));
            
            String[] unmatched = {"13616", "13621", "51"};
            List<Map<String, Object>> emps = db.query("SELECT emp_id, emp_name, device_enroll_id FROM employees");
            
            for (String u : unmatched) {
                System.out.println("\nSearching for match for UID: '" + u + "'");
                boolean found = false;
                for (Map<String, Object> e : emps) {
                    String eid = DatabaseManager.str(e, "device_enroll_id");
                    String sid = DatabaseManager.str(e, "emp_id");
                    
                    // Try exact match
                    if (eid.equals(u) || sid.equals(u)) {
                        System.out.println("  EXACT MATCH: Emp=" + sid + " (" + e.get("emp_name") + ") EnrollID=" + eid);
                        found = true;
                    }
                    
                    // Try numeric match
                    try {
                        long uNum = Long.parseLong(u);
                        if (!eid.isEmpty() && Long.parseLong(eid) == uNum) {
                            System.out.println("  NUMERIC MATCH (EnrollID): Emp=" + sid + " (" + e.get("emp_name") + ") EnrollID=" + eid);
                            found = true;
                        }
                        if (Long.parseLong(sid) == uNum) {
                            System.out.println("  NUMERIC MATCH (EmpID): Emp=" + sid + " (" + e.get("emp_name") + ") EnrollID=" + eid);
                            found = true;
                        }
                    } catch (Exception ignored) {}
                }
                if (!found) System.out.println("  NO MATCH FOUND.");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
