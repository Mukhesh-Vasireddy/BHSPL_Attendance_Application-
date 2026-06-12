package com.bhspl.db;

import java.io.*;
import java.util.*;

/**
 * Reads / writes bhspl_config.properties to
 * %LOCALAPPDATA%\BHSPL_Attendance\  (same location as the Python INI file).
 */
public class ConfigManager {

    private static final String APP_DIR  = "BHSPL_Attendance_Java";
    private static final String CFG_FILE = "bhspl_config.properties";

    private static File getConfigFile() {
        String base = System.getenv("LOCALAPPDATA");
        if (base == null) base = System.getenv("APPDATA");
        if (base == null) base = System.getProperty("user.home");
        File dir = new File(base, APP_DIR);
        dir.mkdirs();
        return new File(dir, CFG_FILE);
    }

    /** Save database settings to the properties file. */
    public static void save(String host, String port, String dbname,
                            String user, String password) throws IOException {
        Properties p = new Properties();
        p.setProperty("host",       host);
        p.setProperty("port",       port);
        p.setProperty("database",   dbname);
        p.setProperty("user",       user);
        p.setProperty("password",   password);
        p.setProperty("configured", "1");
        try (FileOutputStream fos = new FileOutputStream(getConfigFile())) {
            p.store(fos, "BHSPL Attendance DB Config");
        }
    }

    /**
     * Load saved settings. Returns null if not configured.
     * Includes automatic migration from old BHSPL_Attendance folder.
     */
    public static Map<String, String> load() {
        if (System.getenv("DB_HOST") != null) {
            Map<String, String> m = new HashMap<>();
            m.put("host", System.getenv("DB_HOST"));
            m.put("port", System.getenv("DB_PORT") != null ? System.getenv("DB_PORT") : "3306");
            m.put("database", System.getenv("DB_NAME") != null ? System.getenv("DB_NAME") : "bhspl_attendance");
            m.put("user", System.getenv("DB_USER") != null ? System.getenv("DB_USER") : "root");
            m.put("password", System.getenv("DB_PASSWORD") != null ? System.getenv("DB_PASSWORD") : "");
            return m;
        }

        File f = getConfigFile();
        if (!f.exists()) return null;
        Properties p = new Properties();
        try (FileInputStream fis = new FileInputStream(f)) {
            p.load(fis);
        } catch (IOException e) {
            return null;
        }
        if (!"1".equals(p.getProperty("configured"))) return null;
        Map<String, String> m = new HashMap<>();
        for (String key : new String[]{"host","port","database","user","password"}) {
            m.put(key, p.getProperty(key, ""));
        }
        return m;
    }

    /** Delete the config file (triggers re-setup on next launch). */
    public static void clear() {
        File f = getConfigFile();
        if (f.exists()) f.delete();
    }

    public static boolean isConfigured() {
        return load() != null;
    }

    public static String getProperty(String key, String defaultVal) {
        File f = getConfigFile();
        if (!f.exists()) return defaultVal;
        Properties p = new Properties();
        try (FileInputStream fis = new FileInputStream(f)) {
            p.load(fis);
            return p.getProperty(key, defaultVal);
        } catch (IOException e) {
            return defaultVal;
        }
    }

    public static void setProperty(String key, String value) {
        File f = getConfigFile();
        Properties p = new Properties();
        if (f.exists()) {
            try (FileInputStream fis = new FileInputStream(f)) {
                p.load(fis);
            } catch (IOException ignored) {}
        }
        p.setProperty(key, value);
        try (FileOutputStream fos = new FileOutputStream(f)) {
            p.store(fos, "BHSPL Attendance Config Update");
        } catch (IOException ignored) {}
    }
}
