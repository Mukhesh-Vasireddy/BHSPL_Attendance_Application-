
package com.bhspl.debug;
import java.io.*;
import java.net.*;
import java.util.*;

public class TcpProbe {
    public static void main(String[] args) throws Exception {
        String ip = "10.2.1.100";
        int port = 4370;
        
        System.out.println("=== TCP PROBE: 10.2.1.100 ===");
        
        // Strategy A: Length (4 bytes) + Connect(1000)
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(ip, port), 5000);
            System.out.println("A: Connected. Sending 4-byte Length framing...");
            OutputStream out = s.getOutputStream();
            // Payload for Connect (8 bytes)
            byte[] payload = new byte[]{ (byte)0xE8, 0x03, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00 };
            byte[] framing = new byte[12];
            framing[0] = 0x08; framing[1] = 0x00; framing[2] = 0x00; framing[3] = 0x00; // Length
            System.arraycopy(payload, 0, framing, 4, 8);
            out.write(framing);
            out.flush();
            s.setSoTimeout(5000);
            byte[] resp = new byte[12];
            int read = s.getInputStream().read(resp);
            System.out.println("A: Response read " + read + " bytes.");
        } catch (Exception e) { System.out.println("A: Error: " + e.getMessage()); }

        // Strategy B: AA 55 + Length (2 bytes) + Connect
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(ip, port), 5000);
            System.out.println("B: Connected. Sending AA 55 framing...");
            OutputStream out = s.getOutputStream();
            byte[] payload = new byte[]{ (byte)0xE8, 0x03, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00 };
            byte[] framing = new byte[12];
            framing[0] = (byte)0xAA; framing[1] = 0x55; framing[2] = 0x08; framing[3] = 0x00; 
            System.arraycopy(payload, 0, framing, 4, 8);
            out.write(framing);
            out.flush();
            s.setSoTimeout(5000);
            byte[] resp = new byte[12];
            int read = s.getInputStream().read(resp);
            System.out.println("B: Response read " + read + " bytes.");
        } catch (Exception e) { System.out.println("B: Error: " + e.getMessage()); }
    }
}
