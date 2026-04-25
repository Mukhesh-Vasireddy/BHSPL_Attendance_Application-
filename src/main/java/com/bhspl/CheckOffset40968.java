package com.bhspl;

import com.bhspl.util.ZkProtocol;
import java.util.*;

public class CheckOffset40968 {
    public static void main(String[] args) {
        ZkProtocol zk = new ZkProtocol("10.2.1.100", 4370, 5000);
        if (zk.connect()) {
            byte[] data = zk.fetchDataWithPayload(13, new byte[4]);
            if (data != null) {
                int start = 40968;
                System.out.println("Dumping 120 bytes at offset " + start);
                for (int i = 0; i < 120; i++) {
                    System.out.printf("%02X ", data[start + i]);
                    if ((i + 1) % 40 == 0) System.out.println();
                }
            }
            zk.disconnect();
        }
    }
}
