package com.bhspl.service;

import com.bhspl.db.DatabaseManager;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Executors;

/**
 * ADMS (Push) listener for ZKTeco devices.
 * Listens for HTTP POST requests from devices and saves logs to database.
 */
public class PushService {

    private static HttpServer server;
    private static int PORT = 8081;
    private static boolean running = false;

    public static void start() {
        start(PORT);
    }

    public static void start(int port) {
        try {
            if (running) {
                System.out.println("PushService (ADMS) is already running.");
                return;
            }
            PORT = port;
            server = HttpServer.create(new InetSocketAddress(PORT), 0);
            server.createContext("/iclock/cdata", new CDataHandler());
            server.createContext("/iclock/getrequest", new GetRequestHandler());
            server.createContext("/iclock/devicecmd", new DeviceCmdHandler());
            
            server.setExecutor(Executors.newCachedThreadPool());
            server.start();
            running = true;
            System.out.println("PushService (ADMS) started successfully on port " + PORT);
        } catch (IOException e) {
            System.err.println("CRITICAL: Failed to start PushService (ADMS) on port " + PORT + ". Reason: " + e.getMessage());
            if (e.getMessage().contains("Address already in use")) {
                System.err.println("Suggestion: Check if another application or another instance of this app is already using port " + PORT);
            }
            running = false;
        }
    }

    public static void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
            running = false;
        }
    }

    public static boolean isRunning() {
        return running;
    }

    public static int getPort() {
        return PORT;
    }

    static class CDataHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String query = exchange.getRequestURI().getQuery();
            Map<String, String> params = parseQuery(query);
            String sn = params.get("SN");
            String table = params.get("table");
            String options = params.get("options");

            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                InputStream is = exchange.getRequestBody();
                String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                
                if ("ATTLOG".equalsIgnoreCase(table)) {
                    processLogs(sn, body);
                    sendResponse(exchange, "OK");
                } else if ("OPERLOG".equalsIgnoreCase(table)) {
                    System.out.println("PushService: Received OperLog from " + sn);
                    sendResponse(exchange, "OK");
                } else {
                    sendResponse(exchange, "OK");
                }
            } else {
                // GET requests
                if ("all".equalsIgnoreCase(options)) {
                    // Device requesting configuration
                    String configResponse = "Stamp=1\n" +
                            "OpStamp=1\n" +
                            "PhotoStamp=1\n" +
                            "ErrorDelay=30\n" +
                            "Delay=10\n" +
                            "TransTimes=00:00;14:00\n" +
                            "TransInterval=1\n" +
                            "TransFlag=1111111111\n" +
                            "Realtime=1\n" +
                            "Encrypt=0";
                    sendResponse(exchange, configResponse);
                } else {
                    sendResponse(exchange, "OK");
                }
            }
        }
    }

    static class GetRequestHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // ZK devices poll this for commands
            // For now, we don't have a command queue, so just return OK
            sendResponse(exchange, "OK");
        }
    }

    static class DeviceCmdHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // Device confirming command execution
            sendResponse(exchange, "OK");
        }
    }

    private static void processLogs(String sn, String body) {
        if (sn == null || body == null) return;
        
        System.out.println("PushService: Received logs from SN: " + sn);
        String[] lines = body.split("\n");
        DatabaseManager db = DatabaseManager.getInstance();
        
        int deviceId = getDeviceId(sn);
        int count = 0;

        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;
            
            // Example line: 15241	2024-04-22 10:30:05	0	0	0	0
            String[] parts = line.split("\t");
            if (parts.length >= 2) {
                String uid = parts[0];
                String timeStr = parts[1];
                int type = parts.length > 2 ? Integer.parseInt(parts[2]) : 0;

                try {
                    // Clean time string (sometimes they have extra info)
                    if (timeStr.length() > 19) timeStr = timeStr.substring(0, 19);
                    
                    db.execute("INSERT IGNORE INTO raw_logs (device_id, emp_id, punch_time, punch_type, synced) VALUES (?,?,?,?,0)",
                            deviceId, uid, timeStr, type);
                    count++;
                } catch (Exception e) {
                    System.err.println("PushService: DB Error: " + e.getMessage() + " for line: " + line);
                }
            }
        }
        System.out.println("PushService: Saved " + count + " logs from " + sn);
        
        // Mark device as active in DB
        try {
            db.execute("UPDATE devices SET last_sync=NOW(), status='Active' WHERE serial_number=?", sn);
        } catch (Exception ignored) {}
    }

    private static int getDeviceId(String sn) {
        try {
            Map<String, Object> dev = DatabaseManager.getInstance().fetchOne("SELECT device_id FROM devices WHERE serial_number=?", sn);
            if (dev != null) return (int) dev.get("device_id");
        } catch (Exception ignored) {}
        return 0;
    }

    private static void sendResponse(HttpExchange exchange, String response) throws IOException {
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain");
        exchange.sendResponseHeaders(200, bytes.length);
        OutputStream os = exchange.getResponseBody();
        os.write(bytes);
        os.close();
    }

    private static Map<String, String> parseQuery(String query) {
        Map<String, String> result = new HashMap<>();
        if (query == null) return result;
        for (String param : query.split("&")) {
            String[] entry = param.split("=");
            if (entry.length > 1) {
                result.put(entry[0], entry[1]);
            } else {
                result.put(entry[0], "");
            }
        }
        return result;
    }
}
