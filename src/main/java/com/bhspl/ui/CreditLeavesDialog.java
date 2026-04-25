package com.bhspl.ui;

import com.bhspl.db.DatabaseManager;
import com.bhspl.util.UIHelper;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Modernized Credit Leaves dialog with advanced targeting and policy-driven defaults.
 */
public class CreditLeavesDialog extends JDialog {
    private final Runnable callback;
    private final DatabaseManager db = DatabaseManager.INSTANCE;
    private JTextField yearField, amountField, empIdField;
    private JComboBox<String> typeCombo, deptCombo, applyToCombo, modeCombo;

    public CreditLeavesDialog(JFrame parent, Runnable callback) {
        super(parent, "Credit Leaves to Employees", true);
        this.callback = callback;
        setUndecorated(true);
        setSize(620, 580);
        UIHelper.centerWindow(this, 620, 580);
        buildUI();
        updateFieldStates();
        setVisible(true);
    }

    private void buildUI() {
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(UIHelper.BG_CARD);
        root.setBorder(BorderFactory.createLineBorder(UIHelper.BORDER, 1));

        // Premium Header
        UIHelper.GradientPanel header = new UIHelper.GradientPanel(UIHelper.PRIMARY, UIHelper.SECONDARY);
        header.setPreferredSize(new Dimension(0, 60));
        header.setLayout(new BorderLayout());
        header.setBorder(new EmptyBorder(0, 20, 0, 10));

        JLabel title = new JLabel("Credit Leaves to Employees");
        title.setFont(UIHelper.FNT_TITLE.deriveFont(16f));
        title.setForeground(Color.WHITE);
        try {
            com.formdev.flatlaf.extras.FlatSVGIcon icon = new com.formdev.flatlaf.extras.FlatSVGIcon("icons/leave_balance.svg", 24, 24);
            icon.setColorFilter(new com.formdev.flatlaf.extras.FlatSVGIcon.ColorFilter(c -> Color.WHITE));
            title.setIcon(icon);
            title.setIconTextGap(12);
        } catch (Exception ignored) {}
        header.add(title, BorderLayout.WEST);

        JButton closeBtn = new JButton("\u00D7");
        closeBtn.setFont(new Font("Segoe UI", Font.PLAIN, 28));
        closeBtn.setForeground(Color.WHITE);
        closeBtn.setContentAreaFilled(false);
        closeBtn.setBorderPainted(false);
        closeBtn.setFocusPainted(false);
        closeBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        closeBtn.addActionListener(e -> dispose());
        header.add(closeBtn, BorderLayout.EAST);

        root.add(header, BorderLayout.NORTH);

        // Body with wider column constraints and proper filling
        JPanel form = new JPanel(new MigLayout("ins 35, wrap 2, gapy 20, fillx", "[200!]30[grow]"));
        form.setBackground(UIHelper.BG_CARD);

        addLabel(form, "Year *");
        yearField = new JTextField(String.valueOf(LocalDate.now().getYear()));
        yearField.setFont(UIHelper.FNT_MAIN);
        yearField.setPreferredSize(new Dimension(0, 38));
        form.add(yearField, "w 120!");

        addLabel(form, "Leave Type *");
        typeCombo = new JComboBox<>(fetchLeaveTypes());
        typeCombo.setFont(UIHelper.FNT_MAIN);
        typeCombo.setPreferredSize(new Dimension(0, 38));
        form.add(typeCombo, "growx");

        addLabel(form, "Days to Credit *");
        JPanel amtRow = new JPanel(new MigLayout("ins 0, gap 10"));
        amtRow.setOpaque(false);
        amountField = new JTextField("0");
        amountField.setFont(UIHelper.FNT_MAIN);
        amtRow.add(amountField, "w 100!, h 38!");
        JLabel hint1 = new JLabel("(0 = use policy default)");
        hint1.setFont(new Font("Segoe UI", Font.ITALIC, 11));
        hint1.setForeground(UIHelper.TEXT_LIGHT);
        amtRow.add(hint1);
        form.add(amtRow, "growx");

        addLabel(form, "Apply To");
        applyToCombo = new JComboBox<>(new String[]{"All Active Employees", "Specific Department", "Single Employee"});
        applyToCombo.setFont(UIHelper.FNT_MAIN);
        applyToCombo.setPreferredSize(new Dimension(0, 38));
        applyToCombo.addActionListener(e -> updateFieldStates());
        form.add(applyToCombo, "growx");

        addLabel(form, "Department");
        deptCombo = new JComboBox<>(fetchDepts());
        deptCombo.setFont(UIHelper.FNT_MAIN);
        deptCombo.setPreferredSize(new Dimension(0, 38));
        form.add(deptCombo, "growx");

        addLabel(form, "Employee ID");
        JPanel empRow = new JPanel(new MigLayout("ins 0, gap 10"));
        empRow.setOpaque(false);
        empIdField = new JTextField();
        empIdField.setFont(UIHelper.FNT_MAIN);
        empRow.add(empIdField, "w 180!, h 38!");
        JLabel hint2 = new JLabel("(for Single Employee only)");
        hint2.setFont(new Font("Segoe UI", Font.ITALIC, 11));
        hint2.setForeground(UIHelper.TEXT_LIGHT);
        empRow.add(hint2);
        form.add(empRow, "growx");

        addLabel(form, "If balance exists");
        modeCombo = new JComboBox<>(new String[]{"Add to existing", "Overwrite Existing"});
        modeCombo.setFont(UIHelper.FNT_MAIN);
        modeCombo.setPreferredSize(new Dimension(0, 38));
        form.add(modeCombo, "growx");

        root.add(form, BorderLayout.CENTER);

        // Footer
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 15, 20));
        footer.setBackground(UIHelper.BG_CARD);
        footer.setBorder(new EmptyBorder(0, 0, 15, 15));

        JButton cancelBtn = UIHelper.makeButton("Close", new Color(0xDC2626));
        try {
            cancelBtn.setIcon(new com.formdev.flatlaf.extras.FlatSVGIcon("icons/absent.svg", 16, 16));
        } catch(Exception ignored) {}
        cancelBtn.addActionListener(e -> dispose());
        footer.add(cancelBtn);

        JButton saveBtn = UIHelper.makeButton("Credit Now", UIHelper.SUCCESS);
        try {
            saveBtn.setIcon(new com.formdev.flatlaf.extras.FlatSVGIcon("icons/present.svg", 16, 16));
        } catch(Exception ignored) {}
        saveBtn.addActionListener(e -> credit());
        footer.add(saveBtn);

        root.add(footer, BorderLayout.SOUTH);
        setContentPane(root);
    }

    private void addLabel(JPanel p, String text) {
        JLabel l = new JLabel(text);
        l.setFont(UIHelper.FNT_BOLD);
        l.setForeground(UIHelper.TEXT_DARK);
        p.add(l);
    }

    private void updateFieldStates() {
        String applyTo = (String) applyToCombo.getSelectedItem();
        deptCombo.setEnabled("Specific Department".equals(applyTo));
        empIdField.setEnabled("Single Employee".equals(applyTo));
        if (!empIdField.isEnabled()) empIdField.setText("");
    }

    private String[] fetchLeaveTypes() {
        try {
            List<Map<String, Object>> rows = db.query("SELECT leave_type FROM leave_policy WHERE status='Active' ORDER BY leave_type");
            List<String> list = new ArrayList<>();
            for (Map<String, Object> r : rows) list.add(r.get("leave_type").toString());
            return list.isEmpty() ? new String[] { "Casual Leave", "Sick Leave" } : list.toArray(new String[0]);
        } catch (Exception e) { return new String[] { "Casual Leave" }; }
    }

    private String[] fetchDepts() {
        try {
            List<Map<String, Object>> rows = db.query("SELECT dept_name FROM departments WHERE status='Active' ORDER BY dept_name");
            List<String> list = new ArrayList<>();
            list.add("All Departments");
            for (Map<String, Object> r : rows) list.add(r.get("dept_name").toString());
            return list.toArray(new String[0]);
        } catch (Exception e) { return new String[] { "All Departments" }; }
    }

    private void credit() {
        String yr = yearField.getText().trim();
        String type = (String) typeCombo.getSelectedItem();
        String applyTo = (String) applyToCombo.getSelectedItem();
        String dept = (String) deptCombo.getSelectedItem();
        String eidSingle = empIdField.getText().trim();
        String mode = (String) modeCombo.getSelectedItem();
        
        if (yr.isEmpty()) { UIHelper.showError(this, "Year is required."); return; }

        double amt;
        try {
            amt = Double.parseDouble(amountField.getText().trim());
            if (amt == 0) {
                Map<String, Object> pol = db.queryOne("SELECT days_per_year FROM leave_policy WHERE leave_type=?", type);
                if (pol != null) amt = DatabaseManager.dbl(pol, "days_per_year");
            }
        } catch (Exception e) { UIHelper.showError(this, "Invalid credit amount."); return; }

        try {
            StringBuilder sql = new StringBuilder("SELECT emp_id FROM employees WHERE status='Active'");
            List<Object> params = new ArrayList<>();
            
            if ("Specific Department".equals(applyTo) && !"All Departments".equals(dept)) {
                sql.append(" AND department=?");
                params.add(dept);
            } else if ("Single Employee".equals(applyTo)) {
                if (eidSingle.isEmpty()) { UIHelper.showError(this, "Employee ID is required."); return; }
                sql.append(" AND emp_id=?");
                params.add(eidSingle);
            }

            List<Map<String, Object>> emps = db.query(sql.toString(), params.toArray());
            if (emps.isEmpty()) {
                UIHelper.showError(this, "No active employees found for the selected criteria.");
                return;
            }

            for (Map<String, Object> e : emps) {
                String eid = DatabaseManager.str(e, "emp_id");
                if ("Overwrite Existing".equals(mode)) {
                    db.execute("INSERT INTO leave_balance (emp_id, leave_type, year, credited, closing_bal) VALUES (?,?,?,?,?) " +
                            "ON DUPLICATE KEY UPDATE credited=?, closing_bal=(opening_bal + carry_fwd + ? - used - lapsed)", 
                            eid, type, yr, amt, amt, amt, amt);
                } else {
                    db.execute("INSERT INTO leave_balance (emp_id, leave_type, year, credited, closing_bal) VALUES (?,?,?,?,?) " +
                            "ON DUPLICATE KEY UPDATE credited=credited+?, closing_bal=closing_bal+?", 
                            eid, type, yr, amt, amt, amt, amt);
                }
            }
            
            UIHelper.showSuccess(this, "Successfully credited " + amt + " " + type + " to " + emps.size() + " employees.");
            if (callback != null) callback.run();
            dispose();
        } catch (Exception e) {
            UIHelper.showError(this, "Error: " + e.getMessage());
        }
    }
}

