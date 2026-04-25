
package com.bhspl;
import com.bhspl.db.DatabaseManager;
import com.bhspl.db.ConfigManager;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

public class EmployeeTrace {
    public static void main(String[] args) throws Exception {
        Map<String, String> cfg = ConfigManager.load();
        DatabaseManager db = DatabaseManager.getInstance();
        db.connect(cfg.get("host"), cfg.get("port"), cfg.get("user"), cfg.get("password"), cfg.get("database"));

        String targetEid = "0001"; // Change as needed based on diagnostics
        String targetDate = "2026-04-10";
        
        System.out.println("=== TRACING PUNCHES FOR EMP " + targetEid + " ON " + targetDate + " ===");
        
        // Simulating the query logic in processRawLogs
        String sql = "SELECT id, emp_id, punch_time FROM raw_logs WHERE (emp_id=? OR emp_id=?) " +
                     "AND (DATE(punch_time) = ? OR (DATE(punch_time) = ? AND HOUR(punch_time) < 10)) " +
                     "ORDER BY punch_time ASC";
        
        List<Map<String, Object>> allLogs = db.fetchAll(sql, targetEid, "1", targetDate, "2026-04-11");
        
        System.out.println("Raw logs found: " + allLogs.size());
        for (Map<String, Object> l : allLogs) {
            System.out.println("  ID: " + l.get("id") + " | Time: " + l.get("punch_time"));
        }

        List<LocalDateTime> list = allLogs.stream()
            .map(map -> LocalDateTime.parse(map.get("punch_time").toString().replace(" ", "T")))
            .distinct()
            .sorted()
            .collect(Collectors.toList());
            
        System.out.println("Distinct sorted punch count: " + list.size());
        if (!list.isEmpty()) {
            LocalDateTime in = list.get(0);
            LocalDateTime out = list.size() > 1 ? list.get(list.size() - 1) : null;
            System.out.println("Resulting IN: " + in);
            System.out.println("Resulting OUT: " + out);
        }

        db.close();
    }
}
