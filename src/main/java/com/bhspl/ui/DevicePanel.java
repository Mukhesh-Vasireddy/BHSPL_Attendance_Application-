package com.bhspl.ui;

import com.bhspl.db.DatabaseManager;
import com.bhspl.util.UIHelper;
import com.formdev.flatlaf.extras.FlatSVGIcon;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import java.awt.*;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

public class DevicePanel extends JPanel {

    private UIHelper.StyledTablePanel tablePanel;

    private static final String[] COLUMNS = {
        "ID", "Device Name", "IP Address", "Serial Number", "Port", "Location", "Status", "Last Sync", "Sync Status"
    };

    public DevicePanel() {
        setBackground(UIHelper.BG_MAIN);
        setLayout(new MigLayout("ins 24, wrap, gap 0, fill", "[grow]", "[] 16 [grow] 8 []"));
        buildUI();
        loadData();
        
        // Auto-refresh every 10 seconds for real-time status updates
        Timer autoRefresh = new Timer(10000, e -> loadData());
        autoRefresh.start();
    }

    private void buildUI() {
        // Toolbar
        JPanel toolbar = new JPanel(new MigLayout("ins 0, gap 0, fillx", "[grow][right]", "[]"));
        toolbar.setOpaque(false);

        JLabel title = new JLabel("Device Management");
        title.setFont(UIHelper.FNT_TITLE);
        title.setForeground(UIHelper.PRIMARY);
        
        // Responsive button panel
        JPanel btnPanel = new JPanel(new MigLayout("ins 0, gap 6, wrap", "[] [] [] [] [] [] [] [] []"));
        btnPanel.setOpaque(false);
        btnPanel.setOpaque(false);

        JButton addBtn     = createCompactButton("Add Device",    UIHelper.SUCCESS,          "plus.svg");
        JButton editBtn    = createCompactButton("Edit Device",   UIHelper.PRIMARY,          "edit.svg");
        JButton deleteBtn  = createCompactButton("Delete Device", UIHelper.DANGER,           "trash.svg");
        JButton testBtn    = createCompactButton("Test Connect",  new Color(0xea580c),       "check.svg");
        JButton syncBtn    = createCompactButton("Sync Logs",     new Color(0x0f766e),       "sync.svg");
        JButton importBtn  = createCompactButton("Import Users",  UIHelper.PRIMARY,          "employees.svg");
        JButton diagBtn    = createCompactButton("Diagnose",      new Color(0x7e22ce),       "search.svg");
        JButton admsBtn    = createCompactButton("Deploy ADMS",   new Color(0x0369a1),       "settings.svg");
        JButton refreshBtn = createCompactButton("Refresh",       new Color(0x475569),       "refresh.svg");

        addBtn.addActionListener(e -> openDeviceDialog(null));
        editBtn.addActionListener(e -> {
            Object idVal = tablePanel.getSelectedValue();
            if (idVal == null) {
                UIHelper.showInfo(this, "Select a device to edit.");
                return;
            }
            openDeviceDialog((Integer) idVal);
        });
        deleteBtn.addActionListener(e -> deleteSelected());
        testBtn.addActionListener(e -> testConnectSelected());
        syncBtn.addActionListener(e -> syncSelected());
        importBtn.addActionListener(e -> importUsersSelected());
        diagBtn.addActionListener(e -> diagnoseSelected());
        admsBtn.addActionListener(e -> deployAdmsSelected());
        refreshBtn.addActionListener(e -> loadData());

        btnPanel.add(addBtn,     "shrink 0");
        btnPanel.add(editBtn,    "shrink 0");
        btnPanel.add(deleteBtn,  "shrink 0");
        btnPanel.add(testBtn,    "shrink 0");
        btnPanel.add(syncBtn,    "shrink 0");
        btnPanel.add(importBtn,  "shrink 0");
        btnPanel.add(diagBtn,    "shrink 0");
        btnPanel.add(admsBtn,    "shrink 0");
        btnPanel.add(refreshBtn, "shrink 0");

        toolbar.add(title,    "left, growx, pushx");
        toolbar.add(btnPanel, "right");
        add(toolbar, "growx");

        // Responsive toolbar
        addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                int w = getWidth();
                if (w < 1000) {
                    toolbar.setLayout(new MigLayout("ins 0, gap 10, wrap", "[grow, fill]"));
                    btnPanel.setLayout(new MigLayout("ins 0, gap 6, wrap 4", "[grow, fill] [grow, fill] [grow, fill] [grow, fill]"));
                } else {
                    toolbar.setLayout(new MigLayout("ins 0, gap 0, fillx", "[grow][right]"));
                    btnPanel.setLayout(new MigLayout("ins 0, gap 6", "[] [] [] [] [] [] [] [] []"));
                }
                toolbar.revalidate();
            }
        });

        // Table
        tablePanel = new UIHelper.StyledTablePanel(COLUMNS);
        tablePanel.setBorder(UIHelper.createCardBorder());
        tablePanel.getTable().getColumnModel().getColumn(0).setMaxWidth(60);
        tablePanel.getTable().getColumnModel().getColumn(3).setMaxWidth(80);
        add(tablePanel, "grow, push, wmin 0");

        // Info Note
        JLabel note = new JLabel("Sync pulls attendance logs from ZKTeco biometric devices over the network.");
        note.setFont(new Font("Segoe UI", Font.ITALIC, 11));
        note.setForeground(UIHelper.TEXT_LIGHT);
        add(note, "gapleft 4");
    }

    /** Helper to create more compact buttons for the toolbar */
    private JButton createCompactButton(String text, Color bg, String icon) {
        JButton b = UIHelper.makeButton(text, bg, icon);
        b.setFont(new Font("Segoe UI", Font.BOLD, 12));
        b.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));
        return b;
    }

    private void loadData() {
        tablePanel.clearRows();
        SwingWorker<List<Map<String, Object>>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<Map<String, Object>> doInBackground() throws Exception {
                return DatabaseManager.getInstance().fetchAll(
                    "SELECT device_id, device_name, ip_address, serial_number, port, location, status, last_sync, last_error FROM devices ORDER BY device_id");
            }
            @Override
            protected void done() {
                try {
                    for (Map<String, Object> r : get()) {
                        String lastErr = (String) r.get("last_error");
                        String syncStatus = (lastErr == null) ? "OK" : "Error: " + lastErr;
                        String loc = (String) r.get("location");
                        String displayLoc = (loc != null && !loc.trim().isEmpty()) ? loc : "Not Assigned";
                        
                        tablePanel.addRow(new Object[]{
                            r.get("device_id"), r.get("device_name"), r.get("ip_address"),
                            r.get("serial_number"), r.get("port"), displayLoc, r.get("status"), r.get("last_sync"),
                            syncStatus
                        });
                    }
                } catch (Exception ignored) {}
            }
        };
        worker.execute();
    }

    private void openDeviceDialog(Integer deviceId) {
        JDialog dlg = new JDialog((JFrame) SwingUtilities.getWindowAncestor(this), 
            (deviceId == null ? "Register New Device" : "Edit Device Settings"), true);
        dlg.setSize(450, 500);
        UIHelper.centerWindow(dlg, 450, 500);
        
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(Color.WHITE);

        // Styled Header
        JPanel header = new JPanel(new MigLayout("ins 0 20 0 20, fill", "[] 15 [grow]", "[]"));
        header.setBackground(UIHelper.PRIMARY);
        header.setPreferredSize(new Dimension(0, 60));
        
        try {
            FlatSVGIcon icon = new FlatSVGIcon("icons/devices.svg", 24, 24);
            icon.setColorFilter(new FlatSVGIcon.ColorFilter(c -> Color.WHITE));
            header.add(new JLabel(icon), "aligny center");
        } catch (Exception ignored) {}
        
        JLabel titleLbl = new JLabel(deviceId == null ? "Add New Device" : "Update Device Settings");
        titleLbl.setFont(new Font("Segoe UI", Font.BOLD, 15));
        titleLbl.setForeground(Color.WHITE);
        header.add(titleLbl, "aligny center");
        root.add(header, BorderLayout.NORTH);

        JPanel p = new JPanel(new MigLayout("ins 24, wrap 2, gap 12", "[][grow]", "[]"));
        p.setOpaque(false);

        JTextField name = new JTextField(14);
        JTextField ip   = new JTextField(14);
        JTextField sn   = new JTextField(14);
        JTextField port = new JTextField("4370", 14);
        JTextField loc  = new JTextField(14);
        JTextField pwd  = new JTextField("0", 14);

        if (deviceId != null) {
            try {
                Map<String, Object> data = DatabaseManager.getInstance().fetchOne("SELECT * FROM devices WHERE device_id=?", deviceId);
                if (data != null) {
                    name.setText(DatabaseManager.str(data, "device_name"));
                    ip.setText(DatabaseManager.str(data, "ip_address"));
                    sn.setText(DatabaseManager.str(data, "serial_number"));
                    port.setText(String.valueOf(data.get("port")));
                    loc.setText(DatabaseManager.str(data, "location"));
                    pwd.setText(String.valueOf(data.get("comm_password")));
                }
            } catch (SQLException ex) {
                UIHelper.showError(this, "Error loading data: " + ex.getMessage());
                return;
            }
        }

        p.add(new JLabel("Device Name:"));   p.add(name, "growx");
        p.add(new JLabel("IP Address:"));    p.add(ip,   "growx");
        p.add(new JLabel("Serial Number:")); p.add(sn,   "growx");
        p.add(new JLabel("Port:"));          p.add(port, "growx");
        p.add(new JLabel("Location:"));      p.add(loc,  "growx");
        p.add(new JLabel("Password:"));      p.add(pwd,  "growx");

        JButton save = UIHelper.makeButton(deviceId == null ? "Save Device" : "Update Device", 
            UIHelper.SUCCESS, deviceId == null ? "plus.svg" : "check.svg");
        save.addActionListener(e -> {
            try {
                int portNum = Integer.parseInt(port.getText().trim());
                int pwdVal  = Integer.parseInt(pwd.getText().trim());
                
                if (deviceId == null) {
                    DatabaseManager.getInstance().execute(
                        "INSERT INTO devices (device_name, ip_address, serial_number, port, location, comm_password) VALUES (?,?,?,?,?,?)",
                        name.getText().trim(), ip.getText().trim(), sn.getText().trim(), portNum, loc.getText().trim(), pwdVal
                    );
                } else {
                    DatabaseManager.getInstance().execute(
                        "UPDATE devices SET device_name=?, ip_address=?, serial_number=?, port=?, location=?, comm_password=? WHERE device_id=?",
                        name.getText().trim(), ip.getText().trim(), sn.getText().trim(), portNum, loc.getText().trim(), pwdVal, deviceId
                    );
                }
                dlg.dispose();
                loadData();
                UIHelper.showSuccess(this, "Device " + (deviceId == null ? "registered" : "updated") + " successfully.");
            } catch (NumberFormatException nfe) {
                UIHelper.showWarning(dlg, "Port and Password must be numeric.");
            } catch (Exception ex) {
                UIHelper.showError(dlg, "Error: " + ex.getMessage());
            }
        });
        
        JButton cancel = UIHelper.makeButton("Cancel", new Color(0x64748B), "x.svg");
        cancel.addActionListener(e -> dlg.dispose());

        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 12));
        footer.setOpaque(false);
        footer.add(cancel);
        footer.add(save);
        
        root.add(p, BorderLayout.CENTER);
        root.add(footer, BorderLayout.SOUTH);
        
        dlg.setContentPane(root);
        dlg.setVisible(true);
    }

    private void testConnectSelected() {
        Object idVal = tablePanel.getSelectedValue();
        if (idVal == null) {
            UIHelper.showInfo(this, "Please select a device first.");
            return;
        }
        int deviceId = (int) idVal;
        try {
            Map<String, Object> dev = DatabaseManager.getInstance().fetchOne("SELECT * FROM devices WHERE device_id=?", deviceId);
            if (dev == null) return;
            
            String ip = DatabaseManager.str(dev, "ip_address");
            int port = DatabaseManager.num(dev, "port");
            int pwd = DatabaseManager.num(dev, "comm_password");
            
            setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
            SwingWorker<Boolean, Void> worker = new SwingWorker<>() {
                @Override
                protected Boolean doInBackground() throws Exception {
                    com.bhspl.util.ZkProtocol zk = new com.bhspl.util.ZkProtocol(ip, port, 5000);
                    zk.setPassword(pwd);
                    boolean res = zk.connect();
                    if (res) zk.disconnect();
                    return res;
                }
                @Override
                protected void done() {
                    setCursor(Cursor.getDefaultCursor());
                    try {
                        if (get()) {
                            UIHelper.showSuccess(DevicePanel.this, "Connection Successful!\nThe device is reachable and responding.");
                        } else {
                            UIHelper.showError(DevicePanel.this, "Connection Failed.\nCheck the device IP, port, and network.");
                        }
                    } catch (Exception e) {
                        UIHelper.showError(DevicePanel.this, "Error: " + e.getMessage());
                    }
                }
            };
            worker.execute();
        } catch (SQLException ex) {
            UIHelper.showError(this, "Database Error: " + ex.getMessage());
        }
    }

    private void importUsersSelected() {
        Object idVal = tablePanel.getSelectedValue();
        if (idVal == null) {
            UIHelper.showInfo(this, "Please select a device first.");
            return;
        }
        try {
            Map<String, Object> dev = DatabaseManager.getInstance().fetchOne("SELECT * FROM devices WHERE device_id=?", idVal);
            if (dev != null) {
                new ImportUsersDialog((JFrame) SwingUtilities.getWindowAncestor(this), dev);
            }
        } catch (SQLException ex) {
            UIHelper.showError(this, "Error loading device: " + ex.getMessage());
        }
    }

    private void diagnoseSelected() {
        Object idVal = tablePanel.getSelectedValue();
        if (idVal == null) {
            UIHelper.showInfo(this, "Please select a device first.");
            return;
        }
        try {
            Map<String, Object> dev = DatabaseManager.getInstance().fetchOne("SELECT * FROM devices WHERE device_id=?", idVal);
            if (dev != null) {
                String ip = DatabaseManager.str(dev, "ip_address");
                int port = DatabaseManager.num(dev, "port");
                int pwd = DatabaseManager.num(dev, "comm_password");
                new DiagnoseDialog((JFrame) SwingUtilities.getWindowAncestor(this), ip, port, pwd);
            }
        } catch (SQLException ex) {
            UIHelper.showError(this, "Error loading device: " + ex.getMessage());
        }
    }

    private void syncSelected() {
        Object idVal = tablePanel.getSelectedValue();
        if (idVal == null) { UIHelper.showInfo(this, "Please select a device first."); return; }
        
        int deviceId = (int) idVal;
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                com.bhspl.service.SyncService.syncDeviceById(deviceId);
                return null;
            }
            @Override
            protected void done() {
                setCursor(Cursor.getDefaultCursor());
                try {
                    get();
                    UIHelper.showSuccess(DevicePanel.this, "Device synced successfully!");
                    loadData();
                } catch (Exception e) {
                    UIHelper.showError(DevicePanel.this, "Sync failed: " + e.getMessage());
                }
            }
        };
        worker.execute();
    }

    private void deleteSelected() {
        Object idVal = tablePanel.getSelectedValue();
        if (idVal == null) { UIHelper.showInfo(this, "Please select a device first."); return; }
        
        int id = (int) idVal;
        if (!UIHelper.confirmYesNo(this, "Remove Device", "Remove this device record?<br>This action cannot be undone.")) return;
        try {
            DatabaseManager.getInstance().execute("DELETE FROM devices WHERE device_id=?", id);
            loadData();
        } catch (SQLException ex) {
            UIHelper.showError(this, "Error: " + ex.getMessage());
        }
    }

    private void deployAdmsSelected() {
        java.util.List<Object> ids = tablePanel.getSelectedValues();
        if (ids.isEmpty()) {
            UIHelper.showInfo(this, "Please select at least one device to configure.");
            return;
        }

        String currentIp = "127.0.0.1";
        try {
            currentIp = java.net.InetAddress.getLocalHost().getHostAddress();
        } catch (Exception ignored) {}

        String serverIp = JOptionPane.showInputDialog(this, 
            "Enter this Server's IP address (reachable by the devices):", currentIp);
        
        if (serverIp == null || serverIp.trim().isEmpty()) return;

        if (!UIHelper.confirmYesNo(this, "Bulk Deploy ADMS", 
            "This will remotely update " + ids.size() + " device(s) to push data to:<br><b>" + serverIp + ":8081</b><br><br>The devices will REBOOT after deployment. Continue?")) {
            return;
        }

        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        SwingWorker<java.util.List<String>, String> worker = new SwingWorker<>() {
            @Override
            protected java.util.List<String> doInBackground() throws Exception {
                java.util.List<String> results = new java.util.ArrayList<>();
                for (Object idVal : ids) {
                    int deviceId = (int) idVal;
                    Map<String, Object> dev = DatabaseManager.getInstance().fetchOne("SELECT * FROM devices WHERE device_id=?", deviceId);
                    if (dev == null) continue;

                    String name = DatabaseManager.str(dev, "device_name");
                    String ip = DatabaseManager.str(dev, "ip_address");
                    int port = DatabaseManager.num(dev, "port");
                    int pwd = DatabaseManager.num(dev, "comm_password");

                    publish("Configuring " + name + " (" + ip + ")...");
                    
                    com.bhspl.util.ZkProtocol zk = new com.bhspl.util.ZkProtocol(ip, port, 10000);
                    zk.setPassword(pwd);
                    if (!zk.connect()) {
                        results.add(name + ": Connection failed.");
                        continue;
                    }

                    try {
                        zk.setOption("ADMSOn", "1");
                        zk.setOption("ADMSHost", serverIp);
                        zk.setOption("ADMSPort", "8081");
                        zk.reboot();
                        results.add(name + ": Success");
                    } catch (Exception e) {
                        results.add(name + ": Error - " + e.getMessage());
                    } finally {
                        zk.disconnect();
                    }
                }
                return results;
            }

            @Override
            protected void done() {
                setCursor(Cursor.getDefaultCursor());
                try {
                    java.util.List<String> results = get();
                    StringBuilder sb = new StringBuilder("<html><b>ADMS Deployment Results:</b><br><br>");
                    for (String r : results) {
                        sb.append(r).append("<br>");
                    }
                    sb.append("</html>");
                    UIHelper.showInfo(DevicePanel.this, sb.toString());
                    loadData();
                } catch (Exception e) {
                    UIHelper.showError(DevicePanel.this, "Error: " + e.getMessage());
                }
            }
        };
        worker.execute();
    }
}
