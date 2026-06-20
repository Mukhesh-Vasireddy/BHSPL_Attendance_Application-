package com.bhspl.debug;

import java.net.*;

public class TestConnection {
    public static void main(String[] args) {
        String ip = "10.2.1.100";
        int port = 4370;
        System.out.println("Testing UDP connection to " + ip + ":" + port + "...");
        
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(5000);
            InetAddress address = InetAddress.getByName(ip);
            
            // Send a simple ZK connection packet (CMD_CONNECT = 1000)
            byte[] pkt = new byte[] { (byte)0xe8, 0x03, 0x17, (byte)0xfc, 0x00, 0x00, 0x00, 0x00 };
            DatagramPacket packet = new DatagramPacket(pkt, pkt.length, address, port);
            socket.send(packet);
            
            System.out.println("Packet sent. Waiting for response...");
            byte[] buf = new byte[1024];
            DatagramPacket reply = new DatagramPacket(buf, buf.length);
            socket.receive(reply);
            
            System.out.println("SUCCESS: Received response from " + ip + " (" + reply.getLength() + " bytes)");
        } catch (SocketTimeoutException e) {
            System.err.println("FAILED: Timeout reached. The device at " + ip + " is not responding.");
        } catch (Exception e) {
            System.err.println("ERROR: " + e.getMessage());
        }
    }
}
