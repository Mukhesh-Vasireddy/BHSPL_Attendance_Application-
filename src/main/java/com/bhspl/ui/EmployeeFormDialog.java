package com.bhspl.ui;

import com.bhspl.db.DatabaseManager;
import com.bhspl.util.UIHelper;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.sql.SQLException;
import java.util.Map;

/**
 * Modern, High-Fidelity Employee Management Form.
 * Redesigned with a premium aesthetic and organized tabbed layout.
 */
public class EmployeeFormDialog extends JDialog {

    private final String empId; // null = new employee
    private boolean saved = false;
    private final DatabaseManager db = DatabaseManager.getInstance();

    // Personal
    private JTextField fEmpId, fName, fPhone, fEmail, fDob, fDoj;
    private JComboBox<String> fGender, fBloodGroup, fStatus;
    // Job
    private JTextField fDept, fDesig, fBasicSalary;
    private JComboBox<String> fShift;
    // Extras
    private JTextField fAddress, fEmContact, fBank, fPan, fAadhaar;

    public EmployeeFormDialog(JFrame parent, String empId) {
        super(parent, empId == null ? "Add New Employee" : "Edit Employee [" + empId + "]", true);
        this.empId = empId;
        
        setSize(700, 750);
        UIHelper.centerWindow(this, 700, 750);
        buildUI();
        if (empId != null) loadEmployee(empId);
        setVisible(true);
    }

    private void buildUI() {
        JPanel root = new JPanel(new MigLayout("fill, ins 0, gap 0, wrap", "[grow]", "[] [grow] []"));
        root.setBackground(Color.WHITE);

        // Header
        UIHelper.GradientPanel hdr = new UIHelper.GradientPanel(UIHelper.PRIMARY, UIHelper.SECONDARY);
        hdr.setLayout(new MigLayout("ins 20", "[] 15 [grow]"));
        JLabel iconLbl = new JLabel(new com.formdev.flatlaf.extras.FlatSVGIcon("icons/employees.svg", 32, 32));
        hdr.add(iconLbl);
        
        JLabel title = new JLabel(empId == null ? "New Employee Registration" : "Update Employee Profile");
        title.setFont(new Font("Segoe UI", Font.BOLD, 20));
        title.setForeground(Color.WHITE);
        hdr.add(title);
        root.add(hdr, "growx, h 80!");

        // Content Area (Tabs)
        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(new Font("Segoe UI", Font.BOLD, 13));
        tabs.setBackground(Color.WHITE);
        tabs.setOpaque(true);
        
        tabs.addTab("Personal Details", buildTab(buildPersonalFields()));
        tabs.addTab("Professional Info", buildTab(buildJobFields()));
        tabs.addTab("Legal & Banking", buildTab(buildDocFields()));
        
        root.add(tabs, "grow, push, ins 10 20 10 20");

        // Footer
        JPanel footer = new JPanel(new MigLayout("ins 20, gap 12", "push [] []"));
        footer.setBackground(new Color(0xF8FAFC));
        
        JButton cancelBtn = UIHelper.makeButton("Cancel", new Color(0x64748B), "x.svg");
        cancelBtn.addActionListener(e -> dispose());
        
        JButton saveBtn = UIHelper.makeButton("Save Employee", UIHelper.SUCCESS, "check.svg");
        saveBtn.addActionListener(e -> onSave());
        
        footer.add(cancelBtn);
        footer.add(saveBtn);
        root.add(footer, "growx");

        setContentPane(root);
    }

    private JPanel buildTab(JPanel content) {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(Color.WHITE);
        p.add(new JScrollPane(content), BorderLayout.CENTER);
        return p;
    }

    private JPanel buildPersonalFields() {
        JPanel p = new JPanel(new MigLayout("ins 20, wrap 2, gapy 15, fillx", "[shrink] 20 [grow, fill]"));
        p.setBackground(Color.WHITE);

        fEmpId = tf("e.g. EMP001"); if (empId != null) fEmpId.setEditable(false);
        fName = tf("Full Name");
        fPhone = tf("Mobile Number");
        fEmail = tf("email@example.com");
        fDob = tf("YYYY-MM-DD");
        fDoj = tf("YYYY-MM-DD");
        fGender = combo("Male", "Female", "Other");
        fBloodGroup = combo("A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-");
        fStatus = combo("Active", "Inactive");

        addField(p, "Employee ID *", fEmpId);
        addField(p, "Full Name *", fName);
        addField(p, "Phone Number", fPhone);
        addField(p, "Email Address", fEmail);
        addField(p, "Date of Birth", fDob);
        addField(p, "Joining Date", fDoj);
        addField(p, "Gender", fGender);
        addField(p, "Blood Group", fBloodGroup);
        addField(p, "Employment Status", fStatus);
        
        return p;
    }

    private JPanel buildJobFields() {
        JPanel p = new JPanel(new MigLayout("ins 20, wrap 2, gapy 15, fillx", "[shrink] 20 [grow, fill]"));
        p.setBackground(Color.WHITE);

        fDept = tf("Department");
        fDesig = tf("Designation");
        fShift = combo("General", "Morning", "Evening", "Night");
        fBasicSalary = tf("0.00");

        addField(p, "Department", fDept);
        addField(p, "Designation", fDesig);
        addField(p, "Default Shift", fShift);
        addField(p, "Monthly CTC / Salary", fBasicSalary);
        
        return p;
    }

