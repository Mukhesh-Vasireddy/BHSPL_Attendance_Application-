package com.bhspl.ui;

import com.bhspl.db.DatabaseManager;
import com.bhspl.util.UIHelper;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Enhanced Holiday Master panel matching the modern dashboard aesthetic.
 * Supports categorization, default holiday loading, and color-coded table rows.
 */
public class HolidayPanel extends JPanel {

    private UIHelper.StyledTablePanel tablePanel;
    private String currentFilter = null;

    private static final String[] COLUMNS = { "ID", "Date", "Day", "Holiday Name", "Type" };
    private static final DateTimeFormatter DISPLAY_FMT = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    // Colors matching the screenshot design
    private static final Color CLR_NATIONAL = new Color(0x991B1B); // Dark Red
    private static final Color CLR_PUBLIC = new Color(0x1D4ED8); // Blue
    private static final Color CLR_FESTIVAL = new Color(0xEA580C); // Orange
    private static final Color CLR_OPTIONAL = new Color(0xCA8A04); // Yellow/Gold
    private static final Color CLR_COMPANY = new Color(0x059669); // Green/Teal
    private static final Color CLR_REGIONAL = new Color(0x4F46E5); // Indigo/Purple

    private static final Color CLR_AMBER_BG = new Color(0xF59E0B); // Load Defaults
    private static final Color CLR_REFRESH = new Color(0x4B5563); // Refresh

    public HolidayPanel() {
        setBackground(UIHelper.BG_MAIN);
        setLayout(new MigLayout("ins 24, wrap, gap 0", "[grow]", "[] 16 [grow]"));
        buildUI();
        loadData();
    }

    private void buildUI() {
        // Toolbar
        JPanel toolbar = new JPanel(new MigLayout("ins 0, wrap, gap 0", "[grow]", "[] 12 []"));
        toolbar.setOpaque(false);

        // Row 1: Title and Main Buttons
        JPanel topRow = new JPanel(new MigLayout("ins 0, gap 12", "push [] 8 [] 8 [] 8 [] 8 []"));
        topRow.setOpaque(false);

        JButton addBtn = UIHelper.makeButton("+ Add Holiday", UIHelper.SUCCESS);
        JButton editBtn = UIHelper.makeButton("— Edit", UIHelper.PRIMARY);
        JButton deleteBtn = UIHelper.makeButton("Delete", UIHelper.DANGER);
        JButton loadDefBtn = UIHelper.makeButton("Load Defaults", CLR_AMBER_BG);
        JButton refreshBtn = UIHelper.makeButton("Refresh", CLR_REFRESH);

        addBtn.addActionListener(e -> openForm(-1));
        editBtn.addActionListener(e -> {
            Object idVal = tablePanel.getSelectedValue();
            if (idVal == null) {
                JOptionPane.showMessageDialog(this, "Select a holiday record to edit.");
                return;
            }
            openForm((int) idVal);
        });
        deleteBtn.addActionListener(e -> deleteSelected());
        loadDefBtn.addActionListener(e -> loadDefaults());
        refreshBtn.addActionListener(e -> loadData());

        topRow.add(addBtn);
        topRow.add(editBtn);
        topRow.add(deleteBtn);
        topRow.add(loadDefBtn);
        topRow.add(refreshBtn);
        toolbar.add(topRow, "growx");

        // Row 2: Category Filters
        JPanel filterRow = new JPanel(new MigLayout("ins 0, gap 8", "[] 8 [] 8 [] 8 [] 8 [] 8 [] 8 []"));
        filterRow.setOpaque(false);

        filterRow.add(createFilterBtn("All Holidays", new Color(0x64748b)));
        filterRow.add(createFilterBtn("National Holiday", CLR_NATIONAL));
        filterRow.add(createFilterBtn("Public Holiday", CLR_PUBLIC));
        filterRow.add(createFilterBtn("Festival Holiday", CLR_FESTIVAL));
        filterRow.add(createFilterBtn("Optional Holiday", CLR_OPTIONAL));
        filterRow.add(createFilterBtn("Company Holiday", CLR_COMPANY));
        filterRow.add(createFilterBtn("Regional Holiday", CLR_REGIONAL));
        toolbar.add(filterRow, "growx");

        add(toolbar, "growx");

        // Table
        tablePanel = new UIHelper.StyledTablePanel(COLUMNS);
        tablePanel.setBorder(UIHelper.createCardBorder());
        tablePanel.getTable().getColumnModel().getColumn(0).setMaxWidth(60);

        // Custom rendering for colors
        styleHolidayTable();

        add(tablePanel, "grow, push");
    }

