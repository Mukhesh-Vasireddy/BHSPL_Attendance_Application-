package com.bhspl.tools;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class CheckLogs {
    public static void main(String[] args) {
        String q1 = "SELECT * FROM raw_logs WHERE emp_id='15241' ORDER BY punch_time DESC LIMIT 10";
        String q2 = "SELECT * FROM raw_logs ORDER BY punch_time DESC LIMIT 5";
        try (Connection c = DbHelper.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement(q1); ResultSet rs = ps.executeQuery()) {
                System.out.println("Sridhar Raw Logs:");
                DbHelper.printResultSet(rs);
            }
            try (PreparedStatement ps2 = c.prepareStatement(q2); ResultSet rs2 = ps2.executeQuery()) {
                System.out.println("Latest 5 Raw Logs:");
                DbHelper.printResultSet(rs2);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
