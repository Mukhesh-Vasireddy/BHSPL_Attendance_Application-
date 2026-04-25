package com.bhspl.ui;

import com.bhspl.db.DatabaseManager;
import com.bhspl.util.UIHelper;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.Map;

/**
 * Modernized Leave Policy form with all advanced HR options and pro-rata rules.
 */
public class LeavePolicyForm extends JDialog {
    private final Integer id;
    private final Runnable callback;
    private final DatabaseManager db = DatabaseManager.INSTANCE;
    private JTextField typeField, daysField, maxCarryField, expireField, minServiceField, descField;
    private JComboBox<String> creditCombo, carryCombo, encashCombo, genderCombo, statusCombo, proRataCombo;

    public LeavePolicyForm(JFrame parent, Integer id, Runnable callback) {
        super(parent, id == null ? "Add Leave Policy" : "Edit Leave Policy", true);
        this.id = id; this.callback = callback;

        setUndecorated(true);
        setSize(550, 680);
        UIHelper.centerWindow(this, 550, 680);
        buildUI();
        if (id != null) loadData();
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

        JLabel title = new JLabel("Leave Policy Master");
        title.setFont(UIHelper.FNT_TITLE.deriveFont(16f));
        title.setForeground(Color.WHITE);
        try {
            com.formdev.flatlaf.extras.FlatSVGIcon icon = new com.formdev.flatlaf.extras.FlatSVGIcon("icons/leave_policy.svg", 24, 24);
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

        // Body with MigLayout
        JPanel form = new JPanel(new MigLayout("ins 35, wrap 2, gapy 15, fillx", "[180!]30[grow]"));
        form.setBackground(UIHelper.BG_CARD);

        addLabel(form, "Leave Type *");
        typeField = createField(); form.add(typeField, "growx");

        addLabel(form, "Days Per Year");
        daysField = createField(); daysField.setText("0.0"); form.add(daysField, "w 120!");

        addLabel(form, "Credit Method");
        creditCombo = new JComboBox<>(new String[]{"Monthly", "Quarterly", "Yearly", "Manual", "Joining"});
        creditCombo.setFont(UIHelper.FNT_MAIN); form.add(creditCombo, "growx");

        addLabel(form, "Carry Forward");
        carryCombo = new JComboBox<>(new String[]{"No", "Yes"});
        carryCombo.setFont(UIHelper.FNT_MAIN); form.add(carryCombo, "growx");

        addLabel(form, "Max Carry Limit");
        maxCarryField = createField(); maxCarryField.setText("0.0"); form.add(maxCarryField, "w 120!");

        addLabel(form, "Expire (months)");
        expireField = createField(); expireField.setText("0"); form.add(expireField, "w 120!");

        addLabel(form, "Encashable");
        encashCombo = new JComboBox<>(new String[]{"No", "Yes"});
        encashCombo.setFont(UIHelper.FNT_MAIN); form.add(encashCombo, "growx");

        addLabel(form, "Pro-rata Basis");
        proRataCombo = new JComboBox<>(new String[]{"No", "Yes"});
        proRataCombo.setSelectedIndex(1);
        proRataCombo.setFont(UIHelper.FNT_MAIN); form.add(proRataCombo, "growx");

        addLabel(form, "Applicable Gender");
        genderCombo = new JComboBox<>(new String[]{"All", "Male", "Female"});
        genderCombo.setFont(UIHelper.FNT_MAIN); form.add(genderCombo, "growx");

        addLabel(form, "Min Service (days)");
        minServiceField = createField(); minServiceField.setText("0"); form.add(minServiceField, "w 120!");

        addLabel(form, "Status");
        statusCombo = new JComboBox<>(new String[]{"Active", "Inactive"});
        statusCombo.setFont(UIHelper.FNT_MAIN); form.add(statusCombo, "growx");

        addLabel(form, "Description");
        descField = createField(); form.add(descField, "growx");

        root.add(form, BorderLayout.CENTER);

        // Footer
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 15, 20));
        footer.setBackground(UIHelper.BG_CARD);
        footer.setBorder(new EmptyBorder(0, 0, 15, 15));

        JButton cancelBtn = UIHelper.makeButton("Cancel", new Color(0x64748B));
        cancelBtn.addActionListener(e -> dispose());
        footer.add(cancelBtn);

        JButton saveBtn = UIHelper.makeButton("Save Policy", UIHelper.SUCCESS);
        saveBtn.addActionListener(e -> save());
        footer.add(saveBtn);

