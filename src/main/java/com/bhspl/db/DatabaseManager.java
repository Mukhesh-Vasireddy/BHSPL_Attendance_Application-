package com.bhspl.db;

import com.bhspl.util.HashUtil;
import com.mysql.cj.jdbc.exceptions.CommunicationsException;
import java.sql.*;
import java.util.*;

/**
 * JDBC database manager — port of Python DatabaseManager class.
 * Manages connection, all table creation, seeds, migrations, and execute/query helpers.
 */
public class DatabaseManager {

    // Singleton
    public static final DatabaseManager INSTANCE = new DatabaseManager();

    public static DatabaseManager getInstance() {
        return INSTANCE;
    }

    public String hashPw(String pw) {
        return com.bhspl.util.HashUtil.sha256(pw);
    }

    private Connection conn;
    private Map<String, String> cfg;

    private DatabaseManager() {}

    // ── Connection ────────────────────────────────────────────────────────────
    public boolean connect(String host, String port, String user,
                           String password, String database) throws SQLException {
        try {
            cfg = new HashMap<>();
            cfg.put("host", host); cfg.put("port", port);
            cfg.put("user", user); cfg.put("password", password);
            cfg.put("database", database);

            String url = String.format(
                "jdbc:mysql://%s:%s/%s?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Kolkata&autoReconnect=true&characterEncoding=UTF-8&useUnicode=true&connectTimeout=5000",
                host, port, database);
            conn = DriverManager.getConnection(url, user, password);
            conn.setAutoCommit(false);
            createTables();
            migrateWeeklyOffs();
            conn.commit();
            return true;
        } catch (SQLException e) {
            throw e;
        } catch (Exception e) {
            throw new SQLException(e);
        }
    }

    private void migrateWeeklyOffs() {
        try {
            DatabaseMetaData md = conn.getMetaData();
            ResultSet rs = md.getColumns(null, null, "weekly_offs", "off_day1");
            if (!rs.next()) {
                // Check if the table exists at all
                ResultSet rs2 = md.getTables(null, null, "weekly_offs", null);
                if (rs2.next()) {
                    System.out.println("Legacy weekly_offs table detected. Migrating...");
                    execute("DROP TABLE weekly_offs");
                    // Re-run createTables specifically for this table
                    execute("CREATE TABLE weekly_offs (" +
                            "  id            INT AUTO_INCREMENT PRIMARY KEY," +
                            "  emp_id        VARCHAR(20)," +
                            "  off_day1      VARCHAR(10) DEFAULT 'Sunday'," +
                            "  off_day2      VARCHAR(10) DEFAULT 'None'," +
                            "  effective_from DATE," +
                            "  effective_to  DATE," +
                            "  remarks       VARCHAR(200)," +
                            "  created_at    TIMESTAMP  DEFAULT CURRENT_TIMESTAMP," +
                            "  FOREIGN KEY (emp_id) REFERENCES employees(emp_id) ON DELETE CASCADE" +
                            ")");
                }
            }
        } catch (Exception e) {
            System.err.println("Migration failed: " + e.getMessage());
        }
    }

    private void reconnect() throws Exception {
        Class.forName("com.mysql.cj.jdbc.Driver");
        String url = String.format(
            "jdbc:mysql://%s:%s/%s?useSSL=false&allowPublicKeyRetrieval=true" +
            "&serverTimezone=Asia/Kolkata&autoReconnect=true" +
            "&characterEncoding=UTF-8&useUnicode=true",
            cfg.get("host"), cfg.get("port"), cfg.get("database"));
        conn = DriverManager.getConnection(url, cfg.get("user"), cfg.get("password"));
        conn.setAutoCommit(false);
    }

    public boolean isConnected() {
        try { return conn != null && !conn.isClosed(); }
        catch (SQLException e) { return false; }
    }

    public void close() {
        try { if (conn != null) conn.close(); } catch (Exception ignored) {}
    }

