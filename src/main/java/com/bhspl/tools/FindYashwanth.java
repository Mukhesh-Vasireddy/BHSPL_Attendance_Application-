package com.bhspl.tools;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class FindYashwanth {
    public static void main(String[] args) {
        String q1 = "SELECT emp_id, emp_name FROM employees WHERE emp_name LIKE '%Yash%'";
        String q2 = "SELECT r.emp_id, COUNT(*) as count, MAX(r.punch_time) as last_punch FROM raw_logs r LEFT JOIN employees e ON r.emp_id = e.emp_id WHERE e.emp_id IS NULL GROUP BY r.emp_id ORDER BY last_punch DESC LIMIT 20";
        try (Connection c = DbHelper.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement(q1); ResultSet rs = ps.executeQuery()) {
                System.out.println("Employees with 'Yash':");
                DbHelper.printResultSet(rs);
            }
            try (PreparedStatement ps2 = c.prepareStatement(q2); ResultSet rs2 = ps2.executeQuery()) {
                System.out.println("\nUnknown IDs in Raw Logs:");
                DbHelper.printResultSet(rs2);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
