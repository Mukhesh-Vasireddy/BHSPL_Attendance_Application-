package com.bhspl.ui;

import com.bhspl.db.DatabaseManager;
import com.bhspl.util.UIHelper;
import com.bhspl.util.ZkProtocol;
import com.formdev.flatlaf.extras.FlatSVGIcon;

import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;

/**
 * Premium Import Users Dialog — fetches users from ZK device and allows
 * multi-select checkbox import of new users into the employees table.
 */
public class ImportUsersDialog extends JDialog {

    // ── Colour palette ────────────────────────────────────────────────────────
    private static final Color C_HEADER_BG   = new Color(0x5B21B6); // Deep violet
    private static final Color C_HEADER_FG   = Color.WHITE;
    private static final Color C_COL_HDR_BG  = new Color(0x7C3AED);
    private static final Color C_COL_HDR_FG  = Color.WHITE;
    private static final Color C_NEW_BG      = new Color(0xFFF7ED); // Warm cream
    private static final Color C_NEW_FG      = new Color(0xC2410C); // Deep orange
    private static final Color C_NEW_CB      = new Color(0xEA580C); // Orange checkbox
    private static final Color C_MAP_BG      = new Color(0xF0FDF4); // Mint green
    private static final Color C_MAP_FG      = new Color(0x15803D); // Forest green
    private static final Color C_SEL_BG      = new Color(0xEDE9FE); // Violet tint
    private static final Color C_BTN_IMPORT  = new Color(0x5B21B6);
    private static final Color C_BTN_ALL     = new Color(0x0F172A);
    private static final Color C_BTN_CLOSE   = new Color(0x6B7280);
    private static final Font  FNT_TITLE     = new Font("Segoe UI", Font.BOLD, 15);
    private static final Font  FNT_SUB       = new Font("Segoe UI", Font.PLAIN, 12);
    private static final Font  FNT_TBL       = new Font("Segoe UI", Font.PLAIN, 12);
    private static final Font  FNT_TBL_HDR   = new Font("Segoe UI", Font.BOLD, 12);
    private static final Font  FNT_STATUS    = new Font("Segoe UI", Font.BOLD, 12);

    // ── State ─────────────────────────────────────────────────────────────────
    private final Map<String, Object> device;
    private final DatabaseManager     db = DatabaseManager.INSTANCE;
    private List<Map<String, String>> deviceUsers = new ArrayList<>();

    // ── UI Components ─────────────────────────────────────────────────────────
    private JTable        table;
    private CheckboxModel model;
    private JProgressBar  progress;
    private JLabel        statusLabel;
    private JButton       importBtn;
    private JButton       selAllBtn;
    private JButton       selNoneBtn;
    private JLabel        counterLabel;

    // ── Constructor ───────────────────────────────────────────────────────────
    public ImportUsersDialog(JFrame parent, Map<String, Object> device) {
        super(parent, "Import Users — " + device.get("ip_address"), true);
        this.device = device;
        setSize(900, 600);
        UIHelper.centerWindow(this, 900, 600);
        buildUI();
        new Thread(this::fetchUsers).start();
        setVisible(true);
    }

    // ── UI Construction ───────────────────────────────────────────────────────
    private void buildUI() {
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(Color.WHITE);

        root.add(buildHeader(),  BorderLayout.NORTH);
        root.add(buildCenter(),  BorderLayout.CENTER);
        root.add(buildFooter(),  BorderLayout.SOUTH);

        setContentPane(root);
    }

