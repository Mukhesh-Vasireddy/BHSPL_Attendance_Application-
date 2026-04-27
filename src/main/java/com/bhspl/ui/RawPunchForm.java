package com.bhspl.ui;

import com.bhspl.db.DatabaseManager;
import com.bhspl.util.UIHelper;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import java.awt.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Modern form to manually add a raw punch log with premium UI.
 */
public class RawPunchForm extends JDialog {

    private final DatabaseManager db = DatabaseManager.INSTANCE;
    private final Runnable callback;
    private JTextField empIdField, deviceIdField, timeField;
    private JComboBox<Integer> typeCombo;
    private final DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public RawPunchForm(JFrame parent, Runnable callback) {
        super(parent, "Manual Punch Entry", true);
        this.callback = callback;
        
        setSize(450, 500);
        UIHelper.centerWindow(this, 450, 500);
        buildUI();
        setVisible(true);
    }

    private void buildUI() {
        JPanel root = new JPanel(new MigLayout("fill, ins 0, gap 0, wrap", "[grow]", "[] [grow] []"));
        root.setBackground(Color.WHITE);

        // Header
        UIHelper.GradientPanel hdr = new UIHelper.GradientPanel(UIHelper.PRIMARY, UIHelper.SECONDARY);
        hdr.setLayout(new MigLayout("ins 20", "[] 15 [grow]"));
        hdr.add(new JLabel(new com.formdev.flatlaf.extras.FlatSVGIcon("icons/raw_logs.svg", 32, 32)));
        
        JLabel title = new JLabel("Add Manual Raw Punch");
        title.setFont(new Font("Segoe UI", Font.BOLD, 18));
        title.setForeground(Color.WHITE);
        hdr.add(title);
        root.add(hdr, "growx, h 80!");

        // Content
        JPanel form = new JPanel(new MigLayout("ins 30, wrap 2, gapy 15, fillx", "[shrink] 20 [grow, fill]"));
        form.setBackground(Color.WHITE);

        addField(form, "Enroll / Emp ID *", empIdField = tf("e.g. 101"));
        addField(form, "Device ID *", deviceIdField = tf("1"));
        addField(form, "Punch Time *", timeField = tf("yyyy-MM-dd HH:mm:ss"));
        timeField.setText(LocalDateTime.now().format(dtf));

        typeCombo = new JComboBox<>(new Integer[]{0, 1, 2, 3, 4, 5});
        typeCombo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                String label = value.toString();
                switch ((Integer) value) {
                    case 0: label = "0 - Check In"; break;
                    case 1: label = "1 - Check Out"; break;
                    case 2: label = "2 - Break Out"; break;
                    case 3: label = "3 - Break In"; break;
                    case 4: label = "4 - OT In"; break;
                    case 5: label = "5 - OT Out"; break;
                }
                return super.getListCellRendererComponent(list, label, index, isSelected, cellHasFocus);
            }
        });
        addField(form, "Punch Type", typeCombo);

        root.add(form, "grow, push");

        // Footer
        JPanel footer = new JPanel(new MigLayout("ins 20, gap 12", "push [] []"));
        footer.setBackground(new Color(0xF8FAFC));
        
        JButton cancelBtn = UIHelper.makeButton("Cancel", new Color(0x64748B), "x.svg");
        cancelBtn.addActionListener(e -> dispose());
        
        JButton saveBtn = UIHelper.makeButton("Save Punch", UIHelper.SUCCESS, "check.svg");
        saveBtn.addActionListener(e -> save());
        
        footer.add(cancelBtn);
        footer.add(saveBtn);
        root.add(footer, "growx");

        setContentPane(root);
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

    private void save() {
        String empId = empIdField.getText().trim();
        String devId = deviceIdField.getText().trim();
        String timeStr = timeField.getText().trim();

        if (empId.isEmpty() || devId.isEmpty() || timeStr.isEmpty()) {
            UIHelper.showError(this, "All starred fields are required.");
            return;
        }

        try {
            LocalDateTime.parse(timeStr, dtf);
            db.execute("INSERT INTO raw_logs (device_id, emp_id, punch_time, punch_type, synced) VALUES (?,?,?,?,0)",
                Integer.parseInt(devId), empId, timeStr, typeCombo.getSelectedItem());

            UIHelper.showSuccess(this, "Raw log added successfully.");
            if (callback != null) callback.run();
            dispose();
        } catch (Exception e) {
            UIHelper.showError(this, "Error: " + e.getMessage());
        }
    }
}
