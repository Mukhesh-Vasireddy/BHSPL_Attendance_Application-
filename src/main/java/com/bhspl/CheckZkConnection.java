package com.bhspl;

import com.bhspl.util.ZkProtocol;
import java.util.*;

public class CheckZkConnection {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: java CheckZkConnection <IP> [PORT] [PASSWORD]");
            return;
        }

        String ip = args[0];
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 4370;
        int pwd = args.length > 2 ? Integer.parseInt(args[2]) : 0;

        System.out.println("--------------------------------------------------");
        System.out.println("STRICT UDP DIAGNOSTIC TOOL");
        System.out.println("Target: " + ip + ":" + port);
        System.out.println("--------------------------------------------------");

        ZkProtocol zk = new ZkProtocol(ip, port, 5000); // 5s timeout
        zk.setPassword(pwd);

        System.out.print("Connecting... ");
        if (zk.connect()) {
            System.out.println("SUCCESS ✅");
            
            Map<String, String> info = zk.getDeviceInfo();
            System.out.println("Device Info:");
            info.forEach((k, v) -> System.out.println("  " + k + ": " + v));

            System.out.print("\nFetching Users... ");
            List<Map<String, String>> users = zk.getUsers();
            System.out.println("Found " + users.size() + " users.");
            for (int i = 0; i < Math.min(3, users.size()); i++) {
                System.out.println("  User " + (i+1) + ": " + users.get(i));
            }

            System.out.print("\nFetching Attendance Logs... ");
            List<Map<String, Object>> logs = zk.getAttendanceRecords();
            System.out.println("Found " + logs.size() + " logs.");
            
            if (!logs.isEmpty()) {
                System.out.println("Latest 3 logs:");
                for (int i = Math.max(0, logs.size() - 3); i < logs.size(); i++) {
                    System.out.println("  Log " + (i+1) + ": " + logs.get(i));
                }
            }

            zk.disconnect();
            System.out.println("\nDisconnected cleanly.");
        } else {
            System.out.println("FAILED ❌");
            System.err.println("Could not establish UDP connection to " + ip);
            System.err.println("Ensure the device is reachable and port " + port + " is open.");
        }
    }
}
