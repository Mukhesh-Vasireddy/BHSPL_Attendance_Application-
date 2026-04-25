package com.bhspl;

import com.bhspl.util.ZkProtocol;
import java.util.*;

public class CheckFinalRecord {
    public static void main(String[] args) {
        ZkProtocol zk = new ZkProtocol("10.2.1.100", 4370, 5000);
        if (zk.connect()) {
            byte[] data = zk.fetchDataWithPayload(13, new byte[4]);
            if (data != null) {
                int start = 314728;
                System.out.println("Dumping record at offset " + start);
                for (int i = 0; i < 40; i++) {
                    System.out.printf("%02X ", data[start + i]);
                }
                System.out.println();
                // ASCII at offset 2
                String uid = new String(data, start + 2, 9).trim();
                System.out.println("UID String at offset 2: [" + uid + "]");
            }
            zk.disconnect();
        }
    }
}
