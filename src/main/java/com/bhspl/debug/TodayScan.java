
package com.bhspl.debug;
import com.bhspl.util.ZkProtocol;
import com.bhspl.db.DatabaseManager;
import com.bhspl.db.ConfigManager;
import java.time.LocalDateTime;
import java.util.*;

public class TodayScan {
    public static void main(String[] args) throws Exception {
        Map<String, String> cfg = ConfigManager.load();
        DatabaseManager db = DatabaseManager.getInstance();
        db.connect(cfg.get("host"), cfg.get("port"), cfg.get("user"), cfg.get("password"), cfg.get("database"));

        System.out.println("=== TARGETED TODAY (APRIL 13) RECOVERY: P4 Office ===");
        ZkProtocol zk = new ZkProtocol("10.2.1.100", 4370, 90000);
        zk.setUseTcp(true);
        zk.setPassword(0);
        
        if (zk.connect()) {
            // We'll fetch the last 400 logs. With 40-byte New SDK records, this is ~16KB.
            // With our new 200ms throttle, this will be very stable.
            List<Map<String, Object>> logs = zk.fetchTailOnly(400);
            
            System.out.println("TodayScan: Retrieved " + logs.size() + " logs from the device tail.");
            int recovered = 0;
            for (Map<String, Object> log : logs) {
                String uid = (String) log.get("uid");
                LocalDateTime pt = (LocalDateTime) log.get("punch_time");
                int type = (int) log.get("punch_type");
                
                // Only process today (April 13) or missing April 11-12
                if (pt.isAfter(LocalDateTime.of(2026, 4, 11, 0, 0))) {
                    int aff = db.execute(
                        "INSERT IGNORE INTO raw_logs (device_id, emp_id, punch_time, punch_type, synced) VALUES (1,?,?,?,0)",
                        uid, pt.toString().replace("T", " "), type
                    );
                    if (aff > 0) recovered++;
                }
            }
            System.out.println("TodayScan: Recovered " + recovered + " missing logs for April 11-13.");
            
            zk.disconnect();
        } else {
            System.out.println("Connection failed.");
        }
        
        db.close();
    }
}
