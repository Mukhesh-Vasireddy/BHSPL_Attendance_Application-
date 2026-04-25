package com.bhspl.ui;

import com.bhspl.db.DatabaseManager;
import com.bhspl.util.UIHelper;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.Map;

/**
 * Enterprise Leave Policy management panel with high-fidelity UI.
 */
public class LeavePolicyPanel extends JPanel {
    private UIHelper.StyledTablePanel tablePanel;
    private JTextField searchField;
    private JLabel statusLbl;
    private static final String[] COLUMNS = {
        "ID", "Leave Type", "Days/Year", "Credit", "Carry Fwd", "Max Carry", 
        "Expire (months)", "Encashable", "Pro-rata", "Gender", "Min Service", "Status"
    };

    public LeavePolicyPanel() {
        setLayout(new MigLayout("ins 24, fill, wrap", "[grow]", "[] 16 [grow] 8 []"));
        setBackground(UIHelper.BG_MAIN);
        buildUI();
        loadData("");
    }

    private void buildUI() {
        // Toolbar with Search
        JPanel toolbar = new JPanel(new MigLayout("ins 0, gap 12", "[] 8 [] push [] 8 [] 8 [] 8 []"));
        toolbar.setOpaque(false);

        searchField = new JTextField(20);
        searchField.setFont(UIHelper.FNT_MEDIUM);
        searchField.putClientProperty("JTextField.placeholderText", "Search leave type...");
        searchField.addActionListener(e -> loadData(searchField.getText().trim()));
        toolbar.add(searchField);

        JButton searchBtn = UIHelper.makeButton("Search", UIHelper.PRIMARY);
        searchBtn.addActionListener(e -> loadData(searchField.getText().trim()));
        toolbar.add(searchBtn);

        JButton addBtn = UIHelper.makeButton("+ Add Policy", UIHelper.SUCCESS);
        addBtn.addActionListener(e -> {
            Window parent = SwingUtilities.getWindowAncestor(this);
            JFrame frame = (parent instanceof JFrame) ? (JFrame) parent : null;
            new LeavePolicyForm(frame, null, () -> loadData(""));
        });
        toolbar.add(addBtn, "right");

        JButton editBtn = UIHelper.makeButton("Edit", UIHelper.PRIMARY);
        editBtn.addActionListener(e -> edit());
        toolbar.add(editBtn);

        JButton delBtn = UIHelper.makeButton("\uD83D\uDDD1 Delete", UIHelper.DANGER);
        delBtn.addActionListener(e -> delete());
        toolbar.add(delBtn);

        JButton refreshBtn = UIHelper.makeButton("Refresh", new Color(0x64748B));
        refreshBtn.addActionListener(e -> {
            searchField.setText("");
            loadData("");
        });
        toolbar.add(refreshBtn);

        add(toolbar, "growx");

        // Styled Table
        tablePanel = new UIHelper.StyledTablePanel(COLUMNS);
        tablePanel.getTable().addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) edit();
            }
        });
        add(tablePanel, "grow, push");

        // Status Bar
        statusLbl = new JLabel("Loading policies...");
        statusLbl.setFont(new Font("Segoe UI", Font.ITALIC, 11));
        statusLbl.setForeground(UIHelper.TEXT_LIGHT);
        add(statusLbl, "gapleft 4");
    }

    private void edit() {
        try {
            int row = tablePanel.getTable().getSelectedRow();
            if (row == -1) {
                UIHelper.showError(this, "Please select a leave policy from the table to edit.");
                return;
            }
            
            // Get ID from the model safely
            int modelRow = tablePanel.getTable().convertRowIndexToModel(row);
            Object idVal = tablePanel.getModel().getValueAt(modelRow, 0);
            
            if (idVal == null) {
                UIHelper.showError(this, "Invalid selection: Policy ID not found.");
                return;
            }
            
            Integer id = Integer.valueOf(idVal.toString());
            Window parent = SwingUtilities.getWindowAncestor(this);
            JFrame frame = (parent instanceof JFrame) ? (JFrame) parent : null;
            
            new LeavePolicyForm(frame, id, () -> loadData(""));
        } catch (Exception e) {
            UIHelper.showError(this, "Edit failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void delete() {
        try {
            int row = tablePanel.getTable().getSelectedRow();
            if (row == -1) {
                UIHelper.showError(this, "Please select a leave policy to delete.");
                return;
            }
            
            int modelRow = tablePanel.getTable().convertRowIndexToModel(row);
            Object idVal = tablePanel.getModel().getValueAt(modelRow, 0);
            
            if (UIHelper.confirm(this, "Confirm Deletion", "Are you sure you want to delete policy #" + idVal + "?")) {
                DatabaseManager.getInstance().execute("DELETE FROM leave_policy WHERE id=?", idVal);
                DatabaseManager.getInstance().commit();
                loadData("");
                UIHelper.showSuccess(this, "Policy #" + idVal + " deleted successfully.");
            }
        } catch (Exception e) {
            UIHelper.showError(this, "Delete failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void loadData(String search) {
        tablePanel.clearRows();
        statusLbl.setText("Fetching data...");
        
        SwingWorker<List<Map<String, Object>>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<Map<String, Object>> doInBackground() throws Exception {
                if (search == null || search.isEmpty()) {
                    return DatabaseManager.getInstance().query("SELECT * FROM leave_policy ORDER BY id");
                } else {
                    String pattern = "%" + search + "%";
                    return DatabaseManager.getInstance().query(
                        "SELECT * FROM leave_policy WHERE leave_type LIKE ? OR description LIKE ? ORDER BY id", 
                        pattern, pattern
                    );
                }
            }

            @Override
            protected void done() {
                try {
                    List<Map<String, Object>> rows = get();
                    for (Map<String, Object> r : rows) {
                        tablePanel.addRow(new Object[]{
                            r.get("id"),
                            r.get("leave_type"),
                            r.get("days_per_year"),
                            r.get("credit_method"),
                            DatabaseManager.num(r, "carry_forward") == 1 ? "Yes" : "No",
                            r.get("max_carry"),
                            r.get("expire_months"),
                            DatabaseManager.num(r, "encashable") == 1 ? "Yes" : "No",
                            DatabaseManager.num(r, "pro_rata") == 1 ? "Yes" : "No",
                            r.get("applicable_gender"),
                            r.get("min_service_days"),
                            r.get("status")
                        });
                    }
                    statusLbl.setText("Showing " + rows.size() + " policies" + 
                        (search.isEmpty() ? "" : " matching '" + search + "'"));
                } catch (Exception e) {
                    statusLbl.setText("Error loading data: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        };
        worker.execute();
    }
}
