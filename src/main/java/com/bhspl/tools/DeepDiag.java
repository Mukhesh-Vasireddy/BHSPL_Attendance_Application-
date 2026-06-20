package com.bhspl.tools;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class DeepDiag {
    public static void main(String[] args) {
        String q1 = "SELECT emp_id, emp_name, device_enroll_id FROM employees WHERE emp_name LIKE '%Yash%' OR emp_name LIKE '%Yash%'";
        String q2 = "SELECT * FROM raw_logs ORDER BY punch_time DESC LIMIT 20";
        String q3 = "SELECT a.*, e.emp_name FROM attendance a LEFT JOIN employees e ON a.emp_id = e.emp_id ORDER BY punch_date DESC, in_time DESC LIMIT 20";
        try (Connection c = DbHelper.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement(q1); ResultSet rs = ps.executeQuery()) {
                System.out.println("--- Searching for Yashwanth ---");
                DbHelper.printResultSet(rs);
            }
            try (PreparedStatement ps2 = c.prepareStatement(q2); ResultSet rs2 = ps2.executeQuery()) {
                System.out.println("\n--- Latest 20 Raw Logs (Any ID) ---");
                DbHelper.printResultSet(rs2);
            }
            try (PreparedStatement ps3 = c.prepareStatement(q3); ResultSet rs3 = ps3.executeQuery()) {
                System.out.println("\n--- Latest 20 Attendance Records (Processed) ---");
                DbHelper.printResultSet(rs3);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
