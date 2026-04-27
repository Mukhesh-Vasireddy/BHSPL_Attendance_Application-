package com.bhspl.ui;

import com.bhspl.db.DatabaseManager;
import com.bhspl.util.UIHelper;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

public class DesignationPanel extends JPanel {
    private UIHelper.StyledTablePanel tablePanel;
    private JTextField searchField;
    private static final String[] COLUMNS = {"ID", "Designation Name", "Description"};

    public DesignationPanel() {
        setLayout(new MigLayout("ins 24, fill, wrap", "[grow]", "[] 20 [grow]"));
        setBackground(UIHelper.BG_MAIN);
        buildUI();
        loadData("");
    }

    private void buildUI() {
        JPanel toolbar = new JPanel(new MigLayout("ins 0, gap 12", "[] 8 [] push []"));
        toolbar.setOpaque(false);
        searchField = new JTextField(20);
        searchField.putClientProperty("JTextField.placeholderText", "Search designations...");
        JButton searchBtn = UIHelper.makeButton("Search", UIHelper.PRIMARY);
        JButton refreshBtn = UIHelper.makeButton("Refresh", new Color(0x334155));
        
        searchBtn.addActionListener(e -> loadData(searchField.getText().trim()));
        refreshBtn.addActionListener(e -> loadData(""));
        
        toolbar.add(searchField);
        toolbar.add(searchBtn);
        toolbar.add(refreshBtn, "right");
        add(toolbar, "growx");

        tablePanel = new UIHelper.StyledTablePanel(COLUMNS);
        add(tablePanel, "grow, push");
    }

    private void loadData(String filter) {
        tablePanel.clearRows();
        try {
            String sql = "SELECT * FROM designations";
            if (!filter.isEmpty()) sql += " WHERE desig_name LIKE '%" + filter + "%'";
            List<Map<String, Object>> rows = DatabaseManager.getInstance().query(sql);
            for (Map<String, Object> r : rows) {
                tablePanel.addRow(new Object[]{r.get("id"), r.get("desig_name"), r.get("description")});
            }
        } catch (SQLException ignored) {}
    }
}
