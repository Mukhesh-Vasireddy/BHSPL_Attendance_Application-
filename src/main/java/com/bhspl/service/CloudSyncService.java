package com.bhspl.service;

import com.bhspl.db.ConfigManager;
import com.bhspl.db.DatabaseManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * Service to automatically synchronize local biometric punch logs (raw_logs)
 * with the central cloud attendance database.
 */
public class CloudSyncService {

    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private static final AtomicBoolean isSyncing = new AtomicBoolean(false);
    private static ScheduledFuture<?> periodicTask = null;

    private static void logToFile(String msg) {
        try (java.io.PrintWriter pw = new java.io.PrintWriter(new java.io.FileWriter("sync_debug.txt", true))) {
            pw.println("[" + new java.util.Date() + "] [CloudSync] " + msg);
        } catch (Exception ignored) {}
    }

    private static void logErrorToFile(String msg, Throwable t) {
        try (java.io.PrintWriter pw = new java.io.PrintWriter(new java.io.FileWriter("cloud_sync_errors.log", true))) {
            pw.println("[" + new java.util.Date() + "] ERROR: " + msg);
            if (t != null) {
                t.printStackTrace(pw);
            }
            pw.println("----------------------------------------");
        } catch (Exception ignored) {}
    }

    public static synchronized void start() {
        String enabled = ConfigManager.getProperty("cloud_sync_enabled", "false");
        if (!"true".equalsIgnoreCase(enabled)) {
            System.out.println("CloudSyncService: Cloud sync is disabled.");
            logToFile("Cloud sync is disabled.");
            if (periodicTask != null) {
                periodicTask.cancel(false);
                periodicTask = null;
            }
            return;
        }

        if (periodicTask != null) {
            System.out.println("CloudSyncService: Background task is already scheduled.");
            return;
        }

        System.out.println("CloudSyncService: Starting background synchronization...");
        logToFile("Starting background synchronization...");
        
        int retryInterval = 30; // default 30 seconds
        try {
            String ri = ConfigManager.getProperty("cloud_sync_retry_interval", "30");
            retryInterval = Integer.parseInt(ri.trim());
            if (retryInterval <= 0) retryInterval = 30;
        } catch (Exception ignored) {}
        
        periodicTask = scheduler.scheduleWithFixedDelay(CloudSyncService::syncPendingLogs, 5, retryInterval, TimeUnit.SECONDS);
    }

    public static synchronized void stop() {
        if (periodicTask != null) {
            periodicTask.cancel(false);
            periodicTask = null;
            System.out.println("CloudSyncService: Background task stopped.");
            logToFile("Background task stopped.");
        }
    }

    public static void triggerSync() {
        String enabled = ConfigManager.getProperty("cloud_sync_enabled", "false");
        if (!"true".equalsIgnoreCase(enabled)) {
            return;
        }
        scheduler.submit(CloudSyncService::syncPendingLogs);
    }

    public static void syncPendingLogs() {
        if (!isSyncing.compareAndSet(false, true)) {
            System.out.println("CloudSyncService: Sync already in progress, skipping.");
            return;
        }
        try {
            DatabaseManager db = DatabaseManager.getInstance();
            if (!db.isConnected()) {
                System.out.println("CloudSyncService: Database is offline, skipping.");
                return;
            }

            String apiUrl = ConfigManager.getProperty("cloud_sync_api_url", "http://103.174.161.68:9002/api/sync/cloud/sync-logs").trim();
            String apiKey = ConfigManager.getProperty("cloud_sync_api_key", "").trim();

            if (apiUrl.isEmpty()) {
                System.out.println("CloudSyncService: API Endpoint URL is empty. Please configure it.");
                return;
            }

            int batchSize = 100; // default 100 logs
            try {
                String bs = ConfigManager.getProperty("cloud_sync_batch_size", "100");
                batchSize = Integer.parseInt(bs.trim());
                if (batchSize <= 0) batchSize = 100;
            } catch (Exception ignored) {}

            // Fetch a batch of unsynced raw logs
            List<Map<String, Object>> unsynced = db.query(
                "SELECT r.id, r.emp_id, r.punch_time, r.punch_type, d.serial_number " +
                "FROM raw_logs r " +
                "LEFT JOIN devices d ON r.device_id = d.device_id " +
                "WHERE r.cloud_synced = 0 " +
                "ORDER BY r.id ASC " +
                "LIMIT " + batchSize
            );

            if (unsynced == null || unsynced.isEmpty()) {
                return;
            }

            System.out.println("CloudSyncService: Found " + unsynced.size() + " unsynced logs. Syncing...");
            logToFile("Syncing batch of " + unsynced.size() + " logs to " + apiUrl);

            // Construct JSON
            String jsonPayload = buildJsonPayload(unsynced);

            // Send via HTTP Client
            HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                System.out.println("CloudSyncService: Sync successful! Server responded: " + response.body());
                logToFile("Sync batch successful.");
                
                // Mark in DB as synced
                List<String> ids = unsynced.stream()
                    .map(log -> log.get("id").toString())
                    .collect(Collectors.toList());
                String idList = String.join(",", ids);
                
                db.execute("UPDATE raw_logs SET cloud_synced = 1 WHERE id IN (" + idList + ")");
                
                // If we synced a full batch, there might be more, trigger immediately again
                if (unsynced.size() == batchSize) {
                    triggerSync();
                }
            } else {
                String errMsg = "HTTP error code " + response.statusCode() + " | Response: " + response.body();
                System.err.println("CloudSyncService: Sync failed. " + errMsg);
                logErrorToFile(errMsg, null);
                logToFile("Sync failed: " + errMsg);
            }

        } catch (Exception e) {
            System.err.println("CloudSyncService: Error during cloud sync: " + e.getMessage());
            logErrorToFile(e.getMessage(), e);
            logToFile("Sync failed with exception: " + e.getMessage());
        } finally {
            isSyncing.set(false);
        }
    }

    private static String buildJsonPayload(List<Map<String, Object>> logs) {
        StringBuilder json = new StringBuilder();
        json.append("[");
        for (int i = 0; i < logs.size(); i++) {
            Map<String, Object> log = logs.get(i);
            String empId = (String) log.get("emp_id");
            String timeStr = formatPunchTime(log.get("punch_time"));
            int type = (log.get("punch_type") != null) ? (int) log.get("punch_type") : 0;
            String sn = (String) log.get("serial_number");
            if (sn == null) sn = "UNKNOWN";

            if (i > 0) json.append(",");
            json.append("{");
            json.append("\"emp_id\":\"").append(escapeJson(empId)).append("\",");
            json.append("\"punch_time\":\"").append(escapeJson(timeStr)).append("\",");
            json.append("\"punch_type\":").append(type).append(",");
            json.append("\"device_sn\":\"").append(escapeJson(sn)).append("\"");
            json.append("}");
        }
        json.append("]");
        return json.toString();
    }

    private static String formatPunchTime(Object pt) {
        if (pt == null) return null;
        if (pt instanceof java.time.LocalDateTime) {
            return ((java.time.LocalDateTime) pt).format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        } else if (pt instanceof java.sql.Timestamp) {
            return ((java.sql.Timestamp) pt).toLocalDateTime().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        } else {
            return pt.toString().replace("T", " ").split("\\.")[0];
        }
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
