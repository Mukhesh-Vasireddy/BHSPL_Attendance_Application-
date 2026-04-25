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
 * Modern Adjust Balance dialog with full breakdown of leave components 
 * and real-time calculation. Supports manual entry if no row selected.
 */
public class AdjustBalanceDialog extends JDialog {
    private String empId, leaveType, year;
    private final Runnable callback;
    private final com.bhspl.db.DatabaseManager db = com.bhspl.db.DatabaseManager.INSTANCE;
    private JTextField eidFld, yearFld, openingFld, creditedFld, carryFld, usedFld, lapsedFld, closingFld;
    private JComboBox<String> typeCombo;

    public AdjustBalanceDialog(JFrame parent, String eid, String type, String yr, Runnable callback) {
        super(parent, "Adjust Leave Balance", true);
        this.empId = eid;
        this.leaveType = type;
        this.year = yr;
        this.callback = callback;
        
        setUndecorated(true);
        setSize(540, 680);
        UIHelper.centerWindow(this, 540, 680);
        buildUI();
        if (empId != null) loadData();
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

        JLabel title = new JLabel("Adjust Leave Balance");
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

        // Body with correct CSS spacing
        JPanel form = new JPanel(new MigLayout("ins 35, wrap 2, gapy 15, fillx", "[180!]25[grow]"));
        form.setBackground(UIHelper.BG_CARD);

        addLabel(form, "Employee ID");
        eidFld = new JTextField(empId == null ? "" : empId);
        eidFld.setFont(UIHelper.FNT_BOLD); eidFld.setPreferredSize(new Dimension(0, 38));
        if (empId != null) eidFld.setEditable(false);
        else {
            eidFld.addFocusListener(new java.awt.event.FocusAdapter() {
                @Override public void focusLost(java.awt.event.FocusEvent e) { attemptLoad(); }
            });
        }
        form.add(eidFld, "growx");

        addLabel(form, "Year");
        yearFld = new JTextField(year == null ? String.valueOf(LocalDate.now().getYear()) : year);
        yearFld.setFont(UIHelper.FNT_BOLD); yearFld.setPreferredSize(new Dimension(0, 38));
        if (year != null) yearFld.setEditable(false);
        else {
            yearFld.addFocusListener(new java.awt.event.FocusAdapter() {
                @Override public void focusLost(java.awt.event.FocusEvent e) { attemptLoad(); }
            });
        }
        form.add(yearFld, "w 120!");

        addLabel(form, "Leave Type");
        if (leaveType != null) {
            JTextField tFld = new JTextField(leaveType); tFld.setEditable(false);
            tFld.setFont(UIHelper.FNT_BOLD); tFld.setPreferredSize(new Dimension(0, 38));
            form.add(tFld, "growx");
        } else {
            typeCombo = new JComboBox<>(fetchLeaveTypes());
            typeCombo.setFont(UIHelper.FNT_BOLD); typeCombo.setPreferredSize(new Dimension(0, 38));
            typeCombo.addActionListener(e -> attemptLoad());
            form.add(typeCombo, "growx");
        }

        JSeparator sep = new JSeparator();
        form.add(sep, "span 2, growx, gaptop 10, gapbottom 10");

        openingFld = createNumberField(); addLabel(form, "Opening Balance"); form.add(openingFld, "growx");
        creditedFld = createNumberField(); addLabel(form, "Credited");        form.add(creditedFld, "growx");
        carryFld    = createNumberField(); addLabel(form, "Carry Forward");   form.add(carryFld, "growx");
        usedFld     = createNumberField(); addLabel(form, "Used");            form.add(usedFld, "growx");
        lapsedFld   = createNumberField(); addLabel(form, "Lapsed");          form.add(lapsedFld, "growx");

        JSeparator sep2 = new JSeparator();
        form.add(sep2, "span 2, growx, gaptop 10, gapbottom 10");

        addLabel(form, "Closing Balance");
        closingFld = createNumberField();
        closingFld.setEditable(false);
        closingFld.setFont(UIHelper.FNT_BOLD.deriveFont(18f));
        closingFld.setForeground(UIHelper.PRIMARY);
        form.add(closingFld, "growx");

        root.add(form, BorderLayout.CENTER);

        // Footer
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 15, 20));
        footer.setBackground(UIHelper.BG_CARD);
        footer.setBorder(new EmptyBorder(0, 0, 15, 15));

        JButton cancelBtn = UIHelper.makeButton("Close", new Color(0x64748B));
        cancelBtn.addActionListener(e -> dispose());
        footer.add(cancelBtn);

        JButton saveBtn = UIHelper.makeButton("Update Balance", UIHelper.SUCCESS);
        saveBtn.addActionListener(e -> update());
        footer.add(saveBtn);

        root.add(footer, BorderLayout.SOUTH);
        setContentPane(root);
    }

    private void attemptLoad() {
        String eid = eidFld.getText().trim();
        String yr = yearFld.getText().trim();
        String type = typeCombo != null ? (String) typeCombo.getSelectedItem() : leaveType;
        
        if (!eid.isEmpty() && !yr.isEmpty() && type != null) {
            this.empId = eid;
            this.year = yr;
            this.leaveType = type;
            loadData();
        }
    }

    private String[] fetchLeaveTypes() {
        try {
            List<Map<String, Object>> rows = db.query("SELECT leave_type FROM leave_policy WHERE status='Active' ORDER BY leave_type");
            List<String> list = new ArrayList<>();
            for (Map<String, Object> r : rows) list.add(r.get("leave_type").toString());
            return list.toArray(new String[0]);
        } catch (Exception e) { return new String[] { "Casual Leave" }; }
    }

    private JTextField createNumberField() {
        JTextField f = new JTextField("0.0");
        f.setFont(UIHelper.FNT_MAIN);
        f.setPreferredSize(new Dimension(0, 38));
        f.addKeyListener(new java.awt.event.KeyAdapter() {
            @Override public void keyReleased(java.awt.event.KeyEvent e) { calculate(); }
        });
        return f;
    }

    private void addLabel(JPanel p, String text) {
        JLabel l = new JLabel(text);
        l.setFont(UIHelper.FNT_BOLD);
        l.setForeground(UIHelper.TEXT_DARK);
        p.add(l);
    }

    private void calculate() {
        try {
            double op = val(openingFld);
            double cr = val(creditedFld);
            double cf = val(carryFld);
            double us = val(usedFld);
            double lp = val(lapsedFld);
            double cl = op + cr + cf - us - lp;
            closingFld.setText(String.format("%.1f", cl));
        } catch (Exception ignored) {}
    }

    private double val(JTextField f) {
        try { return Double.parseDouble(f.getText().trim()); } catch (Exception e) { return 0; }
    }

    private void loadData() {
        try {
            Map<String, Object> r = db.queryOne(
                    "SELECT * FROM leave_balance WHERE emp_id=? AND leave_type=? AND year=?", empId, leaveType, year);
            if (r != null) {
                openingFld.setText(String.valueOf(r.getOrDefault("opening_bal", "0.0")));
                creditedFld.setText(String.valueOf(r.getOrDefault("credited", "0.0")));
                carryFld.setText(String.valueOf(r.getOrDefault("carry_fwd", "0.0")));
                usedFld.setText(String.valueOf(r.getOrDefault("used", "0.0")));
                lapsedFld.setText(String.valueOf(r.getOrDefault("lapsed", "0.0")));
                calculate();
            } else {
                openingFld.setText("0.0"); creditedFld.setText("0.0"); carryFld.setText("0.0");
                usedFld.setText("0.0"); lapsedFld.setText("0.0"); calculate();
            }
        } catch (Exception ignored) {}
    }

    private void update() {
        String eid = eidFld.getText().trim();
        String yr = yearFld.getText().trim();
        String type = typeCombo != null ? (String) typeCombo.getSelectedItem() : leaveType;

        if (eid.isEmpty() || yr.isEmpty() || type == null) {
            UIHelper.showError(this, "Please provide Employee ID, Year and Leave Type.");
            return;
        }

        try {
            double op = val(openingFld);
            double cr = val(creditedFld);
            double cf = val(carryFld);
            double us = val(usedFld);
            double lp = val(lapsedFld);
            double cl = op + cr + cf - us - lp;

            db.execute(
                    "INSERT INTO leave_balance (emp_id, leave_type, year, opening_bal, credited, carry_fwd, used, lapsed, closing_bal) " +
                    "VALUES (?,?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE " +
                    "opening_bal=?, credited=?, carry_fwd=?, used=?, lapsed=?, closing_bal=?",
                    eid, type, yr, op, cr, cf, us, lp, cl,
                    op, cr, cf, us, lp, cl);
            
            if (callback != null) callback.run();
            dispose();
        } catch (Exception e) {
            UIHelper.showError(this, "Error updating balance: " + e.getMessage());
        }
    }
}

