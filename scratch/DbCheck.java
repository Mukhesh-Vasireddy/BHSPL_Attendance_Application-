import com.bhspl.db.DatabaseManager;
import java.util.List;
import java.util.Map;

public class DbCheck {
    public static void main(String[] args) throws Exception {
        DatabaseManager db = DatabaseManager.getInstance();
        List<Map<String, Object>> logs = db.query("SELECT punch_time, emp_id FROM raw_logs ORDER BY punch_time DESC LIMIT 20");
        System.out.println("--- RECENT LOGS ---");
        for (Map<String, Object> log : logs) {
            System.out.println(log.get("punch_time") + " | ID: " + log.get("emp_id"));
        }
        
        List<Map<String, Object>> emps = db.query("SELECT emp_id, emp_name FROM employees LIMIT 5");
        System.out.println("--- EMP SAMPLES ---");
        for (Map<String, Object> e : emps) {
            System.out.println(e.get("emp_id") + " | " + e.get("emp_name"));
        }
    }
}