    /** Purple gradient header with device info */
    private JPanel buildHeader() {
        JPanel hdr = new JPanel(new BorderLayout());
        hdr.setBackground(C_HEADER_BG);
        hdr.setPreferredSize(new Dimension(0, 72));
        hdr.setBorder(BorderFactory.createEmptyBorder(0, 24, 0, 24));

        // Left: icon + title
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        left.setOpaque(false);

        // Biometric device SVG icon
        try {
            FlatSVGIcon svgIcon = new FlatSVGIcon("icons/biometric.svg", 34, 34);
            svgIcon.setColorFilter(new FlatSVGIcon.ColorFilter(c -> new Color(0xC4B5FD)));
            JLabel icon = new JLabel(svgIcon);
            left.add(icon);
        } catch (Exception ex) {
            JLabel icon = new JLabel("⬡");
            icon.setFont(new Font("Segoe UI", Font.BOLD, 28));
            icon.setForeground(new Color(0xC4B5FD));
            left.add(icon);
        }

        JPanel text = new JPanel();
        text.setOpaque(false);
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        JLabel titleLbl = new JLabel("Import Biometric Users");
        titleLbl.setFont(FNT_TITLE);
        titleLbl.setForeground(C_HEADER_FG);
        String loc = DatabaseManager.str(device, "location");
        String displayLoc = (loc != null && !loc.trim().isEmpty()) ? "  ·  " + loc : "";
        JLabel subLbl = new JLabel(
            DatabaseManager.str(device, "device_name") + displayLoc + "  ·  " +
            DatabaseManager.str(device, "ip_address") + ":" +
            DatabaseManager.num(device, "port"));
        subLbl.setFont(FNT_SUB);
        subLbl.setForeground(new Color(0xC4B5FD));
        text.add(titleLbl);
        text.add(subLbl);
        left.add(text);
        hdr.add(left, BorderLayout.CENTER);

        // Right: counter badge
        counterLabel = new JLabel("—");
        counterLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));
        counterLabel.setForeground(new Color(0xFDE68A));
        counterLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 4));
        hdr.add(counterLabel, BorderLayout.EAST);

        // Vertical centering hack
        hdr.addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent e) {
                left.setBorder(BorderFactory.createEmptyBorder(
                    (hdr.getHeight() - left.getPreferredSize().height) / 2, 0, 0, 0));
            }
        });

        return hdr;
    }

    /** Table + legend */
    private JPanel buildCenter() {
        // ── Table model with checkbox column ──
        model = new CheckboxModel();
        table = new JTable(model);
        table.setFont(FNT_TBL);
        table.setRowHeight(32);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.setSelectionBackground(C_SEL_BG);
        table.setFocusable(true);

        // Column header styling
        JTableHeader hdr = table.getTableHeader();
        hdr.setFont(FNT_TBL_HDR);
        hdr.setBackground(C_COL_HDR_BG);
        hdr.setForeground(C_COL_HDR_FG);
        hdr.setPreferredSize(new Dimension(0, 36));
        hdr.setDefaultRenderer(new HeaderRenderer());

        // Column widths
        table.getColumnModel().getColumn(0).setPreferredWidth(44);   // ☑
        table.getColumnModel().getColumn(0).setMaxWidth(44);
        table.getColumnModel().getColumn(1).setPreferredWidth(100);  // Enroll ID
        table.getColumnModel().getColumn(2).setPreferredWidth(160);  // Device Name
        table.getColumnModel().getColumn(3).setPreferredWidth(110);  // Emp ID
        table.getColumnModel().getColumn(4).setPreferredWidth(190);  // Emp Name
        table.getColumnModel().getColumn(5).setPreferredWidth(140);  // Status

        // Checkbox column renderer/editor
        table.getColumnModel().getColumn(0).setCellRenderer(new CheckboxCellRenderer());
        table.getColumnModel().getColumn(0).setCellEditor(new CheckboxCellEditor());

        // Row colour renderer for all other columns
        javax.swing.table.TableCellRenderer rowRenderer = new RowColorRenderer();
        for (int c = 1; c < model.getColumnCount(); c++) {
            table.getColumnModel().getColumn(c).setCellRenderer(rowRenderer);
        }

        // Click anywhere on row to toggle checkbox
        table.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                int row = table.rowAtPoint(e.getPoint());
                int col = table.columnAtPoint(e.getPoint());
                if (row < 0) return;
                // Toggle if clicking anywhere except the checkbox column itself
                // (checkbox column already handled by editor)
                if (col != 0) {
                    boolean cur = (Boolean) model.getValueAt(row, 0);
                    String status = (String) model.getValueAt(row, 5);
                    if (!"Already Mapped".equals(status)) {
                        model.setValueAt(!cur, row, 0);
                    }
                }
                updateCounter();
            }
        });

        JScrollPane sp = new JScrollPane(table);
        sp.setBorder(BorderFactory.createEmptyBorder());
        sp.getViewport().setBackground(Color.WHITE);

        // ── Legend panel ──
        JPanel legend = new JPanel(new FlowLayout(FlowLayout.LEFT, 18, 8));
        legend.setBackground(new Color(0xF9FAFB));
        legend.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(0xE5E7EB)));
        legend.add(badge("New — will be added", C_NEW_FG, C_NEW_BG, "plus.svg"));
        legend.add(badge("Already Mapped",      C_MAP_FG, C_MAP_BG, "check.svg"));

        JLabel hint = new JLabel("Click row to select · Ctrl+A to select all");
        hint.setFont(new Font("Segoe UI", Font.ITALIC, 11));
        hint.setForeground(new Color(0x94A3B8));
        try {
            FlatSVGIcon infoIcon = new FlatSVGIcon("icons/info.svg", 13, 13);
            infoIcon.setColorFilter(new FlatSVGIcon.ColorFilter(c -> new Color(0x94A3B8)));
            hint.setIcon(infoIcon);
            hint.setIconTextGap(5);
        } catch (Exception ignored) {}
        legend.add(hint);

        JPanel center = new JPanel(new BorderLayout());
        center.add(sp,     BorderLayout.CENTER);
        center.add(legend, BorderLayout.SOUTH);
        return center;
    }

    private JLabel badge(String text, Color fg, Color bg, String iconName) {
        JLabel l = new JLabel(text);
        l.setFont(new Font("Segoe UI", Font.BOLD, 11));
        l.setForeground(fg);
        l.setBackground(bg);
        l.setOpaque(true);
        l.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(fg.brighter(), 1, true),
            BorderFactory.createEmptyBorder(3, 10, 3, 10)));
        l.setIconTextGap(6);
        if (iconName != null) {
            try {
                FlatSVGIcon icon = new FlatSVGIcon("icons/" + iconName, 12, 12);
                icon.setColorFilter(new FlatSVGIcon.ColorFilter(c -> fg));
                l.setIcon(icon);
            } catch (Exception ignored) {}
        }
        return l;
    }

    /** Footer with progress + action buttons */
    private JPanel buildFooter() {
        JPanel footer = new JPanel(new BorderLayout());
        footer.setBackground(new Color(0xF8FAFC));
        footer.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(0xE2E8F0)),
            BorderFactory.createEmptyBorder(12, 20, 12, 20)));

        // Progress row
        JPanel progRow = new JPanel(new BorderLayout(10, 4));
        progRow.setOpaque(false);
        progress = new JProgressBar();
        progress.setIndeterminate(true);
        progress.setPreferredSize(new Dimension(0, 6));
        progress.setBorderPainted(false);
        progress.setForeground(C_HEADER_BG);
        statusLabel = new JLabel("Connecting to device…");
        statusLabel.setFont(FNT_STATUS);
        statusLabel.setForeground(new Color(0x475569));
        progRow.add(progress,    BorderLayout.NORTH);
        progRow.add(statusLabel, BorderLayout.CENTER);

        // Button row
        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        btnRow.setOpaque(false);
        btnRow.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));

        selAllBtn  = styledBtn("Select All New",  C_BTN_ALL,              "check.svg");
        selNoneBtn = styledBtn("Deselect All",    C_BTN_CLOSE,            "x.svg");
        importBtn  = styledBtn("Import Selected", C_BTN_IMPORT,           "employees.svg");
        JButton closeBtn = styledBtn("Close",     new Color(0xDC2626),    "x.svg");

        importBtn.setEnabled(false);
        selAllBtn.setEnabled(false);
        selNoneBtn.setEnabled(false);

        selAllBtn.addActionListener(e  -> { selectAll(true);  updateCounter(); });
        selNoneBtn.addActionListener(e -> { selectAll(false); updateCounter(); });
        importBtn.addActionListener(e  -> doImport());
        closeBtn.addActionListener(e   -> dispose());

        btnRow.add(selAllBtn);
        btnRow.add(selNoneBtn);
        btnRow.add(Box.createHorizontalStrut(20));
        btnRow.add(importBtn);
        btnRow.add(closeBtn);

        footer.add(progRow, BorderLayout.NORTH);
        footer.add(btnRow,  BorderLayout.SOUTH);
        return footer;
    }

    private JButton styledBtn(String text, Color bg, String iconName) {
        JButton b = new JButton(text);
        b.setFont(new Font("Segoe UI", Font.BOLD, 12));
        b.setBackground(bg);
        b.setForeground(Color.WHITE);
        b.setFocusPainted(false);
        b.setBorder(BorderFactory.createEmptyBorder(8, 14, 8, 18));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setOpaque(true);
        b.setHorizontalTextPosition(SwingConstants.RIGHT);
        b.setIconTextGap(8);
        // SVG icon
        if (iconName != null) {
            try {
                FlatSVGIcon icon = new FlatSVGIcon("icons/" + iconName, 15, 15);
                icon.setColorFilter(new FlatSVGIcon.ColorFilter(c -> Color.WHITE));
                b.setIcon(icon);
            } catch (Exception ignored) {}
        }
        // Hover effect
        b.addMouseListener(new MouseAdapter() {
            final Color orig = bg;
            @Override public void mouseEntered(MouseEvent e) { b.setBackground(orig.darker()); }
            @Override public void mouseExited(MouseEvent e)  { b.setBackground(orig); }
        });
        return b;
    }

    // ── Data fetching ─────────────────────────────────────────────────────────
    private void fetchUsers() {
        String ip   = DatabaseManager.str(device, "ip_address");
        int    port = DatabaseManager.num(device, "port");
        ZkProtocol zk = new ZkProtocol(ip, port, 10000);

        try {
            if (zk.connect()) {
                setStatus("Reading users from device…");
                deviceUsers = zk.getUsers();
                zk.disconnect();
                setStatus("Matching against Employee Master…");
                populateTable();
            } else {
                setStatus("❌ Connection failed. Check device IP and port.");
                SwingUtilities.invokeLater(() -> { progress.setIndeterminate(false); });
            }
        } catch (Exception ex) {
            setStatus("Error: " + ex.getMessage());
        }
    }

    private void populateTable() {
        try {
            List<Map<String, Object>> existing =
                db.query("SELECT emp_id, emp_name, device_enroll_id FROM employees");

            // Build fast-lookup set of normalised enroll IDs already in DB
            Map<String, Map<String, Object>> dbMap = new LinkedHashMap<>();
            for (Map<String, Object> e : existing) {
                String norm = normalise(DatabaseManager.str(e, "device_enroll_id"));
                dbMap.put(norm, e);
            }

            List<Object[]> rows = new ArrayList<>();
            int newCount = 0;

            for (Map<String, String> u : deviceUsers) {
                String uid    = u.get("user_id");
                String name   = u.get("name");
                String norm   = normalise(uid);
                boolean isNew = !dbMap.containsKey(norm);

                String empId   = "";
                String empName = "";
                String status;

                if (!isNew) {
                    Map<String, Object> matched = dbMap.get(norm);
                    empId   = DatabaseManager.str(matched, "emp_id");
                    empName = DatabaseManager.str(matched, "emp_name");
                    status  = "Already Mapped";
                } else {
                    status = "New — will be added";
                    newCount++;
                }

                // CheckboxModel row: [Boolean, enrollId, deviceName, empId, empName, status]
                rows.add(new Object[]{ isNew, uid, name, empId, empName, status });
            }

            final int finalNew = newCount;
            SwingUtilities.invokeLater(() -> {
                model.setRowCount(0);
                for (Object[] r : rows) model.addRow(r);

                progress.setIndeterminate(false);
                progress.setValue(100);
                importBtn.setEnabled(finalNew > 0);
                selAllBtn.setEnabled(finalNew > 0);
                selNoneBtn.setEnabled(true);

                if (finalNew == 0) {
                    setStatus("✅ All " + deviceUsers.size() + " users are already in the database.");
                } else {
                    setStatus("Found " + finalNew + " new user(s) — " +
                        (deviceUsers.size() - finalNew) + " already mapped.");
                }
                updateCounter();
            });

        } catch (Exception ex) {
            setStatus("Error: " + ex.getMessage());
        }
    }

    // ── Import action ─────────────────────────────────────────────────────────
    private void doImport() {
        List<Object[]> toImport = new ArrayList<>();
        for (int r = 0; r < model.getRowCount(); r++) {
            if (Boolean.TRUE.equals(model.getValueAt(r, 0)) &&
                "New — will be added".equals(model.getValueAt(r, 5))) {
                toImport.add(new Object[]{
                    model.getValueAt(r, 1),  // enrollId
                    model.getValueAt(r, 2)   // deviceName
                });
            }
        }

        if (toImport.isEmpty()) {
            showInfo("Please select at least one new user to import.");
            return;
        }

        // Build a modern confirm dialog
        String msg = "<html><b>" + toImport.size() + " new user(s) will be added:</b><br><br>";
        for (Object[] row : toImport)
            msg += "&nbsp;&nbsp;• <b>" + row[1] + "</b>  (Enroll ID: " + row[0] + ")<br>";
        msg += "<br>They will be added with minimal info. You can edit full details later.</html>";

        if (!UIHelper.confirm(this, "Confirm Import", msg)) return;

        int saved = 0;
        List<String> errors = new ArrayList<>();
        for (Object[] row : toImport) {
            String enrollId  = row[0].toString();
            String name      = row[1].toString();
            String empId     = enrollId; // use enroll ID as emp_id by default

            try {
                db.execute(
                    "INSERT INTO employees (emp_id, emp_name, device_enroll_id, status) " +
                    "VALUES (?, ?, ?, 'Active') " +
                    "ON CONFLICT(emp_id) DO UPDATE SET emp_name=excluded.emp_name, device_enroll_id=excluded.device_enroll_id",
                    empId, name, enrollId);
                saved++;
            } catch (Exception ex) {
                // Try MySQL syntax if SQLite failed
                try {
                    db.execute(
                        "INSERT INTO employees (emp_id, emp_name, device_enroll_id, status) " +
                        "VALUES (?, ?, ?, 'Active') " +
                        "ON DUPLICATE KEY UPDATE emp_name=VALUES(emp_name), device_enroll_id=VALUES(device_enroll_id)",
                        empId, name, enrollId);
                    saved++;
                } catch (Exception ex2) {
                    errors.add(name + ": " + ex2.getMessage());
                }
            }
        }

        if (errors.isEmpty()) {
            UIHelper.showSuccess(this, saved + " employee(s) imported successfully!\nYou can edit their full details from Employee Master.");
        } else {
            UIHelper.showInfo(this, "Imported " + saved + " / " + toImport.size() + " users.\nErrors:\n" +
                String.join("\n", errors));
        }

        // Refresh the table to reflect new status
        new Thread(this::fetchUsers).start();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private static String normalise(String s) {
        if (s == null) return "";
        s = s.trim().replaceAll("^0+", "");
        return s.isEmpty() ? "0" : s;
    }

    private void selectAll(boolean select) {
        for (int r = 0; r < model.getRowCount(); r++) {
            if ("New — will be added".equals(model.getValueAt(r, 5)))
                model.setValueAt(select, r, 0);
        }
    }

    private void updateCounter() {
        long sel = 0;
        long total = 0;
        for (int r = 0; r < model.getRowCount(); r++) {
            if ("New — will be added".equals(model.getValueAt(r, 5))) {
                total++;
                if (Boolean.TRUE.equals(model.getValueAt(r, 0))) sel++;
            }
        }
        counterLabel.setText(sel + " / " + total + " new selected");
    }

    private void setStatus(String msg) {
        SwingUtilities.invokeLater(() -> statusLabel.setText(msg));
    }


    private void showInfo(String msg) {
        UIHelper.showInfo(this, msg);
    }

    // ── Inner classes ─────────────────────────────────────────────────────────

    /** Table model with Boolean checkbox in column 0 */
    private static class CheckboxModel extends DefaultTableModel {
        static final String[] COLS = {"☑", "Enroll ID", "Name on Device", "Employee ID", "Employee Name", "Status"};

        CheckboxModel() { super(COLS, 0); }

        @Override public Class<?> getColumnClass(int col) {
            return col == 0 ? Boolean.class : String.class;
        }

        @Override public boolean isCellEditable(int row, int col) {
            // Only allow editing checkbox, and only for new users
            if (col != 0) return false;
            return "New — will be added".equals(getValueAt(row, 5));
        }
    }

    /** Colours each row based on status */
    private static class RowColorRenderer extends DefaultTableCellRenderer {
        @Override public Component getTableCellRendererComponent(
                JTable t, Object v, boolean sel, boolean foc, int row, int col) {
            super.getTableCellRendererComponent(t, v, sel, foc, row, col);
            String status = (String) t.getModel().getValueAt(row, 5);
            setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(0xF1F5F9)));

            if (sel) {
                setBackground(C_SEL_BG);
                setForeground(new Color(0x3730A3));
            } else if ("Already Mapped".equals(status)) {
                setBackground(C_MAP_BG);
                setForeground(C_MAP_FG);
            } else {
                setBackground(C_NEW_BG);
                setForeground(C_NEW_FG);
            }
            return this;
        }
    }

    /** Custom checkbox renderer matching the row colours */
    private static class CheckboxCellRenderer extends JCheckBox implements TableCellRenderer {
        CheckboxCellRenderer() {
            setHorizontalAlignment(SwingConstants.CENTER);
            setOpaque(true);
        }

        @Override public Component getTableCellRendererComponent(
                JTable t, Object v, boolean sel, boolean foc, int row, int col) {
            String status = (String) t.getModel().getValueAt(row, 5);
            setSelected(Boolean.TRUE.equals(v));
            setEnabled(!"Already Mapped".equals(status));

            if (sel) {
                setBackground(C_SEL_BG);
            } else if ("Already Mapped".equals(status)) {
                setBackground(C_MAP_BG);
            } else {
                setBackground(C_NEW_BG);
                setForeground(C_NEW_CB);
            }
            return this;
        }
    }

    /** Checkbox cell editor */
    private static class CheckboxCellEditor extends DefaultCellEditor {
        CheckboxCellEditor() { super(new JCheckBox()); }

        @Override public Component getTableCellEditorComponent(
                JTable t, Object v, boolean sel, int row, int col) {
            JCheckBox cb = (JCheckBox) super.getTableCellEditorComponent(t, v, sel, row, col);
            cb.setHorizontalAlignment(SwingConstants.CENTER);
            cb.setSelected(Boolean.TRUE.equals(v));
            cb.setBackground(C_NEW_BG);
            return cb;
        }
    }

    /** Column header renderer */
    private static class HeaderRenderer extends DefaultTableCellRenderer {
        HeaderRenderer() {
            setHorizontalAlignment(SwingConstants.CENTER);
            setOpaque(true);
        }

        @Override public Component getTableCellRendererComponent(
                JTable t, Object v, boolean sel, boolean foc, int row, int col) {
            super.getTableCellRendererComponent(t, v, sel, foc, row, col);
            setBackground(C_COL_HDR_BG);
            setForeground(C_COL_HDR_FG);
            setFont(FNT_TBL_HDR);
            setBorder(BorderFactory.createMatteBorder(0, 0, 2, 1, new Color(0x6D28D9)));
            return this;
        }
    }
}
