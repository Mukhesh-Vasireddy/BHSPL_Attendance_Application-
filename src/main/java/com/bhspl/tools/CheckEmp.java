package com.bhspl.tools;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class CheckEmp {
    public static void main(String[] args) {
        String sql = "SELECT emp_id, emp_name, device_enroll_id FROM employees WHERE emp_name LIKE '%Sri%' OR emp_name LIKE '%Yash%'";
        try (Connection c = DbHelper.getConnection(); PreparedStatement ps = c.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            DbHelper.printResultSet(rs);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
