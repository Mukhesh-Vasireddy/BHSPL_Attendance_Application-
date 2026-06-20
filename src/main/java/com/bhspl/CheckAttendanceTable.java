package com.bhspl;

import com.bhspl.db.ConfigManager;
import com.bhspl.db.DatabaseManager;
import java.util.*;

public class CheckAttendanceTable {
    public static void main(String[] args) {
        Map<String, String> cfg = ConfigManager.load();
        DatabaseManager db = DatabaseManager.getInstance();
        try {
            db.connect(cfg.get("host"), cfg.get("port"), cfg.get("user"), cfg.get("password"), cfg.get("database"));
            
            System.out.println("Checking for attendance records from 2026-04-23...");
            List<Map<String, Object>> records = db.query("SELECT * FROM attendance WHERE punch_date='2026-04-23'");
            System.out.println("Found " + records.size() + " records in attendance table.");
            for (Map<String, Object> r : records) {
                System.out.println("Att: Emp=" + r.get("emp_id") + " | Status=" + r.get("status") + " | In=" + r.get("in_time") + " | Out=" + r.get("out_time"));
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
