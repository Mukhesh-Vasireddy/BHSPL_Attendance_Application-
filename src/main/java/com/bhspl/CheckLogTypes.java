package com.bhspl;

import com.bhspl.db.DatabaseManager;
import java.util.*;
import java.io.*;

public class CheckLogTypes {
    public static void main(String[] args) {
        DatabaseManager db = DatabaseManager.getInstance();
        try (PrintWriter pw = new PrintWriter(new FileWriter("debug_types.txt"))) {
            db.connect("127.0.0.1", "3306", "root", "user", "bhspl_attendance");
            
            pw.println("Checking for raw_logs from 2026-05-06...");
            List<Map<String, Object>> logs = db.query("SELECT punch_time, emp_id FROM raw_logs WHERE DATE(punch_time) = '2026-05-06' LIMIT 10");
            pw.println("Found " + logs.size() + " logs for today.");
            
            for (Map<String, Object> log : logs) {
                Object p = log.get("punch_time");
                pw.println("Type: " + (p==null?"null":p.getClass().getName()) + " | Value: " + p + " | toString: " + p.toString());
                
                String datePart = p.toString().replace("T", " ").split(" ")[0];
                pw.println("  Extracted Date Part: '" + datePart + "'");
            }
            
            pw.println("\nChecking Employees...");
            List<Map<String, Object>> emps = db.query("SELECT emp_id, emp_name, device_enroll_id FROM employees WHERE status='Active' LIMIT 10");
            for (Map<String, Object> e : emps) {
                pw.println("Emp: ID='" + e.get("emp_id") + "' | Name='" + e.get("emp_name") + "' | EnrollID='" + e.get("device_enroll_id") + "'");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
