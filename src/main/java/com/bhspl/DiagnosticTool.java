package com.bhspl;

import com.bhspl.db.DatabaseManager;
import com.bhspl.core.Config;
import java.time.LocalDate;
import java.util.*;

public class DiagnosticTool {
    public static void main(String[] args) {
        try {
            Map<String, String> cfg = Config.loadDbConfig();
            DatabaseManager db = DatabaseManager.getInstance();
            db.connect(cfg.get("host"), cfg.get("port"), cfg.get("user"), cfg.get("password"), cfg.get("database"));

            System.out.println("--- DEVICE STATUS ---");
            List<Map<String, Object>> devices = db.fetchAll("SELECT device_id, device_name, ip_address, status, last_sync, last_error FROM devices");
            for (Map<String, Object> d : devices) {
                System.out.println(d);
            }

            System.out.println("\n--- RECENT RAW LOGS (LAST 10) ---");
            List<Map<String, Object>> logs = db.fetchAll("SELECT id, emp_id, punch_time, device_id FROM raw_logs ORDER BY id DESC LIMIT 10");
            for (Map<String, Object> l : logs) {
                System.out.println(l);
            }

            System.out.println("\n--- TODAY'S ATTENDANCE RECORDS ---");
            List<Map<String, Object>> att = db.fetchAll("SELECT * FROM attendance WHERE punch_date = CURDATE()");
            for (Map<String, Object> a : att) {
                System.out.println(a);
            }
            
            System.out.println("\n--- RECENT EMPLOYEES ---");
            List<Map<String, Object>> emps = db.fetchAll("SELECT emp_id, emp_name, status FROM employees LIMIT 5");
            for (Map<String, Object> e : emps) {
                System.out.println(e);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
