package com.bhspl;

import com.bhspl.db.DatabaseManager;
import java.util.*;

public class DumpEmps {
    public static void main(String[] args) {
        DatabaseManager db = DatabaseManager.getInstance();
        try {
            db.connect("127.0.0.1", "3306", "root", "user", "bhspl_attendance");
            List<Map<String, Object>> emps = db
                    .query("SELECT emp_id, emp_name, device_enroll_id FROM employees LIMIT 20");
            System.out.println("--- Employee Dump ---");
            for (Map<String, Object> e : emps) {
                System.out.println("ID: '" + e.get("emp_id") + "' | Name: '" + e.get("emp_name") + "' | EnrollID: '"
                        + e.get("device_enroll_id") + "'");
            }

            List<Map<String, Object>> logs = db
                    .query("SELECT emp_id, punch_time FROM raw_logs WHERE DATE(punch_time) = '2026-05-06' LIMIT 10");
            System.out.println("\n--- Log Dump (Today) ---");
            for (Map<String, Object> l : logs) {
                System.out.println("ID: '" + l.get("emp_id") + "' | Time: '" + l.get("punch_time") + "'");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
