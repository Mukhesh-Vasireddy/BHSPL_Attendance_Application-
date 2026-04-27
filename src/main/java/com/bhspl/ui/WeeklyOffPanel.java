package com.bhspl.ui;

import com.bhspl.db.DatabaseManager;
import com.bhspl.util.UIHelper;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import java.awt.*;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

public class WeeklyOffPanel extends JPanel {
    private UIHelper.StyledTablePanel tablePanel;
    private JComboBox<String> deptCombo;
    private static final String[] COLUMNS = {"ID", "Emp ID", "Name", "Dept", "Off Day 1", "Off Day 2", "From", "To", "Remarks"};

    public WeeklyOffPanel() {
        setLayout(new MigLayout("ins 24, fill, wrap", "[grow]", "[] 15 [] 15 [grow]"));
        setBackground(UIHelper.BG_MAIN);
        buildUI();
        loadData();
    }

    private void buildUI() {
        // Title
        JLabel title = new JLabel("Weekly Off Master");
        title.setFont(new Font("Segoe UI", Font.BOLD, 22));
        title.setForeground(new Color(0x1E293B));
        add(title, "gapbottom 5");

        // Action Buttons
        JPanel actions = new JPanel(new MigLayout("ins 0, gap 10", "[] [] [] [] []"));
        actions.setOpaque(false);
        
        JButton assignBtn = UIHelper.makeButton("Assign Off", UIHelper.SUCCESS, "plus.svg");
        assignBtn.addActionListener(e -> showAssignOffDialog());
        actions.add(assignBtn);
        actions.add(UIHelper.makeButton("Edit", UIHelper.PRIMARY, "edit.svg"));
        actions.add(UIHelper.makeButton("Delete", UIHelper.DANGER, "trash.svg"));
        actions.add(UIHelper.makeButton("Bulk Assign", UIHelper.WARNING, "employees.svg"));
        
        JButton refreshBtn = UIHelper.makeButton("Refresh", new Color(0x334155), "sync.svg");
        refreshBtn.addActionListener(e -> loadData());
        actions.add(refreshBtn);
        
        add(actions, "growx");

        // Filter Area
        JPanel filterArea = new JPanel(new MigLayout("ins 0, gap 10", "[] [] []"));
        filterArea.setOpaque(false);
        
        filterArea.add(new JLabel("Dept:"));
        deptCombo = new JComboBox<>(new String[]{"All"});
        loadDepartments();
        filterArea.add(deptCombo, "w 150!");
        
        JButton filterBtn = UIHelper.makeButton("Filter", UIHelper.PRIMARY);
        filterBtn.addActionListener(e -> loadData());
        filterArea.add(filterBtn);
        
        add(filterArea, "growx");

        // Table
        tablePanel = new UIHelper.StyledTablePanel(COLUMNS);
        add(tablePanel, "grow, push");
    }

    private void loadDepartments() {
        try {
            List<Map<String, Object>> depts = DatabaseManager.getInstance().query("SELECT dept_name FROM departments");
            for (Map<String, Object> d : depts) {
                deptCombo.addItem((String) d.get("dept_name"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void loadData() {
        tablePanel.clearRows();
        try {
            String sql = "SELECT w.*, e.emp_name, e.department as dept_name " +
                         "FROM weekly_offs w " +
                         "JOIN employees e ON w.emp_id = e.emp_id";
            
            String selectedDept = (String) deptCombo.getSelectedItem();
            if (selectedDept != null && !"All".equals(selectedDept)) {
                sql += " WHERE e.department = '" + selectedDept + "'";
            }
            
            List<Map<String, Object>> rows = DatabaseManager.getInstance().query(sql);
            for (Map<String, Object> r : rows) {
                tablePanel.addRow(new Object[]{
                    r.get("id"),
                    r.get("emp_id"),
                    r.get("emp_name"),
                    r.get("dept_name"),
                    r.get("off_day1"),
                    r.get("off_day2"),
                    r.get("effective_from"),
                    r.get("effective_to"),
                    r.get("remarks")
                });
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void showAssignOffDialog() {
        JDialog dialog = new JDialog((Frame) SwingUtilities.getWindowAncestor(this), "Assign Weekly Off", true);
        dialog.setLayout(new MigLayout("ins 20, wrap 2, gapy 15", "[shrink] 15 [grow, fill]", "[]"));
        
        JComboBox<String> empCombo = new JComboBox<>();
        loadEmployeesIntoCombo(empCombo);
        
        String[] days = {"Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday", "None"};
        JComboBox<String> day1Combo = new JComboBox<>(days);
        JComboBox<String> day2Combo = new JComboBox<>(days);
        day1Combo.setSelectedItem("Sunday");
        day2Combo.setSelectedItem("None");

        JTextField fromDate = new JTextField(java.time.LocalDate.now().toString());
        JTextField toDate = new JTextField("2099-12-31");
        JTextField remarks = new JTextField();

        dialog.add(new JLabel("Select Employee:")); dialog.add(empCombo);
        dialog.add(new JLabel("Off Day 1:")); dialog.add(day1Combo);
        dialog.add(new JLabel("Off Day 2:")); dialog.add(day2Combo);
        dialog.add(new JLabel("Effective From:")); dialog.add(fromDate);
        dialog.add(new JLabel("Effective To:")); dialog.add(toDate);
        dialog.add(new JLabel("Remarks:")); dialog.add(remarks);

        JButton saveBtn = UIHelper.makeButton("Save Assignment", UIHelper.SUCCESS);
        saveBtn.addActionListener(e -> {
            String empStr = (String) empCombo.getSelectedItem();
            if (empStr == null) return;
            String empId = empStr.split(" - ")[0];
            
            try {
                DatabaseManager.getInstance().execute(
                    "INSERT INTO weekly_offs (emp_id, off_day1, off_day2, effective_from, effective_to, remarks) VALUES (?,?,?,?,?,?)",
                    empId, day1Combo.getSelectedItem(), day2Combo.getSelectedItem(), fromDate.getText(), toDate.getText(), remarks.getText()
                );
                dialog.dispose();
                loadData();
                UIHelper.showSuccess(this, "Weekly off assigned successfully!");
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(dialog, "Error: " + ex.getMessage());
            }
        });

        dialog.add(saveBtn, "span 2, growx, gaptop 10");
        dialog.pack();
        dialog.setSize(450, dialog.getHeight());
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    private void loadEmployeesIntoCombo(JComboBox<String> combo) {
        try {
            List<Map<String, Object>> emps = DatabaseManager.getInstance().query("SELECT emp_id, emp_name FROM employees ORDER BY emp_name");
            for (Map<String, Object> e : emps) {
                combo.addItem(e.get("emp_id") + " - " + e.get("emp_name"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
