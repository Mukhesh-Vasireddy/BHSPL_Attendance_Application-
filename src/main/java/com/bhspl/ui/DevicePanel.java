package com.bhspl.ui;

import com.bhspl.db.DatabaseManager;
import com.bhspl.util.UIHelper;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import java.awt.*;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

public class DevicePanel extends JPanel {

    private UIHelper.StyledTablePanel tablePanel;

    private static final String[] COLUMNS = {
        "ID", "Device Name", "IP Address", "Port", "Location", "Status", "Last Sync", "Sync Status"
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
        JPanel toolbar = new JPanel(new MigLayout("ins 0, gap 12", "[] push [] 8 [] 8 [] 8 []"));
        toolbar.setOpaque(false);

        JLabel title = new JLabel("Device Management");
        title.setFont(UIHelper.FNT_TITLE);
        title.setForeground(UIHelper.PRIMARY);
        
        JButton addBtn     = UIHelper.makeButton("Add Device", UIHelper.SUCCESS);
        JButton editBtn    = UIHelper.makeButton("Edit", UIHelper.PRIMARY);
        JButton syncBtn    = UIHelper.makeButton("Sync Now", new Color(0x6366f1));
        JButton deleteBtn  = UIHelper.makeButton("Remove", UIHelper.DANGER);
        JButton refreshBtn = UIHelper.makeButton("Refresh", new Color(0x334155));

        addBtn.addActionListener(e -> openDeviceDialog(null));
        editBtn.addActionListener(e -> {
            Object idVal = tablePanel.getSelectedValue();
            if (idVal == null) {
                JOptionPane.showMessageDialog(this, "Select a device to edit.");
                return;
            }
            openDeviceDialog((Integer) idVal);
        });
        syncBtn.addActionListener(e -> syncSelected());
        deleteBtn.addActionListener(e -> deleteSelected());
        refreshBtn.addActionListener(e -> loadData());

        toolbar.add(title);
        toolbar.add(addBtn, "right");
        toolbar.add(editBtn);
        toolbar.add(syncBtn);
        toolbar.add(deleteBtn);
        toolbar.add(refreshBtn);
        add(toolbar, "growx");

        // Table
        tablePanel = new UIHelper.StyledTablePanel(COLUMNS);
        tablePanel.setBorder(UIHelper.createCardBorder());
        tablePanel.getTable().getColumnModel().getColumn(0).setMaxWidth(60);
        tablePanel.getTable().getColumnModel().getColumn(3).setMaxWidth(80);
        add(tablePanel, "grow, push");

        // Info Note
        JLabel note = new JLabel("Sync pulls attendance logs from ZKTeco biometric devices over the network.");
        note.setFont(new Font("Segoe UI", Font.ITALIC, 11));
        note.setForeground(UIHelper.TEXT_LIGHT);
        add(note, "gapleft 4");
    }

    private void loadData() {
        tablePanel.clearRows();
        SwingWorker<List<Map<String, Object>>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<Map<String, Object>> doInBackground() throws Exception {
                return DatabaseManager.getInstance().fetchAll(
                    "SELECT device_id, device_name, ip_address, port, location, status, last_sync, last_error FROM devices ORDER BY device_id");
            }
            @Override
            protected void done() {
                try {
                    for (Map<String, Object> r : get()) {
                        String lastErr = (String) r.get("last_error");
                        String syncStatus = (lastErr == null) ? "OK" : "Error: " + lastErr;
                        
                        tablePanel.addRow(new Object[]{
                            r.get("device_id"), r.get("device_name"), r.get("ip_address"),
                            r.get("port"), r.get("location"), r.get("status"), r.get("last_sync"),
                            syncStatus
                        });
                    }
                } catch (Exception ignored) {}
            }
        };
        worker.execute();
    }

    private void openDeviceDialog(Integer deviceId) {
        String title = (deviceId == null) ? "Register New Device" : "Edit Device Settings";
        JDialog dlg = new JDialog((JFrame) SwingUtilities.getWindowAncestor(this), title, true);
        dlg.setSize(400, 320);
        UIHelper.centerWindow(dlg, 400, 320);

        JPanel p = new JPanel(new MigLayout("ins 24, wrap, gap 12", "[][grow]", "[]"));
        p.setBackground(Color.WHITE);

        JTextField name = new JTextField(14);
        JTextField ip   = new JTextField(14);
        JTextField port = new JTextField("4370", 14);
        JTextField loc  = new JTextField(14);
        JTextField pwd  = new JTextField("0", 14);

        if (deviceId != null) {
            try {
                Map<String, Object> data = DatabaseManager.getInstance().fetchOne("SELECT * FROM devices WHERE device_id=?", deviceId);
                if (data != null) {
                    name.setText(DatabaseManager.str(data, "device_name"));
                    ip.setText(DatabaseManager.str(data, "ip_address"));
                    port.setText(String.valueOf(data.get("port")));
                    loc.setText(DatabaseManager.str(data, "location"));
                    pwd.setText(String.valueOf(data.get("comm_password")));
                }
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(this, "Error loading data: " + ex.getMessage());
                return;
            }
        }

        Object[][] rows = {
            {"Device Name", name}, 
            {"IP Address", ip}, 
            {"Port", port}, 
            {"Location", loc},
            {"Comm Password", pwd}
        };
        for (Object[] row : rows) {
            p.add(new JLabel((String) row[0]));
            p.add((Component) row[1], "growx");
        }

        JButton save = UIHelper.makeButton(deviceId == null ? "Save Device" : "Update Device", UIHelper.SUCCESS);
        save.addActionListener(e -> {
            try {
                int portNum = Integer.parseInt(port.getText().trim());
                int pwdVal  = Integer.parseInt(pwd.getText().trim());
                
                if (deviceId == null) {
                    DatabaseManager.getInstance().execute(
                        "INSERT INTO devices (device_name, ip_address, port, location, comm_password) VALUES (?,?,?,?,?)",
                        name.getText().trim(), ip.getText().trim(), portNum, loc.getText().trim(), pwdVal
                    );
                } else {
                    DatabaseManager.getInstance().execute(
                        "UPDATE devices SET device_name=?, ip_address=?, port=?, location=?, comm_password=? WHERE device_id=?",
                        name.getText().trim(), ip.getText().trim(), portNum, loc.getText().trim(), pwdVal, deviceId
                    );
                }
                dlg.dispose();
                loadData();
            } catch (NumberFormatException nfe) {
                JOptionPane.showMessageDialog(dlg, "Port and Password must be numeric.");
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(dlg, "Error: " + ex.getMessage());
            }
        });
        
        p.add(save, "skip, growx, gaptop 12");
        dlg.setContentPane(p);
        dlg.setVisible(true);
    }

    private void syncSelected() {
        Object idVal = tablePanel.getSelectedValue();
        if (idVal == null) { JOptionPane.showMessageDialog(this, "Select a device first."); return; }
        
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
                    JOptionPane.showMessageDialog(DevicePanel.this, "Device synced successfully!");
                    loadData();
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(DevicePanel.this, "Sync failed: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    private void deleteSelected() {
        Object idVal = tablePanel.getSelectedValue();
        if (idVal == null) { JOptionPane.showMessageDialog(this, "Select a device first."); return; }
        
        int id = (int) idVal;
        int confirm = JOptionPane.showConfirmDialog(this, "Remove this device record?", "Confirm Remove", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) return;
        try {
            DatabaseManager.getInstance().execute("DELETE FROM devices WHERE device_id=?", id);
            loadData();
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Error: " + ex.getMessage());
        }
    }
}
