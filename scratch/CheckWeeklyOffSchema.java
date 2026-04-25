import java.sql.*;
import java.util.*;

public class CheckWeeklyOffSchema {
    public static void main(String[] args) {
        String url = "jdbc:mysql://localhost:3306/attendance_db";
        String user = "root";
        String pass = "root";
        
        try (Connection conn = DriverManager.getConnection(url, user, pass)) {
            DatabaseMetaData md = conn.getMetaData();
            ResultSet rs = md.getColumns(null, null, "weekly_offs", null);
            System.out.println("Columns in weekly_offs:");
            while (rs.next()) {
                System.out.println(rs.getString("COLUMN_NAME"));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
