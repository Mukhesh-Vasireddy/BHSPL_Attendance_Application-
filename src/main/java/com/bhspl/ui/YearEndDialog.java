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
 * Modern Year-End processing dialog with carry-forward rules and targeting.
 */
public class YearEndDialog extends JDialog {
    private final Runnable callback;
    private final DatabaseManager db = DatabaseManager.INSTANCE;
    private JComboBox<String> fromYearCombo, toYearCombo, typeCombo, applyToCombo, deptCombo;

    public YearEndDialog(JFrame parent, Runnable callback) {
        super(parent, "Year End Processing", true);
        this.callback = callback;
        setUndecorated(true);
        setSize(580, 500);
        UIHelper.centerWindow(this, 580, 500);
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

        JLabel title = new JLabel("Year End Carry Forward");
        title.setFont(UIHelper.FNT_TITLE.deriveFont(16f));
        title.setForeground(Color.WHITE);
        try {
            com.formdev.flatlaf.extras.FlatSVGIcon icon = new com.formdev.flatlaf.extras.FlatSVGIcon("icons/sync.svg", 24, 24);
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

        // Body with correct CSS spacing
        JPanel form = new JPanel(new MigLayout("ins 35, wrap 2, gapy 20, fillx", "[180!]30[grow]"));
        form.setBackground(UIHelper.BG_CARD);

        int y = LocalDate.now().getYear();
        
        addLabel(form, "From Year");
        fromYearCombo = new JComboBox<>(new String[]{String.valueOf(y-1), String.valueOf(y)});
        fromYearCombo.setSelectedItem(String.valueOf(y-1));
        fromYearCombo.setFont(UIHelper.FNT_MAIN);
        fromYearCombo.setPreferredSize(new Dimension(0, 38));
        form.add(fromYearCombo, "w 140!");

        addLabel(form, "To Year");
        toYearCombo = new JComboBox<>(new String[]{String.valueOf(y), String.valueOf(y+1)});
        toYearCombo.setSelectedItem(String.valueOf(y));
        toYearCombo.setFont(UIHelper.FNT_MAIN);
        toYearCombo.setPreferredSize(new Dimension(0, 38));
        form.add(toYearCombo, "w 140!");

        addLabel(form, "Leave Type");
        typeCombo = new JComboBox<>(fetchLeaveTypes());
        typeCombo.setFont(UIHelper.FNT_MAIN);
        typeCombo.setPreferredSize(new Dimension(0, 38));
        form.add(typeCombo, "growx");

        addLabel(form, "Apply To");
        applyToCombo = new JComboBox<>(new String[]{"All Active Employees", "Specific Department"});
        applyToCombo.setFont(UIHelper.FNT_MAIN);
        applyToCombo.setPreferredSize(new Dimension(0, 38));
        applyToCombo.addActionListener(e -> updateFieldStates());
        form.add(applyToCombo, "growx");

        addLabel(form, "Department");
        deptCombo = new JComboBox<>(fetchDepts());
        deptCombo.setFont(UIHelper.FNT_MAIN);
        deptCombo.setPreferredSize(new Dimension(0, 38));
        form.add(deptCombo, "growx");

        JTextArea hint = new JTextArea("This process will calculate and carry forward balances from the 'From Year' to 'To Year' based on individual leave policy rules (Max Carry Forward limits).");
        hint.setWrapStyleWord(true); hint.setLineWrap(true); hint.setEditable(false); hint.setOpaque(false);
        hint.setFont(new Font("Segoe UI", Font.ITALIC, 11));
        hint.setForeground(UIHelper.TEXT_LIGHT);
        form.add(hint, "span 2, growx, gaptop 15");

        root.add(form, BorderLayout.CENTER);

        // Footer
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 15, 20));
        footer.setBackground(UIHelper.BG_CARD);
        footer.setBorder(new EmptyBorder(0, 0, 15, 15));

        JButton cancelBtn = UIHelper.makeButton("Close", new Color(0x64748B));
        cancelBtn.addActionListener(e -> dispose());
        footer.add(cancelBtn);

        JButton runBtn = UIHelper.makeButton("Run Processing", UIHelper.SUCCESS);
        try {
            runBtn.setIcon(new com.formdev.flatlaf.extras.FlatSVGIcon("icons/sync.svg", 16, 16));
        } catch(Exception ignored) {}
        runBtn.addActionListener(e -> process());
        footer.add(runBtn);

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
        deptCombo.setEnabled("Specific Department".equals(applyToCombo.getSelectedItem()));
    }

    private String[] fetchLeaveTypes() {
        try {
            List<Map<String, Object>> rows = db.query("SELECT leave_type FROM leave_policy WHERE status='Active' ORDER BY leave_type");
            List<String> list = new ArrayList<>();
            list.add("All Leave Types");
            for (Map<String, Object> r : rows) list.add(r.get("leave_type").toString());
            return list.toArray(new String[0]);
        } catch (Exception e) { return new String[] { "All Leave Types" }; }
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

    private void process() {
        String from = (String) fromYearCombo.getSelectedItem();
        String to = (String) toYearCombo.getSelectedItem();
        String typeFilter = (String) typeCombo.getSelectedItem();
        String applyTo = (String) applyToCombo.getSelectedItem();
        String dept = (String) deptCombo.getSelectedItem();
        
        try {
            String policySql = "SELECT * FROM leave_policy WHERE carry_forward=1";
            if (!"All Leave Types".equals(typeFilter)) policySql += " AND leave_type='" + typeFilter + "'";
            
            List<Map<String, Object>> policies = db.query(policySql);
            if (policies.isEmpty()) {
                UIHelper.showError(this, "No leave policies found with Carry Forward enabled for the selection.");
                return;
            }

            int count = 0;
            for (Map<String, Object> pol : policies) {
                String type = DatabaseManager.str(pol, "leave_type");
                double max = DatabaseManager.dbl(pol, "max_carry");
                
                String insertSql = "INSERT INTO leave_balance (emp_id, leave_type, year, carry_fwd, opening_bal, closing_bal) " +
                    "SELECT b.emp_id, b.leave_type, ?, LEAST(b.closing_bal, ?), LEAST(b.closing_bal, ?), LEAST(b.closing_bal, ?) " +
                    "FROM leave_balance b JOIN employees e ON b.emp_id = e.emp_id " +
                    "WHERE b.year=? AND b.leave_type=? AND e.status='Active'";
                
                List<Object> params = new ArrayList<>();
                params.add(to); params.add(max); params.add(max); params.add(max); params.add(from); params.add(type);

                if ("Specific Department".equals(applyTo) && !"All Departments".equals(dept)) {
                    insertSql += " AND e.department=?";
                    params.add(dept);
                }
                
                insertSql += " ON DUPLICATE KEY UPDATE carry_fwd=VALUES(carry_fwd), opening_bal=VALUES(opening_bal), closing_bal=VALUES(closing_bal)";
                
                db.execute(insertSql, params.toArray());
                count++;
            }
            
            UIHelper.showSuccess(this, "Year end processing complete for " + count + " leave types.");
            if (callback != null) callback.run();
            dispose();
        } catch (Exception e) {
            UIHelper.showError(this, "Error: " + e.getMessage());
        }
    }
}
