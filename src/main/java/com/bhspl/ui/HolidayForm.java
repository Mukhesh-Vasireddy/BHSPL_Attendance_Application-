package com.bhspl.ui;

import com.bhspl.db.DatabaseManager;
import com.bhspl.util.UIHelper;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;

public class HolidayForm extends JDialog {
    private final Integer id;
    private final Runnable callback;
    private final DatabaseManager db = DatabaseManager.INSTANCE;
    private JTextField dateField, nameField;
    private JComboBox<String> typeCombo;

    public HolidayForm(JFrame parent, Integer id, Runnable callback) {
        super(parent, id == null ? "Register Holiday" : "Edit Holiday #" + id, true);
        this.id = id; this.callback = callback;
        setUndecorated(true);
        setSize(450, 360); 
        UIHelper.centerWindow(this, 450, 360);
        buildUI();
        if (id != null) loadData();
        setVisible(true);
    }

    private void buildUI() {
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(UIHelper.BG_CARD);
        root.setBorder(BorderFactory.createLineBorder(UIHelper.BORDER, 1));

        // Premium Header with Bavya Gradient
        UIHelper.GradientPanel header = new UIHelper.GradientPanel(UIHelper.PRIMARY, UIHelper.SECONDARY);
        header.setPreferredSize(new Dimension(0, 60));
        header.setLayout(new BorderLayout());
        header.setBorder(new javax.swing.border.EmptyBorder(0, 20, 0, 10));

        JLabel title = new JLabel(id == null ? "Register New Holiday" : "Update Holiday Details");
        title.setFont(UIHelper.FNT_TITLE.deriveFont(16f));
        title.setForeground(Color.WHITE);
        
        // Header Icon
        try {
            com.formdev.flatlaf.extras.FlatSVGIcon hIcon = new com.formdev.flatlaf.extras.FlatSVGIcon("icons/holidays.svg", 24, 24);
            hIcon.setColorFilter(new com.formdev.flatlaf.extras.FlatSVGIcon.ColorFilter(c -> Color.WHITE));
            title.setIcon(hIcon);
            title.setIconTextGap(12);
        } catch (Exception ignored) {}
        header.add(title, BorderLayout.WEST);

        JButton closeBtn = new JButton("\u00D7"); // Use multiplication sign for better rendering
        closeBtn.setFont(new Font("Segoe UI", Font.PLAIN, 24));
        closeBtn.setForeground(Color.WHITE);
        closeBtn.setContentAreaFilled(false);
        closeBtn.setBorderPainted(false);
        closeBtn.setFocusPainted(false);
        closeBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        closeBtn.addActionListener(e -> dispose());
        header.add(closeBtn, BorderLayout.EAST);

        root.add(header, BorderLayout.NORTH);

        // Form Body with adjusted constraints to prevent overlap
        JPanel form = new JPanel(new MigLayout("ins 30, wrap 2, gapy 20, fillx", "[180!]20[grow]"));
        form.setBackground(UIHelper.BG_CARD);

        JLabel lblDate = new JLabel("Date (YYYY-MM-DD)");
        lblDate.setFont(UIHelper.FNT_BOLD);
        lblDate.setForeground(UIHelper.TEXT_DARK);
        try {
            lblDate.setIcon(new com.formdev.flatlaf.extras.FlatSVGIcon("icons/holidays.svg", 16, 16));
            lblDate.setIconTextGap(10);
        } catch (Exception ignored) {}
        form.add(lblDate);

        dateField = new JTextField();
        dateField.setFont(UIHelper.FNT_MAIN);
        dateField.setText(LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
        dateField.setPreferredSize(new Dimension(0, 38));
        form.add(dateField, "growx");

        JLabel lblName = new JLabel("Holiday Name");
        lblName.setFont(UIHelper.FNT_BOLD);
        lblName.setForeground(UIHelper.TEXT_DARK);
        try {
            lblName.setIcon(new com.formdev.flatlaf.extras.FlatSVGIcon("icons/edit.svg", 16, 16));
            lblName.setIconTextGap(10);
        } catch (Exception ignored) {}
        form.add(lblName);

        nameField = new JTextField();
        nameField.setFont(UIHelper.FNT_MAIN);
        nameField.setPreferredSize(new Dimension(0, 38));
        form.add(nameField, "growx");

        JLabel lblType = new JLabel("Type");
        lblType.setFont(UIHelper.FNT_BOLD);
        lblType.setForeground(UIHelper.TEXT_DARK);
        try {
            lblType.setIcon(new com.formdev.flatlaf.extras.FlatSVGIcon("icons/reports.svg", 16, 16));
            lblType.setIconTextGap(10);
        } catch (Exception ignored) {}
        form.add(lblType);

        typeCombo = new JComboBox<>(new String[]{
            "National Holiday", "Public Holiday", "Festival Holiday", "State Holiday", "Company Holiday", "Optional Holiday", "Restricted"
        });
        typeCombo.setFont(UIHelper.FNT_MAIN);
        typeCombo.setPreferredSize(new Dimension(0, 38));
        form.add(typeCombo, "growx");

        root.add(form, BorderLayout.CENTER);

        // Footer
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 15, 20));
        footer.setBackground(UIHelper.BG_CARD);
        footer.setBorder(new EmptyBorder(0, 0, 10, 10));
        
        JButton cancelBtn = UIHelper.makeButton("Cancel", new Color(0x64748B));
        cancelBtn.addActionListener(e -> dispose());
        footer.add(cancelBtn);

        JButton saveBtn = UIHelper.makeButton(id == null ? "Save Holiday" : "Update", UIHelper.PRIMARY);
        saveBtn.addActionListener(e -> save());
        footer.add(saveBtn);

        root.add(footer, BorderLayout.SOUTH);
        setContentPane(root);
    }

    private void loadData() {
        try {
            Map<String, Object> r = db.queryOne("SELECT * FROM holidays WHERE id=?", id);
            if (r != null) {
                Object d = r.get("holiday_date");
                if (d != null) {
                    if (d instanceof java.sql.Date) {
                        dateField.setText(DateTimeFormatter.ofPattern("yyyy-MM-dd").format(((java.sql.Date) d).toLocalDate()));
                    } else {
                        dateField.setText(d.toString());
                    }
                }
                nameField.setText(DatabaseManager.str(r, "holiday_name"));
                typeCombo.setSelectedItem(DatabaseManager.str(r, "holiday_type"));
            }
        } catch (Exception e) {
            UIHelper.showError(this, "Failed to load holiday: " + e.getMessage());
        }
    }

    private void save() {
        String name = nameField.getText().trim();
        String ds = dateField.getText().trim();
        if (name.isEmpty() || ds.isEmpty()) {
            UIHelper.showError(this, "Please fill all required fields.");
            return;
        }
        try {
            String isoDate;
            if (ds.matches("\\d{2}-\\d{2}-\\d{4}")) {
                String[] p = ds.split("-"); isoDate = p[2] + "-" + p[1] + "-" + p[0];
            } else {
                isoDate = ds;
            }
            
            if (id == null) {
                db.execute("INSERT INTO holidays (holiday_date, holiday_name, holiday_type) VALUES (?,?,?)",
                    isoDate, name, typeCombo.getSelectedItem());
            } else {
                db.execute("UPDATE holidays SET holiday_date=?, holiday_name=?, holiday_type=? WHERE id=?",
                    isoDate, name, typeCombo.getSelectedItem(), id);
            }
            callback.run(); 
            dispose();
        } catch (Exception e) {
            UIHelper.showError(this, "Error saving holiday: " + e.getMessage());
        }
    }
}
