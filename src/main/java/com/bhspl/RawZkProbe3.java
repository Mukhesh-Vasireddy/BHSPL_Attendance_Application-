package com.bhspl;

import java.net.*;
import java.io.*;

/**
 * Dumps the raw bytes at the attendance section start (offset 40968)
 * and tries multiple timestamp field offsets to find valid dates.
 */
public class RawZkProbe3 {
    static DatagramSocket sock;
    static String ip;
    static int port;
    static int session = 0;
    static int replyId = 1;

    public static void main(String[] args) throws Exception {
        ip   = args.length > 0 ? args[0] : "10.2.1.100";
        port = args.length > 1 ? Integer.parseInt(args[1]) : 4370;

        sock = new DatagramSocket();
        sock.setSoTimeout(5000);

        // Connect + auth
        byte[] cr = sendRaw(1000, 0, 0, null);
        session = leShort(cr, 4); replyId = leShort(cr, 6);
        if (leShort(cr, 0) == 2005) {
            byte[] pd = new byte[4]; putIntLE(pd, 0, makeCommKey(0, session, 0x34373030));
            sendRaw(1002, session, ++replyId, pd);
        }
        sendRaw(1013, session, ++replyId, null); // disable
        sendRaw(1502, session, ++replyId, null); // free_data

        System.out.println("Collecting CMD13 stream...");
        byte[] r13 = sendRaw(13, session, ++replyId, new byte[4]);
        int totalSize = (r13.length >= 12) ? (int)leInt(r13, 8) : 0;
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        if (r13.length > 12) bos.write(r13, 12, r13.length - 12);

        for (int i = 0; i < 1000; i++) {
            if (totalSize > 0 && bos.size() >= totalSize) break;
            byte[] c = recv(); if (c == null) break;
            if (leShort(c, 0) != 1501) break;
            int dl = c.length - 8; if (dl > 0) bos.write(c, 8, dl);
            sendRaw(2000, session, ++replyId, null);
        }
        sendRaw(1502, session, ++replyId, null);
        recv();
        sendRaw(1014, session, ++replyId, null);
        recv();
        sendRaw(1001, session, ++replyId, null);
        sock.close();

        byte[] data = bos.toByteArray();
        System.out.println("Total collected: " + data.length + " bytes");

        // Sub-header
        int userCount = (int)leInt(data, 0);
        System.out.println("Sub-header: userCount=" + userCount);
        System.out.println("Sub-header bytes 4-7: " + hex(data, 4, 4) + " = " + leInt(data, 4));

        // Try different user record sizes to find attendance start
        for (int recSize : new int[]{28, 40, 72}) {
            int attOff = 8 + userCount * recSize;
            System.out.println("\n--- userRecSize=" + recSize + " -> attOffset=" + attOff + " ---");
            if (attOff >= data.length) { System.out.println("  (beyond data)"); continue; }

            // Dump first 64 bytes of attendance section
            System.out.print("  First 64 bytes: ");
            System.out.println(hex(data, attOff, Math.min(64, data.length - attOff)));

            // Try decoding as 16-byte records with time at various offsets
            for (int timeOff : new int[]{4, 8, 0}) {
                System.out.print("  16B recs, time@" + timeOff + ": ");
                int found = 0;
                for (int k = 0; k < Math.min(50, (data.length - attOff) / 16); k++) {
                    int idx = attOff + k * 16;
                    long t = leInt(data, idx + timeOff);
                    if (t == 0) continue;
                    try {
                        java.time.LocalDateTime dt = decodeZkTime(t);
                        if (dt.getYear() >= 2015 && dt.getYear() <= 2026) {
                            int uid = leShort(data, idx);
                            System.out.print("uid=" + uid + " " + dt + " | ");
                            if (++found >= 3) break;
                        }
                    } catch (Exception ignored) {}
                }
                if (found == 0) System.out.print("none");
                System.out.println();
            }
        }
    }

    static java.time.LocalDateTime decodeZkTime(long t) {
        int sec=(int)(t%60);t/=60; int min=(int)(t%60);t/=60;
        int hour=(int)(t%24);t/=24; int day=(int)(t%31)+1;t/=31;
        int month=(int)(t%12)+1;t/=12; int year=(int)(t+2000);
        return java.time.LocalDateTime.of(year,month,day,hour,min,sec);
    }

    static String hex(byte[] b, int off, int len) {
        StringBuilder sb = new StringBuilder();
        for (int i = off; i < off + len && i < b.length; i++) sb.append(String.format("%02X ", b[i]));
        return sb.toString().trim();
    }

    static byte[] sendRaw(int cmd, int sess, int seq, byte[] data) throws Exception {
        int dl = (data==null)?0:data.length;
        byte[] pkt = new byte[8+dl];
        putShortLE(pkt,0,cmd); putShortLE(pkt,4,sess); putShortLE(pkt,6,seq);
        if(data!=null) System.arraycopy(data,0,pkt,8,dl);
        putShortLE(pkt,2,checksum(pkt));
        sock.send(new DatagramPacket(pkt,pkt.length,InetAddress.getByName(ip),port));
        return recv();
    }
    static byte[] recv() {
        try { byte[] b=new byte[65535]; DatagramPacket p=new DatagramPacket(b,b.length); sock.receive(p);
            byte[] r=new byte[p.getLength()]; System.arraycopy(b,0,r,0,r.length); return r;
        } catch(Exception e){return null;}
    }
    static int leShort(byte[] b,int o){return (b[o]&0xFF)|((b[o+1]&0xFF)<<8);}
    static long leInt(byte[] b,int o){return(b[o]&0xFFL)|((b[o+1]&0xFFL)<<8)|((b[o+2]&0xFFL)<<16)|((b[o+3]&0xFFL)<<24);}
    static void putShortLE(byte[] b,int o,int v){b[o]=(byte)(v&0xFF);b[o+1]=(byte)((v>>8)&0xFF);}
    static void putIntLE(byte[] b,int o,int v){b[o]=(byte)(v&0xFF);b[o+1]=(byte)((v>>8)&0xFF);b[o+2]=(byte)((v>>16)&0xFF);b[o+3]=(byte)((v>>24)&0xFF);}
    static int checksum(byte[] d){int c=0;for(int i=0;i<d.length-1;i+=2){c+=(d[i]&0xFF)|((d[i+1]&0xFF)<<8);while((c>>16)>0)c=(c&0xFFFF)+(c>>16);}if(d.length%2!=0){c+=(d[d.length-1]&0xFF);while((c>>16)>0)c=(c&0xFFFF)+(c>>16);}return(~c)&0xFFFF;}
    static int makeCommKey(int p,int s,int k){long r=0;p&=0xFFFF;s&=0xFFFF;for(int i=0;i<32;i++){if((k&(1<<i))!=0)r=(r<<1)|1;else r=(r<<1);}return(int)((((r>>16)&0xFFFF)^s)<<16)|(int)((r&0xFFFF)^p);}
}
