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
    private static volatile boolean running = false;
    private static volatile boolean userStopped = false;
    private static volatile String lastError = null;

    public static void start() {
        int port = PORT;
        String envPort = System.getenv("ADMS_PORT");
        if (envPort != null) {
            try {
                port = Integer.parseInt(envPort);
            } catch (NumberFormatException ignored) {}
        } else {
            String propPort = com.bhspl.db.ConfigManager.getProperty("adms_port", null);
            if (propPort != null) {
                try {
                    port = Integer.parseInt(propPort);
                } catch (NumberFormatException ignored) {}
            }
        }
        start(port);
    }

    public static void start(int port) {
        try {
            if (running) {
                System.out.println("PushService (ADMS) is already running.");
                return;
            }
            userStopped = false; // Reset flag when manually started
            PORT = port;
            
            // Self-healing: if the port is occupied by a ghost process, force stop it first
            if (isRunning() && !running) {
                System.out.println("PushService: Port " + PORT + " is occupied externally. Force-stopping ghost process...");
                forceStop();
                try { Thread.sleep(800); } catch (Exception ignored) {}
            }
            
            server = HttpServer.create(new InetSocketAddress(PORT), 0);
            server.createContext("/iclock/cdata", new CDataHandler());
            server.createContext("/iclock/getrequest", new GetRequestHandler());
            server.createContext("/iclock/devicecmd", new DeviceCmdHandler());
            
            server.setExecutor(Executors.newCachedThreadPool());
            server.start();
            running = true;
            lastError = null;
            System.out.println("PushService (ADMS) started successfully on port " + PORT);
        } catch (IOException e) {
            lastError = e.getMessage();
            System.err.println("CRITICAL: Failed to start PushService (ADMS) on port " + PORT + ". Reason: " + e.getMessage());
            if (e.getMessage().contains("Address already in use")) {
                System.err.println("Suggestion: Check if another application or another instance of this app is already using port " + PORT);
            }
            running = false;
        }
    }

    public static void stop() {
        System.out.println("PushService: Stopping service...");
        userStopped = true; // Set flag to prevent self-healing
        if (server != null) {
            try {
                server.stop(0);
                server = null;
            } catch (Exception e) {
                System.err.println("PushService: Error stopping server: " + e.getMessage());
            }
        }
        
        // Aggressive cleanup: check if port is still busy and force it closed
        running = false;
        forceStop();
        
        // Wait a moment for OS to release the socket
        try { Thread.sleep(500); } catch (Exception ignored) {}
        System.out.println("PushService: Service stopped.");
    }

    public static void forceStop() {
        try {
            long currentPid = ProcessHandle.current().pid();
            Process p = Runtime.getRuntime().exec("cmd /c netstat -ano | findstr :" + PORT);
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.contains("LISTENING")) {
                        String[] parts = line.trim().split("\\s+");
                        String pidStr = parts[parts.length - 1];
                        long pid = Long.parseLong(pidStr);
                        
                        if (pid != currentPid) {
                            System.err.println("PushService WARNING: Port " + PORT + " is occupied by EXTERNAL process PID " + pidStr + ".");
                            System.err.println("PushService: Automatic taskkill is disabled for safety. Please manually stop PID " + pidStr + " if ADMS cannot start.");
                        } else {
                            System.out.println("PushService: Current process is holding the port, server.stop() should handle it.");
                        }
                        running = false;
                        lastError = "Port occupied by PID " + pidStr;
                    }
                }
            }
            try (InputStream err = p.getErrorStream()) { err.readAllBytes(); } catch (Exception ignored) {}
        } catch (Exception e) {
            System.err.println("PushService: Failed to check port status: " + e.getMessage());
        }
    }

    public static boolean isRunning() {
        if (running) {
            lastError = null;
            return true;
        }
        try {
            // Use OS command to check if port is listening
            Process p = Runtime.getRuntime().exec("cmd /c netstat -ano | findstr LISTENING | findstr :" + PORT);
            boolean isFound;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                isFound = reader.readLine() != null;
            }
            try (InputStream err = p.getErrorStream()) { err.readAllBytes(); } catch (Exception ignored) {}
            if (isFound) lastError = null;
            return isFound;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isInternalRunning() {
        return running;
    }

    public static int getPort() {
        return PORT;
    }

    public static String getLastError() {
        return lastError;
    }

    public static boolean isUserStopped() {
        return userStopped;
    }

    static class CDataHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String method = exchange.getRequestMethod();
            String query = exchange.getRequestURI().getQuery();
            String path = exchange.getRequestURI().getPath();
            String ip = exchange.getRemoteAddress().getAddress().getHostAddress();
            
            Map<String, String> params = parseQuery(query);
            String sn = params.get("SN");
            String table = params.get("TABLE");
            String options = params.get("OPTIONS");

            // Check if device is registered in the devices table
            if (sn != null) {
                checkDeviceRegistered(sn);
            }

            if ("POST".equalsIgnoreCase(method)) {
                InputStream is = exchange.getRequestBody();
                byte[] bodyBytes = is.readAllBytes();
                int payloadSize = bodyBytes.length;
                String body = new String(bodyBytes, StandardCharsets.UTF_8);
                
                int insertedCount = 0;
                boolean triggeredAsync = false;

                if ("ATTLOG".equalsIgnoreCase(table)) {
                    insertedCount = processLogs(sn, body);
                    if (insertedCount > 0) {
                        triggeredAsync = true;
                    }
                    sendResponse(exchange, "OK");
                } else if ("OPERLOG".equalsIgnoreCase(table)) {
                    sendResponse(exchange, "OK");
                } else {
                    sendResponse(exchange, "OK");
                }

                logRequest(path, method, ip, sn, payloadSize, 
                           "ATTLOG".equalsIgnoreCase(table) ? insertedCount : null, 
                           "ATTLOG".equalsIgnoreCase(table) ? triggeredAsync : null);
            } else {
                // GET requests
                logRequest(path, method, ip, sn, 0, null, null);
                if ("all".equalsIgnoreCase(options)) {
                    // Device requesting configuration
                    String configResponse = "registry=ok\r\n" +
                            "RegistryCode=" + (sn != null ? sn : "JJA1253800527") + "\r\n" +
                            "ServerVersion=3.1.1\r\n" +
                            "ServerName=ADMS\r\n" +
                            "PushProtVer=2.4.0\r\n" +
                            "ErrorDelay=30\r\n" +
                            "Delay=10\r\n" +
                            "TransTimes=00:00;14:00\r\n" +
                            "TransInterval=1\r\n" +
                            "TransFlag=1111111111\r\n" +
                            "Realtime=1\r\n" +
                            "Encrypt=0\r\n";
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
            String method = exchange.getRequestMethod();
            String query = exchange.getRequestURI().getQuery();
            String path = exchange.getRequestURI().getPath();
            String ip = exchange.getRemoteAddress().getAddress().getHostAddress();

            Map<String, String> params = parseQuery(query);
            String sn = params.get("SN");

            if (sn != null) {
                checkDeviceRegistered(sn);
            }

            int payloadSize = 0;
            if ("POST".equalsIgnoreCase(method)) {
                InputStream is = exchange.getRequestBody();
                payloadSize = is.readAllBytes().length;
            }

            logRequest(path, method, ip, sn, payloadSize, null, null);
            sendResponse(exchange, "OK");
        }
    }

    static class DeviceCmdHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String method = exchange.getRequestMethod();
            String query = exchange.getRequestURI().getQuery();
            String path = exchange.getRequestURI().getPath();
            String ip = exchange.getRemoteAddress().getAddress().getHostAddress();

            Map<String, String> params = parseQuery(query);
            String sn = params.get("SN");

            if (sn != null) {
                checkDeviceRegistered(sn);
            }

            int payloadSize = 0;
            if ("POST".equalsIgnoreCase(method)) {
                InputStream is = exchange.getRequestBody();
                payloadSize = is.readAllBytes().length;
            }

            logRequest(path, method, ip, sn, payloadSize, null, null);
            sendResponse(exchange, "OK");
        }
    }

    private static int processLogs(String sn, String body) {
        if (sn == null || body == null) return 0;
        
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
                String uid = parts[0].trim();
                String timeStr = parts[1].trim();
                if (uid.isEmpty() || "0".equals(uid) || !uid.matches("\\d+")) {
                    System.out.println("PushService: Discarding invalid/failed attempt with UID: " + uid);
                    continue;
                }
                try {
                    int type = parts.length > 2 ? Integer.parseInt(parts[2]) : 0;
                    
                    // Clean time string (sometimes they have extra info)
                    if (timeStr.length() > 19) timeStr = timeStr.substring(0, 19);
                    
                    // Check if duplicate swipe exists in raw_logs within 1 minute (respecting punch_type and device_id)
                    List<Map<String, Object>> nearbyList = db.query(
                        "SELECT punch_time, punch_type FROM raw_logs WHERE emp_id = ? AND device_id = ? AND punch_time >= DATE_SUB(?, INTERVAL 1 MINUTE) AND punch_time <= DATE_ADD(?, INTERVAL 1 MINUTE)",
                        uid, deviceId, timeStr, timeStr
                    );
                    
                    boolean isDuplicate = false;
                    if (nearbyList != null && !nearbyList.isEmpty()) {
                        java.time.LocalDateTime incomingTime = java.time.LocalDateTime.parse(timeStr.replace(" ", "T"));
                        long minDiffSeconds = Long.MAX_VALUE;
                        int closestType = -1;
                        
                        for (Map<String, Object> nearby : nearbyList) {
                            Object pt = nearby.get("punch_time");
                            int exType = nearby.get("punch_type") != null ? (int) nearby.get("punch_type") : 0;
                            java.time.LocalDateTime exTime = null;
                            if (pt instanceof java.time.LocalDateTime) {
                                exTime = (java.time.LocalDateTime) pt;
                            } else if (pt instanceof java.sql.Timestamp) {
                                exTime = ((java.sql.Timestamp) pt).toLocalDateTime();
                            } else if (pt != null) {
                                try {
                                    exTime = java.time.LocalDateTime.parse(pt.toString().replace(" ", "T").split("\\.")[0]);
                                } catch (Exception ignored) {}
                            }
                            
                            if (exTime != null) {
                                long diff = Math.abs(java.time.Duration.between(exTime, incomingTime).getSeconds());
                                if (diff < minDiffSeconds) {
                                    minDiffSeconds = diff;
                                    closestType = exType;
                                }
                            }
                        }
                        
                        if (minDiffSeconds < 60 && closestType == type) {
                            isDuplicate = true;
                        }
                    }
                    
                    if (isDuplicate) {
                        System.out.println("PushService: Skipping duplicate swipe log for emp_id " + uid + " at " + timeStr + " (Type: " + type + ")");
                        continue;
                    }

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

        if (count > 0) {
            System.out.println("PushService: Triggering async raw log processing for new logs...");
            com.bhspl.service.SyncService.processRawLogsAsync();
        }
        return count;
    }

    private static int getDeviceId(String sn) {
        if (sn == null || sn.isEmpty()) return 0;
        try {
            Map<String, Object> dev = DatabaseManager.getInstance().fetchOne("SELECT device_id FROM devices WHERE serial_number=?", sn);
            if (dev != null) return (int) dev.get("device_id");
        } catch (Exception ignored) {}
        return 0;
    }

    private static void checkDeviceRegistered(String sn) {
        if (sn == null || sn.trim().isEmpty()) return;
        try {
            Map<String, Object> dev = DatabaseManager.getInstance().fetchOne("SELECT device_id FROM devices WHERE serial_number=?", sn);
            if (dev == null) {
                String warn = "WARNING: Device serial number '" + sn + "' is not registered in the 'devices' table!";
                System.err.println("PushService: " + warn);
                logToFile("PushService: " + warn);
            }
        } catch (Exception e) {
            System.err.println("PushService: Error querying devices for SN " + sn + ": " + e.getMessage());
        }
    }

    private static void logRequest(String endpoint, String method, String ip, String sn, int payloadSize, Integer insertedCount, Boolean triggeredAsync) {
        String msg = String.format("ADMS Request - Endpoint: %-18s | Method: %-4s | IP: %-15s | SN: %-15s | PayloadSize: %5d bytes",
                endpoint, method, ip, sn != null ? sn : "UNKNOWN", payloadSize);
        if (insertedCount != null) {
            msg += " | InsertedLogs: " + insertedCount;
        }
        if (triggeredAsync != null) {
            msg += " | TriggeredAsync: " + triggeredAsync;
        }
        System.out.println("PushService: " + msg);
        logToFile("PushService: " + msg);
    }

    private static void logToFile(String msg) {
        try (java.io.PrintWriter pw = new java.io.PrintWriter(new java.io.FileWriter("sync_debug.txt", true))) {
            pw.println("[" + new java.util.Date() + "] " + msg);
        } catch (Exception ignored) {}
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
            if (param.isEmpty()) continue;
            String[] entry = param.split("=");
            if (entry.length > 0) {
                String key = entry[0].trim().toUpperCase();
                String val = entry.length > 1 ? entry[1].trim() : "";
                result.put(key, val);
            }
        }
        return result;
    }
}
