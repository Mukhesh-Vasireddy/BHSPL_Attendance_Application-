
package com.bhspl;
import com.bhspl.db.DatabaseManager;
import com.bhspl.db.ConfigManager;
import com.bhspl.util.ZkProtocol;
import java.util.*;

public class DeviceAudit {
    public static void main(String[] args) throws Exception {
        Map<String, String> cfg = ConfigManager.load();
        DatabaseManager db = DatabaseManager.getInstance();
        db.connect(cfg.get("host"), cfg.get("port"), cfg.get("user"), cfg.get("password"), cfg.get("database"));

        List<Map<String, Object>> devices = db.fetchAll("SELECT * FROM devices WHERE status='Active'");
        for (Map<String, Object> dev : devices) {
            String ip = (String) dev.get("ip_address");
            int port = (int) dev.get("port");
            System.out.println("Auditing Device: " + dev.get("device_name") + " (" + ip + ")");
            
            ZkProtocol zk = new ZkProtocol(ip, port, 30000);
            if (zk.connect()) {
                System.out.println("Connection: SUCCESS");
                List<Map<String, Object>> records = zk.getAttendanceRecords();
                System.out.println("Total Records fetched from memory: " + (records != null ? records.size() : "NULL"));
                
                if (records != null && !records.isEmpty()) {
                    System.out.println("Latest 5 records in device memory:");
                    for (int i = Math.max(0, records.size() - 5); i < records.size(); i++) {
                        System.out.println("  " + records.get(i));
                    }
                }
                zk.disconnect();
            } else {
                System.out.println("Connection: FAILED");
            }
        }
        db.close();
    }
}
