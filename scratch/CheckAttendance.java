
import com.bhspl.db.DatabaseManager;
import java.util.*;

public class CheckAttendance {
    public static void main(String[] args) throws Exception {
        DatabaseManager db = DatabaseManager.getInstance();
        List<Map<String, Object>> res = db.query("SELECT COUNT(*) as cnt FROM attendance");
        System.out.println("TOTAL ATTENDANCE RECORDS: " + res.get(0).get("cnt"));
        
        List<Map<String, Object>> sample = db.query("SELECT * FROM attendance ORDER BY punch_date DESC LIMIT 5");
        for(Map<String, Object> s : sample) {
            System.out.println("Date: " + s.get("punch_date") + " | Emp: " + s.get("emp_id") + " | Status: " + s.get("status"));
        }
    }
}
