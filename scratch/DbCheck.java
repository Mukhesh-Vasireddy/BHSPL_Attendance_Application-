import java.sql.*;
import java.util.*;

public class DbCheck {
    public static void main(String[] args) throws Exception {
        String url = "jdbc:mysql://localhost:3306/bhspl_db?useSSL=false&serverTimezone=Asia/Kolkata";
        String user = "root";
        String pass = "root"; // Assuming root/root or similar based on common setups seen in previous sessions
        
        try (Connection conn = DriverManager.getConnection(url, user, pass)) {
            System.out.println("--- Table Counts ---");
            checkCount(conn, "employees");
            checkCount(conn, "devices");
            checkCount(conn, "raw_logs");
            checkCount(conn, "attendance");
            
            System.out.println("\n--- Sample Raw Logs (Top 5) ---");
            printQuery(conn, "SELECT emp_id, punch_time, synced FROM raw_logs ORDER BY punch_time DESC LIMIT 5");
            
            System.out.println("\n--- Sample Attendance (Top 5) ---");
            printQuery(conn, "SELECT emp_id, punch_date, in_time, out_time, status FROM attendance ORDER BY punch_date DESC LIMIT 5");
        } catch (Exception e) {
            System.err.println("Database check failed: " + e.getMessage());
        }
    }
    
    private static void checkCount(Connection conn, String table) throws Exception {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + table)) {
            if (rs.next()) System.out.println(table + ": " + rs.getInt(1));
        }
    }
    
    private static void printQuery(Connection conn, String sql) throws Exception {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
             ResultSetMetaData meta = rs.getMetaData();
             int cols = meta.getColumnCount();
             while (rs.next()) {
                 for (int i = 1; i <= cols; i++) {
                     System.out.print(meta.getColumnLabel(i) + ": " + rs.getObject(i) + " | ");
                 }
                 System.out.println();
             }
        }
    }
}
