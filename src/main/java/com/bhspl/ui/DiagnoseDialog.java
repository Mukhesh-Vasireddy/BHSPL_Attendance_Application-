package com.bhspl.ui;

import com.bhspl.util.UIHelper;
import com.bhspl.util.ZkProtocol;

import javax.swing.*;
import java.awt.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Deep diagnostic dialog for ZK devices.
 */
public class DiagnoseDialog extends JDialog {

    private final String ip;
    private final int port;
    private final int password;
    private JTextArea logText;
    private JProgressBar progress;
    private boolean alive = true;

    public DiagnoseDialog(JFrame parent, String ip, int port, int password) {
        super(parent, "Device Diagnostics — " + ip + ":" + port, true);
        this.ip = ip;
        this.port = port;
        this.password = password;
        setSize(600, 540);
        UIHelper.centerWindow(this, 600, 540);
        buildUI();

        new Thread(this::runDiagnostics).start();
        setVisible(true);
    }

    private void buildUI() {
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(new Color(0x1e1e2e));

        JPanel hdr = new JPanel();
        hdr.setBackground(new Color(0x4a148c));
        hdr.setPreferredSize(new Dimension(0, 50));
        JLabel title = new JLabel("Device Diagnostics — " + ip + ":" + port);
        title.setFont(new Font("Segoe UI", Font.BOLD, 14));
        title.setForeground(Color.WHITE);
        hdr.add(title);
        root.add(hdr, BorderLayout.NORTH);

        logText = new JTextArea();
        logText.setBackground(new Color(0x0d1117));
        logText.setForeground(new Color(0x58e16f));
        logText.setFont(new Font("Consolas", Font.PLAIN, 12));
        logText.setEditable(false);
        logText.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        root.add(new JScrollPane(logText), BorderLayout.CENTER);

        JPanel bot = new JPanel(new BorderLayout());
        bot.setBackground(new Color(0x1e1e2e));
        bot.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        progress = new JProgressBar();
        progress.setIndeterminate(true);
        bot.add(progress, BorderLayout.CENTER);

        JButton closeBtn = UIHelper.makeButton("Close", UIHelper.BTN_DANGER);
        closeBtn.addActionListener(e -> {
            alive = false;
            dispose();
        });
        bot.add(closeBtn, BorderLayout.EAST);
        root.add(bot, BorderLayout.SOUTH);

        setContentPane(root);
        log("Starting diagnostics for " + ip + ":" + port + " ...");
    }

    private void log(String msg) {
        SwingUtilities.invokeLater(() -> {
            String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
            logText.append("[" + ts + "]  " + msg + "\n");
            logText.setCaretPosition(logText.getDocument().getLength());
        });
    }

    private void runDiagnostics() {
        ZkProtocol zk = new ZkProtocol(ip, port, 10000);
        zk.setPassword(password);
        try {
            log("Attempting ZK UDP connection...");
            if (zk.connect()) {
                log("Connected!");
                log("──────────────────────────────────────────────────");

                // Fetch info
                Map<String, String> info = zk.getDeviceInfo();
                for (Map.Entry<String, String> e : info.entrySet()) {
                    log("   " + String.format("%-14s", e.getKey()) + ": " + e.getValue());
                }

                log("──────────────────────────────────────────────────");
                log("Reading attendance logs via raw ZK UDP...");
                List<Map<String, Object>> logs = zk.getAttendanceRecords();
                if (logs != null) {
                    log("   raw ZK UDP  → " + logs.size() + " records");
                    if (!logs.isEmpty()) {
                        Map<String, Object> first = logs.get(0);
                        Map<String, Object> last = logs.get(logs.size() - 1);
                        log("   First: uid=" + first.get("user_id") + "  " + first.get("timestamp"));
                        log("   Last : uid=" + last.get("user_id") + "  " + last.get("timestamp"));
                    }
                } else {
                    log("   raw ZK UDP  → 0 records or error");
                }

                zk.disconnect();
                log("──────────────────────────────────────────────────");
                log("Diagnostics complete.");
            } else {
                log("Cannot connect. Check IP / network / Port (4370).");
            }
        } catch (Exception e) {
            log("Error: " + e.getMessage());
        } finally {
            SwingUtilities.invokeLater(() -> progress.setIndeterminate(false));
        }
    }
}
