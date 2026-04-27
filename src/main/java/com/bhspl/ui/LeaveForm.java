package com.bhspl.ui;

import com.bhspl.db.DatabaseManager;
import com.bhspl.util.UIHelper;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import java.awt.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Modern, high-fidelity Leave Application Form.
 * Undecorated, rounded, and premium styled.
 */
public class LeaveForm extends JDialog {
    private final Integer id;
    private final Runnable callback;
    private final DatabaseManager db = DatabaseManager.INSTANCE;
    private JTextField empIdField, fromField, toField, daysField, reasonField;
    private JComboBox<String> typeCombo, statusCombo;
    private Point initialClick;

    public LeaveForm(JFrame parent, Integer id, Runnable callback) {
        super(parent, id == null ? "New Leave Application" : "Edit Leave Request", true);
        this.id = id;
        this.callback = callback;
        
        setUndecorated(true);
        setBackground(new Color(0, 0, 0, 0));
        setSize(480, 580);
        UIHelper.centerWindow(this, 480, 580);
        buildUI();
        if (id != null) loadData();
        setVisible(true);
    }

    private void buildUI() {
        UIHelper.RoundedPanel root = new UIHelper.RoundedPanel(24);
        root.setBackground(Color.WHITE);
        root.setLayout(new MigLayout("fill, ins 0, gap 0, wrap", "[grow]", "[] [grow] []"));

        // Dragging Support
        root.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mousePressed(java.awt.event.MouseEvent e) { initialClick = e.getPoint(); }
        });
        root.addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
            public void mouseDragged(java.awt.event.MouseEvent e) {
                int thisX = getLocation().x; int thisY = getLocation().y;
                int xMoved = e.getX() - initialClick.x; int yMoved = e.getY() - initialClick.y;
                setLocation(thisX + xMoved, thisY + yMoved);
            }
        });

        // Header
        UIHelper.GradientPanel hdr = new UIHelper.GradientPanel(UIHelper.PRIMARY, UIHelper.SECONDARY);
        hdr.setLayout(new MigLayout("ins 20", "[] 15 [grow] []"));
        
        JLabel iconLbl = new JLabel(new com.formdev.flatlaf.extras.FlatSVGIcon("icons/leave_policy.svg", 32, 32));
        hdr.add(iconLbl);
        
        JLabel title = new JLabel(id == null ? "Apply Leave Request" : "Update Leave Details");
        title.setFont(new Font("Segoe UI", Font.BOLD, 18));
        title.setForeground(Color.WHITE);
        hdr.add(title);

        JButton closeBtn = new JButton();
        try {
            com.formdev.flatlaf.extras.FlatSVGIcon cIcon = new com.formdev.flatlaf.extras.FlatSVGIcon("icons/x.svg", 16, 16);
            cIcon.setColorFilter(new com.formdev.flatlaf.extras.FlatSVGIcon.ColorFilter(c -> Color.WHITE));
            closeBtn.setIcon(cIcon);
        } catch (Exception e) { closeBtn.setText("X"); }
        closeBtn.setBorderPainted(false);
        closeBtn.setContentAreaFilled(false);
        closeBtn.setFocusPainted(false);
        closeBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        closeBtn.addActionListener(e -> dispose());
        hdr.add(closeBtn, "right");

        root.add(hdr, "growx, h 80!");

        // Form Content
        JPanel form = new JPanel(new MigLayout("ins 30, wrap 2, gapy 12, fillx", "[shrink] 20 [grow, fill]"));
        form.setBackground(Color.WHITE);

        addField(form, "Employee ID *", empIdField = tf("e.g. 101"));
        
        addField(form, "Leave Type", typeCombo = combo(fetchTypes()));
        
        addField(form, "From Date", fromField = tf("dd-mm-yyyy"));
        fromField.setText(LocalDate.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy")));
        
        addField(form, "To Date", toField = tf("dd-mm-yyyy"));
        toField.setText(LocalDate.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy")));
        
        addField(form, "Total Days", daysField = tf("e.g. 1.0"));
        daysField.setText("1.0");
        
        addField(form, "Reason", reasonField = tf("Reason for leave"));
        addField(form, "Status", statusCombo = combo("Pending", "Approved", "Rejected"));

        root.add(new JScrollPane(form), "grow, push");

        // Footer
        JPanel footer = new JPanel(new MigLayout("ins 20, gap 12", "push [] []"));
        footer.setBackground(new Color(0xF8FAFC));
        
        JButton cancelBtn = UIHelper.makeButton("Cancel", new Color(0x64748B), "x.svg");
        cancelBtn.addActionListener(e -> dispose());
        
        JButton saveBtn = UIHelper.makeButton(id == null ? "Submit Application" : "Save Changes", UIHelper.SUCCESS, "check.svg");
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

    private JComboBox<String> combo(String... items) {
        JComboBox<String> cb = new JComboBox<>(items);
        cb.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        cb.setPreferredSize(new Dimension(0, 32));
        return cb;
    }

    private String[] fetchTypes() {
        try {
            List<Map<String, Object>> rows = db.query("SELECT leave_type FROM leave_policy WHERE status='Active'");
            List<String> list = new ArrayList<>();
            for (Map<String, Object> r : rows) list.add(r.get("leave_type").toString());
            if (list.isEmpty()) return new String[]{"Casual Leave", "Sick Leave", "Earned Leave", "LWP"};
            return list.toArray(new String[0]);
        } catch (Exception e) { return new String[]{"Casual Leave", "Sick Leave", "Earned Leave", "LWP"}; }
    }

    private void loadData() {
        try {
            Map<String, Object> r = db.queryOne("SELECT * FROM leaves WHERE id=?", id);
            if (r != null) {
                empIdField.setText(DatabaseManager.str(r, "emp_id"));
                typeCombo.setSelectedItem(DatabaseManager.str(r, "leave_type"));
                fromField.setText(fmtDate(r.get("from_date")));
                toField.setText(fmtDate(r.get("to_date")));
                daysField.setText(DatabaseManager.str(r, "days"));
                reasonField.setText(DatabaseManager.str(r, "reason"));
                statusCombo.setSelectedItem(DatabaseManager.str(r, "status"));
            }
        } catch (Exception e) {}
    }

    private String fmtDate(Object v) {
        if (v == null) return "";
        if (v instanceof java.sql.Date) return DateTimeFormatter.ofPattern("dd-MM-yyyy").format(((java.sql.Date) v).toLocalDate());
        return v.toString();
    }

    private void save() {
        String eid = empIdField.getText().trim();
        if (eid.isEmpty()) { UIHelper.showError(this, "Employee ID is required."); return; }
        
        try {
            String isoFrom = parseDate(fromField.getText().trim());
            String isoTo = parseDate(toField.getText().trim());
            double days = Double.parseDouble(daysField.getText().trim().isEmpty() ? "1" : daysField.getText().trim());
            String status = statusCombo.getSelectedItem().toString();
            String ltype = typeCombo.getSelectedItem().toString();
            String reason = reasonField.getText().trim();

            if (id == null) {
                db.execute("INSERT INTO leaves (emp_id, leave_type, from_date, to_date, days, reason, status, applied_on) VALUES (?,?,?,?,?,?,?,NOW())", 
                    eid, ltype, isoFrom, isoTo, days, reason, status);
            } else {
                db.execute("UPDATE leaves SET emp_id=?, leave_type=?, from_date=?, to_date=?, days=?, reason=?, status=? WHERE id=?", 
                    eid, ltype, isoFrom, isoTo, days, reason, status, id);
            }

            if ("Approved".equals(status)) {
                LocalDate start = LocalDate.parse(isoFrom);
                LocalDate end = LocalDate.parse(isoTo);
                for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
                    db.execute("INSERT INTO attendance (emp_id, punch_date, status, remarks) VALUES (?,?,?,?) ON DUPLICATE KEY UPDATE status=?, remarks=?", 
                        eid, d.toString(), ltype, "Leave Approved", ltype, "Leave Approved");
                }
            }
            
            UIHelper.showSuccess(this, "Leave application saved successfully.");
            callback.run(); 
            dispose();
        } catch (Exception e) {
            UIHelper.showError(this, "Error: " + e.getMessage());
        }
    }

    private String parseDate(String s) {
        if (s.isEmpty()) return null;
        if (s.matches("\\d{2}-\\d{2}-\\d{4}")) {
            String[] p = s.split("-"); 
            return p[2] + "-" + p[1] + "-" + p[0];
        }
        return s;
    }
}
