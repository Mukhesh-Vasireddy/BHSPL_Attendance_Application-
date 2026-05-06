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
    private static final int ZK_CMD_REBOOT = 1004;
    private static final int ZK_CMD_OPTIONS_WR = 11;

    private static final int ZK_CMD_READALLDATA = 13;   // Legacy CMD – works on this device
    private static final int ZK_CMD_ATTLOG = 1503;       // Not supported on this device (returns 4989)
    private static final int ZK_CMD_FREE_DATA = 1502;
    private static final int ZK_CMD_PREPARE_DATA = 1500;
    private static final int ZK_CMD_DATA = 1501;

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
        return sendCommand(cmd, null);
    }

    private boolean sendCommand(int cmd, byte[] data) {
        try {
            byte[] resp = sendAndReceive(cmd, data);
            return resp != null && getShortLE(resp, 0) == ZK_CMD_ACK_OK;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Sets a configuration option on the device.
     * Common ADMS options: ADMSHost, ADMSPort, ADMSOn
     */
    public boolean setOption(String key, String value) {
        String data = key + "=" + value + "\0";
        return sendCommand(ZK_CMD_OPTIONS_WR, data.getBytes());
    }

    /**
     * Reboots the device. Required after changing ADMS settings.
     */
    public boolean reboot() {
        return sendCommand(ZK_CMD_REBOOT);
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

    /**
     * Fetches users from the ZK device.
     * 
     * Diagnostic findings for this specific device:
     *   - CMD 1503 + FCT_USER payload → returns CMD 2000 (ACK_OK) with NO data — NOT SUPPORTED
     *   - CMD 9 (USERTEMP_RRQ) direct → returns CMD 1500 (PREPARE_DATA) with totalSize=3388 ✓
     *   - CMD 13 (READALLDATA)        → returns CMD 1501 DATA chunks (for attendance)
     * 
     * So we use CMD 9 directly, then read the CMD 1501 DATA chunks with ACK_OK like CMD 13.
     * 
     * 28-byte user record struct (Python pyzk '<HB5s8sIxBhI'):
     *   offset  0: uid        (2 bytes short LE)
     *   offset  2: privilege  (1 byte)
     *   offset  3: password   (5 bytes)
     *   offset  8: name       (8 bytes, null-terminated UTF-8)
     *   offset 16: card       (4 bytes int LE)
     *   offset 20: x (pad)   (1 byte)
     *   offset 21: group_id  (1 byte)
     *   offset 22: timezone  (2 bytes)
     *   offset 24: user_id   (4 bytes int LE) ← enrollment ID matched to device_enroll_id
     *   TOTAL = 28 bytes
     */
    public List<Map<String, String>> getUsers() {
        List<Map<String, String>> users = new ArrayList<>();
        System.out.println("ZkProtocol: getUsers() — using CMD 9 (USERTEMP_RRQ)");

        try {
            // Send CMD 9 directly (no payload needed)
            byte[] resp = sendAndReceive(9, null);
            if (resp == null) {
                System.err.println("ZkProtocol: getUsers — no response from CMD 9");
                return users;
            }

            int respCmd = getShortLE(resp, 0);
            System.out.println("ZkProtocol: getUsers CMD 9 response code = " + respCmd + " len=" + resp.length);

            if (respCmd != ZK_CMD_PREPARE_DATA) {
                System.err.println("ZkProtocol: getUsers — expected PREPARE_DATA (1500), got " + respCmd);
                return users;
            }

            int totalSize = (int) getIntLE(resp, 8);
            System.out.println("ZkProtocol: getUsers expecting " + totalSize + " bytes (" + (totalSize / 28) + " possible 28-byte slots)");

            // Read the streamed DATA chunks exactly like CMD 13 does for attendance
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            for (int i = 0; i < 2000; i++) {
                if (bos.size() >= totalSize) break;
                byte[] chunk = recv();
                if (chunk == null) break;

                int chunkCmd = getShortLE(chunk, 0);
                if (chunkCmd == ZK_CMD_DATA) {          // 1501
                    int dataLen = chunk.length - 8;
                    if (dataLen > 0) bos.write(chunk, 8, dataLen);
                    // Acknowledge each chunk
                    send(makePacket(ZK_CMD_ACK_OK, session, ++replyId, null));
                } else {
                    System.out.println("ZkProtocol: getUsers chunk loop got CMD=" + chunkCmd + ", stopping");
                    break;
                }
            }

            // Free the device buffer
            send(makePacket(ZK_CMD_FREE_DATA, session, ++replyId, null));
            recv();

            byte[] userdata = bos.toByteArray();
            System.out.println("ZkProtocol: getUsers received " + userdata.length + " bytes total");

            if (userdata.length < 4) {
                System.err.println("ZkProtocol: getUsers — insufficient data");
                return users;
            }

            // First 4 bytes = declared size header
            int declaredSize = (int) getIntLE(userdata, 0);
            System.out.println("ZkProtocol: getUsers declared size in header = " + declaredSize);

            // Auto-detect record size: Python pyzk supports 28 and 72 bytes.
            // 28-byte struct '<HB5s8sIxBhI':  user_id is a 4-byte int  at offset 24
            // 72-byte struct '<HB8s24sIx7sx24s': user_id is a 24-byte str at offset 48 (preserves leading zeros!)
            // Calculate from actual data: (totalBytes - 4 header) / userCount
            // We know totalSize from PREPARE_DATA and we'll detect from the data length.
            int dataBytes = userdata.length - 4;
            int recSize;
            if (dataBytes > 0 && dataBytes % 72 == 0) {
                recSize = 72;
            } else if (dataBytes > 0 && dataBytes % 28 == 0) {
                recSize = 28;
            } else {
                // Fallback: guess based on which divides better
                recSize = (dataBytes % 72 < dataBytes % 28) ? 72 : 28;
            }
            System.out.println("ZkProtocol: getUsers using " + recSize + "-byte record format (" + (dataBytes / recSize) + " records)");

            int offset = 4;
            Set<String> seen = new HashSet<>();

            while (offset + recSize <= userdata.length) {
                int uid = getShortLE(userdata, offset);   // 2 bytes at 0 (internal device UID)
                String name, userIdStr;

                if (recSize == 72) {
                    // 72-byte: '<HB8s24sIx7sx24s'
                    // offset  0: uid        (2 bytes short)
                    // offset  2: privilege  (1 byte)
                    // offset  3: password   (8 bytes)
                    // offset 11: name       (24 bytes string — null-terminated)
                    // offset 35: card       (4 bytes int)
                    // offset 39: x (pad)   (1 byte)
                    // offset 40: group_id  (7 bytes string)
                    // offset 47: x (pad)   (1 byte)
                    // offset 48: user_id   (24 bytes string — preserves leading zeros like "07544"!)
                    name      = readString(userdata, offset + 11, 24).trim();
                    userIdStr = readString(userdata, offset + 48, 24).trim();
                } else {
                    // 28-byte: '<HB5s8sIxBhI'
                    // offset  0: uid        (2 bytes short)
                    // offset  2: privilege  (1 byte)
                    // offset  3: password   (5 bytes)
                    // offset  8: name       (8 bytes string)
                    // offset 16: card       (4 bytes int)
                    // offset 20: x (pad)   (1 byte)
                    // offset 21: group_id  (1 byte)
                    // offset 22: timezone  (2 bytes)
                    // offset 24: user_id   (4 bytes int — loses leading zeros)
                    name = readString(userdata, offset + 8, 8).trim();
                    int userId = (int) getIntLE(userdata, offset + 24);
                    userIdStr = String.valueOf(userId);
                }

                if (name.isEmpty()) name = "NN-" + userIdStr;

                System.out.println("  User: uid=" + uid + " user_id=[" + userIdStr + "] name=" + name);

                if (!userIdStr.isEmpty() && !userIdStr.equals("0") && !seen.contains(userIdStr)) {
                    seen.add(userIdStr);
                    Map<String, String> u = new HashMap<>();
                    u.put("user_id", userIdStr);   // enrollment ID — exact string from device (preserves leading zeros)
                    u.put("name", name);
                    u.put("uid", String.valueOf(uid));
                    users.add(u);
                }

                offset += recSize;
            }

            System.out.println("ZkProtocol: getUsers complete — found " + users.size() + " unique users.");

        } catch (Exception e) {
            System.err.println("ZkProtocol: getUsers exception: " + e.getMessage());
            e.printStackTrace();
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

