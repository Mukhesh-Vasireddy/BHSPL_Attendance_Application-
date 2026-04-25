package com.bhspl;

import java.net.*;

/**
 * Raw UDP probe — sends commands manually and dumps the EXACT bytes back from
 * the device.  This bypasses ZkProtocol entirely so we can see the truth.
 * Run with: java -cp target/... com.bhspl.RawZkProbe <IP> [PORT] [PASSWORD]
 */
public class RawZkProbe {

    static DatagramSocket sock;
    static String ip;
    static int port;
    static int session = 0;
    static int replyId = 1;

    public static void main(String[] args) throws Exception {
        ip   = args.length > 0 ? args[0] : "192.168.1.201";
        port = args.length > 1 ? Integer.parseInt(args[1]) : 4370;
        int pwd = args.length > 2 ? Integer.parseInt(args[2]) : 0;

        sock = new DatagramSocket();
        sock.setSoTimeout(5000);

        System.out.println("=== RAW ZK PROBE ===");
        System.out.println("Target: " + ip + ":" + port);

        // Step 1 – CONNECT (1000)
        System.out.println("\n--- CMD 1000 (CONNECT) ---");
        byte[] connResp = sendRaw(1000, 0, 0, null);
        if (connResp == null) { System.out.println("No response to CONNECT. Aborting."); return; }
        dumpPacket("CONNECT response", connResp);
        session = leShort(connResp, 4);
        replyId = leShort(connResp, 6);
        System.out.println("  -> Session=" + session + "  ReplyId=" + replyId);

        int connCmd = leShort(connResp, 0);
        if (connCmd == 2005 || connCmd == 4989) {
            // Need auth
            System.out.println("--- CMD 1002 (AUTH) ---");
            int code = makeCommKey(pwd, session, 0x34373030);
            byte[] pwdData = new byte[4];
            putIntLE(pwdData, 0, code);
            byte[] authResp = sendRaw(1002, session, ++replyId, pwdData);
            if (authResp != null) dumpPacket("AUTH response", authResp);
            else System.out.println("  AUTH: no response");
        }

        // Step 2 – DISABLE device (1013)
        System.out.println("\n--- CMD 1013 (DISABLE) ---");
        byte[] disResp = sendRaw(1013, session, ++replyId, null);
        if (disResp != null) dumpPacket("DISABLE response", disResp);

        // Step 3 – FREE_DATA (1502)
        System.out.println("\n--- CMD 1502 (FREE_DATA) ---");
        byte[] freeResp = sendRaw(1502, session, ++replyId, null);
        if (freeResp != null) dumpPacket("FREE_DATA response", freeResp);
        else System.out.println("  FREE_DATA: no response");

        // Step 4 – CMD_ATTLOG (1503) — THE KEY TEST
        System.out.println("\n--- CMD 1503 (ATTLOG) ---");
        byte[] attResp = sendRaw(1503, session, ++replyId, null);
        if (attResp != null) {
            dumpPacket("ATTLOG response", attResp);
            int respCmd = leShort(attResp, 0);
            System.out.println("  -> Response CMD code = " + respCmd);
            if (respCmd == 1500) {
                int total = (int) leInt(attResp, 8);
                System.out.println("  -> PREPARE_DATA: total=" + total + " bytes to follow");
                // Drain data chunks
                int received = 0;
                for (int i = 0; i < 20; i++) {
                    byte[] chunk = recv();
                    if (chunk == null) break;
                    int cCmd = leShort(chunk, 0);
                    System.out.println("  Chunk " + i + ": CMD=" + cCmd + " len=" + chunk.length);
                    if (cCmd != 1501) break;
                    received += chunk.length - 8;
                    // ACK
                    sendRaw(2000, session, ++replyId, null);
                }
                System.out.println("  -> Total data bytes received: " + received);
            } else if (respCmd == 2000) {
                System.out.println("  -> ACK_OK with " + (attResp.length - 8) + " inline bytes");
            } else {
                System.out.println("  -> UNEXPECTED response code: " + respCmd);
            }
        } else {
            System.out.println("  ATTLOG: NO RESPONSE (timeout)");
        }

        // Step 5 – Also try CMD 13 (legacy CMD_READALLDATA)
        System.out.println("\n--- CMD 13 (READALLDATA / legacy) ---");
        byte[] cmd13data = new byte[4]; // 4 zero bytes
        byte[] r13 = sendRaw(13, session, ++replyId, cmd13data);
        if (r13 != null) {
            dumpPacket("CMD13 response", r13);
            System.out.println("  -> CMD13 response code: " + leShort(r13, 0));
        } else {
            System.out.println("  CMD13: no response");
        }

        // Step 6 – ENABLE (1014) and EXIT (1001)
        System.out.println("\n--- CMD 1014 (ENABLE) ---");
        byte[] enResp = sendRaw(1014, session, ++replyId, null);
        if (enResp != null) dumpPacket("ENABLE response", enResp);

        System.out.println("\n--- CMD 1001 (EXIT) ---");
        sendRaw(1001, session, ++replyId, null);
        sock.close();
        System.out.println("\n=== PROBE COMPLETE ===");
    }

