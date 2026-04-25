package com.bhspl.ui;

import com.bhspl.db.DatabaseManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.sql.SQLException;
import java.util.Map;

public class EmployeeFormDialog extends JDialog {

    private final String empId; // null = new employee
    private boolean saved = false;

    private final Color PRIMARY = Color.decode("#1a237e");
    private final Color SUCCESS = Color.decode("#2e7d32");
    private final Color DANGER  = Color.decode("#c62828");

    // Personal
    private JTextField fEmpId, fName, fPhone, fEmail, fDob, fDoj;
    private JComboBox<String> fGender, fBloodGroup, fStatus;
    // Job
    private JTextField fDept, fDesig, fBasicSalary;
    private JComboBox<String> fShift;
    // Extras
    private JTextField fAddress, fEmContact, fBank, fPan, fAadhaar;

    public EmployeeFormDialog(JFrame parent, String empId) {
        super(parent, empId == null ? "Add New Employee" : "Edit Employee – " + empId, true);
        this.empId = empId;
        setSize(620, 640);
        setLocationRelativeTo(parent);
        setResizable(false);
        buildUI();
        if (empId != null) loadEmployee(empId);
    }

    private void buildUI() {
        JPanel main = new JPanel(new BorderLayout(0, 8));
        main.setBorder(new EmptyBorder(14, 16, 14, 16));
        setContentPane(main);

        JTabbedPane tabs = new JTabbedPane();
        tabs.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        tabs.addTab("Personal",  buildPersonalTab());
        tabs.addTab("Job",       buildJobTab());
        tabs.addTab("Documents", buildDocTab());
        main.add(tabs, BorderLayout.CENTER);

        // Buttons
        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        JButton saveBtn   = new JButton("Save");
        saveBtn.setBackground(SUCCESS);
        saveBtn.setForeground(Color.WHITE);
        saveBtn.setFocusPainted(false);
        saveBtn.addActionListener(e -> onSave());

        JButton cancelBtn = new JButton("Cancel");
        cancelBtn.setFocusPainted(false);
        cancelBtn.addActionListener(e -> dispose());

        btnRow.add(cancelBtn);
        btnRow.add(saveBtn);
        main.add(btnRow, BorderLayout.SOUTH);
    }

    private JPanel buildPersonalTab() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBorder(new EmptyBorder(10, 10, 10, 10));
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(5, 6, 5, 6);
        g.fill = GridBagConstraints.HORIZONTAL;

        fEmpId = tf(); fName = tf(); fPhone = tf(); fEmail = tf();
        fDob = tf("YYYY-MM-DD"); fDoj = tf("YYYY-MM-DD");
        fGender = combo("Male", "Female", "Other");
        fBloodGroup = combo("A+","A-","B+","B-","AB+","AB-","O+","O-");
        fStatus = combo("Active", "Inactive");

        Object[][] rows = {
            {"Emp ID *",    fEmpId},
            {"Full Name *", fName},
            {"Phone",       fPhone},
            {"Email",       fEmail},
            {"Date of Birth", fDob},
            {"Date of Join",  fDoj},
            {"Gender",      fGender},
            {"Blood Group", fBloodGroup},
            {"Status",      fStatus},
        };
        addRows(p, g, rows);

        if (empId != null) fEmpId.setEditable(false);
        return p;
    }

    private JPanel buildJobTab() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBorder(new EmptyBorder(10, 10, 10, 10));
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(5, 6, 5, 6);
        g.fill = GridBagConstraints.HORIZONTAL;

        fDept = tf(); fDesig = tf(); fBasicSalary = tf("0.00");
        fShift = combo("General", "Morning", "Evening", "Night");

        Object[][] rows = {
            {"Department",    fDept},
            {"Designation",   fDesig},
            {"Shift",         fShift},
            {"Basic Salary ₹",fBasicSalary},
        };
        addRows(p, g, rows);
        return p;
    }

    private JPanel buildDocTab() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBorder(new EmptyBorder(10, 10, 10, 10));
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(5, 6, 5, 6);
        g.fill = GridBagConstraints.HORIZONTAL;

        fAddress = tf(); fEmContact = tf(); fBank = tf(); fPan = tf(); fAadhaar = tf();

        Object[][] rows = {
            {"Address",           fAddress},
            {"Emergency Contact", fEmContact},
            {"Bank Account",      fBank},
            {"PAN Number",        fPan},
            {"Aadhaar Number",    fAadhaar},
        };
        addRows(p, g, rows);
        return p;
    }

    private void addRows(JPanel panel, GridBagConstraints g, Object[][] rows) {
        for (int i = 0; i < rows.length; i++) {
            g.gridx = 0; g.gridy = i; g.weightx = 0.3;
            JLabel lbl = new JLabel((String) rows[i][0]);
            lbl.setFont(new Font("Segoe UI", Font.BOLD, 12));
            panel.add(lbl, g);

            g.gridx = 1; g.weightx = 0.7;
            panel.add((Component) rows[i][1], g);
        }
    }

    private JTextField tf() { return tf(null); }
    private JTextField tf(String placeholder) {
        JTextField f = new JTextField(18);
        f.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        if (placeholder != null) f.putClientProperty("JTextField.placeholderText", placeholder);
        return f;
    }
    private JComboBox<String> combo(String... items) {
        JComboBox<String> cb = new JComboBox<>(items);
        cb.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        return cb;
    }

    private void loadEmployee(String id) {
        try {
            Map<String, Object> r = DatabaseManager.getInstance()
                .fetchOne("SELECT * FROM employees WHERE emp_id=?", id);
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
        String id = fEmpId.getText().trim();
        String name = fName.getText().trim();
        if (id.isEmpty() || name.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Emp ID and Name are required.");
            return;
        }
        try {
            DatabaseManager db = DatabaseManager.getInstance();
            if (empId == null) {
                db.execute(
                    "INSERT INTO employees (emp_id, emp_name, phone, email, dob, doj, gender, blood_group, status, " +
                    "department, designation, shift, basic_salary, address, emergency_contact, bank_account, pan_number, aadhaar) " +
                    "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    id, name, val(fPhone), val(fEmail), val(fDob), val(fDoj),
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
            JOptionPane.showMessageDialog(this, "Employee saved successfully.");
            dispose();
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Error: " + ex.getMessage());
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
