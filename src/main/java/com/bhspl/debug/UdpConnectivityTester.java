
package com.bhspl.debug;
import com.bhspl.util.ZkProtocol;
import java.util.*;

public class UdpConnectivityTester {
    public static void main(String[] args) throws Exception {
        System.out.println("=== UDP CONNECTIVITY TESTER: P4 Office ===");
        ZkProtocol zk = new ZkProtocol("10.2.1.100", 4370, 10000);
        zk.setUseTcp(false);
        
        if (zk.connect()) {
            System.out.println("Connected and Authenticated.");
            
            System.out.println("Testing USER FETCH (1504)...");
            List<Map<String, String>> users = zk.getUsers();
            System.out.println("Retrieved " + users.size() + " users.");
            
            if (users.isEmpty()) {
                System.out.println("Testing DEVICE INFO...");
                System.out.println(zk.getDeviceInfo());
            }
            
            zk.disconnect();
        } else {
            System.out.println("Connection failed.");
        }
    }
}
