
package com.bhspl.debug;
import com.bhspl.util.ZkProtocol;
import com.bhspl.db.DatabaseManager;
import com.bhspl.db.ConfigManager;
import java.time.LocalDateTime;
import java.util.*;

public class UdpRecovery {
    public static void main(String[] args) throws Exception {
        Map<String, String> cfg = ConfigManager.load();
        DatabaseManager db = DatabaseManager.getInstance();
        db.connect(cfg.get("host"), cfg.get("port"), cfg.get("user"), cfg.get("password"), cfg.get("database"));

        System.out.println("=== ROBUST UDP RECOVERY (APRIL 13): P4 Office ===");
        // 5s per packet timeout for high latency
        ZkProtocol zk = new ZkProtocol("10.2.1.100", 4370, 5000); 
        zk.setUseTcp(false); 
        zk.setPassword(0);
        
        if (zk.connect()) {
            System.out.println("UdpRecovery: Connected to P4 Office. Pulling last 500 logs...");
            List<Map<String, Object>> logs = zk.fetchTailOnly(500);
            
            System.out.println("UdpRecovery: Fetched " + logs.size() + " records from tail.");
            int recovered = 0;
            for (Map<String, Object> log : logs) {
                String uid = (String) log.get("uid");
                LocalDateTime pt = (LocalDateTime) log.get("punch_time");
                int type = (int) log.get("punch_type");
                
                // Focusing on April 11-13
                if (pt.isAfter(LocalDateTime.of(2026, 4, 11, 0, 0))) {
                    int aff = db.execute(
                        "INSERT IGNORE INTO raw_logs (device_id, emp_id, punch_time, punch_type, synced) VALUES (1,?,?,?,0)",
                        uid, pt.toString().replace("T", " "), type
                    );
                    if (aff > 0) recovered++;
                }
            }
            System.out.println("UdpRecovery: Successfully recovered " + recovered + " missing logs for April 11-13.");
            zk.disconnect();
        } else {
            System.out.println("UdpRecovery: Connection failed (UDP even with retries).");
        }
        
        db.close();
    }
}
