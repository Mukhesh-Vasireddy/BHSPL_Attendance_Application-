package com.bhspl.ui;

import com.bhspl.db.DatabaseManager;
import com.bhspl.util.UIHelper;
import com.bhspl.util.ExcelExporter;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Modern Leave Balance panel with bulk actions, year-end processing, 
 * and summary analytics cards.
 */
public class LeaveBalancePanel extends JPanel {
    private UIHelper.StyledTablePanel tablePanel;
    private JComboBox<String> yearFilter, deptFilter, typeFilter;
    private JLabel lblEmpCount, lblTotalBal, lblTotalUsed, lblTotalLapsed;

    private static final String[] COLUMNS = {
        "Emp ID", "Name", "Dept", "Leave Type", "Year", "Opening", "Credited", "Carry Fwd", "Used", "Lapsed", "Balance"
    };

    public LeaveBalancePanel() {
        setLayout(new MigLayout("ins 24, fill, wrap, gapy 0", "[grow]", "[] 20 [] 20 [] 24 [grow]"));
        setBackground(UIHelper.BG_MAIN);
        buildUI();
        loadFilters();
        loadData();
    }

    private void buildUI() {
        // 1. Header & Title Area
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        
        JLabel titleLbl = new JLabel("Leave Balance");
        titleLbl.setFont(UIHelper.FNT_TITLE);
        titleLbl.setForeground(UIHelper.PRIMARY);
        try {
            com.formdev.flatlaf.extras.FlatSVGIcon icon = new com.formdev.flatlaf.extras.FlatSVGIcon("icons/leave_balance.svg", 24, 24);
            icon.setColorFilter(new com.formdev.flatlaf.extras.FlatSVGIcon.ColorFilter(c -> UIHelper.PRIMARY));
            titleLbl.setIcon(icon);
            titleLbl.setIconTextGap(12);
        } catch (Exception ignored) {}
        header.add(titleLbl, BorderLayout.WEST);

        // 2. Action Buttons Toolbar
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        actions.setOpaque(false);
        
        JButton creditBtn = UIHelper.makeButton("+ Credit Leaves", UIHelper.SUCCESS);
        creditBtn.addActionListener(e -> new CreditLeavesDialog((JFrame) SwingUtilities.getWindowAncestor(this), this::loadData));
        
        JButton yearEndBtn = UIHelper.makeButton("Year-End Process", UIHelper.WARNING);
        yearEndBtn.addActionListener(e -> new YearEndDialog((JFrame) SwingUtilities.getWindowAncestor(this), this::loadData));
        
        JButton adjustBtn = UIHelper.makeButton("\u2014 Adjust Balance", UIHelper.PRIMARY);
        adjustBtn.addActionListener(e -> {
            int row = tablePanel.getTable().getSelectedRow();
            if (row != -1) {
                String eid = tablePanel.getModel().getValueAt(tablePanel.getTable().convertRowIndexToModel(row), 0).toString();
                String type = tablePanel.getModel().getValueAt(tablePanel.getTable().convertRowIndexToModel(row), 3).toString();
                String year = tablePanel.getModel().getValueAt(tablePanel.getTable().convertRowIndexToModel(row), 4).toString();
                new AdjustBalanceDialog((JFrame) SwingUtilities.getWindowAncestor(this), eid, type, year, this::loadData);
            } else {
                new AdjustBalanceDialog((JFrame) SwingUtilities.getWindowAncestor(this), null, null, null, this::loadData);
            }
        });
        
        JButton refreshBtn = UIHelper.makeButton("Refresh", new Color(0x64748B));
        refreshBtn.addActionListener(e -> loadData());
        
        JButton exportBtn = UIHelper.makeButton("Export Excel", UIHelper.SUCCESS);
        exportBtn.addActionListener(e -> exportToExcel());

        actions.add(creditBtn);
        actions.add(yearEndBtn);
        actions.add(adjustBtn);
        actions.add(refreshBtn);
        actions.add(exportBtn);
        header.add(actions, BorderLayout.EAST);
        add(header, "growx");

        // 3. Filter Section
        JPanel filters = new JPanel(new MigLayout("ins 15, gap 15", "[] 8 [] 15 [] 8 [] 15 [] 8 [] 20 []"));
        filters.setBackground(UIHelper.BG_CARD);
        filters.setBorder(BorderFactory.createLineBorder(UIHelper.BORDER, 1));
        
        filters.add(new JLabel("Year:"));
        yearFilter = new JComboBox<>();
        int curY = LocalDate.now().getYear();
        for(int y=curY-1; y<=curY+1; y++) yearFilter.addItem(String.valueOf(y));
        yearFilter.setSelectedItem(String.valueOf(curY));
        filters.add(yearFilter, "w 100!");

        filters.add(new JLabel("Dept:"));
        deptFilter = new JComboBox<>();
        filters.add(deptFilter, "w 180!");

        filters.add(new JLabel("Leave Type:"));
        typeFilter = new JComboBox<>();
        filters.add(typeFilter, "w 180!");

        JButton filterBtn = UIHelper.makeButton("Filter", UIHelper.PRIMARY);
        try {
            filterBtn.setIcon(new com.formdev.flatlaf.extras.FlatSVGIcon("icons/reports.svg", 16, 16));
        } catch(Exception ignored) {}
        filterBtn.addActionListener(e -> loadData());
        filters.add(filterBtn, "w 120!");
        
        add(filters, "growx");

        // 4. Summary Stats Cards
        JPanel summary = new JPanel(new GridLayout(1, 4, 20, 0));
        summary.setOpaque(false);
        summary.add(createStatCard("Employees", lblEmpCount = new JLabel("0"), new Color(0x1E293B)));
        summary.add(createStatCard("Total Balance", lblTotalBal = new JLabel("0 days"), UIHelper.SUCCESS));
        summary.add(createStatCard("Total Used", lblTotalUsed = new JLabel("0 days"), UIHelper.WARNING));
        summary.add(createStatCard("Total Lapsed", lblTotalLapsed = new JLabel("0 days"), UIHelper.DANGER));
        add(summary, "growx, h 100!");

        // 5. Data Table
        tablePanel = new UIHelper.StyledTablePanel(COLUMNS);
        tablePanel.setBorder(UIHelper.createCardBorder());
        add(tablePanel, "grow, push");
    }

