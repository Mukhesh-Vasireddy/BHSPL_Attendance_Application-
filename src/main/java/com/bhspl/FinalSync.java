
package com.bhspl;
import com.bhspl.util.ZkProtocol;
import com.bhspl.db.DatabaseManager;
import com.bhspl.db.ConfigManager;
import java.time.LocalDateTime;
import java.util.*;

public class FinalSync {
    public static void main(String[] args) throws Exception {
        Map<String, String> cfg = ConfigManager.load();
        DatabaseManager db = DatabaseManager.getInstance();
        db.connect(cfg.get("host"), cfg.get("port"), cfg.get("user"), cfg.get("password"), cfg.get("database"));

        System.out.println("=== TARGETED RECOVERY: EMPLOYEE 14507 at P4 Office ===");
        ZkProtocol zk = new ZkProtocol("10.2.1.100", 4370, 90000);
        zk.setPassword(0);
        
        if (zk.connect()) {
            // Targeted fetch of the last 200 logs specifically using the new 512-byte chunks
            List<Map<String, Object>> logs = zk.fetchTailOnly(200);
            
            System.out.println("FinalSync: Retrieved " + logs.size() + " logs from the device tail.");
            int recovered = 0;
            for (Map<String, Object> log : logs) {
                String uid = (String) log.get("uid");
                LocalDateTime pt = (LocalDateTime) log.get("punch_time");
                int type = (int) log.get("punch_type");
                
                int aff = db.execute(
                    "INSERT IGNORE INTO raw_logs (device_id, emp_id, punch_time, punch_type, synced) VALUES (1,?,?,?,0)",
                    uid, pt.toString().replace("T", " "), type
                );
                if (aff > 0) recovered++;
            }
            System.out.println("FinalSync: Inserted " + recovered + " new logs into raw_logs.");
            
            zk.disconnect();
        } else {
            System.out.println("Connection failed.");
        }
        
        db.close();
    }
}
