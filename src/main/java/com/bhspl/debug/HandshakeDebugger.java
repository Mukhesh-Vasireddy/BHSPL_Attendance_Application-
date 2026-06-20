
package com.bhspl.debug;
import java.io.*;
import java.net.*;

public class HandshakeDebugger {
    public static void main(String[] args) throws Exception {
        String ip = "10.2.1.100";
        int port = 4370;
        
        System.out.println("=== TCP HANDSHAKE DEBUGGER: 10.2.1.100 ===");
        
        // Strategy: Legacy Connect Packet [E8 03] [CSUM] [00 00] [00 00]
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(ip, port), 5000);
            System.out.println("Try #1: Legacy Connect (8 bytes)...");
            OutputStream out = s.getOutputStream();
            
            // AA 55 [Len 08 00] [Packet E8 03 17 FC 00 00 00 00]
            byte[] pkt = new byte[]{ (byte)0xAA, 0x55, 0x08, 0x00, (byte)0xE8, 0x03, 0x17, (byte)0xFC, 0x00, 0x00, 0x00, 0x00 };
            out.write(pkt);
            out.flush();
            
            s.setSoTimeout(5000);
            byte[] head = new byte[4];
            int r = s.getInputStream().read(head);
            if (r == 4) {
                System.out.println("Success! Got TCP Header: " + bytesToHex(head));
                int len = (head[2] & 0xFF) | ((head[3] & 0xFF) << 8);
                byte[] resp = new byte[len];
                s.getInputStream().read(resp);
                System.out.println("Response Payload: " + bytesToHex(resp));
            } else {
                System.out.println("Failed. Read " + r + " bytes.");
            }
        } catch (Exception e) { System.out.println("Error: " + e.getMessage()); }
    }
    
    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02X ", b));
        return sb.toString();
    }
}
