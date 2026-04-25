package com.bhspl.ui;

import com.bhspl.db.DatabaseManager;
import com.bhspl.util.UIHelper;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import java.awt.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Form to manually add a raw punch log.
 */
public class RawPunchForm extends JDialog {

    private final DatabaseManager db = DatabaseManager.INSTANCE;
    private final Runnable callback;
    private JTextField empIdField, deviceIdField, timeField;
    private JComboBox<Integer> typeCombo;
    private final DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public RawPunchForm(JFrame parent, Runnable callback) {
        super(parent, "Add Raw Punch Log", true);
        this.callback = callback;
        setSize(400, 350);
        UIHelper.centerWindow(this, 400, 350);
        
        getContentPane().setBackground(Color.WHITE);
        setLayout(new MigLayout("ins 24, wrap, gap 12", "[grow, fill]", "[] 16 [grow] 24 []"));

        buildUI();
    }

    private void buildUI() {
        // Header
        JLabel title = new JLabel("Manual Raw Punch Entry");
        title.setFont(UIHelper.FNT_TITLE);
        title.setForeground(UIHelper.PRIMARY);
        add(title);

        // Form
        JPanel form = new JPanel(new MigLayout("ins 0, wrap 2, gap 16", "[right][grow, fill]"));
        form.setOpaque(false);

        empIdField = new JTextField(15);
        deviceIdField = new JTextField("1", 15);
        timeField = new JTextField(LocalDateTime.now().format(dtf), 15);
        
        // 0=Check-In, 1=Check-Out, etc. (standard ZK types)
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

        Object[][] rows = {
            {"Enroll / Emp ID *", empIdField},
            {"Device ID *", deviceIdField},
            {"Punch Time *", timeField},
            {"Punch Type", typeCombo}
        };

        for (Object[] row : rows) {
            JLabel lbl = new JLabel((String) row[0]);
            lbl.setFont(UIHelper.FNT_BOLD);
            form.add(lbl);
            
            Component comp = (Component) row[1];
            comp.setFont(UIHelper.FNT_MAIN);
            if (comp instanceof JTextField) ((JTextField) comp).setPreferredSize(new Dimension(0, 32));
            form.add(comp);
        }
        add(form, "grow");

        // Footer
        JPanel footer = new JPanel(new MigLayout("ins 0, gap 12", "grow [] 8 []"));
        footer.setOpaque(false);

        JButton saveBtn = UIHelper.makeButton("Save", UIHelper.SUCCESS);
        saveBtn.addActionListener(e -> save());
        
        JButton cancelBtn = UIHelper.makeButton("Cancel", UIHelper.DANGER);
        cancelBtn.addActionListener(e -> dispose());

        footer.add(saveBtn, "cell 1 0, h 36!, w 80!");
        footer.add(cancelBtn, "cell 2 0, h 36!, w 80!");
        add(footer, "growx");

        getRootPane().setDefaultButton(saveBtn);
    }

    private void save() {
        String empId = empIdField.getText().trim();
        String devId = deviceIdField.getText().trim();
        String timeStr = timeField.getText().trim();

        if (empId.isEmpty() || devId.isEmpty() || timeStr.isEmpty()) {
            JOptionPane.showMessageDialog(this, "All starred fields are required.", "Input Error", JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            // Validate time format
            LocalDateTime.parse(timeStr, dtf);

            db.execute("INSERT INTO raw_logs (device_id, emp_id, punch_time, punch_type, synced) VALUES (?,?,?,?,0)",
                Integer.parseInt(devId), empId, timeStr, typeCombo.getSelectedItem());

            JOptionPane.showMessageDialog(this, "Raw log added successfully.");
            if (callback != null) callback.run();
            dispose();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Error: " + e.getMessage(), "Database Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}
