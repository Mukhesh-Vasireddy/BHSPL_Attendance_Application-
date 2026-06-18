package com.bhspl.ui;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import com.bhspl.db.DatabaseManager;
import com.bhspl.service.SyncService;
import com.bhspl.util.UIHelper;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import java.awt.*;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public class DashboardPanel extends JPanel {

    private UIHelper.StyledTablePanel tablePanel;

    public DashboardPanel() {
        setBackground(UIHelper.BG_MAIN);
        setLayout(new MigLayout("ins 24, wrap, gap 24, fill", "[grow]", "[]24[grow]"));
        buildUI();

        // Initial load
        refreshCards(null, null, null, null);
        loadTable();

        // Removed auto-refresh timer per user request
    }

    public void publicLoadTable() {
        refreshCards(null, null, null, null);
        loadTable();
    }

    private void buildUI() {
        // Stats Row
        JPanel statsRow = new JPanel(new MigLayout("ins 0, gap 16, wrap 4", "[grow, fill]"));
        statsRow.setOpaque(false);

        JPanel card1 = statCard("...", "Today Present", UIHelper.SUCCESS, new Color(0xF0FDF4), "present.svg");
        JPanel card2 = statCard("...", "Today Absents", UIHelper.DANGER, new Color(0xFEF2F2), "absent.svg");
        JPanel card3 = statCard("...", "Total Employees", UIHelper.PRIMARY, new Color(0xF5F3FF), "total.svg");
        JPanel card4 = statCard("...", "On Leave Today", UIHelper.ACCENT, new Color(0xFFFBEB), "leaves_card.svg");

        statsRow.add(card1, "growx");
        statsRow.add(card2, "growx");
        statsRow.add(card3, "growx");
        statsRow.add(card4, "growx");

        add(statsRow, "growx");

        // Responsive stats row
        addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent e) {
                int w = getWidth();
                String layout;
                if (w < 700) layout = "ins 0, gap 16, wrap 1";
                else if (w < 1100) layout = "ins 0, gap 16, wrap 2";
                else layout = "ins 0, gap 16, wrap 4";
                
                if (statsRow.getLayout() instanceof MigLayout) {
                    statsRow.setLayout(new MigLayout(layout, "[grow, fill]"));
                    statsRow.revalidate();
                }
            }
        });

        // Content Row
        JPanel contentRow = new JPanel(new MigLayout("ins 0, wrap", "[grow]", "[grow]"));
        contentRow.setOpaque(false);
        contentRow.add(recentAttendanceSection(), "grow, push");
        add(contentRow, "grow, push");
    }

    private void refreshCards(JPanel c1, JPanel c2, JPanel c3, JPanel c4) {
        // Find existing cards if null (for timer refresh)
        if (c1 == null) {
            if (getComponentCount() == 0)
                return;
            Component comp0 = getComponent(0);
            if (!(comp0 instanceof JPanel))
                return;
            JPanel statsRow = (JPanel) comp0;
            if (statsRow.getComponentCount() < 4)
                return;
            c1 = (JPanel) statsRow.getComponent(0);
            c2 = (JPanel) statsRow.getComponent(1);
            c3 = (JPanel) statsRow.getComponent(2);
            c4 = (JPanel) statsRow.getComponent(3);
        }
        loadStats(c1, c2, c3, c4);
    }

    private void loadStats(JPanel c1, JPanel c2, JPanel c3, JPanel c4) {
        new SwingWorker<int[], Void>() {
            @Override
            protected int[] doInBackground() {
                DatabaseManager db = DatabaseManager.getInstance();
                int[] s = new int[4];
                String today = LocalDate.now().toString();
                try {
                    Map<String, Object> r;
                    // 1. Total Active
                    r = db.fetchOne("SELECT COUNT(*) AS c FROM employees WHERE status='Active'");
                    int total = r != null ? ((Number) r.get("c")).intValue() : 0;
                    s[2] = total;

                    // 2. Today Present
                    r = db.fetchOne("SELECT COUNT(DISTINCT emp_id) AS c FROM attendance WHERE punch_date=?", today);
                    int present = r != null ? ((Number) r.get("c")).intValue() : 0;
                    s[0] = present;

                    // 3. Today Absents (Total - Present)
                    s[1] = Math.max(0, total - present);

                    // 4. On Leave Today
                    r = db.fetchOne(
                            "SELECT COUNT(*) AS c FROM leaves WHERE status='Approved' AND ? BETWEEN from_date AND to_date",
                            today);
                    s[3] = r != null ? ((Number) r.get("c")).intValue() : 0;
                } catch (SQLException ignored) {
                }
                return s;
            }

            @Override
            protected void done() {
                try {
                    int[] s = get();
                    updateCard(c1, String.valueOf(s[0]));
                    updateCard(c2, String.valueOf(s[1]));
                    updateCard(c3, String.valueOf(s[2]));
                    updateCard(c4, String.valueOf(s[3]));
                } catch (Exception ignored) {
                }
            }
        }.execute();
    }

    private void updateCard(JPanel card, String val) {
        JLabel lbl = findLabel(card, 28);
        if (lbl != null)
            lbl.setText(val);
    }

    private JLabel findLabel(Container container, int minSize) {
        for (Component c : container.getComponents()) {
            if (c instanceof JLabel) {
                JLabel l = (JLabel) c;
                if (l.getFont().getSize() >= minSize)
                    return l;
            } else if (c instanceof Container) {
                JLabel l = findLabel((Container) c, minSize);
                if (l != null)
                    return l;
            }
        }
        return null;
    }

    private JPanel statCard(String value, String label, Color accentColor, Color bgColor, String iconPath) {
        UIHelper.RoundedPanel card = new UIHelper.RoundedPanel(16);
        card.setLayout(new MigLayout("ins 24, gap 15", "[grow][]", "[] 4 []"));
        card.setBackground(bgColor);
        card.setBorderColor(new Color(0xE2E8F0));

        // Add accent border on the left
        card.setBorder(BorderFactory.createMatteBorder(0, 5, 0, 0, accentColor));

        JPanel textSide = new JPanel(new MigLayout("ins 0, wrap, gap 0", "[grow]", "[] 4 []"));
        textSide.setOpaque(false);

        JLabel valLabel = new JLabel(value);
        valLabel.setFont(new Font("Segoe UI", Font.BOLD, 38));
        valLabel.setForeground(accentColor);

        JLabel lblLabel = new JLabel(label.toUpperCase());
        lblLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblLabel.setForeground(UIHelper.TEXT_LIGHT);

        textSide.add(valLabel);
        textSide.add(lblLabel);

        card.add(textSide, "growx");

        FlatSVGIcon icon = new FlatSVGIcon("icons/" + iconPath, 48, 48);
        icon.setColorFilter(new FlatSVGIcon.ColorFilter(color -> accentColor));
        JLabel iconLbl = new JLabel(icon);
        iconLbl.putClientProperty("FlatLaf.style", "alpha: 30"); // Subtle transparency
        card.add(iconLbl, "right");

        return card;
    }

    private JPanel recentAttendanceSection() {
        JPanel panel = new JPanel(new MigLayout("ins 24, wrap, gap 20", "[grow]", "[] [grow]"));
        panel.setBackground(Color.WHITE);
        panel.setBorder(UIHelper.createCardBorder());

        JPanel header = new JPanel(new MigLayout("ins 0", "[grow][]", "[]"));
        header.setOpaque(false);

        JLabel title = new JLabel("Live Attendance Overview");
        title.setFont(new Font("Segoe UI", Font.BOLD, 20));
        title.setForeground(UIHelper.TEXT_DARK);
        header.add(title, "growx");

        JButton refreshBtn = UIHelper.makeButton("Refresh Data", new Color(0x334155), "refresh.svg");
        refreshBtn.setFont(new Font("Segoe UI", Font.BOLD, 13));
        refreshBtn.addActionListener(e -> {
            refreshBtn.setEnabled(false);
            refreshBtn.setText("Syncing...");
            SyncService.forceUpdateToday(() -> {
                loadTable();
                refreshBtn.setEnabled(true);
                refreshBtn.setText("Refresh Data");
            });
        });
        header.add(refreshBtn);

        panel.add(header, "growx");

        String[] cols = { "Emp ID", "Name", "Device", "Location", "In Time", "Punches", "Status" };
        tablePanel = new UIHelper.StyledTablePanel(cols);
        // Emp ID, Name, Device, Location, In Time, Punches, Status
        int[] widths = { 80, 180, 130, 130, 90, 80, 110 };
        for (int i = 0; i < widths.length; i++) {
            tablePanel.getTable().getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }

        panel.add(tablePanel, "grow, push");
        return panel;
    }

    private void loadTable() {
        if (tablePanel == null)
            return;
        tablePanel.clearRows();

        SwingWorker<List<Map<String, Object>>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<Map<String, Object>> doInBackground() throws Exception {
                DatabaseManager db = DatabaseManager.getInstance();
                String today = LocalDate.now().toString();
                return db.fetchAll(
                        "SELECT e.emp_id, e.emp_name, a.device_id, " +
                                "COALESCE(d.device_name, CASE WHEN a.in_time IS NOT NULL THEN 'Manual/No Device' ELSE '-' END) AS device_name, " +
                                "COALESCE(d.location, CASE WHEN a.in_time IS NOT NULL THEN 'Not Assigned' ELSE '-' END) AS device_location, " +
                                "DATE_FORMAT(a.in_time, '%H:%i') AS in_time, " +
                                "COALESCE(p1.cnt, p2.cnt, 0) as punches, " +
                                "COALESCE(" +
                                "  a.status, " +
                                "  (SELECT UPPER(leave_type) FROM leaves l WHERE l.emp_id = e.emp_id AND l.status='Approved' AND ? BETWEEN l.from_date AND l.to_date LIMIT 1), " +
                                "  (SELECT UPPER(holiday_name) FROM holidays h WHERE h.holiday_date = ? LIMIT 1), " +
                                "  (SELECT UPPER(CASE WHEN DAYNAME(?) = off_day1 THEN off_day1 ELSE off_day2 END) FROM weekly_offs w " +
                                "   WHERE w.emp_id = e.emp_id AND (? >= w.effective_from) AND (w.effective_to IS NULL OR ? <= w.effective_to) " +
                                "   AND (DAYNAME(?) = w.off_day1 OR DAYNAME(?) = w.off_day2) LIMIT 1), " +
                                "  (CASE WHEN DAYNAME(?) = s.weekly_off1 THEN UPPER(s.weekly_off1) WHEN DAYNAME(?) = s.weekly_off2 THEN UPPER(s.weekly_off2) END), " +
                                "  'Absent' " +
                                ") as status " +
                                "FROM employees e " +
                                "LEFT JOIN shifts s ON e.shift = s.shift_name " +
                                "LEFT JOIN attendance a ON e.emp_id = a.emp_id AND a.punch_date = ? " +
                                "LEFT JOIN devices d ON a.device_id = d.device_id " +
                                "LEFT JOIN (SELECT emp_id, device_id, COUNT(*) as cnt FROM raw_logs WHERE punch_time >= CURDATE() GROUP BY emp_id, device_id) p1 ON e.emp_id = p1.emp_id AND (a.device_id = p1.device_id OR (a.device_id IS NULL AND p1.device_id = 0)) " +
                                "LEFT JOIN (SELECT emp_id, device_id, COUNT(*) as cnt FROM raw_logs WHERE punch_time >= CURDATE() GROUP BY emp_id, device_id) p2 ON e.device_enroll_id = p2.emp_id AND (a.device_id = p2.device_id OR (a.device_id IS NULL AND p2.device_id = 0)) " +
                                "WHERE e.status = 'Active' " +
                                "GROUP BY e.emp_id, e.emp_name, a.device_id, d.device_name, d.location, a.in_time, a.status, p1.cnt, p2.cnt " +
                                "ORDER BY (a.in_time IS NULL) ASC, a.in_time DESC, e.emp_name ASC LIMIT 100",
                        today, today, today, today, today, today, today, today, today, today);
            }

            @Override
            protected void done() {
                try {
                    for (Map<String, Object> r : get()) {
                        tablePanel.addRow(new Object[] {
                                r.get("emp_id"), r.get("emp_name"),
                                r.get("device_name"),
                                r.get("device_location"),
                                (r.get("in_time") != null ? r.get("in_time") : "—"),
                                r.get("punches"),
                                r.get("status")
                        });
                    }
                } catch (Exception ignored) {
                }
            }
        };
        worker.execute();
    }

}
