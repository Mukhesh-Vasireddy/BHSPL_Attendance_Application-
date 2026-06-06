package com.bhspl.ui;

import com.bhspl.db.DatabaseManager;
import com.bhspl.util.UIHelper;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import java.awt.*;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/**
 * Modern Leave Manager Panel with premium UI and integrated LeaveForm.
 */
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
        JPanel toolbar = new JPanel(new MigLayout("ins 0, gap 12", "[] 8 [] push [] 8 [] 8 [] 8 [] 8 []"));
        toolbar.setOpaque(false);

        statusFilter = new JComboBox<>(new String[] { "All", "Pending", "Approved", "Rejected" });
        statusFilter.setFont(UIHelper.FNT_MEDIUM);
        statusFilter.addActionListener(e -> loadData());

        JButton applyBtn = UIHelper.makeButton("Apply Leave", UIHelper.PRIMARY, "plus.svg");
        JButton editBtn = UIHelper.makeButton("Edit", new Color(0x6366F1), "edit.svg");
        JButton approveBtn = UIHelper.makeButton("Approve", UIHelper.SUCCESS, "check.svg");
        JButton rejectBtn = UIHelper.makeButton("Reject", UIHelper.DANGER, "x.svg");
        JButton refreshBtn = UIHelper.makeButton("Refresh", new Color(0x334155), "refresh.svg");

        applyBtn.addActionListener(e -> openApplyDialog(null));
        editBtn.addActionListener(e -> {
            Object id = tablePanel.getSelectedValue();
            if (id == null) {
                UIHelper.showError(this, "Select a leave record to edit.");
                return;
            }
            openApplyDialog((Integer) id);
        });
        approveBtn.addActionListener(e -> updateStatus("Approved"));
        rejectBtn.addActionListener(e -> updateStatus("Rejected"));
        refreshBtn.addActionListener(e -> loadData());

        toolbar.add(new JLabel("Status:"));
        toolbar.add(statusFilter);
        toolbar.add(applyBtn, "right");
        toolbar.add(editBtn);
        toolbar.add(approveBtn);
        toolbar.add(rejectBtn);
        toolbar.add(refreshBtn);

        add(toolbar, "growx");

        // Table
        tablePanel = new UIHelper.StyledTablePanel(COLUMNS);
        tablePanel.setBorder(UIHelper.createCardBorder());
        tablePanel.getTable().getColumnModel().getColumn(0).setMaxWidth(60);
        add(tablePanel, "grow, push, wmin 0");
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
                java.util.List<Object> params = new java.util.ArrayList<>();
                if (!"All".equals(filter)) {
                    sql += " WHERE l.status=?";
                    params.add(filter);
                }
                sql += " ORDER BY l.applied_on DESC";
                return DatabaseManager.getInstance().fetchAll(sql, params.toArray());
            }

            @Override
            protected void done() {
                try {
                    for (Map<String, Object> r : get()) {
                        tablePanel.addRow(new Object[] {
                                r.get("id"), r.get("emp_id"), r.get("emp_name"),
                                r.get("leave_type"), r.get("from_date"), r.get("to_date"),
                                r.get("days"), r.get("status"), r.get("applied_on")
                        });
                    }
                } catch (Exception ignored) {
                }
            }
        };
        worker.execute();
    }

    private void updateStatus(String newStatus) {
        Object idVal = tablePanel.getSelectedValue();
        if (idVal == null) {
            UIHelper.showError(this, "Select a leave record first.");
            return;
        }

        if (!UIHelper.confirm(this, "Confirm " + newStatus, "Mark leave request #" + idVal + " as " + newStatus + "?"))
            return;

        int id = (int) idVal;
        try {
            DatabaseManager.getInstance().execute("UPDATE leaves SET status=? WHERE id=?", newStatus, id);
            UIHelper.showSuccess(this, "Leave request " + newStatus.toLowerCase() + " successfully.");
            loadData();
        } catch (SQLException ex) {
            UIHelper.showError(this, "Error: " + ex.getMessage());
        }
    }

    private void openApplyDialog(Integer id) {
        new LeaveForm((JFrame) SwingUtilities.getWindowAncestor(this), id, this::loadData);
    }
}