    private JPanel buildDocFields() {
        JPanel p = new JPanel(new MigLayout("ins 20, wrap 2, gapy 15, fillx", "[shrink] 20 [grow, fill]"));
        p.setBackground(Color.WHITE);

        fAddress = tf("Residential Address");
        fEmContact = tf("Emergency Name / Number");
        fBank = tf("Account No & IFSC");
        fPan = tf("PAN Number");
        fAadhaar = tf("Aadhaar Number");

        addField(p, "Full Address", fAddress);
        addField(p, "Emergency Contact", fEmContact);
        addField(p, "Bank Account Info", fBank);
        addField(p, "PAN Card Number", fPan);
        addField(p, "Aadhaar Card Number", fAadhaar);
        
        return p;
    }

    private void addField(JPanel p, String label, JComponent field) {
        JLabel l = new JLabel(label);
        l.setFont(new Font("Segoe UI", Font.BOLD, 13));
        l.setForeground(new Color(0x475569));
        p.add(l);
        p.add(field);
    }

    private JTextField tf(String placeholder) {
        JTextField f = new JTextField();
        f.putClientProperty("JTextField.placeholderText", placeholder);
        f.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        return f;
    }

    private JComboBox<String> combo(String... items) {
        JComboBox<String> cb = new JComboBox<>(items);
        cb.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        return cb;
    }

    private void loadEmployee(String id) {
        try {
            Map<String, Object> r = db.fetchOne("SELECT * FROM employees WHERE emp_id=?", id);
            if (r == null) return;
            setText(fEmpId, r.get("emp_id"));
            setText(fName, r.get("emp_name"));
            setText(fPhone, r.get("phone"));
            setText(fEmail, r.get("email"));
            setText(fDob, r.get("dob"));
            setText(fDoj, r.get("doj"));
            setCombo(fGender, r.get("gender"));
            setCombo(fBloodGroup, r.get("blood_group"));
            setCombo(fStatus, r.get("status"));
            setText(fDept, r.get("department"));
            setText(fDesig, r.get("designation"));
            setCombo(fShift, r.get("shift"));
            setText(fBasicSalary, r.get("basic_salary"));
            setText(fAddress, r.get("address"));
            setText(fEmContact, r.get("emergency_contact"));
            setText(fBank, r.get("bank_account"));
            setText(fPan, r.get("pan_number"));
            setText(fAadhaar, r.get("aadhaar"));
        } catch (Exception ignored) {}
    }

    private void setText(JTextField f, Object val) { if (val != null) f.setText(val.toString()); }
    private void setCombo(JComboBox<String> cb, Object val) {
        if (val != null) cb.setSelectedItem(val.toString());
    }

    private void onSave() {
        String idStr = fEmpId.getText().trim();
        String name = fName.getText().trim();
        if (idStr.isEmpty() || name.isEmpty()) {
            UIHelper.showError(this, "Employee ID and Name are mandatory.");
            return;
        }
        try {
            if (empId == null) {
                db.execute(
                    "INSERT INTO employees (emp_id, emp_name, phone, email, dob, doj, gender, blood_group, status, " +
                    "department, designation, shift, basic_salary, address, emergency_contact, bank_account, pan_number, aadhaar) " +
                    "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    idStr, name, val(fPhone), val(fEmail), val(fDob), val(fDoj),
                    fGender.getSelectedItem(), fBloodGroup.getSelectedItem(), fStatus.getSelectedItem(),
                    val(fDept), val(fDesig), fShift.getSelectedItem(), salaryVal(),
                    val(fAddress), val(fEmContact), val(fBank), val(fPan), val(fAadhaar)
                );
            } else {
                db.execute(
                    "UPDATE employees SET emp_name=?, phone=?, email=?, dob=?, doj=?, gender=?, blood_group=?, status=?, " +
                    "department=?, designation=?, shift=?, basic_salary=?, address=?, emergency_contact=?, " +
                    "bank_account=?, pan_number=?, aadhaar=? WHERE emp_id=?",
                    name, val(fPhone), val(fEmail), val(fDob), val(fDoj),
                    fGender.getSelectedItem(), fBloodGroup.getSelectedItem(), fStatus.getSelectedItem(),
                    val(fDept), val(fDesig), fShift.getSelectedItem(), salaryVal(),
                    val(fAddress), val(fEmContact), val(fBank), val(fPan), val(fAadhaar),
                    empId
                );
            }
            saved = true;
            UIHelper.showSuccess(this, "Employee profile updated successfully.");
            dispose();
        } catch (SQLException ex) {
            UIHelper.showError(this, "Database Error: " + ex.getMessage());
        }
    }

    private String val(JTextField f) {
        String s = f.getText().trim();
        return s.isEmpty() ? null : s;
    }
    private Double salaryVal() {
        try { return Double.parseDouble(fBasicSalary.getText().trim()); }
        catch (NumberFormatException e) { return 0.0; }
    }

    public boolean isSaved() { return saved; }
}