    private JButton createFilterBtn(String text, Color bg) {
        JButton btn = UIHelper.makeButton(text, bg);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 10));
        btn.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));
        btn.addActionListener(e -> {
            if (text.equals("All Holidays") || text.equals(currentFilter)) {
                currentFilter = null;
            } else {
                currentFilter = text;
            }
            loadData();
        });
        return btn;
    }

    private void styleHolidayTable() {
        JTable table = tablePanel.getTable();
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object val, boolean sel, boolean foc, int row,
                    int col) {
                Component c = super.getTableCellRendererComponent(t, val, sel, foc, row, col);
                String type = t.getValueAt(row, 4).toString();

                if (!sel) {
                    if (type.contains("National"))
                        c.setBackground(new Color(0xFFF1F2)); // Pink
                    else if (type.contains("Festival"))
                        c.setBackground(new Color(0xFFFBEB)); // Cream
                    else if (type.contains("Public"))
                        c.setBackground(new Color(0xEFF6FF)); // Light Blue
                    else
                        c.setBackground(row % 2 == 0 ? UIHelper.TREE_ODD : Color.WHITE);
                }

                setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
                setHorizontalAlignment(col == 3 ? SwingConstants.LEFT : SwingConstants.CENTER);
                return c;
            }
        });
    }

    private void loadData() {
        tablePanel.clearRows();
        SwingWorker<List<Map<String, Object>>, Void> w = new SwingWorker<>() {
            @Override
            protected List<Map<String, Object>> doInBackground() throws Exception {
                String sql = "SELECT id, holiday_date, holiday_name, holiday_type FROM holidays ";
                if (currentFilter != null)
                    sql += " WHERE holiday_type = '" + currentFilter + "'";
                sql += " ORDER BY holiday_date";
                return DatabaseManager.getInstance().fetchAll(sql);
            }

            @Override
            protected void done() {
                try {
                    for (Map<String, Object> r : get()) {
                        LocalDate d = LocalDate.parse(r.get("holiday_date").toString());
                        String day = d.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH);
                        tablePanel.addRow(new Object[] {
                                r.get("id"),
                                d.format(DISPLAY_FMT),
                                day,
                                r.get("holiday_name"),
                                r.get("holiday_type")
                        });
                    }
                } catch (Exception ignored) {
                }
            }
        };
        w.execute();
    }

    private void openForm(int id) {
        new HolidayForm((JFrame) SwingUtilities.getWindowAncestor(this), id < 0 ? null : id, this::loadData);
    }

    private void deleteSelected() {
        Object idVal = tablePanel.getSelectedValue();
        if (idVal == null) {
            JOptionPane.showMessageDialog(this, "Select a holiday record first.");
            return;
        }

        int id = (int) idVal;
        if (JOptionPane.showConfirmDialog(this, "Permanently delete this holiday record?", "Confirm Delete",
                JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION)
            return;
        try {
            DatabaseManager.getInstance().execute("DELETE FROM holidays WHERE id=?", id);
            loadData();
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Error: " + ex.getMessage());
        }
    }

    private void loadDefaults() {
        if (JOptionPane.showConfirmDialog(this, "Load common 2026 holidays into the master list?", "Load Defaults",
                JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION)
            return;

        Object[][] defaults = {
                { "2026-01-01", "New Year's Day", "National Holiday" },
                { "2026-01-26", "Republic Day", "National Holiday" },
                { "2026-03-25", "Holi", "Festival Holiday" },
                { "2026-04-14", "Dr. Ambedkar Jayanti", "National Holiday" },
                { "2026-04-18", "Good Friday", "Festival Holiday" },
                { "2026-05-01", "Labour Day", "National Holiday" },
                { "2026-08-15", "Independence Day", "National Holiday" },
                { "2026-08-27", "Janmashtami", "Festival Holiday" },
                { "2026-10-02", "Gandhi Jayanti", "National Holiday" },
                { "2026-10-24", "Dussehra", "Festival Holiday" },
                { "2026-11-01", "Diwali", "Festival Holiday" },
                { "2026-11-05", "Bhai Dooj", "Festival Holiday" },
                { "2026-12-25", "Christmas Day", "Public Holiday" }
        };

        try {
            for (Object[] h : defaults) {
                DatabaseManager.getInstance().execute(
                        "INSERT IGNORE INTO holidays (holiday_date, holiday_name, holiday_type) VALUES (?,?,?)",
                        h[0], h[1], h[2]);
            }
            loadData();
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(this, "Error loading defaults: " + ex.getMessage());
        }
    }
}
