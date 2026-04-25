package com.bhspl.ui;

import com.bhspl.db.DatabaseManager;
import com.bhspl.util.UIHelper;
import com.bhspl.util.ZkProtocol;

import javax.swing.*;
import java.awt.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Main sync dialog for fetching logs from devices and processing them.
 */
public class SyncDialog extends JDialog {

    private final DatabaseManager db = DatabaseManager.INSTANCE;
    private JComboBox<String> deviceCombo, protocolCombo;
    private JTextField timeoutField, pwdField, dateFromField;
    private JCheckBox clearLogsCheck;
    private JTextArea logBox;
    private JProgressBar progress;
    private JLabel statusLabel;
    private JButton syncBtn;
    
    private Map<String, Map<String, Object>> deviceMap = new HashMap<>();
    private final DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");
    private boolean running = false;
    private boolean alive = true;

    public SyncDialog(JFrame parent) {
        super(parent, "Sync Device Logs", true);
        setSize(700, 620);
        UIHelper.centerWindow(this, 700, 620);
        buildUI();
        loadDevices();
        setVisible(true);
    }

    private void buildUI() {
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(UIHelper.BG_CARD);

        // Header
        JPanel hdr = new JPanel();
        hdr.setBackground(new Color(0x00695c));
        hdr.setPreferredSize(new Dimension(0, 50));
        JLabel title = new JLabel("Sync Device Attendance Logs");
        title.setFont(new Font("Segoe UI", Font.BOLD, 14));
        title.setForeground(Color.WHITE);
        hdr.add(title);
        root.add(hdr, BorderLayout.NORTH);

        // Side panel for options
        JPanel side = new JPanel();
        side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));
        side.setPreferredSize(new Dimension(220, 0));
        side.setBackground(new Color(0xf0f4f8));
        side.setBorder(BorderFactory.createEmptyBorder(10, 15, 10, 15));

        addSideLabel(side, "Device");
        deviceCombo = new JComboBox<>();
        side.add(deviceCombo);

        addSideLabel(side, "Protocol");
        protocolCombo = new JComboBox<>(new String[]{"UDP (default)", "TCP"});
        side.add(protocolCombo);

        addSideLabel(side, "Timeout (sec)");
        timeoutField = new JTextField("30");
        side.add(timeoutField);

        addSideLabel(side, "Password");
        pwdField = new JTextField("0");
        side.add(pwdField);

        addSideLabel(side, "Fetch From");
        dateFromField = new JTextField(LocalDate.now().withDayOfMonth(1).format(DateTimeFormatter.ofPattern("dd-MM-yyyy")));
        side.add(dateFromField);
        JLabel dHint = new JLabel("DD-MM-YYYY  (blank=all)");
        dHint.setFont(new Font("Segoe UI", Font.PLAIN, 10)); dHint.setForeground(Color.GRAY);
        side.add(dHint);

        side.add(Box.createVerticalStrut(15));
        clearLogsCheck = new JCheckBox("Clear device logs after sync");
        clearLogsCheck.setOpaque(false);
        side.add(clearLogsCheck);

        side.add(Box.createVerticalStrut(20));
        JButton diagBtn = UIHelper.makeButton("Diagnose Device", new Color(0x4a148c));
        diagBtn.addActionListener(e -> diagnose());
        side.add(diagBtn);

        root.add(side, BorderLayout.WEST);

        // Main log area
        JPanel main = new JPanel(new BorderLayout());
        main.setBackground(UIHelper.BG_CARD);
        main.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        logBox = new JTextArea();
        logBox.setBackground(new Color(0x0d1117));
        logBox.setForeground(new Color(0x58e16f));
        logBox.setFont(new Font("Consolas", Font.PLAIN, 12));
        logBox.setEditable(false);
        main.add(new JScrollPane(logBox), BorderLayout.CENTER);

        JPanel bot = new JPanel(new BorderLayout(5, 5));
        bot.setOpaque(false);
        progress = new JProgressBar();
        bot.add(progress, BorderLayout.NORTH);
        statusLabel = new JLabel("Ready");
        statusLabel.setFont(new Font("Segoe UI", Font.BOLD, 11));
        bot.add(statusLabel, BorderLayout.WEST);
        
        syncBtn = UIHelper.makeButton("Start Sync", new Color(0x00695c));
        syncBtn.addActionListener(e -> startSync());
        bot.add(syncBtn, BorderLayout.EAST);
        
        main.add(bot, BorderLayout.SOUTH);
        root.add(main, BorderLayout.CENTER);

        setContentPane(root);
        log("========================================\n" +
            "  BHSPL - Device Sync Ready\n" +
            "========================================\n" +
            "Configure options and click Start Sync.");
    }

    private void addSideLabel(JPanel p, String text) {
        JLabel l = new JLabel(text);
        l.setFont(new Font("Segoe UI", Font.BOLD, 11));
        l.setBorder(BorderFactory.createEmptyBorder(10, 0, 4, 0));
        p.add(l);
    }

    private void log(String msg) {
        SwingUtilities.invokeLater(() -> {
            String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
            logBox.append("[" + ts + "]  " + msg + "\n");
            logBox.setCaretPosition(logBox.getDocument().getLength());
        });
    }

    private void loadDevices() {
        try {
            List<Map<String, Object>> rows = db.query("SELECT * FROM devices WHERE status='Active'");
            deviceCombo.removeAllItems();
            for (Map<String, Object> r : rows) {
                String label = DatabaseManager.str(r, "device_name") + " (" + DatabaseManager.str(r, "ip_address") + ")";
                deviceCombo.addItem(label);
                deviceMap.put(label, r);
            }
        } catch (Exception e) {
            log("Error loading devices: " + e.getMessage());
        }
    }

    private void diagnose() {
        Map<String, Object> dev = deviceMap.get(deviceCombo.getSelectedItem());
        if (dev == null) return;
        new DiagnoseDialog((JFrame) getParent(), 
            DatabaseManager.str(dev, "ip_address"), 
            DatabaseManager.num(dev, "port"), 
            DatabaseManager.num(dev, "comm_password"));
    }

    private void startSync() {
        if (running) return;
        Map<String, Object> dev = deviceMap.get(deviceCombo.getSelectedItem());
        if (dev == null) { log("No device selected."); return; }
        
        running = true;
        syncBtn.setEnabled(false);
        progress.setIndeterminate(true);
        statusLabel.setText("Connecting...");
        
        String ip = DatabaseManager.str(dev, "ip_address");
        int port = DatabaseManager.num(dev, "port");
        String dateFilter = parseDate(dateFromField.getText().trim());
        
        new Thread(() -> runSync(dev, ip, port, dateFilter)).start();
    }

    private void runSync(Map<String, Object> dev, String ip, int port, String dateFilter) {
        ZkProtocol zk = new ZkProtocol(ip, port, 30000); // 30s timeout
        zk.setPassword(DatabaseManager.num(dev, "comm_password"));
        try {
            log("Connecting to " + ip + ":" + port + "...");
            if (zk.connect()) {
                log("Connected!");
                log("Fetching attendance logs...");
                SwingUtilities.invokeLater(() -> statusLabel.setText("Downloading..."));
                
                List<Map<String, Object>> logs = zk.getAttendanceRecords();
                if (logs != null) {
                    log("Fetched " + logs.size() + " records.");
                    
                    int saved = 0, skipped = 0;
                    for (Map<String, Object> l : logs) {
                        Object ptObj = l.get("punch_time");
                        String punchTime = (ptObj instanceof LocalDateTime) ? ((LocalDateTime) ptObj).toString().replace("T", " ") : ptObj.toString();
                        if (dateFilter != null && punchTime.compareTo(dateFilter) < 0) continue;
                        
                        try {
                            db.execute("INSERT IGNORE INTO raw_logs (device_id, emp_id, punch_time, punch_type, synced) VALUES (?,?,?,?,0)",
                                dev.get("device_id"), l.get("uid"), punchTime, l.get("punch_type"));
                            saved++;
                        } catch (Exception e) { skipped++; }
                    }
                    log("Saved: " + saved + " new records.");
                    if (skipped > 0) log("Skipped: " + skipped + " duplicates.");
                    
                    if (clearLogsCheck.isSelected() && saved > 0) {
                        log("Clearing device logs...");
                        if (zk.clearAttendanceRecords()) {
                            log("Device logs cleared successfully.");
                        } else {
                            log("Device refused to clear logs or command not supported.");
                        }
                    }
                    
                    log("────────────────────────────────────────");
                    log("Processing raw logs into attendance table...");
                    SwingUtilities.invokeLater(() -> statusLabel.setText("Processing..."));
                    com.bhspl.service.SyncService.processRawLogs(this::log);
                } else {
                    log("Failed to fetch logs.");
                }
                zk.disconnect();
                log("Sync complete!");
                SwingUtilities.invokeLater(() -> statusLabel.setText("Done"));
            } else {
                log("Connection failed.");
                SwingUtilities.invokeLater(() -> statusLabel.setText("Error"));
            }
        } catch (Exception e) {
            log("Error: " + e.getMessage());
            SwingUtilities.invokeLater(() -> statusLabel.setText("Error"));
        } finally {
            running = false;
            SwingUtilities.invokeLater(() -> {
                progress.setIndeterminate(false);
                progress.setValue(100);
                syncBtn.setEnabled(true);
            });
        }
    }


    private String parseDate(String s) {
        if (s.isEmpty()) return null;
        if (s.matches("\\d{2}-\\d{2}-\\d{4}")) {
            String[] p = s.split("-"); return p[2] + "-" + p[1] + "-" + p[0];
        }
        return s;
    }
}
