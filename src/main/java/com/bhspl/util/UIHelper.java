package com.bhspl.util;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.table.*;
import net.miginfocom.swing.MigLayout;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

/**
 * Shared UI helpers — colors, button factory, table factory.
 * Refactored for a premium, modern aesthetic.
 */
public class UIHelper {
    // ── BAVYA Brand Palette (Extracted from Logo) ──────────────────────────
    public static final Color PRIMARY = new Color(0x8E0E6C); // Bavya Purple
    public static final Color SECONDARY = new Color(0xD8005A); // Bavya Magenta
    public static final Color ACCENT = new Color(0xF99D1C); // Bavya Orange
    public static final Color SUCCESS = new Color(0x059669); // Emerald 600
    public static final Color WARNING_COL = ACCENT;
    public static final Color WARNING = WARNING_COL;
    public static final Color DANGER = SECONDARY;
    public static final Color BG_MAIN = new Color(0xF8FAFC); // Slate 50
    public static final Color BG_CARD = Color.WHITE;
    public static final Color TEXT_DARK = new Color(0x0F172A); // Slate 900
    public static final Color TEXT_LIGHT = new Color(0x64748B); // Slate 500
    public static final Color TEXT_MUTE = new Color(0x94A3B8); // Slate 400
    public static final Color BORDER = new Color(0xE2E8F0); // Slate 200
    public static final Color HEADER_FG = Color.WHITE;

    // Light Tints for Dashboard Cards
    public static final Color BG_SUCCESS_LIT = new Color(0xF0FDF4);
    public static final Color BG_DANGER_LIT = new Color(0xFFF1F2);
    public static final Color BG_PRIMARY_LIT = new Color(0xFDF4FF);
    public static final Color BG_WARNING_LIT = new Color(0xFFFBEB);

    // Sidebar colors
    public static final Color SIDEBAR_BG = Color.WHITE;
    public static final Color SIDEBAR_HOVER = new Color(0xF1F5F9); // Slate 100
    public static final Color SIDEBAR_SEL = new Color(0xEEF2FF); // Indigo 50
    public static final Color SIDEBAR_TEXT_SEL = PRIMARY;

    // Typography
    public static final Font FNT_MAIN = new Font("Segoe UI", Font.PLAIN, 14);
    public static final Font FNT_BOLD = new Font("Segoe UI", Font.BOLD, 14);
    public static final Font FNT_MEDIUM = new Font("Segoe UI", Font.PLAIN, 15);
    public static final Font FNT_TITLE = new Font("Segoe UI", Font.BOLD, 20);
    public static final Font FNT_STAT = new Font("Segoe UI", Font.BOLD, 36);

    // Legacy Button/Tree color constants (mapped to new palette for compatibility)
    public static final Color BTN_PRIMARY = PRIMARY;
    public static final Color BTN_SUCCESS = SUCCESS;
    public static final Color BTN_DANGER = DANGER;
    public static final Color BTN_WARNING = WARNING_COL;
    public static final Color TREE_ODD = new Color(0xF8FAFC);
    public static final Color TREE_EVEN = Color.WHITE;
    public static final Color TREE_SELECT = new Color(0xEEF2FF);

