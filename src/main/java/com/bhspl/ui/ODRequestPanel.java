package com.bhspl.ui;

import com.bhspl.db.DatabaseManager;
import com.bhspl.util.UIHelper;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import java.awt.*;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

public class ODRequestPanel extends JPanel {
    private UIHelper.StyledTablePanel tablePanel;
    private static final String[] COLUMNS = {"ID", "Employee", "Date", "Reason", "Status"};

    public ODRequestPanel() {
        setLayout(new MigLayout("ins 24, fill, wrap", "[grow]", "[] 20 [grow]"));
        setBackground(UIHelper.BG_MAIN);
        buildUI();
        loadData();
    }

    private void buildUI() {
        JLabel title = new JLabel("On Duty (OD) Requests");
        title.setFont(new Font("Segoe UI", Font.BOLD, 18));
        add(title, "gapbottom 10");

        tablePanel = new UIHelper.StyledTablePanel(COLUMNS);
        add(tablePanel, "grow, push");
    }

    private void loadData() {
        tablePanel.clearRows();
        try {
            List<Map<String, Object>> rows = DatabaseManager.getInstance().query(
                "SELECT o.*, e.emp_name FROM od_requests o JOIN employees e ON o.emp_id = e.emp_id");
            for (Map<String, Object> r : rows) {
                tablePanel.addRow(new Object[]{r.get("id"), r.get("emp_name"), r.get("request_date"), r.get("reason"), r.get("status")});
            }
        } catch (SQLException ignored) {}
    }
}
