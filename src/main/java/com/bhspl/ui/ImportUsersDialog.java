package com.bhspl.ui;

import com.bhspl.db.DatabaseManager;
import com.bhspl.util.UIHelper;
import com.bhspl.util.ZkProtocol;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Dialog to import users from a ZK device.
 */
public class ImportUsersDialog extends JDialog {

    private final Map<String, Object> device;
    private final DatabaseManager db = DatabaseManager.INSTANCE;
    private JTable table;
    private DefaultTableModel model;
    private JProgressBar progress;
    private JLabel statusLabel;
    private JButton importBtn;
    private List<Map<String, String>> deviceUsers = new ArrayList<>();

    public ImportUsersDialog(JFrame parent, Map<String, Object> device) {
        super(parent, "Import Users — " + device.get("ip_address"), true);
        this.device = device;
        setSize(800, 560);
        UIHelper.centerWindow(this, 800, 560);
        buildUI();
        
        new Thread(this::fetchUsers).start();
        setVisible(true);
    }

    private void buildUI() {
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(UIHelper.BG_CARD);

        JPanel hdr = new JPanel();
        hdr.setBackground(new Color(0x1565c0));
        hdr.setPreferredSize(new Dimension(0, 50));
        JLabel title = new JLabel("Import Users from " + device.get("device_name") + " (" + device.get("ip_address") + ")");
        title.setFont(new Font("Segoe UI", Font.BOLD, 14));
        title.setForeground(Color.WHITE);
        hdr.add(title);
        root.add(hdr, BorderLayout.NORTH);

        String[] cols = {"Enroll ID", "Name on Device", "Employee ID", "Employee Name", "Status"};
        model = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        table = new JTable(model);
        UIHelper.styleTable(table);
        root.add(new JScrollPane(table), BorderLayout.CENTER);

        JPanel bot = new JPanel(new BorderLayout());
        bot.setBackground(UIHelper.BG_CARD);
        bot.setBorder(BorderFactory.createEmptyBorder(10, 15, 10, 15));

        JPanel progPanel = new JPanel(new BorderLayout(5, 5));
        progPanel.setOpaque(false);
        progress = new JProgressBar();
        progress.setIndeterminate(true);
        progPanel.add(progress, BorderLayout.NORTH);
        statusLabel = new JLabel("Connecting to device...");
        statusLabel.setFont(new Font("Segoe UI", Font.BOLD, 11));
        progPanel.add(statusLabel, BorderLayout.CENTER);
        bot.add(progPanel, BorderLayout.NORTH);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
        btnPanel.setOpaque(false);
        importBtn = UIHelper.makeButton("Import Selected", UIHelper.BTN_PRIMARY);
        importBtn.setEnabled(false);
        importBtn.addActionListener(e -> openImportEdit());
        btnPanel.add(importBtn);
        
        JButton closeBtn = UIHelper.makeButton("Close", UIHelper.BTN_DANGER);
        closeBtn.addActionListener(e -> dispose());
        btnPanel.add(closeBtn);
        bot.add(btnPanel, BorderLayout.SOUTH);

        root.add(bot, BorderLayout.SOUTH);
        setContentPane(root);
    }

    private void fetchUsers() {
        String ip = DatabaseManager.str(device, "ip_address");
        int port = DatabaseManager.num(device, "port");
        ZkProtocol zk = new ZkProtocol(ip, port, 10000);
        
        try {
            if (zk.connect()) {
                setStatus("Reading users from device...");
                deviceUsers = zk.getUsers();
                zk.disconnect();
                
                setStatus("Checking Employee Master...");
                populateTable();
                setStatus("Found " + deviceUsers.size() + " users.");
                SwingUtilities.invokeLater(() -> {
                    progress.setIndeterminate(false);
                    progress.setValue(100);
                    importBtn.setEnabled(true);
                });
            } else {
                setStatus("Connection failed.");
            }
        } catch (Exception e) {
            setStatus("Error: " + e.getMessage());
        }
    }

    private void setStatus(String msg) {
        SwingUtilities.invokeLater(() -> statusLabel.setText(msg));
    }

    private void populateTable() {
        try {
            List<Map<String, Object>> existing = db.query("SELECT emp_id, emp_name, device_enroll_id FROM employees");
            model.setRowCount(0);
            
            for (Map<String, String> u : deviceUsers) {
                String uid = u.get("user_id");
                String name = u.get("name");
                
                String empId = "", empName = "", status = "New — will be added";
                for (Map<String, Object> e : existing) {
                    if (uid.equals(DatabaseManager.str(e, "device_enroll_id"))) {
                        empId = DatabaseManager.str(e, "emp_id");
                        empName = DatabaseManager.str(e, "emp_name");
                        status = "Already Mapped";
                        break;
                    }
                }
                
                final String fEmpId = empId, fEmpName = empName, fStatus = status;
                SwingUtilities.invokeLater(() -> model.addRow(new Object[]{uid, name, fEmpId, fEmpName, fStatus}));
            }
        } catch (Exception e) {
            setStatus("Error matching users: " + e.getMessage());
        }
    }

    private void openImportEdit() {
        int[] selected = table.getSelectedRows();
        if (selected.length == 0) {
            JOptionPane.showMessageDialog(this, "Please select at least one row to import.");
            return;
        }
        
        List<Map<String, String>> toImport = new ArrayList<>();
        for (int i : selected) {
            Map<String, String> u = deviceUsers.get(i);
            toImport.add(u);
        }
        
        new ImportEditDialog(this, toImport, this::fetchUsers);
    }
}
