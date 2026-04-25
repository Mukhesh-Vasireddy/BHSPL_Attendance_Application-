package com.bhspl.ui;

import com.bhspl.db.DatabaseManager;
import com.bhspl.util.UIHelper;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import java.awt.*;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public class LeavePanel extends JPanel {

    private UIHelper.StyledTablePanel tablePanel;
    private JComboBox<String> statusFilter;

    private static final String[] COLUMNS = {
        "ID", "Emp ID", "Name", "Leave Type", "From", "To", "Days", "Status", "Applied On"
    };

    public LeavePanel() {
        setBackground(UIHelper.BG_MAIN);
        setLayout(new MigLayout("ins 24, wrap, gap 0, fill", "[grow]", "[] 16 [grow]"));
        buildUI();
        loadData();
    }

    private void buildUI() {
        // Toolbar
        JPanel toolbar = new JPanel(new MigLayout("ins 0, gap 12", "[] 8 [] push [] 8 [] 8 [] 8 []"));
        toolbar.setOpaque(false);

        statusFilter = new JComboBox<>(new String[]{"All", "Pending", "Approved", "Rejected"});
        statusFilter.setFont(UIHelper.FNT_MEDIUM);
        statusFilter.addActionListener(e -> loadData());

        JButton applyBtn   = UIHelper.makeButton("Apply Leave", UIHelper.PRIMARY);
        JButton approveBtn = UIHelper.makeButton("Approve", UIHelper.SUCCESS);
        JButton rejectBtn  = UIHelper.makeButton("Reject", UIHelper.DANGER);
        JButton refreshBtn = UIHelper.makeButton("Refresh", new Color(0x334155));

        applyBtn.addActionListener(e -> openApplyDialog());
        approveBtn.addActionListener(e -> updateStatus("Approved"));
        rejectBtn.addActionListener(e -> updateStatus("Rejected"));
        refreshBtn.addActionListener(e -> loadData());

        toolbar.add(new JLabel("Status:"));
        toolbar.add(statusFilter);
        toolbar.add(applyBtn, "right");
        toolbar.add(approveBtn);
        toolbar.add(rejectBtn);
        toolbar.add(refreshBtn);

        add(toolbar, "growx");

        // Table
        tablePanel = new UIHelper.StyledTablePanel(COLUMNS);
        tablePanel.setBorder(UIHelper.createCardBorder());
        tablePanel.getTable().getColumnModel().getColumn(0).setMaxWidth(60);
        add(tablePanel, "grow, push");
    }

    private void loadData() {
        tablePanel.clearRows();
        String filter = (String) statusFilter.getSelectedItem();
        SwingWorker<List<Map<String, Object>>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<Map<String, Object>> doInBackground() throws Exception {
                String sql = "SELECT l.id, l.emp_id, e.emp_name, l.leave_type, " +
                    "l.from_date, l.to_date, l.days, l.status, l.applied_on " +
                    "FROM leaves l JOIN employees e ON l.emp_id=e.emp_id";
                if (!"All".equals(filter)) sql += " WHERE l.status='" + filter + "'";
                sql += " ORDER BY l.applied_on DESC";
                return DatabaseManager.getInstance().fetchAll(sql);
            }
            @Override
            protected void done() {
                try {
                    for (Map<String, Object> r : get()) {
                        tablePanel.addRow(new Object[]{
                            r.get("id"), r.get("emp_id"), r.get("emp_name"),
                            r.get("leave_type"), r.get("from_date"), r.get("to_date"),
                            r.get("days"), r.get("status"), r.get("applied_on")
                        });
                    }
                } catch (Exception ignored) {}
            }
        };
        worker.execute();
    }

    private void updateStatus(String newStatus) {
        Object idVal = tablePanel.getSelectedValue();
        if (idVal == null) { JOptionPane.showMessageDialog(this, "Select a leave record first."); return; }
        
        int id = (int) idVal;
        try {
            DatabaseManager.getInstance().execute("UPDATE leaves SET status=? WHERE id=?", newStatus, id);
            loadData();
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Error: " + ex.getMessage());
        }
    }

    private void openApplyDialog() {
        JDialog dlg = new JDialog((JFrame) SwingUtilities.getWindowAncestor(this), "Apply Leave Request", true);
        dlg.setSize(420, 380);
        UIHelper.centerWindow(dlg, 420, 380);

        JPanel p = new JPanel(new MigLayout("ins 24, wrap, gap 12", "[][grow]", "[]"));
        p.setBackground(Color.WHITE);

        JTextField empId     = new JTextField(14);
        JComboBox<String> leaveType = new JComboBox<>(new String[]{"Casual","Sick","Earned","Maternity","Paternity","Unpaid"});
        JTextField fromDate  = new JTextField(LocalDate.now().toString(), 14);
        JTextField toDate    = new JTextField(LocalDate.now().toString(), 14);
        JTextField days      = new JTextField("1", 14);
        JTextField reason    = new JTextField(14);

        Object[][] rows = {
            {"Emp ID", empId}, {"Leave Type", leaveType}, {"From Date", fromDate},
            {"To Date", toDate}, {"Days Count", days}, {"Reason", reason}
        };
        for (Object[] row : rows) {
            p.add(new JLabel((String) row[0]));
            p.add((Component) row[1], "growx");
        }

        JButton save = UIHelper.makeButton("Submit Application", UIHelper.SUCCESS);
        save.addActionListener(e -> {
            try {
                String eId = empId.getText().trim();
                if (eId.isEmpty()) {
                    JOptionPane.showMessageDialog(dlg, "Employee ID is required.");
                    return;
                }

                // Check if employee exists
                Map<String, Object> emp = DatabaseManager.getInstance().fetchOne("SELECT * FROM employees WHERE emp_id=?", eId);
                if (emp == null) {
                    JOptionPane.showMessageDialog(dlg, "Employee ID not found.");
                    return;
                }

                int d;
                try {
                    d = Integer.parseInt(days.getText().trim());
                } catch (NumberFormatException ex) {
                    JOptionPane.showMessageDialog(dlg, "Days must be a valid number.");
                    return;
                }

                DatabaseManager.getInstance().execute(
                    "INSERT INTO leaves (emp_id, leave_type, from_date, to_date, days, reason, status) VALUES (?,?,?,?,?,?,'Pending')",
                    eId, leaveType.getSelectedItem().toString(),
                    fromDate.getText().trim(), toDate.getText().trim(), d,
                    reason.getText().trim()
                );
                
                JOptionPane.showMessageDialog(dlg, "Leave applied successfully.");
                dlg.dispose();
                loadData();
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(dlg, "Error: " + ex.getMessage());
                ex.printStackTrace();
            }
        });

        p.add(save, "skip, growx, gaptop 12");
        dlg.setContentPane(p);
        dlg.setVisible(true);
    }
}