    private JPanel createStatCard(String title, JLabel value, Color color) {
        JPanel p = new JPanel(new MigLayout("ins 20, wrap", "[grow, center]", "[] 8 []"));
        p.setBackground(color);
        JLabel t = new JLabel(title.toUpperCase());
        t.setForeground(new Color(255, 255, 255, 160));
        t.setFont(new Font("Segoe UI", Font.BOLD, 11));
        p.add(t);
        value.setForeground(Color.WHITE);
        value.setFont(new Font("Segoe UI", Font.BOLD, 24));
        p.add(value);
        return p;
    }

    private void loadFilters() {
        try {
            deptFilter.removeAllItems();
            deptFilter.addItem("All");
            List<Map<String, Object>> depts = DatabaseManager.getInstance().query("SELECT dept_name FROM departments WHERE status='Active' ORDER BY dept_name");
            for (Map<String, Object> d : depts) deptFilter.addItem(DatabaseManager.str(d, "dept_name"));

            typeFilter.removeAllItems();
            typeFilter.addItem("All");
            List<Map<String, Object>> types = DatabaseManager.getInstance().query("SELECT leave_type FROM leave_policy WHERE status='Active' ORDER BY leave_type");
            for (Map<String, Object> t : types) typeFilter.addItem(DatabaseManager.str(t, "leave_type"));
        } catch (Exception ignored) {}
    }

    private void loadData() {
        tablePanel.clearRows();
        String year = (String) yearFilter.getSelectedItem();
        String dept = (String) deptFilter.getSelectedItem();
        String type = (String) typeFilter.getSelectedItem();

        StringBuilder sql = new StringBuilder(
            "SELECT b.*, e.emp_name, e.department FROM leave_balance b " +
            "JOIN employees e ON b.emp_id = e.emp_id WHERE b.year = ? "
        );
        List<Object> params = new ArrayList<>();
        params.add(year);

        if (dept != null && !"All".equals(dept)) {
            sql.append(" AND e.department = ? ");
            params.add(dept);
        }
        if (type != null && !"All".equals(type)) {
            sql.append(" AND b.leave_type = ? ");
            params.add(type);
        }
        sql.append(" ORDER BY e.department, e.emp_id, b.leave_type");

        try {
            List<Map<String, Object>> rows = DatabaseManager.getInstance().query(sql.toString(), params.toArray());
            double tBal = 0, tUsed = 0, tLapsed = 0;
            java.util.Set<String> emps = new java.util.HashSet<>();

            for (Map<String, Object> r : rows) {
                tablePanel.addRow(new Object[]{
                    r.get("emp_id"), 
                    r.get("emp_name"), 
                    r.get("department"), 
                    r.get("leave_type"), 
                    r.get("year"),
                    r.get("opening_bal"), 
                    r.get("credited"), 
                    r.get("carry_fwd"), 
                    r.get("used"), 
                    r.get("lapsed"), 
                    r.get("closing_bal")
                });
                tBal += DatabaseManager.dbl(r, "closing_bal");
                tUsed += DatabaseManager.dbl(r, "used");
                tLapsed += DatabaseManager.dbl(r, "lapsed");
                emps.add(DatabaseManager.str(r, "emp_id"));
            }

            lblEmpCount.setText(String.valueOf(emps.size()));
            lblTotalBal.setText(String.format("%.1f", tBal));
            lblTotalUsed.setText(String.format("%.1f", tUsed));
            lblTotalLapsed.setText(String.format("%.1f", tLapsed));

        } catch (SQLException e) {
            UIHelper.showError(this, "Failed to load leave balances: " + e.getMessage());
        }
    }

    private void exportToExcel() {
        JFileChooser jfc = new JFileChooser();
        jfc.setDialogTitle("Export Leave Balances");
        jfc.setSelectedFile(new java.io.File("Leave_Balances_" + LocalDate.now() + ".xlsx"));
        if (jfc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                ExcelExporter.exportTable(tablePanel.getTable(), jfc.getSelectedFile().getAbsolutePath());
                UIHelper.showSuccess(this, "Exported successfully to " + jfc.getSelectedFile().getName());
            } catch (Exception e) {
                UIHelper.showError(this, "Export failed: " + e.getMessage());
            }
        }
    }
}