    // ── Table creation ────────────────────────────────────────────────────────
    private void createTables() throws Exception {
        String[] tables = {
            // employees
            "CREATE TABLE IF NOT EXISTS employees (" +
            "  emp_id        VARCHAR(20)  PRIMARY KEY," +
            "  emp_name      VARCHAR(100) NOT NULL," +
            "  dob           DATE," +
            "  doj           DATE," +
            "  gender        VARCHAR(10)," +
            "  email         VARCHAR(100)," +
            "  phone         VARCHAR(15)," +
            "  department    VARCHAR(50)," +
            "  designation   VARCHAR(50)," +
            "  shift         VARCHAR(30)  DEFAULT 'General'," +
            "  blood_group   VARCHAR(5)," +
            "  address       TEXT," +
            "  emergency_contact VARCHAR(15)," +
            "  bank_account  VARCHAR(20)," +
            "  pan_number    VARCHAR(15)," +
            "  aadhaar       VARCHAR(12)," +
            "  basic_salary  DECIMAL(10,2) DEFAULT 0," +
            "  status        VARCHAR(10)  DEFAULT 'Active'," +
            "  photo_path    VARCHAR(255)," +
            "  device_id     INT          DEFAULT 0," +
            "  finger_id     INT          DEFAULT 0," +
            "  device_enroll_id VARCHAR(20) DEFAULT NULL," +
            "  created_at    TIMESTAMP    DEFAULT CURRENT_TIMESTAMP," +
            "  updated_at    TIMESTAMP    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP" +
            ")",

            // attendance
            "CREATE TABLE IF NOT EXISTS attendance (" +
            "  id            INT AUTO_INCREMENT PRIMARY KEY," +
            "  emp_id        VARCHAR(20)," +
            "  punch_date    DATE," +
            "  in_time       DATETIME," +
            "  out_time      DATETIME," +
            "  work_hours    DECIMAL(5,2) DEFAULT 0," +
            "  overtime      DECIMAL(5,2) DEFAULT 0," +
            "  status        VARCHAR(20)  DEFAULT 'Present'," +
            "  late_mins     INT          DEFAULT 0," +
            "  early_mins    INT          DEFAULT 0," +
            "  punch_type    VARCHAR(20)  DEFAULT 'Device'," +
            "  remarks       VARCHAR(200)," +
            "  UNIQUE KEY uq_emp_in_time (emp_id, in_time)," +
            "  FOREIGN KEY (emp_id) REFERENCES employees(emp_id) ON DELETE CASCADE" +
            ")",

            // devices
            "CREATE TABLE IF NOT EXISTS devices (" +
            "  device_id     INT AUTO_INCREMENT PRIMARY KEY," +
            "  device_name   VARCHAR(100)," +
            "  ip_address    VARCHAR(20)," +
            "  port          INT DEFAULT 4370," +
            "  serial_number VARCHAR(50)," +
            "  location      VARCHAR(100)," +
            "  status        VARCHAR(20) DEFAULT 'Active'," +
            "  comm_password INT DEFAULT 0," +
            "  last_sync     DATETIME," +
            "  created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
            ")",

            // leaves
            "CREATE TABLE IF NOT EXISTS leaves (" +
            "  id            INT AUTO_INCREMENT PRIMARY KEY," +
            "  emp_id        VARCHAR(20)," +
            "  leave_type    VARCHAR(30)," +
            "  from_date     DATE," +
            "  to_date       DATE," +
            "  days          INT," +
            "  reason        TEXT," +
            "  status        VARCHAR(20) DEFAULT 'Pending'," +
            "  applied_on    TIMESTAMP  DEFAULT CURRENT_TIMESTAMP," +
            "  approved_by   VARCHAR(100)," +
            "  FOREIGN KEY (emp_id) REFERENCES employees(emp_id) ON DELETE CASCADE" +
            ")",

            // holidays
            "CREATE TABLE IF NOT EXISTS holidays (" +
            "  id            INT AUTO_INCREMENT PRIMARY KEY," +
            "  holiday_date  DATE UNIQUE," +
            "  holiday_name  VARCHAR(100)," +
            "  holiday_type  VARCHAR(30) DEFAULT 'National'" +
            ")",

            // users
            "CREATE TABLE IF NOT EXISTS users (" +
            "  id            INT AUTO_INCREMENT PRIMARY KEY," +
            "  username      VARCHAR(50) UNIQUE," +
            "  password_hash VARCHAR(64)," +
            "  role          VARCHAR(20) DEFAULT 'Operator'," +
            "  emp_id        VARCHAR(20)," +
            "  last_login    DATETIME," +
            "  status        VARCHAR(10) DEFAULT 'Active'" +
            ")",

            // raw_logs
            "CREATE TABLE IF NOT EXISTS raw_logs (" +
            "  id            INT AUTO_INCREMENT PRIMARY KEY," +
            "  device_id     INT," +
            "  emp_id        VARCHAR(20)," +
            "  punch_time    DATETIME," +
            "  punch_type    INT  DEFAULT 0," +
            "  synced        TINYINT DEFAULT 0," +
            "  created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
            "  UNIQUE KEY uq_emp_time (emp_id, punch_time)" +
            ")",

            // departments
            "CREATE TABLE IF NOT EXISTS departments (" +
            "  id            INT AUTO_INCREMENT PRIMARY KEY," +
            "  dept_name     VARCHAR(100) UNIQUE NOT NULL," +
            "  dept_code     VARCHAR(20)," +
            "  head_name     VARCHAR(100)," +
            "  description   VARCHAR(200)," +
            "  status        VARCHAR(10) DEFAULT 'Active'," +
            "  created_at    TIMESTAMP  DEFAULT CURRENT_TIMESTAMP" +
            ")",

            // designations
            "CREATE TABLE IF NOT EXISTS designations (" +
            "  id            INT AUTO_INCREMENT PRIMARY KEY," +
            "  desig_name    VARCHAR(100) UNIQUE NOT NULL," +
            "  level_order   INT DEFAULT 99," +
            "  description   VARCHAR(200)," +
            "  status        VARCHAR(10) DEFAULT 'Active'," +
            "  created_at    TIMESTAMP  DEFAULT CURRENT_TIMESTAMP" +
            ")",

            // shifts
            "CREATE TABLE IF NOT EXISTS shifts (" +
            "  id            INT AUTO_INCREMENT PRIMARY KEY," +
            "  shift_name    VARCHAR(100) UNIQUE NOT NULL," +
            "  start_time    TIME NOT NULL," +
            "  end_time      TIME NOT NULL," +
            "  break_mins    INT  DEFAULT 30," +
            "  grace_mins    INT  DEFAULT 5," +
            "  weekly_off1   VARCHAR(10) DEFAULT 'Sunday'," +
            "  weekly_off2   VARCHAR(10) DEFAULT 'None'," +
            "  work_hours    DECIMAL(4,2) DEFAULT 8.00," +
            "  overtime_after DECIMAL(4,2) DEFAULT 9.00," +
            "  status        VARCHAR(10) DEFAULT 'Active'," +
            "  created_at    TIMESTAMP  DEFAULT CURRENT_TIMESTAMP" +
            ")",

            // weekly_offs
            "CREATE TABLE IF NOT EXISTS weekly_offs (" +
            "  id            INT AUTO_INCREMENT PRIMARY KEY," +
            "  emp_id        VARCHAR(20)," +
            "  off_day1      VARCHAR(10) DEFAULT 'Sunday'," +
            "  off_day2      VARCHAR(10) DEFAULT 'None'," +
            "  effective_from DATE," +
            "  effective_to  DATE," +
            "  remarks       VARCHAR(200)," +
            "  created_at    TIMESTAMP  DEFAULT CURRENT_TIMESTAMP," +
            "  FOREIGN KEY (emp_id) REFERENCES employees(emp_id) ON DELETE CASCADE" +
            ")",

            // leave_policy
            "CREATE TABLE IF NOT EXISTS leave_policy (" +
            "  id              INT AUTO_INCREMENT PRIMARY KEY," +
            "  leave_type      VARCHAR(30) UNIQUE NOT NULL," +
            "  days_per_year   DECIMAL(5,1) DEFAULT 0," +
            "  credit_method   VARCHAR(20)  DEFAULT 'Yearly'," +
            "  carry_forward   TINYINT      DEFAULT 0," +
            "  max_carry       DECIMAL(5,1) DEFAULT 0," +
            "  expire_months   INT          DEFAULT 0," +
            "  encashable      TINYINT      DEFAULT 0," +
            "  applicable_gender VARCHAR(10) DEFAULT 'All'," +
            "  min_service_days INT         DEFAULT 0," +
            "  description     VARCHAR(200)," +
            "  status          VARCHAR(10)  DEFAULT 'Active'," +
            "  pro_rata        TINYINT      DEFAULT 1," +
            "  created_at      TIMESTAMP    DEFAULT CURRENT_TIMESTAMP" +
            ")",

            // leave_balance
            "CREATE TABLE IF NOT EXISTS leave_balance (" +
            "  id              INT AUTO_INCREMENT PRIMARY KEY," +
            "  emp_id          VARCHAR(20)," +
            "  leave_type      VARCHAR(30)," +
            "  year            INT," +
            "  opening_bal     DECIMAL(5,1) DEFAULT 0," +
            "  credited        DECIMAL(5,1) DEFAULT 0," +
            "  carry_fwd       DECIMAL(5,1) DEFAULT 0," +
            "  used            DECIMAL(5,1) DEFAULT 0," +
            "  lapsed          DECIMAL(5,1) DEFAULT 0," +
            "  closing_bal     DECIMAL(5,1) DEFAULT 0," +
            "  last_updated    TIMESTAMP    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP," +
            "  UNIQUE KEY uq_emp_lt_yr (emp_id, leave_type, year)," +
            "  FOREIGN KEY (emp_id) REFERENCES employees(emp_id) ON DELETE CASCADE" +
            ")",

            // od_requests
            "CREATE TABLE IF NOT EXISTS od_requests (" +
            "  id              INT AUTO_INCREMENT PRIMARY KEY," +
            "  emp_id          VARCHAR(20)," +
            "  od_from         DATE NOT NULL," +
            "  od_to           DATE NOT NULL," +
            "  od_days         DECIMAL(5,2) DEFAULT 1.0," +
            "  from_time       TIME," +
            "  to_time         TIME," +
            "  od_type         VARCHAR(30) DEFAULT 'Full Day'," +
            "  location        VARCHAR(100)," +
            "  purpose         TEXT," +
            "  reason          TEXT," +
            "  status          VARCHAR(20) DEFAULT 'Pending'," +
            "  applied_on      TIMESTAMP   DEFAULT CURRENT_TIMESTAMP," +
            "  approved_by     VARCHAR(100)," +
            "  approved_on     DATETIME," +
            "  remarks         VARCHAR(200)," +
            "  FOREIGN KEY (emp_id) REFERENCES employees(emp_id) ON DELETE CASCADE" +
            ")"
        };

        try (Statement st = conn.createStatement()) {
            for (String sql : tables) {
                st.execute(sql);
            }
        }

        seedDefaults();
        runMigrations();
    }

