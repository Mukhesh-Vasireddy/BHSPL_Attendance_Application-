
package com.bhspl;
import com.bhspl.util.ZkProtocol;
import java.util.*;

public class DeviceResearcher {
    public static void main(String[] args) throws Exception {
        // Correct constructor as per ZkProtocol.java
        ZkProtocol zk = new ZkProtocol("10.2.1.100", 4370, 30000);
        zk.setPassword(0); // Assuming 0 as per earlier turns
        
        if (zk.connect()) {
            System.out.println("Connected to device.");
            
            // CMD 11 with "~AttCount" usually returns the log count
            // We'll try fetching basic device info first
            byte[] resp = zk.fetchData(11); // GET_OPTION 
            if (resp != null) {
                System.out.println("Device Options: " + new String(resp));
            } else {
                System.out.println("Options fetch failed (timed out).");
            }
            
            zk.disconnect();
        } else {
            System.out.println("Connection failed.");
        }
    }
}
