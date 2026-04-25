package com.bhspl.ui;

import com.bhspl.db.DatabaseManager;
import com.bhspl.util.UIHelper;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import java.awt.*;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

public class DepartmentPanel extends JPanel {

    private UIHelper.StyledTablePanel tablePanel;

    private static final String[] COLUMNS = {"ID", "Dept Name", "Code", "Head", "Status", "Created"};

    public DepartmentPanel() {
        setBackground(UIHelper.BG_MAIN);
        setLayout(new MigLayout("ins 24, wrap, gap 0", "[grow]", "[] 16 [grow]"));
        buildUI();
        loadData();
    }

    private void buildUI() {
        // Toolbar
        JPanel toolbar = new JPanel(new MigLayout("ins 0, gap 12", "[] push [] 8 [] 8 [] 8 []"));
        toolbar.setOpaque(false);

        JLabel title = new JLabel("Department Structure");
        title.setFont(UIHelper.FNT_TITLE);
        title.setForeground(UIHelper.PRIMARY);

        JButton addBtn     = UIHelper.makeButton("Add Dept", UIHelper.SUCCESS);
        JButton editBtn    = UIHelper.makeButton("Edit", UIHelper.PRIMARY);
        JButton deleteBtn  = UIHelper.makeButton("Delete", UIHelper.DANGER);
        JButton refreshBtn = UIHelper.makeButton("Refresh", new Color(0x334155));

        addBtn.addActionListener(e -> openForm(-1));
        editBtn.addActionListener(e -> {
            Object id = tablePanel.getSelectedValue();
            if (id == null) { JOptionPane.showMessageDialog(this, "Select a department first."); return; }
            openForm((int) id);
        });
        deleteBtn.addActionListener(e -> deleteSelected());
        refreshBtn.addActionListener(e -> loadData());

        toolbar.add(title);
        toolbar.add(addBtn, "right");
        toolbar.add(editBtn);
        toolbar.add(deleteBtn);
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
        SwingWorker<List<Map<String, Object>>, Void> w = new SwingWorker<>() {
            @Override protected List<Map<String, Object>> doInBackground() throws Exception {
                return DatabaseManager.getInstance().fetchAll(
                    "SELECT id, dept_name, dept_code, head_name, status, created_at FROM departments ORDER BY dept_name");
            }
            @Override protected void done() {
                try {
                    for (Map<String, Object> r : get())
                        tablePanel.addRow(new Object[]{r.get("id"), r.get("dept_name"), r.get("dept_code"), r.get("head_name"), r.get("status"), r.get("created_at")});
                } catch (Exception ignored) {}
            }
        };
        w.execute();
    }

    private void openForm(int id) {
        JDialog dlg = new JDialog((JFrame) SwingUtilities.getWindowAncestor(this),
            id < 0 ? "Create Department" : "Edit Department", true);
        dlg.setSize(400, 320);
        UIHelper.centerWindow(dlg, 400, 320);

        JPanel p = new JPanel(new MigLayout("ins 24, wrap, gap 12", "[][grow]", "[]"));
        p.setBackground(Color.WHITE);

        JTextField name = new JTextField(14), code = new JTextField(14), head = new JTextField(14);
        JComboBox<String> status = new JComboBox<>(new String[]{"Active", "Inactive"});

        if (id >= 0) {
            try {
                Map<String, Object> r = DatabaseManager.getInstance().fetchOne("SELECT * FROM departments WHERE id=?", id);
                if (r != null) {
                    name.setText((String) r.get("dept_name"));
                    code.setText((String) r.get("dept_code"));
                    head.setText((String) r.get("head_name"));
                    status.setSelectedItem(r.get("status"));
                }
            } catch (Exception ignored) {}
        }

        Object[][] rows = {{"Dept Name", name}, {"Short Code", code}, {"Head Person", head}, {"Status", status}};
        for (Object[] row : rows) {
            p.add(new JLabel((String) row[0]));
            p.add((Component) row[1], "growx");
        }

        JButton save = UIHelper.makeButton("Save Department", UIHelper.SUCCESS);
        save.addActionListener(e -> {
            try {
                String n = name.getText().trim();
                if (n.isEmpty()) { JOptionPane.showMessageDialog(dlg, "Name is required."); return; }
                
                if (id < 0) DatabaseManager.getInstance().execute(
                    "INSERT INTO departments (dept_name, dept_code, head_name, status) VALUES(?,?,?,?)",
                    n, code.getText().trim(), head.getText().trim(), status.getSelectedItem());
                else DatabaseManager.getInstance().execute(
                    "UPDATE departments SET dept_name=?, dept_code=?, head_name=?, status=? WHERE id=?",
                    n, code.getText().trim(), head.getText().trim(), status.getSelectedItem(), id);
                dlg.dispose();
                loadData();
            } catch (SQLException ex) { JOptionPane.showMessageDialog(dlg, "Error: " + ex.getMessage()); }
        });

        p.add(save, "skip, growx, gaptop 12");
        dlg.setContentPane(p);
        dlg.setVisible(true);
    }

    private void deleteSelected() {
        Object idVal = tablePanel.getSelectedValue();
        if (idVal == null) { JOptionPane.showMessageDialog(this, "Select a department first."); return; }
        
        int id = (int) idVal;
        if (JOptionPane.showConfirmDialog(this, "Permanently delete this department record?", "Confirm Delete", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        try {
            DatabaseManager.getInstance().execute("DELETE FROM departments WHERE id=?", id);
            loadData();
        } catch (SQLException ex) { JOptionPane.showMessageDialog(this, "Error: " + ex.getMessage()); }
    }
}
