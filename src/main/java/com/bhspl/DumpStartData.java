package com.bhspl;

import com.bhspl.util.ZkProtocol;
import java.util.*;
import java.time.LocalDateTime;

public class DumpStartData {
    public static void main(String[] args) {
        ZkProtocol zk = new ZkProtocol("10.2.1.100", 4370, 5000);
        if (zk.connect()) {
            System.out.println("Connected.");
            byte[] data = zk.fetchDataWithPayload(13, new byte[4]);
            if (data != null) {
                int userCount = (int) getIntLE(data, 0);
                int attOffset = 8 + userCount * 28;
                System.out.println("attOffset: " + attOffset);
                System.out.println("Dumping first 100 attendance records...");
                for (int k = 0; k < 100; k++) {
                    int i = attOffset + k * 16;
                    if (i + 15 >= data.length) break;
                    long t = getIntLE(data, i);
                    int uid = getShortLE(data, i + 4);
                    System.out.println("Rec " + k + ": TimeVal=" + t + " UID=" + uid + " Decoded=" + decodeZkTime(t));
                }
            }
            zk.disconnect();
        }
    }

    static long getIntLE(byte[] b, int o) {
        return (b[o] & 0xFFL) | ((b[o+1] & 0xFFL) << 8) | ((b[o+2] & 0xFFL) << 16) | ((b[o+3] & 0xFFL) << 24);
    }
    static int getShortLE(byte[] b, int o) {
        return (b[o] & 0xFF) | ((b[o+1] & 0xFF) << 8);
    }
    static LocalDateTime decodeZkTime(long t) {
        int sec=(int)(t%60); t/=60; int min=(int)(t%60); t/=60;
        int hour=(int)(t%24); t/=24; int day=(int)(t%31)+1; t/=31;
        int month=(int)(t%12)+1; t/=12; int year=(int)(t+2000);
        try { return LocalDateTime.of(year, month, day, hour, min, sec); } catch (Exception e) { return null; }
    }
}
