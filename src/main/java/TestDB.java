import com.bhspl.db.DatabaseManager;
import java.util.List;
import java.util.Map;

public class TestDB {
    public static void main(String[] args) {
        try {
            DatabaseManager db = DatabaseManager.getInstance();
            List<Map<String, Object>> result = db.query("SELECT * FROM users");
            if (result.size() > 0) {
                System.out.println("Keys: " + result.get(0).keySet());
            } else {
                System.out.println("No users.");
            }
            
            // let's try selecting allowed_modules
            db.query("SELECT allowed_modules FROM users");
            System.out.println("SUCCESS SELECTING allowed_modules");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
