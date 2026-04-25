package com.bhspl.ui;

import com.bhspl.db.DatabaseManager;
import com.bhspl.util.UIHelper;

import javax.swing.*;
import java.awt.*;
import java.util.Map;

/**
 * Dialog to add or edit a biometric device.
 */
public class DeviceForm extends JDialog {

    private final Integer deviceId;
    private final Runnable callback;
    private final DatabaseManager db = DatabaseManager.INSTANCE;
    private JTextField nameField, ipField, portField, snField, locField;
    private JComboBox<String> statusCombo;

    public DeviceForm(JFrame parent, Integer deviceId, Runnable callback) {
        super(parent, deviceId == null ? "Add Device" : "Edit Device #" + deviceId, true);
        this.deviceId = deviceId;
        this.callback = callback;
        setSize(400, 420);
        UIHelper.centerWindow(this, 400, 420);
        buildUI();
        if (deviceId != null)
            loadData();
        setVisible(true);
    }

    private void buildUI() {
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(UIHelper.BG_CARD);

        JPanel hdr = new JPanel();
        hdr.setBackground(UIHelper.PRIMARY);
        hdr.setPreferredSize(new Dimension(0, 50));
        JLabel title = new JLabel("Device Configuration");
        title.setFont(new Font("Segoe UI", Font.BOLD, 14));
        title.setForeground(Color.WHITE);
        hdr.add(title);
        root.add(hdr, BorderLayout.NORTH);

        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(UIHelper.BG_CARD);
        form.setBorder(BorderFactory.createEmptyBorder(25, 35, 25, 35));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.anchor = GridBagConstraints.EAST;

        String[] labels = { "Device Name *", "IP Address *", "Port *", "Serial Number", "Location", "Status" };
        for (int i = 0; i < labels.length; i++) {
            gbc.gridx = 0;
            gbc.gridy = i;
            JLabel lbl = new JLabel(labels[i] + ":");
            lbl.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            form.add(lbl, gbc);

            gbc.gridx = 1;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.weightx = 1.0;
            if (i == 5) {
                statusCombo = new JComboBox<>(new String[] { "Active", "Inactive" });
                statusCombo.setFont(new Font("Segoe UI", Font.PLAIN, 12));
                form.add(statusCombo, gbc);
            } else {
                JTextField txt = new JTextField(15);
                txt.setFont(new Font("Segoe UI", Font.PLAIN, 12));
                if (i == 0) {
                    nameField = txt;
                    txt.setText("ZK K40");
                } else if (i == 1) {
                    ipField = txt;
                    txt.setText("192.168.1.201");
                } else if (i == 2) {
                    portField = txt;
                    txt.setText("4370");
                } else if (i == 3)
                    snField = txt;
                else if (i == 4)
                    locField = txt;
                form.add(txt, gbc);
            }
        }
        root.add(form, BorderLayout.CENTER);

        JPanel footer = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 10));
        footer.setBackground(UIHelper.BG_CARD);
        JButton saveBtn = UIHelper.makeButton("Save", UIHelper.BTN_SUCCESS);
        saveBtn.addActionListener(e -> save());
        footer.add(saveBtn);
        JButton cancelBtn = UIHelper.makeButton("Cancel", UIHelper.BTN_DANGER);
        cancelBtn.addActionListener(e -> dispose());
        footer.add(cancelBtn);
        root.add(footer, BorderLayout.SOUTH);

        setContentPane(root);
    }

    private void loadData() {
        try {
            Map<String, Object> r = db.queryOne("SELECT * FROM devices WHERE device_id=?", deviceId);
            if (r == null)
                return;
            nameField.setText(DatabaseManager.str(r, "device_name"));
            ipField.setText(DatabaseManager.str(r, "ip_address"));
            portField.setText(DatabaseManager.str(r, "port"));
            snField.setText(DatabaseManager.str(r, "serial_number"));
            locField.setText(DatabaseManager.str(r, "location"));
            statusCombo.setSelectedItem(DatabaseManager.str(r, "status"));
        } catch (Exception e) {
        }
    }

    private void save() {
        String name = nameField.getText().trim();
        String ip = ipField.getText().trim();
        if (name.isEmpty() || ip.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Name and IP are required.");
            return;
        }
        try {
            int port = Integer.parseInt(portField.getText().trim());
            if (deviceId == null) {
                db.execute(
                        "INSERT INTO devices (device_name, ip_address, port, serial_number, location, status) VALUES (?,?,?,?,?,?)",
                        name, ip, port, snField.getText().trim(), locField.getText().trim(),
                        statusCombo.getSelectedItem().toString());
            } else {
                db.execute(
                        "UPDATE devices SET device_name=?, ip_address=?, port=?, serial_number=?, location=?, status=? WHERE device_id=?",
                        name, ip, port, snField.getText().trim(), locField.getText().trim(),
                        statusCombo.getSelectedItem().toString(), deviceId);
            }
            if (callback != null)
                callback.run();
            dispose();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Error: " + e.getMessage());
        }
    }
}
