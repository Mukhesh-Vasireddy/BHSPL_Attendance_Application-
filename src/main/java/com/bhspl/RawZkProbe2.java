package com.bhspl;

import java.net.*;

/**
 * Focused probe: connects, runs CMD 13 (READALLDATA), collects ALL data chunks,
 * then tries to decode them as both 16-byte and 40-byte records to find the
 * right format.
 */
public class RawZkProbe2 {

    static DatagramSocket sock;
    static String ip;
    static int port;
    static int session = 0;
    static int replyId = 1;

    public static void main(String[] args) throws Exception {
        ip = args.length > 0 ? args[0] : "10.2.1.100";
        port = args.length > 1 ? Integer.parseInt(args[1]) : 4370;
        int pwd = args.length > 2 ? Integer.parseInt(args[2]) : 0;

        sock = new DatagramSocket();
        sock.setSoTimeout(5000);

        System.out.println("=== CMD-13 DATA PROBE ===  Target: " + ip + ":" + port);

        // CONNECT
        byte[] cr = sendRaw(1000, 0, 0, null);
        if (cr == null) {
            System.out.println("No connect response.");
            return;
        }
        session = leShort(cr, 4);
        replyId = leShort(cr, 6);
        System.out.println("Connected. Session=" + session + " ReplyId=" + replyId + " CMD=" + leShort(cr, 0));

        // AUTH if needed
        if (leShort(cr, 0) == 2005 || leShort(cr, 0) == 4989) {
            int code = makeCommKey(pwd, session, 0x34373030);
            byte[] pd = new byte[4];
            putIntLE(pd, 0, code);
            byte[] ar = sendRaw(1002, session, ++replyId, pd);
            System.out.println("Auth: CMD=" + (ar != null ? leShort(ar, 0) : "null"));
        }

        // DISABLE
        byte[] dr = sendRaw(1013, session, ++replyId, null);
        System.out.println("Disable: CMD=" + (dr != null ? leShort(dr, 0) : "null"));

        // FREE_DATA
        byte[] fr = sendRaw(1502, session, ++replyId, null);
        System.out.println("FreeData: CMD=" + (fr != null ? leShort(fr, 0) : "null"));

        // CMD 13 - send with 4-byte zero payload
        System.out.println("\n--- Sending CMD 13 (READALLDATA) ---");
        byte[] payload = new byte[4];
        byte[] r13 = sendRaw(13, session, ++replyId, payload);
        if (r13 == null) {
            System.out.println("CMD 13 timeout.");
            return;
        }
        System.out.println("CMD 13 initial response: CMD=" + leShort(r13, 0) + " len=" + r13.length);
        System.out.println("Raw bytes: " + hex(r13));

        int totalSize = (r13.length >= 12) ? (int) leInt(r13, 8) : 0;
        System.out.println("Total expected data: " + totalSize + " bytes");

        // Collect ALL data chunks
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();

        // Inline data in PREPARE_DATA if any (offset 12+)
        if (leShort(r13, 0) == 1500 && r13.length > 12) {
            bos.write(r13, 12, r13.length - 12);
            System.out.println("  Inline " + (r13.length - 12) + " bytes from PREPARE_DATA");
        }

        int chunkCount = 0;
        for (int i = 0; i < 200; i++) {
            if (totalSize > 0 && bos.size() >= totalSize)
                break;
            byte[] chunk = recv();
            if (chunk == null) {
                System.out.println("  Timeout after chunk " + i);
                break;
            }
            int cCmd = leShort(chunk, 0);
            if (cCmd != 1501) {
                System.out.println("  Non-DATA chunk: CMD=" + cCmd + " -> stopping");
                break;
            }
            int dataLen = chunk.length - 8;
            if (dataLen > 0)
                bos.write(chunk, 8, dataLen);
            chunkCount++;
            System.out
                    .println("  Chunk #" + chunkCount + ": " + dataLen + " bytes  (total so far: " + bos.size() + ")");
            // ACK
            sendRaw(2000, session, ++replyId, null);
        }

        byte[] allData = bos.toByteArray();
        System.out.println("\nTotal data collected: " + allData.length + " bytes in " + chunkCount + " chunks");

        // Dump first 160 bytes raw
        int dumpLen = Math.min(160, allData.length);
        System.out.println("\nFirst " + dumpLen + " raw bytes:");
        for (int i = 0; i < dumpLen; i++) {
            System.out.printf("%02X ", allData[i]);
            if ((i + 1) % 16 == 0)
                System.out.println();
        }
        System.out.println();

        // Try decoding as 16-byte records
        System.out.println("\n--- Decode attempt: 16-byte records ---");
        decodeRecords(allData, 16, 2 /* uid offset */, 8 /* time offset */, 12 /* punch offset */, 10);

        // Try decoding as 40-byte records
        System.out.println("\n--- Decode attempt: 40-byte records (uid@0, time@24, punch@28) ---");
        decodeRecords(allData, 40, 0, 24, 28, 10);

        // Try decoding as 40-byte records with uid@0, time@28
        System.out.println("\n--- Decode attempt: 40-byte records (uid@0, time@28, punch@32) ---");
        decodeRecords(allData, 40, 0, 28, 32, 10);

        // ENABLE + EXIT
        sendRaw(1014, session, ++replyId, null);
        sendRaw(1001, session, ++replyId, null);
        sock.close();
        System.out.println("\n=== PROBE COMPLETE ===");
    }

