package com.bhspl.service;

import com.bhspl.db.DatabaseManager;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.Executors;

/**
 * ADMS (Push) listener for ZKTeco devices.
 * Listens for HTTP POST requests from devices and saves logs to database.
 */
public class PushService {

    private static HttpServer server;
    private static final int PORT = 8081;

    public static void start() {
        try {
            if (server != null) return;
            server = HttpServer.create(new InetSocketAddress(PORT), 0);
            server.createContext("/iclock/cdata", new CDataHandler());
            server.createContext("/iclock/getrequest", new GetRequestHandler());
            server.setExecutor(Executors.newCachedThreadPool());
            server.start();
            System.out.println("PushService (ADMS) started on port " + PORT);
        } catch (IOException e) {
            System.err.println("Failed to start PushService: " + e.getMessage());
        }
    }

    public static void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    static class CDataHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String query = exchange.getRequestURI().getQuery();
            Map<String, String> params = parseQuery(query);
            String sn = params.get("SN");
            String table = params.get("table");

            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                InputStream is = exchange.getRequestBody();
                String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                
                if ("ATTLOG".equalsIgnoreCase(table)) {
                    processLogs(sn, body);
                }
                
                sendResponse(exchange, "OK");
            } else {
                // Initial handshake or option request
                sendResponse(exchange, "OK");
            }
        }
    }

    static class GetRequestHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            // ZK devices poll this for commands
            sendResponse(exchange, "OK");
        }
    }

    private static void processLogs(String sn, String body) {
        System.out.println("PushService: Received logs from SN: " + sn);
        String[] lines = body.split("\n");
        DatabaseManager db = DatabaseManager.getInstance();
        
        int deviceId = getDeviceId(sn);
        int count = 0;

        for (String line : lines) {
            if (line.trim().isEmpty()) continue;
            // Example line: 15241	2024-04-22 10:30:05	0	0	0	0
            String[] parts = line.split("\t");
            if (parts.length >= 2) {
                String uid = parts[0];
                String timeStr = parts[1];
                int type = parts.length > 2 ? Integer.parseInt(parts[2]) : 0;

                try {
                    db.execute("INSERT IGNORE INTO raw_logs (device_id, emp_id, punch_time, punch_type, synced) VALUES (?,?,?,?,0)",
                            deviceId, uid, timeStr, type);
                    count++;
                } catch (Exception e) {
                    System.err.println("PushService: DB Error: " + e.getMessage());
                }
            }
        }
        System.out.println("PushService: Saved " + count + " logs from " + sn);
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
