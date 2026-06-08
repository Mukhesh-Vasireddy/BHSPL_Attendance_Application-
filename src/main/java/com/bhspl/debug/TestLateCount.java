package com.bhspl.debug;

import com.bhspl.db.DatabaseManager;
import java.util.List;
import java.util.Map;

public class TestLateCount {
    public static void main(String[] args) {
        try {
            DatabaseManager db = DatabaseManager.getInstance();
            db.connect("127.0.0.1", "3306", "root", "user", "bhspl_attendance");
            long lateCount = db.queryLong("SELECT COUNT(*) FROM attendance WHERE punch_date = CURDATE() AND status = 'Late'");
            System.out.println("Late Count with CURDATE: " + lateCount);

            long lateCountStr = db.queryLong("SELECT COUNT(*) FROM attendance WHERE punch_date = '" + java.time.LocalDate.now().toString() + "' AND status = 'Late'");
            System.out.println("Late Count with String Date: " + lateCountStr);
            
            List<Map<String, Object>> lates = db.query("SELECT * FROM attendance WHERE punch_date = CURDATE() AND status = 'Late'");
            System.out.println("Late records: " + lates.size());
            
            // Check if there are any late records for today at all? Maybe the status is something else
            List<Map<String, Object>> todayAll = db.query("SELECT status, COUNT(*) as cnt FROM attendance WHERE punch_date = CURDATE() GROUP BY status");
            System.out.println("Today attendance status group: " + todayAll);

            // Also check attendance table schema
            System.out.println("Done.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
