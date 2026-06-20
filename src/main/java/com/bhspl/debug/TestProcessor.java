package com.bhspl.debug;

import com.bhspl.db.DatabaseManager;
import com.bhspl.db.ConfigManager;
import java.util.*;

public class TestProcessor {
    public static void main(String[] args) throws Exception {
        Map<String, String> cfg = ConfigManager.load();
        DatabaseManager db = DatabaseManager.getInstance();
        db.connect(cfg.get("host"), cfg.get("port"), cfg.get("user"), cfg.get("password"), cfg.get("database"));

        System.out.println("Total Employees: " + db.fetchOne("SELECT COUNT(*) AS c FROM employees").get("c"));
        System.out.println("Raw Logs Total: " + db.fetchOne("SELECT COUNT(*) AS c FROM raw_logs").get("c"));
        System.out.println("Unsynced Logs: " + db.fetchOne("SELECT COUNT(*) AS c FROM raw_logs WHERE synced=0").get("c"));
        System.out.println("Attendance Rows Today: " + db.fetchOne("SELECT COUNT(*) AS c FROM attendance WHERE punch_date=CURDATE()").get("c"));
        
        db.close();
    }
}
