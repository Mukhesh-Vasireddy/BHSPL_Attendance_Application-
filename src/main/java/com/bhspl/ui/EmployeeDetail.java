package com.bhspl.ui;

import com.bhspl.db.DatabaseManager;
import com.bhspl.util.UIHelper;

import javax.swing.*;
import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.Map;

/**
 * Read-only detail view for an employee.
 */
public class EmployeeDetail extends JDialog {

    private final String empId;
    private final DatabaseManager db = DatabaseManager.INSTANCE;
    private final SimpleDateFormat df = new SimpleDateFormat("dd-MM-yyyy");

    public EmployeeDetail(JFrame parent, String empId) {
        super(parent, "Employee Details — " + empId, true);
        this.empId = empId;
        setSize(600, 640);
        UIHelper.centerWindow(this, 600, 640);
        buildUI();
        setVisible(true);
    }

    private void buildUI() {
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(UIHelper.BG_CARD);

        Map<String, Object> r = null;
        try {
            r = db.queryOne("SELECT * FROM employees WHERE emp_id=?", empId);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Error fetching employee: " + e.getMessage());
        }

        if (r == null) {
            dispose();
            return;
        }

        // Header
        JPanel hdr = new JPanel();
        hdr.setBackground(UIHelper.PRIMARY);
        hdr.setPreferredSize(new Dimension(0, 80));
        hdr.setLayout(new BoxLayout(hdr, BoxLayout.Y_AXIS));
        
        JLabel nameText = new JLabel(DatabaseManager.str(r, "emp_name"));
        nameText.setFont(new Font("Segoe UI", Font.BOLD, 18));
        nameText.setForeground(Color.WHITE); nameText.setAlignmentX(CENTER_ALIGNMENT);
        
        JLabel subText = new JLabel("ID: " + empId + "  |  " + DatabaseManager.str(r, "designation") + "  |  " + DatabaseManager.str(r, "department"));
        subText.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        subText.setForeground(new Color(0x90caf9)); subText.setAlignmentX(CENTER_ALIGNMENT);
        
        hdr.add(Box.createVerticalGlue());
        hdr.add(nameText); hdr.add(subText);
        hdr.add(Box.createVerticalGlue());
        root.add(hdr, BorderLayout.NORTH);

        // Details list
        JPanel details = new JPanel();
        details.setLayout(new BoxLayout(details, BoxLayout.Y_AXIS));
        details.setBackground(UIHelper.BG_CARD);
        details.setBorder(BorderFactory.createEmptyBorder(15, 20, 15, 20));

        String[][] fields = {
            {"Emp ID", "emp_id"}, {"Full Name", "emp_name"}, {"Date of Birth", "dob"},
            {"Date of Join", "doj"}, {"Gender", "gender"}, {"Blood Group", "blood_group"},
            {"Department", "department"}, {"Designation", "designation"}, {"Shift", "shift"},
            {"Phone", "phone"}, {"Email", "email"}, {"Emergency Contact", "emergency_contact"},
            {"Basic Salary", "basic_salary"}, {"Bank Account", "bank_account"},
            {"PAN", "pan_number"}, {"Aadhaar", "aadhaar"}, {"Status", "status"},
            {"Device ID", "device_id"}, {"Finger ID", "finger_id"}, {"Device Enroll ID", "device_enroll_id"}
        };

        for (int i = 0; i < fields.length; i++) {
            String label = fields[i][0];
            String key = fields[i][1];
            Object val = r.get(key);
            String s = (val == null) ? "" : val.toString();
            
            if (val instanceof java.sql.Date || val instanceof java.util.Date) {
                s = df.format(val);
            } else if (key.equals("basic_salary")) {
                s = "₹ " + String.format("%,.2f", DatabaseManager.dbl(r, "basic_salary"));
            }

            JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
            row.setBackground(i % 2 == 0 ? UIHelper.TREE_ODD : UIHelper.TREE_EVEN);
            row.setMaximumSize(new Dimension(600, 30));
            
            JLabel kl = new JLabel(label + ":");
            kl.setFont(new Font("Segoe UI", Font.BOLD, 12));
            kl.setForeground(UIHelper.PRIMARY);
            kl.setPreferredSize(new Dimension(150, 20));
            
            JLabel vl = new JLabel(s);
            vl.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            vl.setForeground(UIHelper.TEXT_DARK);
            
            row.add(kl); row.add(vl);
            details.add(row);
        }

        JScrollPane sp = new JScrollPane(details);
        sp.setBorder(null);
        root.add(sp, BorderLayout.CENTER);

        // Footer
        JPanel footer = new JPanel();
        footer.setBackground(UIHelper.BG_CARD);
        JButton closeBtn = UIHelper.makeButton("Close", UIHelper.BTN_DANGER);
        closeBtn.addActionListener(e -> dispose());
        footer.add(closeBtn);
        root.add(footer, BorderLayout.SOUTH);

        setContentPane(root);
    }
}