    // --- Build and send a standard 8-byte ZK UDP packet ---
    static byte[] sendRaw(int cmd, int sess, int seq, byte[] data) throws Exception {
        int datLen = (data == null) ? 0 : data.length;
        byte[] pkt = new byte[8 + datLen];
        putShortLE(pkt, 0, cmd);
        putShortLE(pkt, 4, sess);
        putShortLE(pkt, 6, seq);
        if (data != null) System.arraycopy(data, 0, pkt, 8, datLen);
        putShortLE(pkt, 2, checksum(pkt));
        System.out.println("  TX: " + hex(pkt));
        InetAddress addr = InetAddress.getByName(ip);
        sock.send(new DatagramPacket(pkt, pkt.length, addr, port));
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
            System.out.println("  (timeout)");
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    static void dumpPacket(String label, byte[] p) {
        System.out.println("  RX [" + label + "] (" + p.length + " bytes): " + hex(p));
        if (p.length >= 8) {
            System.out.println("    CMD=" + leShort(p,0) + "  CHK=" + leShort(p,2)
                    + "  SES=" + leShort(p,4) + "  SEQ=" + leShort(p,6));
        }
    }

    // --- helpers ---
    static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format("%02X ", x));
        return sb.toString().trim();
    }

    static int leShort(byte[] b, int off) {
        return (b[off] & 0xFF) | ((b[off+1] & 0xFF) << 8);
    }

    static long leInt(byte[] b, int off) {
        return (b[off] & 0xFFL) | ((b[off+1] & 0xFFL) << 8)
             | ((b[off+2] & 0xFFL) << 16) | ((b[off+3] & 0xFFL) << 24);
    }

    static void putShortLE(byte[] b, int off, int v) {
        b[off]   = (byte)(v & 0xFF);
        b[off+1] = (byte)((v >> 8) & 0xFF);
    }

    static void putIntLE(byte[] b, int off, int v) {
        b[off]   = (byte)(v & 0xFF);
        b[off+1] = (byte)((v >> 8) & 0xFF);
        b[off+2] = (byte)((v >> 16) & 0xFF);
        b[off+3] = (byte)((v >> 24) & 0xFF);
    }

    static int checksum(byte[] data) {
        int chk = 0;
        int len = data.length;
        for (int i = 0; i < len - 1; i += 2) {
            chk += (data[i] & 0xFF) | ((data[i+1] & 0xFF) << 8);
            while ((chk >> 16) > 0) chk = (chk & 0xFFFF) + (chk >> 16);
        }
        if (len % 2 != 0) {
            chk += (data[len-1] & 0xFF);
            while ((chk >> 16) > 0) chk = (chk & 0xFFFF) + (chk >> 16);
        }
        return (~chk) & 0xFFFF;
    }

    static int makeCommKey(int password, int session, int key) {
        long res = 0;
        password &= 0xFFFF;
        session  &= 0xFFFF;
        for (int i = 0; i < 32; i++) {
            if ((key & (1 << i)) != 0) res = (res << 1) | 1;
            else res = (res << 1);
        }
        int low  = (int)((res & 0xFFFF) ^ password);
        int high = (int)(((res >> 16) & 0xFFFF) ^ session);
        return (high << 16) | low;
    }
}
