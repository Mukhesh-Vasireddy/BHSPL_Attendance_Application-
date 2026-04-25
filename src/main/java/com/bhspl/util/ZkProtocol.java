package com.bhspl.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Strict Legacy UDP Protocol for ZKTeco Biometric Devices.
 * Re-implemented to remove complex 'New SDK' logic and enforce 8-byte packet
 * structure.
 */
public class ZkProtocol {
    private static final int ZK_CMD_CONNECT = 1000;
    private static final int ZK_CMD_EXIT = 1001;
    private static final int ZK_CMD_ENABLEDEVICE = 1014;
    private static final int ZK_CMD_DISABLEDEVICE = 1013;
    private static final int ZK_CMD_ACK_OK = 2000;
    private static final int ZK_CMD_ACK_UNAUTH = 2005;

    private static final int ZK_CMD_READALLDATA = 13;   // Legacy CMD – works on this device
    private static final int ZK_CMD_ATTLOG = 1503;       // Not supported on this device (returns 4989)
    private static final int ZK_CMD_FREE_DATA = 1502;
    private static final int ZK_CMD_PREPARE_DATA = 1500;
    private static final int ZK_CMD_DATA = 1501;
    private static final int ZK_CMD_USER = 1504;

    private final String ip;
    private final int port;
    private final int timeoutMs;
    private DatagramSocket socket;
    private int session = 0;
    private int replyId = 0;
    private int password = 0;

    public ZkProtocol(String ip, int port, int timeoutMs) {
        this.ip = ip;
        this.port = port;
        this.timeoutMs = timeoutMs;
    }

    public void setPassword(int pwd) {
        this.password = pwd;
    }

    public void setUseTcp(boolean useTcp) {
        if (useTcp)
            System.out.println("ZkProtocol: TCP is disabled. Strictly using Legacy UDP.");
    }

