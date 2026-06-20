package com.bhspl.tools;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class CheckToday {
    public static void main(String[] args) {
        String q1 = "SELECT * FROM raw_logs WHERE emp_id='15241' AND DATE(punch_time) = CURDATE() ORDER BY punch_time DESC";
        String q2 = "SELECT emp_id, emp_name FROM employees WHERE emp_name LIKE '%Yash%'";
        try (Connection c = DbHelper.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement(q1); ResultSet rs = ps.executeQuery()) {
                System.out.println("Sridhar Today:");
                DbHelper.printResultSet(rs);
            }
            try (PreparedStatement ps2 = c.prepareStatement(q2); ResultSet rs2 = ps2.executeQuery()) {
                System.out.println("Yashwanth Search:");
                DbHelper.printResultSet(rs2);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
