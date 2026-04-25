package com.bhspl.ui;

import com.bhspl.db.DatabaseManager;
import com.bhspl.util.UIHelper;

import javax.swing.*;
import java.awt.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.text.SimpleDateFormat;

/**
 * Dialog for adding or editing an employee.
 * Replicates the multi-tab Python EmployeeForm.
 */
public class EmployeeForm extends JDialog {

    private final String empId;
    private final Runnable callback;
    private final DatabaseManager db = DatabaseManager.INSTANCE;
    private final Map<String, JComponent> fields = new HashMap<>();
    private final DateTimeFormatter df = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    public EmployeeForm(JFrame parent, String empId, Runnable callback) {
        super(parent, empId == null ? "Add Employee" : "Edit Employee — " + empId, true);
        this.empId = empId;
        this.callback = callback;
        
        setSize(720, 680);
        UIHelper.centerWindow(this, 720, 680);
        buildUI();
        if (empId != null) loadData();
        setVisible(true);
    }

    private void buildUI() {
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(UIHelper.BG_CARD);

        // Header
        JPanel hdr = new JPanel();
        hdr.setBackground(UIHelper.PRIMARY);
        hdr.setPreferredSize(new Dimension(0, 50));
        JLabel title = new JLabel((empId == null ? "Add New Employee" : "Edit Employee — " + empId));
        title.setFont(new Font("Segoe UI", Font.BOLD, 14));
        title.setForeground(Color.WHITE);
        hdr.add(title);
        root.add(hdr, BorderLayout.NORTH);

        // Tabs
        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(new Font("Segoe UI", Font.BOLD, 11));
        
        tabs.addTab("  Basic Info  ", buildBasicTab());
        tabs.addTab("  Contact  ", buildContactTab());
        tabs.addTab("  Finance & IDs  ", buildFinanceTab());
        tabs.addTab("  Device  ", buildDeviceTab());
        
        root.add(tabs, BorderLayout.CENTER);

        // Footer buttons
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 10));
        footer.setBackground(UIHelper.BG_CARD);
        footer.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, UIHelper.BORDER));
        
        JButton saveBtn = UIHelper.makeButton("Save", UIHelper.BTN_SUCCESS);
        saveBtn.setPreferredSize(new Dimension(120, 32));
        saveBtn.addActionListener(e -> save());
        footer.add(saveBtn);
        
        JButton cancelBtn = UIHelper.makeButton("Cancel", UIHelper.BTN_DANGER);
        cancelBtn.setPreferredSize(new Dimension(100, 32));
        cancelBtn.addActionListener(e -> dispose());
        footer.add(cancelBtn);
        
        root.add(footer, BorderLayout.SOUTH);
        setContentPane(root);
    }

    private JPanel buildBasicTab() {
        JPanel p = createTabPanel(2);
        addField(p, "Emp ID *", "emp_id", new JTextField());
        addField(p, "Full Name *", "emp_name", new JTextField());
        addField(p, "Date of Birth", "dob", new JTextField("DD-MM-YYYY"));
        addField(p, "Date of Join", "doj", new JTextField(LocalDate.now().format(df)));
        addField(p, "Gender", "gender", new JComboBox<>(new String[]{"Male", "Female", "Other"}));
        addField(p, "Blood Group", "blood_group", new JComboBox<>(new String[]{"A+", "A-", "B+", "B-", "O+", "O-", "AB+", "AB-"}));
        
        // Dynamic combos from DB
        addField(p, "Department *", "department", new JComboBox<>(fetchList("SELECT dept_name FROM departments ORDER BY dept_name")));
        addField(p, "Designation *", "designation", new JComboBox<>(fetchList("SELECT desig_name FROM designations ORDER BY level_order")));
        addField(p, "Shift", "shift", new JComboBox<>(fetchList("SELECT shift_name FROM shifts ORDER BY shift_name")));
        addField(p, "Status", "status", new JComboBox<>(new String[]{"Active", "Inactive"}));
        
        return p;
    }

    private JPanel buildContactTab() {
        JPanel p = createTabPanel(1);
        addField(p, "Phone *", "phone", new JTextField());
        addField(p, "Email", "email", new JTextField());
        addField(p, "Emergency Contact", "emergency_contact", new JTextField());
        JTextArea addr = new JTextArea(4, 20);
        addr.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        addr.setBorder(BorderFactory.createLineBorder(UIHelper.BORDER));
        addField(p, "Address", "address", new JScrollPane(addr));
        fields.put("address", addr); // Store the text area, not the scroll pane
        return p;
    }

    private JPanel buildFinanceTab() {
        JPanel p = createTabPanel(1);
        addField(p, "Basic Salary", "basic_salary", new JTextField("0"));
        addField(p, "Bank Account", "bank_account", new JTextField());
        addField(p, "PAN Number", "pan_number", new JTextField());
        addField(p, "Aadhaar Number", "aadhaar", new JTextField());
        return p;
    }

    private JPanel buildDeviceTab() {
        JPanel p = createTabPanel(1);
        
        // Device Info Hint
        JTextArea hint = new JTextArea("Device Enroll ID = numeric ID assigned by biometric device.\nCheck device LCD or use Diagnose in Device Manager.");
        hint.setEditable(false); hint.setOpaque(true);
        hint.setBackground(new Color(0xe8f5e9)); hint.setForeground(new Color(0x1b5e20));
        hint.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        hint.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        p.add(hint, createGbc(0, 0, 2));

        addField(p, "Device ID", "device_id", new JTextField("0"), 1);
        addField(p, "Finger ID", "finger_id", new JTextField("0"), 2);
        addField(p, "Device Enroll ID", "device_enroll_id", new JTextField(), 3);
        
        return p;
    }

    private JPanel createTabPanel(int cols) {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBackground(UIHelper.BG_CARD);
        p.setBorder(BorderFactory.createEmptyBorder(20, 25, 20, 25));
        return p;
    }

    private GridBagConstraints createGbc(int x, int y, int width) {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = x; gbc.gridy = y; gbc.gridwidth = width;
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;
        return gbc;
    }

    private void addField(JPanel p, String label, String key, JComponent comp) {
        addField(p, label, key, comp, p.getComponentCount() / 2);
    }

    private void addField(JPanel p, String label, String key, JComponent comp, int row) {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 8, 2, 8);
        gbc.anchor = GridBagConstraints.EAST;
        gbc.gridx = 0; gbc.gridy = row;
        
        JLabel lbl = new JLabel(label + ":");
        lbl.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        p.add(lbl, gbc);
        
        gbc.gridx = 1; gbc.anchor = GridBagConstraints.WEST; gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        comp.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        p.add(comp, gbc);
        
        if (!fields.containsKey(key)) fields.put(key, comp);
    }

    private String[] fetchList(String sql) {
        try {
            List<Map<String, Object>> rows = db.query(sql);
            List<String> list = new ArrayList<>();
            list.add(""); // Empty default
            for (Map<String, Object> r : rows) {
                list.add(r.values().iterator().next().toString());
            }
            return list.toArray(new String[0]);
        } catch (Exception e) {
            return new String[]{""};
        }
    }

    private void loadData() {
        try {
            Map<String, Object> r = db.queryOne("SELECT * FROM employees WHERE emp_id=?", empId);
            if (r == null) return;
            
            for (Map.Entry<String, JComponent> entry : fields.entrySet()) {
                String k = entry.getKey();
                JComponent c = entry.getValue();
                Object val = r.get(k);
                String s = (val == null) ? "" : val.toString();
                
                if (val instanceof java.sql.Date || val instanceof java.util.Date) {
                    s = new SimpleDateFormat("dd-MM-yyyy").format(val);
                }

                if (c instanceof JTextField) ((JTextField) c).setText(s);
                else if (c instanceof JComboBox) ((JComboBox<?>) c).setSelectedItem(s);
                else if (c instanceof JTextArea) ((JTextArea) c).setText(s);
            }
            
            // Lock emp_id if editing
            if (fields.get("emp_id") instanceof JTextField) {
                ((JTextField) fields.get("emp_id")).setEditable(false);
                ((JTextField) fields.get("emp_id")).setBackground(new Color(0xf0f0f0));
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Error loading employee: " + e.getMessage());
        }
    }

    private void save() {
        String eid = getVal("emp_id");
        String name = getVal("emp_name");
        
        if (eid.isEmpty() || name.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Emp ID and Name are required.", "Required", JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            Object[] data = {
                getVal("emp_name"), parseDate("dob"), parseDate("doj"), getVal("gender"),
                getVal("email"), getVal("phone"), getVal("department"), getVal("designation"),
                getVal("shift"), getVal("blood_group"), getVal("address"), getVal("emergency_contact"),
                getVal("bank_account"), getVal("pan_number"), getVal("aadhaar"),
                Double.parseDouble(getVal("basic_salary").isEmpty() ? "0" : getVal("basic_salary")),
                getVal("status"), 
                Integer.parseInt(getVal("device_id").isEmpty() ? "0" : getVal("device_id")),
                Integer.parseInt(getVal("finger_id").isEmpty() ? "0" : getVal("finger_id")),
                getVal("device_enroll_id").isEmpty() ? null : getVal("device_enroll_id")
            };

            if (empId == null) {
                // Insert
                Object[] fullData = new Object[data.length + 1];
                fullData[0] = eid;
                System.arraycopy(data, 0, fullData, 1, data.length);
                db.execute("INSERT INTO employees (emp_id, emp_name, dob, doj, gender, email, phone, " +
                    "department, designation, shift, blood_group, address, emergency_contact, " +
                    "bank_account, pan_number, aadhaar, basic_salary, status, device_id, finger_id, device_enroll_id) " +
                    "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)", fullData);
            } else {
                // Update
                Object[] updateData = new Object[data.length + 1];
                System.arraycopy(data, 0, updateData, 0, data.length);
                updateData[data.length] = empId;
                db.execute("UPDATE employees SET emp_name=?, dob=?, doj=?, gender=?, email=?, phone=?, " +
                    "department=?, designation=?, shift=?, blood_group=?, address=?, emergency_contact=?, " +
                    "bank_account=?, pan_number=?, aadhaar=?, basic_salary=?, status=?, device_id=?, finger_id=?, device_enroll_id=? " +
                    "WHERE emp_id=?", updateData);
            }
            
            JOptionPane.showMessageDialog(this, "Employee saved successfully.");
            if (callback != null) callback.run();
            dispose();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Error saving employee: " + e.getMessage());
        }
    }

    private String getVal(String key) {
        JComponent c = fields.get(key);
        if (c instanceof JTextField) return ((JTextField) c).getText().trim();
        if (c instanceof JComboBox) {
            Object selected = ((JComboBox<?>) c).getSelectedItem();
            return selected == null ? "" : selected.toString().trim();
        }
        if (c instanceof JTextArea) return ((JTextArea) c).getText().trim();
        return "";
    }

    private java.sql.Date parseDate(String key) {
        String s = getVal(key);
        if (s.isEmpty() || s.contains("D")) return null;
        try {
            // Support dd-mm-yyyy or yyyy-mm-dd
            if (s.matches("\\d{2}-\\d{2}-\\d{4}")) {
                String[] p = s.split("-");
                return java.sql.Date.valueOf(p[2] + "-" + p[1] + "-" + p[0]);
            }
            return java.sql.Date.valueOf(s);
        } catch (Exception e) {
            return null;
        }
    }
}