    /**
     * Creates a premium card border.
     */
    public static Border createCardBorder() {
        return BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0xE2E8F0), 1, true),
                new EmptyBorder(24, 24, 24, 24));
    }

    /**
     * A modern panel with rounded corners and optional border.
     */
    public static class RoundedPanel extends JPanel {
        private final int arc;
        private Color borderColor = new Color(0xE2E8F0);

        public RoundedPanel(int arc) {
            this.arc = arc;
            setOpaque(false);
        }

        public void setBorderColor(Color c) {
            this.borderColor = c;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(getBackground());
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), arc, arc);
            if (borderColor != null) {
                g2.setColor(borderColor);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
            }
            g2.dispose();
            super.paintComponent(g);
        }
    }

    public static boolean confirm(Component parent, String title, String message) {
        JDialog dlg = new JDialog(parent instanceof Window ? (Window) parent : null, title,
                Dialog.ModalityType.APPLICATION_MODAL);
        dlg.setUndecorated(true);
        dlg.setBackground(new Color(0, 0, 0, 0));

        RoundedPanel card = new RoundedPanel(16);
        card.setBackground(Color.WHITE);
        card.setLayout(new MigLayout("ins 30, wrap, gap 20", "[350!]", "[] [] []"));

        JLabel titleLbl = new JLabel(title.toUpperCase());
        titleLbl.setFont(new Font("Segoe UI", Font.BOLD, 14));
        titleLbl.setForeground(PRIMARY);

        JLabel msgLbl = new JLabel("<html><div style='width: 300px;'>" + message + "</div></html>");
        msgLbl.setFont(new Font("Segoe UI", Font.PLAIN, 15));
        msgLbl.setForeground(TEXT_DARK);

        JPanel btnBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        btnBar.setOpaque(false);
        JButton noBtn = makeButton("Cancel", new Color(0x64748B));
        JButton yesBtn = makeButton("Confirm", PRIMARY);

        final boolean[] result = { false };
        noBtn.addActionListener(e -> dlg.dispose());
        yesBtn.addActionListener(e -> {
            result[0] = true;
            dlg.dispose();
        });

        btnBar.add(noBtn);
        btnBar.add(yesBtn);

        card.add(titleLbl);
        card.add(msgLbl);
        card.add(btnBar, "growx, gaptop 10");

        dlg.setContentPane(card);
        dlg.pack();
        dlg.setLocationRelativeTo(parent);
        dlg.setVisible(true);

        return result[0];
    }

    // ── darken a color slightly (for hover) ──────────────────────────────────
    public static Color darken(Color c) {
        float[] hsb = Color.RGBtoHSB(c.getRed(), c.getGreen(), c.getBlue(), null);
        return Color.getHSBColor(hsb[0], hsb[1], Math.max(0f, hsb[2] - 0.08f));
    }

    /**
     * Creates a modern styled button with rounded corners and subtle transitions.
     */
    public static JButton makeButton(String text, Color bg) {
        return makeButton(text, bg, null);
    }

    /**
     * Creates a modern styled button with rounded corners and optional icon.
     */
    public static JButton makeButton(String text, Color bg, String iconPath) {
        JButton btn = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                int arc = 10;
                if (getModel().isPressed()) {
                    g2.setColor(darken(bg));
                } else if (getModel().isRollover()) {
                    g2.setColor(bg.brighter());
                } else {
                    g2.setColor(bg);
                }

                g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), arc, arc));
                g2.dispose();
                super.paintComponent(g);
            }
        };

        if (iconPath != null) {
            try {
                com.formdev.flatlaf.extras.FlatSVGIcon icon = new com.formdev.flatlaf.extras.FlatSVGIcon(
                        "icons/" + iconPath, 16, 16);
                icon.setColorFilter(new com.formdev.flatlaf.extras.FlatSVGIcon.ColorFilter(c -> Color.WHITE));
                btn.setIcon(icon);
                btn.setIconTextGap(8);
            } catch (Exception ignored) {
            }
        }

        btn.setContentAreaFilled(false);
        btn.setForeground(Color.WHITE);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 14));
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setBorder(new EmptyBorder(10, 20, 10, 20));
        return btn;
    }

    public static String statusSym(String raw) {
        if (raw == null || raw.isEmpty())
            return "A";
        switch (raw) {
            case "Present":
                return "P";
            case "Absent":
                return "A";
            case "Half Day":
                return "HD";
            case "On Leave":
                return "CL";
            case "Casual Leave":
                return "CL";
            case "Sick Leave":
                return "SL";
            case "Earned Leave":
                return "EL";
            case "Comp Off":
                return "CO";
            case "LWP":
                return "LWP";
            case "Maternity Leave":
                return "ML";
            case "Maternity":
                return "ML";
            case "Paternity Leave":
                return "PL";
            case "Paternity":
                return "PL";
            case "OD":
                return "OD";
            case "On Duty":
                return "OD";
            case "WeekOff":
                return "WO";
            case "Week Off":
                return "WO";
            case "Holiday":
                return "PH";
            default:
                return raw.length() >= 3 ? raw.substring(0, 3).toUpperCase() : raw.toUpperCase();
        }
    }

    /**
     * Styles a raw JTable with modern typography and alternating rows.
     */
    public static void styleTable(JTable table) {
        table.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        table.setRowHeight(44);
        table.setGridColor(new Color(0xF1F5F9));
        table.setSelectionBackground(new Color(0xF1F5F9));
        table.setSelectionForeground(TEXT_DARK);
        table.setShowGrid(false);
        table.setShowHorizontalLines(true);
        table.setIntercellSpacing(new Dimension(0, 1));

        JTableHeader header = table.getTableHeader();
        header.setBackground(PRIMARY);
        header.setForeground(Color.WHITE);
        header.setFont(new Font("Segoe UI", Font.BOLD, 13));
        header.setReorderingAllowed(false);
        header.setPreferredSize(new Dimension(header.getWidth(), 48));

        // Header Renderer with padding
        header.setDefaultRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object val, boolean s, boolean f, int r, int c) {
                JLabel l = (JLabel) super.getTableCellRendererComponent(t, val, s, f, r, c);
                l.setBackground(PRIMARY);
                l.setForeground(Color.WHITE);
                l.setHorizontalAlignment(SwingConstants.CENTER);
                l.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, new Color(0x7E0E5C)));
                return l;
            }
        });

        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object val, boolean sel, boolean focus, int row,
                    int col) {
                Component c = super.getTableCellRendererComponent(t, val, sel, focus, row, col);
                setBorder(BorderFactory.createEmptyBorder(0, 16, 0, 16));

                if (!sel) {
                    c.setBackground(row % 2 == 0 ? Color.WHITE : new Color(0xF8FAFC));
                }

                String s = val == null ? "" : val.toString().trim();

                if (t.getColumnName(col).toLowerCase().contains("status")) {
                    if (s.equalsIgnoreCase("Present") || s.equalsIgnoreCase("Active")
                            || s.equalsIgnoreCase("Success")) {
                        setForeground(SUCCESS);
                        setFont(new Font("Segoe UI", Font.BOLD, 14));
                    } else if (s.equalsIgnoreCase("Absent") || s.equalsIgnoreCase("Inactive")
                            || s.equalsIgnoreCase("Error") || s.equalsIgnoreCase("Late")) {
                        setForeground(DANGER);
                        setFont(new Font("Segoe UI", Font.BOLD, 14));
                    } else {
                        setForeground(TEXT_DARK);
                        setFont(new Font("Segoe UI", Font.PLAIN, 14));
                    }
                } else {
                    setForeground(TEXT_DARK);
                }

                if (s.matches("^[\\d.:\\-/]+$")) {
                    setHorizontalAlignment(SwingConstants.CENTER);
                } else {
                    setHorizontalAlignment(SwingConstants.LEFT);
                }
                return c;
            }
        });
    }

    /** Status badges for reports. */
    public static Color symBg(String sym) {
        switch (sym == null ? "" : sym) {
            case "P":
                return new Color(0xDCFCE7); // Emerald 100
            case "A":
                return new Color(0xFEE2E2); // Rose 100
            case "HD":
                return new Color(0xFEF3C7); // Amber 100
            case "CL":
            case "SL":
            case "EL":
                return new Color(0xE0E7FF); // Indigo 100
            case "WO":
            case "PH":
                return new Color(0xF1F5F9); // Slate 100
            default:
                return new Color(0xF1F5F9);
        }
    }

    public static Color symFg(String sym) {
        switch (sym == null ? "" : sym) {
            case "P":
                return new Color(0x065F46);
            case "A":
                return new Color(0x991B1B);
            case "HD":
                return new Color(0x92400E);
            case "CL":
            case "SL":
            case "EL":
                return new Color(0x3730A3);
            case "WO":
            case "PH":
                return new Color(0x475569);
            default:
                return new Color(0x475569);
        }
    }

    public static void centerWindow(Window w, int width, int height) {
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        int h = Math.min(height, screen.height - 80);
        int wd = Math.min(width, screen.width - 40);
        w.setBounds((screen.width - wd) / 2, (screen.height - h) / 2, wd, h);
    }

    /** Container for a modern table. */
    public static class StyledTablePanel extends JPanel {
        private final DefaultTableModel model;
        private final JTable table;

        public StyledTablePanel(String[] columns) {
            super(new BorderLayout());
            setBackground(BG_CARD);
            setBorder(BorderFactory.createLineBorder(BORDER, 1));
            model = new DefaultTableModel(columns, 0) {
                @Override
                public boolean isCellEditable(int r, int c) {
                    return false;
                }
            };
            table = new JTable(model);
            styleTable(table);
            JScrollPane sp = new JScrollPane(table);
            sp.setBorder(BorderFactory.createEmptyBorder());
            sp.getViewport().setBackground(Color.WHITE);
            add(sp, BorderLayout.CENTER);
        }

        public JTable getTable() {
            return table;
        }

        public DefaultTableModel getModel() {
            return model;
        }

        public void clearRows() {
            model.setRowCount(0);
        }

        public void addRow(Object[] rowData) {
            model.addRow(rowData);
        }

        public Object getSelectedValue() {
            int row = table.getSelectedRow();
            if (row < 0)
                return null;
            return model.getValueAt(table.convertRowIndexToModel(row), 0);
        }
    }

    /**
     * A modern gradient panel used for branding areas.
     */
    public static class GradientPanel extends JPanel {
        private final Color c1;
        private final Color c2;

        public GradientPanel(Color c1, Color c2) {
            this.c1 = c1;
            this.c2 = c2;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            GradientPaint gp = new GradientPaint(0, 0, c1, getWidth(), getHeight(), c2);
            g2.setPaint(gp);
            g2.fillRect(0, 0, getWidth(), getHeight());
            g2.dispose();
            super.paintComponent(g);
        }
    }

    public static void showSuccess(Component parent, String message) {
        showMessage(parent, "Success", message, SUCCESS);
    }

    public static void showError(Component parent, String message) {
        showMessage(parent, "Error", message, DANGER);
    }

    private static void showMessage(Component parent, String title, String message, Color color) {
        JDialog dlg = new JDialog(parent instanceof Window ? (Window) parent : null, title,
                Dialog.ModalityType.APPLICATION_MODAL);
        dlg.setUndecorated(true);
        dlg.setBackground(new Color(0, 0, 0, 0));

        RoundedPanel card = new RoundedPanel(16);
        card.setBackground(Color.WHITE);
        card.setLayout(new MigLayout("ins 24, wrap, gap 16", "[320!]", "[] [] []"));

        JLabel titleLbl = new JLabel(title.toUpperCase());
        titleLbl.setFont(new Font("Segoe UI", Font.BOLD, 14));
        titleLbl.setForeground(color);

        JLabel msgLbl = new JLabel("<html><div style='width: 280px;'>" + message + "</div></html>");
        msgLbl.setFont(new Font("Segoe UI", Font.PLAIN, 15));
        msgLbl.setForeground(TEXT_DARK);

        JButton okBtn = makeButton("OK", color);
        okBtn.addActionListener(e -> dlg.dispose());

        card.add(titleLbl);
        card.add(msgLbl);
        card.add(okBtn, "right, gaptop 10, w 80!");

        dlg.setContentPane(card);
        dlg.pack();
        dlg.setLocationRelativeTo(parent);
        dlg.setVisible(true);
    }
    /**
     * A modern multi-color gradient panel inspired by the brand logo.
     * Supports Purple, Magenta, and Orange transitions.
     */
    public static class BrandGradientPanel extends JPanel {
        private final float overlayOpacity;
        private final boolean showPattern;

        public BrandGradientPanel(float overlayOpacity, boolean showPattern) {
            this.overlayOpacity = overlayOpacity;
            this.showPattern = showPattern;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            
            // 1. Deeply Muted Multi-color Gradient (Purple, Magenta, Orange)
            float[] fractions = {0.0f, 0.5f, 1.0f};
            Color[] colors = {
                new Color(0x312e81), // Deep Navy/Purple
                new Color(0x701a75), // Muted Magenta
                new Color(0x7c2d12)  // Deep Muted Orange
            };
            LinearGradientPaint lgp = new LinearGradientPaint(0, 0, getWidth(), getHeight(), fractions, colors);
            g2.setPaint(lgp);
            g2.fillRect(0, 0, getWidth(), getHeight());

            // 2. Minimal Pattern (Optional) - Very low opacity (2%)
            if (showPattern) {
                g2.setColor(new Color(255, 255, 255, 5)); // 2% white opacity
                for (int i = 0; i < getWidth(); i += 60) {
                    for (int j = 0; j < getHeight(); j += 60) {
                        g2.drawOval(i, j, 30, 30);
                    }
                }
            }

            // 3. Dark Overlay
            if (overlayOpacity > 0) {
                g2.setColor(new Color(0, 0, 0, (int)(255 * overlayOpacity)));
                g2.fillRect(0, 0, getWidth(), getHeight());
            }

            g2.dispose();
            super.paintComponent(g);
        }
    }
}
