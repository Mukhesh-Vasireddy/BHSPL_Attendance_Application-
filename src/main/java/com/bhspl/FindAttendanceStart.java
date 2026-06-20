package com.bhspl;

import com.bhspl.util.ZkProtocol;
import java.time.LocalDateTime;

public class FindAttendanceStart {
    public static void main(String[] args) {
        ZkProtocol zk = new ZkProtocol("10.2.1.100", 4370, 5000);
        if (zk.connect()) {
            byte[] data = zk.fetchDataWithPayload(13, new byte[4]);
            if (data != null) {
                System.out.println("Scanning for first valid log...");
                // Scan every byte for a timestamp in 2024-2026 range
                for (int i = 0; i < data.length - 4; i++) {
                    long t = getIntLE(data, i);
                    if (t > 757382400 && t < 946684800) { // 2024-2030
                        LocalDateTime dt = decodeZkTime(t);
                        if (dt != null && dt.getYear() >= 2024 && dt.getYear() <= 2026) {
                            System.out.println("Found possible log at offset " + i + ": " + dt);
                            // Check if there's another one 40 bytes later
                            if (i + 40 + 4 <= data.length) {
                                long t2 = getIntLE(data, i + 40);
                                LocalDateTime dt2 = decodeZkTime(t2);
                                if (dt2 != null && dt2.getYear() >= 2024 && dt2.getYear() <= 2026) {
                                    System.out.println("Confirmed 40-byte record size starting at offset " + i);
                                    break;
                                }
                            }
                        }
                    }
                }
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
        try { return LocalDateTime.of(year, month, day, hour, min, sec); } catch (Exception e) { return null; }
    }
}
