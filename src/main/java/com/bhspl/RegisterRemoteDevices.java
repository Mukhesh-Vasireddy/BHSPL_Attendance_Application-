package com.bhspl;

import com.bhspl.db.ConfigManager;
import com.bhspl.db.DatabaseManager;
import java.util.*;

public class RegisterRemoteDevices {
    public static void main(String[] args) throws Exception {
        Map<String, String> cfg = ConfigManager.load();
        if (cfg == null) {
            System.out.println("No database configuration found. Please run the app first.");
            return;
        }

        DatabaseManager db = DatabaseManager.getInstance();
        db.connect(cfg.get("host"), cfg.get("port"), cfg.get("user"), cfg.get("password"), cfg.get("database"));

        String[][] remoteDevices = {
            {"Remote Device 1", "JJA1253800527"},
            {"Remote Device 2", "FD07650"}
        };

        for (String[] dev : remoteDevices) {
            String name = dev[0];
            String sn = dev[1];
            
            // Check if already exists
            Map<String, Object> existing = db.fetchOne("SELECT device_id FROM devices WHERE serial_number = ?", sn);
            if (existing == null) {
                db.execute("INSERT INTO devices (device_name, ip_address, serial_number, port, status) VALUES (?, ?, ?, ?, ?)",
                        name, "0.0.0.0", sn, 4370, "Active");
                System.out.println("Registered: " + name + " [" + sn + "]");
            } else {
                System.out.println("Device already exists: " + sn);
            }
        }

        db.close();
        System.out.println("Registration complete.");
    }
}