    private void seedDefaults() throws Exception {
        // Default admin user
        long cnt = queryLong("SELECT COUNT(*) FROM users");
        if (cnt == 0) {
            String ph = HashUtil.sha256("admin123");
            execute("INSERT IGNORE INTO users (username,password_hash,role) VALUES (?,?,?)",
                    "admin", ph, "Admin");
        }

        // Default departments
        if (queryLong("SELECT COUNT(*) FROM departments") == 0) {
            String[] depts = {"Administration","Accounts & Finance","Human Resources",
                "Information Technology","Operations","Sales & Marketing",
                "Production","Quality Control","Stores & Purchase",
                "Security","Housekeeping","Management"};
            for (String d : depts) {
                execute("INSERT IGNORE INTO departments (dept_name) VALUES (?)", d);
            }
        }

        // Default designations
        if (queryLong("SELECT COUNT(*) FROM designations") == 0) {
            Object[][] desigs = {
                {"Managing Director", 1}, {"Director", 2}, {"General Manager", 3},
                {"Manager", 4}, {"Deputy Manager", 5}, {"Assistant Manager", 6},
                {"Senior Executive", 7}, {"Executive", 8}, {"Senior Officer", 9},
                {"Officer", 10}, {"Junior Officer", 11}, {"Supervisor", 12},
                {"Senior Technician", 13}, {"Technician", 14}, {"Helper", 15},
                {"Trainee", 16}, {"Intern", 17}, {"Consultant", 18}
            };
            for (Object[] d : desigs) {
                execute("INSERT IGNORE INTO designations (desig_name,level_order) VALUES (?,?)",
                        d[0], d[1]);
            }
        }

        // Default shifts
        if (queryLong("SELECT COUNT(*) FROM shifts") == 0) {
            Object[][] shifts = {
                {"General",          "09:00:00","18:00:00", 30,10,"Sunday","None",   8.00,9.00},
                {"Morning",          "06:00:00","14:00:00", 30,10,"Sunday","None",   8.00,9.00},
                {"Afternoon",        "14:00:00","22:00:00", 30,10,"Sunday","None",   8.00,9.00},
                {"Night",            "22:00:00","06:00:00", 30,10,"Sunday","None",   8.00,9.00},
                {"Half Day AM",      "09:00:00","13:00:00",  0, 5,"Sunday","None",   4.00,5.00},
                {"Half Day PM",      "14:00:00","18:00:00",  0, 5,"Sunday","None",   4.00,5.00},
                {"Extended General", "09:00:00","19:00:00", 60,10,"Sunday","None",   9.00,10.00},
                {"Weekend",          "10:00:00","17:00:00", 30,10,"Saturday","Sunday",6.00,7.00},
            };
            String sql = "INSERT IGNORE INTO shifts " +
                "(shift_name,start_time,end_time,break_mins,grace_mins," +
                "weekly_off1,weekly_off2,work_hours,overtime_after) VALUES (?,?,?,?,?,?,?,?,?)";
            for (Object[] s : shifts) execute(sql, s);
        }

        // Default leave policies
        if (queryLong("SELECT 1 FROM leave_policy LIMIT 1") == 0) {
            Object[][] policies = {
                {"Casual Leave",   10,"Yearly",  0,  0,12,0,"All",  0, "10 days per year."},
                {"Sick Leave",     12,"Yearly",  0,  0,12,0,"All",  0, "12 days per year."},
                {"Earned Leave",   15,"Yearly",  1, 30, 0,1,"All", 90, "15 days EL. Carry max 30."},
                {"Comp Off",        0,"Manual",  1,  5, 3,0,"All",  0, "Comp off."},
                {"Maternity Leave",90,"Manual",  0,  0, 0,0,"Female",80,"90 days."},
                {"Paternity Leave", 5,"Manual",  0,  0, 0,0,"Male", 80,"5 days."},
                {"LWP",             0,"Manual",  0,  0, 0,0,"All",  0, "Leave Without Pay."},
            };
            String sql = "INSERT IGNORE INTO leave_policy " +
                "(leave_type,days_per_year,credit_method,carry_forward,max_carry," +
                "expire_months,encashable,applicable_gender,min_service_days,description) " +
                "VALUES (?,?,?,?,?,?,?,?,?,?)";
            for (Object[] p : policies) execute(sql, p);
        }

        conn.commit();
    }

