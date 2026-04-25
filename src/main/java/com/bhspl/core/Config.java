package com.bhspl.core;

import java.io.*;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Map;

public class Config {

    private static String getConfigPath() {
        String appData = System.getenv("LOCALAPPDATA");
        if (appData == null || appData.isEmpty()) {
            appData = System.getenv("APPDATA");
        }
        if (appData == null || appData.isEmpty()) {
            appData = System.getProperty("user.home");
        }

        File cfgDir = new File(appData, "BHSPL_Attendance_Java");
        if (!cfgDir.exists()) {
            cfgDir.mkdirs();
        }

        return new File(cfgDir, "bhspl_config.ini").getAbsolutePath();
    }

    public static void saveDbConfig(String host, String port, String dbname, String user, String password) {
        String filePath = getConfigPath();
        try (PrintWriter writer = new PrintWriter(new FileWriter(filePath))) {
            writer.println("[DATABASE]");
            writer.println("host=" + host);
            writer.println("port=" + port);
            writer.println("database=" + dbname);
            writer.println("user=" + user);
            writer.println("password=" + password);
            writer.println("configured=1");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static Map<String, String> loadDbConfig() {
        File file = new File(getConfigPath());
        if (!file.exists()) {
            // Check for migration from old folder
            try {
                String appData = System.getenv("LOCALAPPDATA");
                if (appData == null || appData.isEmpty())
                    appData = System.getenv("APPDATA");
                if (appData == null || appData.isEmpty())
                    appData = System.getProperty("user.home");

                File oldFile = new File(new File(appData, "BHSPL_Attendance"), "bhspl_config.ini");
                if (oldFile.exists()) {
                    System.out.println("Config: Migrating configuration from legacy folder...");
                    Files.copy(oldFile.toPath(), file.toPath());
                }
            } catch (Exception e) {
                System.err.println("Config: Migration failed: " + e.getMessage());
            }
        }

        if (!file.exists()) {
            return null;
        }

        Map<String, String> config = new HashMap<>();
        boolean inDatabaseSection = false;

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith(";") || line.startsWith("#")) {
                    continue;
                }
                if (line.equals("[DATABASE]")) {
                    inDatabaseSection = true;
                    continue;
                } else if (line.startsWith("[")) {
                    inDatabaseSection = false;
                    continue;
                }

                if (inDatabaseSection && line.contains("=")) {
                    int eqIndex = line.indexOf('=');
                    String key = line.substring(0, eqIndex).trim();
                    String value = line.substring(eqIndex + 1).trim();
                    config.put(key, value);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }

        if (!"1".equals(config.get("configured"))) {
            return null;
        }

        return config;
    }

    public static void clearDbConfig() {
        File file = new File(getConfigPath());
        if (file.exists()) {
            file.delete();
        }
    }
}