        root.add(footer, BorderLayout.SOUTH);
        setContentPane(root);
    }

    private JTextField createField() {
        JTextField f = new JTextField();
        f.setFont(UIHelper.FNT_MAIN);
        f.setPreferredSize(new Dimension(0, 38));
        return f;
    }

    private void addLabel(JPanel p, String text) {
        JLabel l = new JLabel(text);
        l.setFont(UIHelper.FNT_BOLD);
        l.setForeground(UIHelper.TEXT_DARK);
        p.add(l);
    }

    private void loadData() {
        try {
            Map<String, Object> r = db.queryOne("SELECT * FROM leave_policy WHERE id=?", id);
            if (r != null) {
                typeField.setText(DatabaseManager.str(r, "leave_type"));
                daysField.setText(DatabaseManager.str(r, "days_per_year"));
                creditCombo.setSelectedItem(DatabaseManager.str(r, "credit_method"));
                carryCombo.setSelectedIndex(DatabaseManager.num(r, "carry_forward"));
                maxCarryField.setText(DatabaseManager.str(r, "max_carry"));
                expireField.setText(DatabaseManager.str(r, "expire_months"));
                encashCombo.setSelectedIndex(DatabaseManager.num(r, "encashable"));
                genderCombo.setSelectedItem(DatabaseManager.str(r, "applicable_gender"));
                minServiceField.setText(DatabaseManager.str(r, "min_service_days"));
                statusCombo.setSelectedItem(DatabaseManager.str(r, "status"));
                descField.setText(DatabaseManager.str(r, "description"));
                
                try { proRataCombo.setSelectedIndex(DatabaseManager.num(r, "pro_rata")); } catch(Exception ignored) {}
            } else {
                UIHelper.showError(this, "Could not find policy data for ID: " + id);
            }
        } catch (Exception e) {
            UIHelper.showError(this, "Error loading policy: " + e.getMessage());
        }
    }

    private void save() {
        String type = typeField.getText().trim();
        if (type.isEmpty()) { 
            UIHelper.showError(this, "Leave Type is required."); 
            typeField.requestFocus();
            return; 
        }
        
        try {
            double days;
            try {
                days = Double.parseDouble(daysField.getText().trim().isEmpty() ? "0" : daysField.getText().trim());
                if (days < 0) throw new NumberFormatException();
            } catch (NumberFormatException e) {
                UIHelper.showError(this, "Days Per Year must be a valid positive number.");
                daysField.requestFocus();
                return;
            }

            double max;
            try {
                max = Double.parseDouble(maxCarryField.getText().trim().isEmpty() ? "0" : maxCarryField.getText().trim());
                if (max < 0) throw new NumberFormatException();
            } catch (NumberFormatException e) {
                UIHelper.showError(this, "Max Carry Limit must be a valid positive number.");
                maxCarryField.requestFocus();
                return;
            }

            int exp;
            try {
                exp = Integer.parseInt(expireField.getText().trim().isEmpty() ? "0" : expireField.getText().trim());
                if (exp < 0) throw new NumberFormatException();
            } catch (NumberFormatException e) {
                UIHelper.showError(this, "Expire Months must be a valid positive integer.");
                expireField.requestFocus();
                return;
            }

            int minSvc;
            try {
                minSvc = Integer.parseInt(minServiceField.getText().trim().isEmpty() ? "0" : minServiceField.getText().trim());
                if (minSvc < 0) throw new NumberFormatException();
            } catch (NumberFormatException e) {
                UIHelper.showError(this, "Min Service Days must be a valid positive integer.");
                minServiceField.requestFocus();
                return;
            }
            
            Object[] data = {
                type, days, creditCombo.getSelectedItem(), carryCombo.getSelectedIndex(), 
                max, exp, encashCombo.getSelectedIndex(), genderCombo.getSelectedItem(), 
                minSvc, descField.getText().trim(), statusCombo.getSelectedItem(), proRataCombo.getSelectedIndex()
            };

            if (id == null) {
                db.execute("INSERT INTO leave_policy (leave_type, days_per_year, credit_method, carry_forward, " +
                        "max_carry, expire_months, encashable, applicable_gender, min_service_days, description, status, pro_rata) " +
                        "VALUES (?,?,?,?,?,?,?,?,?,?,?,?)", data);
            } else {
                Object[] uData = new Object[data.length + 1];
                System.arraycopy(data, 0, uData, 0, data.length); uData[data.length] = id;
                db.execute("UPDATE leave_policy SET leave_type=?, days_per_year=?, credit_method=?, carry_forward=?, " +
                        "max_carry=?, expire_months=?, encashable=?, applicable_gender=?, min_service_days=?, description=?, status=?, pro_rata=? WHERE id=?", uData);
            }
            
            db.commit();
            UIHelper.showSuccess(this, "Leave policy saved successfully.");
            if (callback != null) callback.run(); 
            dispose();
        } catch (Exception e) {
            UIHelper.showError(this, "Error saving policy: " + e.getMessage());
        }
    }
}

