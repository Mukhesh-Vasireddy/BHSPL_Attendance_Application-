package com.bhspl;

import com.bhspl.util.ZkProtocol;
import java.time.LocalDateTime;

public class ScanForToday {
    public static void main(String[] args) {
        ZkProtocol zk = new ZkProtocol("10.2.1.100", 4370, 5000);
        if (zk.connect()) {
            System.out.println("Connected.");
            byte[] data = zk.fetchDataWithPayload(13, new byte[4]);
            if (data != null) {
                System.out.println("Data received: " + data.length + " bytes.");
                System.out.println("Scanning every byte offset for 2026-04-23 timestamps...");
                int found = 0;
                for (int i = 0; i < data.length - 4; i++) {
                    long t = getIntLE(data, i);
                    if (t > 845596800 && t < 845683200) { // 2026-04-23 range
                        LocalDateTime dt = decodeZkTime(t);
                        System.out.println("Found at offset " + i + ": " + dt + " (Value=" + t + ")");
                        found++;
                        if (found > 50) break;
                    }
                }
                if (found == 0) System.out.println("No records for today found in the entire blob.");
            }
            zk.disconnect();
        }
    }

    static long getIntLE(byte[] b, int o) {
        return (b[o] & 0xFFL) | ((b[o+1] & 0xFFL) << 8) | ((b[o+2] & 0xFFL) << 16) | ((b[o+3] & 0xFFL) << 24);
    }
    static LocalDateTime decodeZkTime(long t) {
        int sec=(int)(t%60); t/=60; int min=(int)(t%60); t/=60;
        int hour=(int)(t%24); t/=24; int day=(int)(t%31)+1; t/=31;
        int month=(int)(t%12)+1; t/=12; int year=(int)(t+2000);
        return LocalDateTime.of(year, month, day, hour, min, sec);
    }
}
