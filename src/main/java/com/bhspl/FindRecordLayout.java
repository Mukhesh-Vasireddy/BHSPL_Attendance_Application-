package com.bhspl;

import com.bhspl.util.ZkProtocol;
import java.util.*;

public class FindRecordLayout {
    public static void main(String[] args) {
        ZkProtocol zk = new ZkProtocol("10.2.1.100", 4370, 5000);
        if (zk.connect()) {
            byte[] data = zk.fetchDataWithPayload(13, new byte[4]);
            if (data != null) {
                // We found a timestamp at 314915 (2026-04-23T09:47:43)
                // This corresponds to EmpID=6.
                // Let's look at the 40 bytes around it.
                int timePos = 314915;
                System.out.println("Analyzing 40 bytes around timePos " + timePos);
                
                // Try assuming time is at offset 24
                int recStart = timePos - 24;
                System.out.println("Assuming recStart = " + recStart + " (Time at offset 24)");
                dumpRec(data, recStart);

                // Try assuming time is at offset 26
                recStart = timePos - 26;
                System.out.println("\nAssuming recStart = " + recStart + " (Time at offset 26)");
                dumpRec(data, recStart);
                
                // Try assuming time is at offset 0
                recStart = timePos;
                System.out.println("\nAssuming recStart = " + recStart + " (Time at offset 0)");
                dumpRec(data, recStart);
            }
            zk.disconnect();
        }
    }

    static void dumpRec(byte[] data, int start) {
        if (start < 0 || start + 40 > data.length) return;
        for (int i = 0; i < 40; i++) {
            System.out.printf("%02X ", data[start + i]);
        }
        System.out.println();
        // Check for UID=6 (06 00) at various offsets
        for (int i = 0; i < 38; i++) {
            int val = (data[start+i] & 0xFF) | ((data[start+i+1] & 0xFF) << 8);
            if (val == 6) System.out.println("  Found UID=6 at offset " + i);
        }
    }
}
