package com.bhspl.ui;

import com.bhspl.db.DatabaseManager;
import com.bhspl.util.UIHelper;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * GRID for editing user details before bulk import.
 */
public class ImportEditDialog extends JDialog {

    private final List<Map<String, String>> users;
    private final Runnable finalCallback;
    private final DatabaseManager db = DatabaseManager.INSTANCE;
    private final List<Map<String, JTextField>> rowFields = new ArrayList<>();

    public ImportEditDialog(JDialog parent, List<Map<String, String>> users, Runnable callback) {
        super(parent, "Review & Edit Before Import — " + users.size() + " users", true);
        this.users = users;
        this.finalCallback = callback;
        setSize(820, 500);
        UIHelper.centerWindow(this, 820, 500);
        buildUI();
        setVisible(true);
    }

    private void buildUI() {
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(UIHelper.BG_CARD);

        JPanel hdr = new JPanel();
        hdr.setBackground(new Color(0x1565c0));
        hdr.setPreferredSize(new Dimension(0, 45));
        JLabel title = new JLabel("Review " + users.size() + " users — fill details then click Import");
        title.setFont(new Font("Segoe UI", Font.BOLD, 12));
        title.setForeground(Color.WHITE);
        hdr.add(title);
        root.add(hdr, BorderLayout.NORTH);

        JPanel grid = new JPanel(new GridBagLayout());
        grid.setBackground(UIHelper.BG_CARD);
        JScrollPane sp = new JScrollPane(grid);
        sp.setBorder(null);
        root.add(sp, BorderLayout.CENTER);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2, 2, 2, 2);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // Headers
        String[] headers = {"Enroll ID", "Device Name", "Emp ID *", "Full Name *", "Department", "Designation", "Phone"};
        for (int i = 0; i < headers.length; i++) {
            gbc.gridx = i; gbc.gridy = 0;
            JLabel l = new JLabel(headers[i]);
            l.setFont(new Font("Segoe UI", Font.BOLD, 11));
            l.setBackground(new Color(0xe3f2fd)); l.setOpaque(true);
            l.setBorder(BorderFactory.createLineBorder(UIHelper.BORDER));
            l.setHorizontalAlignment(SwingConstants.CENTER);
            l.setPreferredSize(new Dimension(110, 25));
            grid.add(l, gbc);
        }

        // Rows
        for (int r = 0; r < users.size(); r++) {
            Map<String, String> u = users.get(r);
            Map<String, JTextField> fields = new HashMap<>();
            
            gbc.gridy = r + 1;
            
            // Enroll ID (label)
            gbc.gridx = 0; JLabel uid = new JLabel(u.get("user_id")); uid.setHorizontalAlignment(SwingConstants.CENTER); grid.add(uid, gbc);
            
            // Device Name (label)
            gbc.gridx = 1; JLabel unm = new JLabel(u.get("name")); grid.add(unm, gbc);
            
            // Emp ID (entry)
            gbc.gridx = 2; JTextField eid = new JTextField(u.get("user_id")); fields.put("emp_id", eid); grid.add(eid, gbc);
            
            // Full Name (entry)
            gbc.gridx = 3; JTextField enm = new JTextField(u.get("name")); fields.put("emp_name", enm); grid.add(enm, gbc);
            
            // Dept (entry)
            gbc.gridx = 4; JTextField dpt = new JTextField(); fields.put("dept", dpt); grid.add(dpt, gbc);
            
            // Desig (entry)
            gbc.gridx = 5; JTextField dsg = new JTextField(); fields.put("desig", dsg); grid.add(dsg, gbc);
            
            // Phone (entry)
            gbc.gridx = 6; JTextField phn = new JTextField(); fields.put("phone", phn); grid.add(phn, gbc);
            
            rowFields.add(fields);
        }

        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 15, 10));
        footer.setBackground(UIHelper.BG_CARD);
        JButton saveBtn = UIHelper.makeButton("Import & Map Selected", UIHelper.BTN_PRIMARY);
        saveBtn.addActionListener(e -> doImport());
        footer.add(saveBtn);
        JButton canBtn = UIHelper.makeButton("Cancel", UIHelper.BTN_DANGER);
        canBtn.addActionListener(e -> dispose());
        footer.add(canBtn);
        root.add(footer, BorderLayout.SOUTH);

        setContentPane(root);
    }

    private void doImport() {
        int count = 0;
        try {
            for (int i = 0; i < users.size(); i++) {
                Map<String, String> u = users.get(i);
                Map<String, JTextField> f = rowFields.get(i);
                
                String eid = f.get("emp_id").getText().trim();
                String enm = f.get("emp_name").getText().trim();
                if (eid.isEmpty() || enm.isEmpty()) continue;
                
                db.execute("INSERT INTO employees (emp_id, emp_name, department, designation, phone, device_enroll_id, status) " +
                    "VALUES (?,?,?,?,?,?,'Active') ON DUPLICATE KEY UPDATE emp_name=VALUES(emp_name), department=VALUES(department), " +
                    "designation=VALUES(designation), phone=VALUES(phone), device_enroll_id=VALUES(device_enroll_id)",
                    eid, enm, f.get("dept").getText().trim(), f.get("desig").getText().trim(), f.get("phone").getText().trim(), u.get("user_id"));
                count++;
            }
            JOptionPane.showMessageDialog(this, count + " employees imported successfully.");
            if (finalCallback != null) finalCallback.run();
            dispose();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Error importing users: " + e.getMessage());
        }
    }
}
