package com.bhspl.tools;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class CheckYashId {
    public static void main(String[] args) {
        String sql = "SELECT emp_id, emp_name, device_enroll_id FROM employees WHERE emp_id='14599' OR device_enroll_id='14599'";
        try (Connection c = DbHelper.getConnection(); PreparedStatement ps = c.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            DbHelper.printResultSet(rs);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