    private void runMigrations() {
        Object[][] migrations = {
            {"employees",  "device_enroll_id", "VARCHAR(20) DEFAULT NULL"},
            {"shifts",     "min_present_mins",  "INT DEFAULT 480"},
            {"shifts",     "half_day_mins",      "INT DEFAULT 240"},
            {"attendance", "early_out_mins",     "INT DEFAULT 0"},
            {"devices",    "comm_password",      "INT DEFAULT 0"},
            {"devices",    "last_error",         "VARCHAR(255)"},
            {"users",      "status",             "VARCHAR(10) DEFAULT 'Active'"},
            {"attendance", "in_time",             "DATETIME"}, // Placeholder to trigger migration logic if needed
            {"od_requests", "od_days",            "DECIMAL(5,2) DEFAULT 1.0"},
        };
        
        // Custom migration to drop old attendance unique key if present
        try {
            // Check if uq_emp_date exists
            Map<String, Object> idx = fetchOne("SHOW INDEX FROM attendance WHERE Key_name = 'uq_emp_date'");
            if (idx != null) {
                System.out.println("Database: Found legacy 'uq_emp_date' constraint. Dropping it to support multiple sessions...");
                execute("ALTER TABLE attendance DROP INDEX uq_emp_date");
                System.out.println("Database: Migrated attendance table to support multiple sessions per day.");
            }
        } catch (Exception ignored) {}
        
        for (Object[] m : migrations) {
            try {
                execute("ALTER TABLE `" + m[0] + "` ADD COLUMN `" + m[1] + "` " + m[2]);
                conn.commit();
            } catch (Exception ignored) {
                // Column may exist, try to MODIFY if it's a type change
                if ("od_requests".equals(m[0]) && "od_days".equals(m[1])) {
                    try {
                        execute("ALTER TABLE od_requests MODIFY COLUMN od_days DECIMAL(5,2) DEFAULT 1.0");
                        conn.commit();
                    } catch (Exception e2) {}
                }
                try { conn.rollback(); } catch (Exception e2) {}
            }
        }

        // Optimized Update Unique index to raw_logs: deduplicate across devices (exclude device_id)
        try {
            boolean indexExists = false;
            try {
                Map<String, Object> check = fetchOne("SHOW INDEX FROM raw_logs WHERE Key_name = 'uq_emp_time'");
                indexExists = (check != null);
            } catch (Exception ignored) {}

            if (!indexExists) {
                System.out.println("Database: Optimizing log table and removing duplicates (this may take a minute)...");
                execute("CREATE TABLE IF NOT EXISTS raw_logs_temp LIKE raw_logs");
                try { execute("ALTER TABLE raw_logs_temp DROP INDEX uq_dev_emp_time"); } catch(Exception ignored){}
                try { execute("ALTER TABLE raw_logs_temp ADD UNIQUE KEY uq_emp_time (emp_id, punch_time)"); } catch(Exception ignored){}
                
                // Copy data removing duplicates
                execute("INSERT IGNORE INTO raw_logs_temp SELECT * FROM raw_logs");
                
                // Swap tables
                execute("DROP TABLE raw_logs");
                execute("RENAME TABLE raw_logs_temp TO raw_logs");
                System.out.println("Database: Log table deduplicated and optimized successfully.");
            }
            conn.commit();
        } catch (Exception e) {
            System.err.println("Database Migration Warning: " + e.getMessage());
            try { conn.rollback(); } catch (Exception ignored) {}
        }
    }

