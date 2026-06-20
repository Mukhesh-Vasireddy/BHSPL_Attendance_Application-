package com.bhspl;

import com.bhspl.util.ZkProtocol;

public class CompareRecords {
    public static void main(String[] args) {
        ZkProtocol zk = new ZkProtocol("10.2.1.100", 4370, 5000);
        if (zk.connect()) {
            byte[] data = zk.fetchDataWithPayload(13, new byte[4]);
            if (data != null) {
                // Find 3 records for today
                int count = 0;
                for (int i = 0; i < data.length - 40; i++) {
                    long t = getIntLE(data, i);
                    if (t > 845596800 && t < 845683200) { // today
                        System.out.println("\nRecord at offset " + i + " (Time: " + t + ")");
                        for (int j = 0; j < 40; j++) {
                            System.out.printf("%02X ", data[i + j]);
                        }
                        System.out.println();
                        count++;
                        if (count >= 5) break;
                        i += 39; // skip this record
                    }
                }
            }
            zk.disconnect();
        }
    }
    static long getIntLE(byte[] b, int o) {
        return (b[o] & 0xFFL) | ((b[o+1] & 0xFFL) << 8) | ((b[o+2] & 0xFFL) << 16) | ((b[o+3] & 0xFFL) << 24);
    }
}
