package com.bhspl.tools;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class CheckSridhar {
    public static void main(String[] args) {
        String sql = "SELECT * FROM raw_logs WHERE emp_id='15241' AND punch_time >= '2026-04-21 00:00:00' ORDER BY punch_time DESC";
        try (Connection c = DbHelper.getConnection(); PreparedStatement ps = c.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            System.out.println("Sridhar Logs Today:");
            DbHelper.printResultSet(rs);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