    // ── Query helpers ─────────────────────────────────────────────────────────

    /** Execute a DML statement (INSERT/UPDATE/DELETE) and return affected rows. */
    public synchronized int execute(String sql, Object... params) throws SQLException {
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                if (!isConnected()) reconnect();
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    setParams(ps, params);
                    int affected = ps.executeUpdate();
                    if (!conn.getAutoCommit()) conn.commit();
                    return affected;
                }
            } catch (SQLException e) {
                if (attempt == 0 && (e instanceof SQLRecoverableException || e instanceof CommunicationsException)) {
                    try { reconnect(); } catch (Exception ignored) {}
                    continue;
                }
                throw e;
            } catch (Exception e) {
                throw new SQLException(e);
            }
        }
        return 0;
    }

    public synchronized void setAutoCommit(boolean val) throws SQLException {
        if (!isConnected()) try { reconnect(); } catch(Exception e) { throw new SQLException(e); }
        if (conn.getAutoCommit() == val) return;
        conn.setAutoCommit(val);
    }

    public synchronized void commit() throws SQLException {
        if (isConnected() && !conn.getAutoCommit()) {
            conn.commit();
        }
    }

    public synchronized void rollback() {
        try { if (isConnected()) conn.rollback(); } catch(Exception ignored) {}
    }

    /** Execute and return list of rows as List<Map<col,value>>. */
    public synchronized List<Map<String, Object>> query(String sql, Object... params) throws SQLException {
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                if (!isConnected()) reconnect();
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    setParams(ps, params);
                    try (ResultSet rs = ps.executeQuery()) {
                        ResultSetMetaData meta = rs.getMetaData();
                        int cols = meta.getColumnCount();
                        List<Map<String, Object>> rows = new ArrayList<>();
                        while (rs.next()) {
                            Map<String, Object> row = new LinkedHashMap<>();
                            for (int i = 1; i <= cols; i++) {
                                row.put(meta.getColumnLabel(i), rs.getObject(i));
                            }
                            rows.add(row);
                        }
                        return rows;
                    }
                }
            } catch (SQLException e) {
                if (attempt == 0 && (e instanceof SQLRecoverableException || e instanceof CommunicationsException)) {
                    try { reconnect(); } catch (Exception ignored) {}
                    continue;
                }
                throw e;
            } catch (Exception e) {
                throw new SQLException(e);
            }
        }
        return Collections.emptyList();
    }

    /** Alias for query() to support migration from core package. */
    public List<Map<String, Object>> fetchAll(String sql, Object... params) throws SQLException {
        return query(sql, params);
    }

    /** Execute and return first row only. */
    public synchronized Map<String, Object> queryOne(String sql, Object... params) throws SQLException {
        List<Map<String, Object>> rows = query(sql, params);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** Alias for queryOne() to support migration from core package. */
    public Map<String, Object> fetchOne(String sql, Object... params) throws SQLException {
        return queryOne(sql, params);
    }

    /** Execute and return single long value (COUNT, etc). */
    public long queryLong(String sql, Object... params) {
        try {
            Map<String, Object> row = queryOne(sql, params);
            if (row == null) return 0;
            Object val = row.values().iterator().next();
            return val == null ? 0 : ((Number) val).longValue();
        } catch (Exception e) {
            return 0;
        }
    }

    private void setParams(PreparedStatement ps, Object[] params) throws SQLException {
        if (params == null) return;
        for (int i = 0; i < params.length; i++) {
            ps.setObject(i + 1, params[i]);
        }
    }

    /** Convenience: safe get string from row map */
    public static String str(Map<String, Object> row, String key) {
        Object v = row == null ? null : row.get(key);
        return v == null ? "" : v.toString();
    }

    /** Convenience: safe get double from row map */
    public static double dbl(Map<String, Object> row, String key) {
        Object v = row == null ? null : row.get(key);
        if (v == null) return 0.0;
        if (v instanceof Number) return ((Number) v).doubleValue();
        try { return Double.parseDouble(v.toString()); } catch (Exception e) { return 0.0; }
    }

    /** Convenience: safe get int from row map */
    public static int num(Map<String, Object> row, String key) {
        Object v = row.get(key);
        if (v == null) return 0;
        if (v instanceof Number) return ((Number) v).intValue();
        try { return Integer.parseInt(v.toString()); } catch (Exception e) { return 0; }
    }

    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("Usage: DatabaseManager \"SELECT ...\"");
            return;
        }
        try {
            java.util.Map<String, String> config = com.bhspl.core.Config.loadDbConfig();
            if (config == null) {
                System.out.println("Config not found!");
                return;
            }
            DatabaseManager db = DatabaseManager.getInstance();
            db.connect(config.get("host"), config.get("port"), config.get("user"), config.get("password"), config.get("database"));
            
            List<Map<String, Object>> results = db.query(args[0]);
            System.out.println("--- Results (" + results.size() + ") ---");
            for (Map<String, Object> row : results) {
                System.out.println(row);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