    public boolean connect() {
        try {
            if (socket == null || socket.isClosed()) {
                socket = new DatagramSocket();
            }
            socket.setSoTimeout(timeoutMs);

            session = 0;
            replyId = 0;

            byte[] pkt = makePacket(ZK_CMD_CONNECT, 0, 0, null);
            send(pkt);
            byte[] resp = recv();

            if (resp == null) {
                send(pkt); // Retry
                resp = recv();
            }

            if (resp == null)
                return false;

            // Legacy packets have CMD at index 0, session at 4, reply at 6
            int cmd = getShortLE(resp, 0);
            session = getShortLE(resp, 4);
            replyId = getShortLE(resp, 6);

            System.out.println("ZkProtocol: Connected (Legacy UDP). Session=" + session + ", ReplyID=" + replyId);

            if (cmd == ZK_CMD_ACK_UNAUTH || cmd == 4989) {
                System.out.println("ZkProtocol: Authenticating...");
                int code = makeCommKey(password, session, 0x34373030);
                byte[] pwdData = new byte[4];
                putIntLE(pwdData, 0, code);

                byte[] authResp = sendAndReceive(ZK_CMD_AUTH, pwdData);
                if (authResp == null)
                    return false;

                int authCmd = getShortLE(authResp, 0);
                if (authCmd != ZK_CMD_ACK_OK) {
                    System.err.println("ZkProtocol: AUTH failed (" + authCmd + ")");
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            System.err.println("ZkProtocol: Connect error: " + e.getMessage());
            return false;
        }
    }

    private static final int ZK_CMD_AUTH = 1002;

    private int makeCommKey(int password, int session, int key) {
        long res = 0;
        password &= 0xFFFF;
        session &= 0xFFFF;
        for (int i = 0; i < 32; i++) {
            if ((key & (1 << i)) != 0)
                res = (res << 1) | 1;
            else
                res = (res << 1);
        }
        int low = (int) ((res & 0xFFFF) ^ password);
        int high = (int) (((res >> 16) & 0xFFFF) ^ session);
        return (high << 16) | low;
    }

    public void disconnect() {
        try {
            if (session != 0) {
                send(makePacket(ZK_CMD_EXIT, session, ++replyId, null));
                recv();
            }
        } catch (Exception ignored) {
        } finally {
            if (socket != null) {
                socket.close();
                socket = null;
            }
            session = 0;
        }
    }

    public boolean disableDevice() {
        return sendCommand(ZK_CMD_DISABLEDEVICE);
    }

    public boolean enableDevice() {
        return sendCommand(ZK_CMD_ENABLEDEVICE);
    }

    private boolean sendCommand(int cmd) {
        try {
            byte[] resp = sendAndReceive(cmd, null);
            return resp != null && getShortLE(resp, 0) == ZK_CMD_ACK_OK;
        } catch (Exception e) {
            return false;
        }
    }

    public List<Map<String, Object>> getAttendanceRecords() {
        // This device rejects CMD 1503 (ATTLOG) with 4989.
        // It uses the legacy CMD 13 (READALLDATA) which returns a combined
        // blob: [8-byte sub-header][user records][attendance records].
        System.out.println("ZkProtocol: Starting attendance fetch (CMD 13 / READALLDATA)...");
        List<Map<String, Object>> records = new ArrayList<>();

        if (!disableDevice()) {
            System.err.println("ZkProtocol: Warning - Device disable failed, proceeding anyway...");
        }

        try {
            System.out.println("ZkProtocol: Pre-clearing device data buffer (FREE_DATA)...");
            resetDeviceState();

            byte[] data = fetchDataWithPayload(ZK_CMD_READALLDATA, new byte[4]);
            if (data == null || data.length < 8) {
                System.out.println("ZkProtocol: No data returned from CMD 13.");
                return records;
            }

            System.out.println("ZkProtocol: Raw data received: " + data.length + " bytes.");

            // Sub-header layout (8 bytes):
            //   bytes 0-3: user record COUNT (LE int) — capacity slots (e.g. 1024)
            //   bytes 4-7: total attendance section size (LE int)
            // Each user record is 40 bytes on this device.
            int userRecordCount = (int) getIntLE(data, 0);
            int userSectionBytes = userRecordCount * 40;
            System.out.println("ZkProtocol: Sub-header: userCount=" + userRecordCount
                    + " -> userSectionBytes=" + userSectionBytes);

            // Attendance records start after the 8-byte sub-header + user section
            int attOffset = 8 + userSectionBytes;
            if (attOffset >= data.length) {
                System.out.println("ZkProtocol: No attendance section (attOffset="
                        + attOffset + " >= len=" + data.length + ")");
                return records;
            }

            // 40-byte attendance record layout discovered for this device:
            //   offset 0-1:  unknown
            //   offset 2-10: UID (ASCII string, null-terminated)
            //   offset 27-30: timestamp (int LE, ZK encoded)
            //   offset 31:   punch type (byte)
            int recSize = 40;
            int attLength = data.length - attOffset;
            int limit = attLength / recSize;
            System.out.println("ZkProtocol: Parsing " + limit + " att records from offset " + attOffset + "...");

            Set<String> seen = new HashSet<>();
            for (int k = 0; k < limit; k++) {
                int i = attOffset + k * recSize;
                try {
                    // Extract Timestamp first to filter garbage
                    long attTime = getIntLE(data, i + 27);
                    if (attTime == 0) continue;

                    LocalDateTime dt = decodeZkTime(attTime);
                    int curYear = java.time.LocalDate.now().getYear();
                    if (dt == null || dt.getYear() < 2010 || dt.getYear() > curYear + 1) continue;

                    // Extract UID as ASCII string
                    StringBuilder sb = new StringBuilder();
                    for (int j = 0; j < 10; j++) {
                        byte b = data[i + 2 + j];
                        if (b == 0) break;
                        sb.append((char) b);
                    }
                    String uid = sb.toString().trim();
                    if (uid.isEmpty() || "0".equals(uid)) continue;

                    int punch = data[i + 31] & 0xFF;

                    String key = uid + "|" + dt.toString();
                    if (!seen.contains(key)) {
                        seen.add(key);
                        Map<String, Object> r = new HashMap<>();
                        r.put("uid", uid);
                        r.put("punch_time", dt);
                        r.put("punch_type", punch);
                        records.add(r);
                    }
                } catch (Exception ignored) {
                }
            }
            System.out.println("ZkProtocol: Fetch complete. Found " + records.size() + " unique attendance logs.");
        } finally {
            enableDevice();
        }
        return records;
    }

    public boolean clearAttendanceRecords() {
        try {
            byte[] resp = sendAndReceive(ZK_CMD_ATTLOG, new byte[] { 0x01 });
            return resp != null;
        } catch (Exception e) {
            return false;
        }
    }

    public List<Map<String, Object>> fetchTailOnly(int count) {
        List<Map<String, Object>> all = getAttendanceRecords();
        if (all == null || all.size() <= count)
            return all;
        return all.subList(all.size() - count, all.size());
    }

    /**
     * Sends CMD_FREE_DATA directly (no sendAndReceive overhead) to reset the
     * device's internal data-pump state.  Called whenever a 4989 is received.
     */
    private void resetDeviceState() {
        try {
            byte[] pkt = makePacket(ZK_CMD_FREE_DATA, session, ++replyId, null);
            System.out.println("ZkProtocol: Sending FREE_DATA to reset device state...");
            send(pkt);
            // Drain any pending reply (best-effort, ignore timeout)
            recv();
        } catch (Exception ignored) {
        }
    }

    /**
     * Like fetchData but sends a data payload with the initial command.
     * CMD 13 (READALLDATA) requires a 4-byte zero payload.
     */
    public byte[] fetchDataWithPayload(int cmd, byte[] cmdPayload) {
        try {
            System.out.println("ZkProtocol: fetchDataWithPayload Sending CMD " + cmd);
            byte[] resp = sendAndReceive(cmd, cmdPayload);
            if (resp == null) {
                System.out.println("ZkProtocol: fetchDataWithPayload got no response for CMD " + cmd);
                return null;
            }

            int cmdResp = getShortLE(resp, 0);
            System.out.println("ZkProtocol: fetchDataWithPayload initial response CMD=" + cmdResp);

            if (cmdResp == ZK_CMD_PREPARE_DATA || cmdResp == ZK_CMD_ACK_OK) {
                int totalSize = (resp.length >= 12) ? (int) getIntLE(resp, 8) : 0;
                System.out.println("ZkProtocol: Stream starting. Expecting " + totalSize + " bytes...");
                ByteArrayOutputStream bos = new ByteArrayOutputStream();

                // Also capture any inline data in the PREPARE_DATA packet (offset 8+4=12)
                if (cmdResp == ZK_CMD_PREPARE_DATA && resp.length > 12) {
                    bos.write(resp, 12, resp.length - 12);
                }

                for (int i = 0; i < 1000; i++) {
                    if (totalSize > 0 && bos.size() >= totalSize)
                        break;
                    byte[] cPkt = recv();
                    if (cPkt == null)
                        break;

                    int cCmd = getShortLE(cPkt, 0);
                    System.out.println("ZkProtocol: Chunk CMD=" + cCmd + " len=" + cPkt.length);
                    if (cCmd == ZK_CMD_DATA) {
                        int cLen = cPkt.length - 8;
                        if (cLen > 0)
                            bos.write(cPkt, 8, cLen);
                        send(makePacket(ZK_CMD_ACK_OK, session, ++replyId, null));
                    } else {
                        break;
                    }
                }

                send(makePacket(ZK_CMD_FREE_DATA, session, ++replyId, null));
                recv();
                System.out.println("ZkProtocol: fetchDataWithPayload total bytes: " + bos.size());
                return bos.toByteArray();
            }

            // Inline data in response itself
            if (resp.length > 8) {
                byte[] d = new byte[resp.length - 8];
                System.arraycopy(resp, 8, d, 0, d.length);
                return d;
            }
            return null;
        } catch (Exception e) {
            System.err.println("ZkProtocol: fetchDataWithPayload exception: " + e.getMessage());
            return null;
        }
    }

    public byte[] fetchData(int cmd) {
        try {
            System.out.println("ZkProtocol: fetchData Sending CMD " + cmd);
            byte[] resp = sendAndReceive(cmd, null);
            if (resp == null) {
                System.out.println("ZkProtocol: fetchData got no response for CMD " + cmd);
                return null;
            }

            int cmdResp = getShortLE(resp, 0);
            System.out.println("ZkProtocol: fetchData initial response CMD=" + cmdResp);

            if (cmdResp == ZK_CMD_PREPARE_DATA || cmdResp == ZK_CMD_ACK_OK) {
                int totalSize = (resp.length >= 12) ? (int) getIntLE(resp, 8) : 0;
                System.out.println("ZkProtocol: Stream starting. Expecting " + totalSize + " bytes...");
                ByteArrayOutputStream bos = new ByteArrayOutputStream();

                for (int i = 0; i < 50; i++) {
                    if (totalSize > 0 && bos.size() >= totalSize)
                        break;
                    byte[] cPkt = recv();
                    if (cPkt == null)
                        continue;

                    int cCmd = getShortLE(cPkt, 0);
                    if (cCmd != ZK_CMD_DATA)
                        break;

                    int cLen = cPkt.length - 8;
                    if (cLen > 0)
                        bos.write(cPkt, 8, cLen);

                    send(makePacket(ZK_CMD_ACK_OK, session, ++replyId, null));
                }

                send(makePacket(ZK_CMD_FREE_DATA, session, ++replyId, null));
                recv();
                return bos.toByteArray();
            }

            if (resp.length > 8) {
                byte[] d = new byte[resp.length - 8];
                System.arraycopy(resp, 8, d, 0, d.length);
                return d;
            }
            return null;
        } catch (Exception e) {
            System.err.println("ZkProtocol: fetchData exception: " + e.getMessage());
            return null;
        }
    }

    private byte[] sendAndReceive(int cmd, byte[] cmdData) throws IOException {
        for (int attempt = 1; attempt <= 3; attempt++) {
            byte[] pkt = makePacket(cmd, session, ++replyId, cmdData);
            System.out.println("ZkProtocol: Preparing CMD " + cmd + " (Attempt " + attempt + ", Session " + session
                    + ", ReplyID " + replyId + ")");
            send(pkt);
            byte[] resp = recv();

            if (resp == null) {
                System.out.println("ZkProtocol: Receive timeout (attempt " + attempt + "/3).");
                continue;
            }

            int cmdResp = getShortLE(resp, 0);
            if (cmdResp == 4989) {
                // 4989 = device busy / data buffer already locked.
                // Must send FREE_DATA to unlock the device before retrying.
                System.out.println("ZkProtocol: Received 4989 on attempt " + attempt + ". Sending FREE_DATA to reset device...");
                resetDeviceState();
                // Brief pause to let device settle
                try { Thread.sleep(300); } catch (InterruptedException ignored) {}
                continue;
            }
            return resp;
        }
        return null;
    }

    private byte[] makePacket(int cmd, int session, int seq, byte[] data) {
        int datLen = (data == null ? 0 : data.length);
        byte[] pkt = new byte[8 + datLen];
        putShortLE(pkt, 0, cmd);
        putShortLE(pkt, 4, session);
        putShortLE(pkt, 6, seq);
        if (data != null)
            System.arraycopy(data, 0, pkt, 8, data.length);
        putShortLE(pkt, 2, checksum(pkt, 0));
        return pkt;
    }

    private void send(byte[] data) throws IOException {
        System.out.println("ZkProtocol: Sending UDP Packet (" + data.length + " bytes): " + bytesToHex(data));
        InetAddress addr = InetAddress.getByName(ip);
        DatagramPacket p = new DatagramPacket(data, data.length, addr, port);
        socket.send(p);
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X ", b));
        }
        return sb.toString().trim();
    }

    private byte[] recv() {
        try {
            byte[] buf = new byte[65535];
            DatagramPacket p = new DatagramPacket(buf, buf.length);
            socket.receive(p);
            byte[] res = new byte[p.getLength()];
            System.arraycopy(buf, 0, res, 0, p.getLength());
            return res;
        } catch (java.net.SocketTimeoutException e) {
            System.err.println("ZkProtocol: UDP Timeout");
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private int checksum(byte[] data, int offset) {
        int chk = 0;
        int len = data.length - offset;
        for (int i = 0; i < len - 1; i += 2) {
            chk += (data[offset + i] & 0xFF) | ((data[offset + i + 1] & 0xFF) << 8);
            while ((chk >> 16) > 0)
                chk = (chk & 0xFFFF) + (chk >> 16);
        }
        if (len % 2 != 0) {
            chk += (data[data.length - 1] & 0xFF);
            while ((chk >> 16) > 0)
                chk = (chk & 0xFFFF) + (chk >> 16);
        }
        return (~chk) & 0xFFFF;
    }

    private LocalDateTime decodeZkTime(long t) {
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
        return LocalDateTime.of(year, month, day, hour, min, sec);
    }

    private static void putShortLE(byte[] b, int off, int v) {
        b[off] = (byte) (v & 0xFF);
        b[off + 1] = (byte) ((v >> 8) & 0xFF);
    }

    private static void putIntLE(byte[] b, int off, int v) {
        b[off] = (byte) (v & 0xFF);
        b[off + 1] = (byte) ((v >> 8) & 0xFF);
        b[off + 2] = (byte) ((v >> 16) & 0xFF);
        b[off + 3] = (byte) ((v >> 24) & 0xFF);
    }

    private static int getShortLE(byte[] b, int off) {
        return (b[off] & 0xFF) | ((b[off + 1] & 0xFF) << 8);
    }

    private static long getIntLE(byte[] b, int off) {
        return (b[off] & 0xFFL) | ((b[off + 1] & 0xFFL) << 8) |
                ((b[off + 2] & 0xFFL) << 16) | ((b[off + 3] & 0xFFL) << 24);
    }

    private static String readString(byte[] data, int offset, int max) {
        int len = 0;
        while (len < max && (offset + len) < data.length && data[offset + len] != 0)
            len++;
        return new String(data, offset, len);
    }

    public List<Map<String, String>> getUsers() {
        List<Map<String, String>> users = new ArrayList<>();
        byte[] data = fetchData(ZK_CMD_USER);
        if (data == null)
            return users;
        int recSize = 28;
        for (int i = 0; i + recSize <= data.length; i += recSize) {
            String uid = String.valueOf(getShortLE(data, i));
            String name = readString(data, i + 2, 24).trim();
            Map<String, String> u = new HashMap<>();
            u.put("user_id", uid);
            u.put("name", name);
            users.add(u);
        }
        return users;
    }

    public Map<String, String> getDeviceInfo() {
        Map<String, String> info = new LinkedHashMap<>();
        info.put("Protocol", "ZK UDP (Legacy)");
        info.put("Connection", "Connected ✅");
        info.put("Session ID", String.valueOf(session));
        return info;
    }
}
