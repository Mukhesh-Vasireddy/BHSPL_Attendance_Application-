package com.bhspl;

import com.bhspl.core.Config;
import com.bhspl.db.DatabaseManager;
import java.util.Map;
import java.util.List;

public class CheckDB {
    public static void main(String[] args) {
        try {
            Map<String, String> config = Config.loadDbConfig();
            if (config != null) {
                DatabaseManager db = DatabaseManager.getInstance();
                db.connect(config.get("host"), config.get("port"), config.get("user"), config.get("password"), config.get("database"));

                List<Map<String, Object>> employees = db.query("SELECT emp_id, emp_name, device_enroll_id, status FROM employees");
                System.out.println("--- Employees in DB ---");
                for (Map<String, Object> e : employees) {
                    System.out.println("DB_ID: '" + e.get("emp_id") + "' | NAME: " + e.get("emp_name") + " | ENROLL_ID: '" + e.get("device_enroll_id") + "' | STATUS: " + e.get("status"));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