    static void decodeRecords(byte[] data, int recSize, int uidOff, int timeOff, int punchOff, int maxPrint) {
        int count = data.length / recSize;
        System.out.println("  Total possible records: " + count);
        int printed = 0;
        for (int k = 0; k < count && printed < maxPrint; k++) {
            int i = k * recSize;
            try {
                long t = leInt(data, i + timeOff);
                if (t == 0)
                    continue;
                java.time.LocalDateTime dt = decodeZkTime(t);
                if (dt.getYear() < 2010 || dt.getYear() > 2030)
                    continue;
                int uid = leShort(data, i + uidOff);
                int punch = data[i + punchOff] & 0xFF;
                System.out.println("  Rec[" + k + "] uid=" + uid + " time=" + dt + " punch=" + punch);
                printed++;
            } catch (Exception e) {
                /* skip */ }
        }
        System.out.println("  -> Printed " + printed + " valid-looking records.");
    }

    static java.time.LocalDateTime decodeZkTime(long t) {
        int sec = (int) (t % 60);
        t /= 60;
        int min = (int) (t % 60);
        t /= 60;
        int hour = (int) (t % 24);
        t /= 24;
        int day = (int) (t % 31) + 1;
        t /= 31;
        int month = (int) (t % 12) + 1;
        t /= 12;
        int year = (int) (t + 2000);
        return java.time.LocalDateTime.of(year, month, day, hour, min, sec);
    }

    static byte[] sendRaw(int cmd, int sess, int seq, byte[] data) throws Exception {
        int datLen = (data == null) ? 0 : data.length;
        byte[] pkt = new byte[8 + datLen];
        putShortLE(pkt, 0, cmd);
        putShortLE(pkt, 4, sess);
        putShortLE(pkt, 6, seq);
        if (data != null)
            System.arraycopy(data, 0, pkt, 8, datLen);
        putShortLE(pkt, 2, checksum(pkt));
        sock.send(new DatagramPacket(pkt, pkt.length, InetAddress.getByName(ip), port));
        return recv();
    }

    static byte[] recv() {
        try {
            byte[] buf = new byte[65535];
            DatagramPacket p = new DatagramPacket(buf, buf.length);
            sock.receive(p);
            byte[] res = new byte[p.getLength()];
            System.arraycopy(buf, 0, res, 0, p.getLength());
            return res;
        } catch (SocketTimeoutException e) {
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (byte x : b)
            sb.append(String.format("%02X ", x));
        return sb.toString().trim();
    }

    static int leShort(byte[] b, int off) {
        return (b[off] & 0xFF) | ((b[off + 1] & 0xFF) << 8);
    }

    static long leInt(byte[] b, int off) {
        return (b[off] & 0xFFL) | ((b[off + 1] & 0xFFL) << 8) | ((b[off + 2] & 0xFFL) << 16)
                | ((b[off + 3] & 0xFFL) << 24);
    }

    static void putShortLE(byte[] b, int off, int v) {
        b[off] = (byte) (v & 0xFF);
        b[off + 1] = (byte) ((v >> 8) & 0xFF);
    }

    static void putIntLE(byte[] b, int off, int v) {
        b[off] = (byte) (v & 0xFF);
        b[off + 1] = (byte) ((v >> 8) & 0xFF);
        b[off + 2] = (byte) ((v >> 16) & 0xFF);
        b[off + 3] = (byte) ((v >> 24) & 0xFF);
    }

    static int checksum(byte[] data) {
        int chk = 0;
        for (int i = 0; i < data.length - 1; i += 2) {
            chk += (data[i] & 0xFF) | ((data[i + 1] & 0xFF) << 8);
            while ((chk >> 16) > 0)
                chk = (chk & 0xFFFF) + (chk >> 16);
        }
        if (data.length % 2 != 0) {
            chk += (data[data.length - 1] & 0xFF);
            while ((chk >> 16) > 0)
                chk = (chk & 0xFFFF) + (chk >> 16);
        }
        return (~chk) & 0xFFFF;
    }

    static int makeCommKey(int password, int session, int key) {
        long res = 0;
        password &= 0xFFFF;
        session &= 0xFFFF;
        for (int i = 0; i < 32; i++) {
            if ((key & (1 << i)) != 0)
                res = (res << 1) | 1;
            else
                res = (res << 1);
        }
        return (int) ((((res >> 16) & 0xFFFF) ^ session) << 16) | (int) ((res & 0xFFFF) ^ password);
    }
}
