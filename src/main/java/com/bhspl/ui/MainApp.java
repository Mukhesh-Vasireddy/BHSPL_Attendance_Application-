package com.bhspl.ui;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import com.bhspl.util.UIHelper;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import com.bhspl.service.SyncService;

public class MainApp extends JFrame {

    private final String username;
    private final String role;

    private JPanel contentPanel;
    private CardLayout cardLayout;
    private JLabel pageTitle;
    private ReportsPanel reportsPanel;

    private static final Object[][] NAV_ITEMS = {
            { "Dashboard", "dashboard.svg", null },
            { "Employee Master", "employees.svg", null },
            { "Attendance", "attendance.svg", null },
            { "FOLDER:Reports", "reports.svg", new String[]{"Daily", "Monthly", "Leave Report"} },
            { "Raw Punch Log", "punch_log.svg", null },
            { "Device Manager", "devices.svg", null },
            { "FOLDER:Leave", "leaves.svg", new String[]{"Leave Manager", "OD Requests", "Leave Policy", "Leave Balance", "Holidays"} },
            { "FOLDER:Masters", "designation.svg", new String[]{"Departments", "Designations", "Shifts", "Weekly Off"} },
            { "FOLDER:System", "settings.svg", new String[]{"User Management", "Settings", "DB Backup", "About"} }
    };

    public MainApp(String username, String role) {
        this.username = username;
        this.role = role;

        setTitle("BHSPL Attendance Management System");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1280, 800);
        setMinimumSize(new Dimension(1024, 700));
        setLocationRelativeTo(null);

        buildUI();
    }

    private void buildUI() {
        setLayout(new MigLayout("ins 0, gap 0, fill", "[280!]0[grow]", "[grow]"));

        // ----- SIDEBAR -----
        JPanel sidebar = new JPanel(new MigLayout("ins 0, wrap, gap 0, fill", "[grow]", "[100!]0[grow]"));
        sidebar.setBackground(UIHelper.SIDEBAR_BG);
        sidebar.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, UIHelper.BORDER));

        // Logo Area
        JPanel logoPanel = new JPanel(new MigLayout("ins 0, fill", "[center]", "[center]"));
        logoPanel.setBackground(UIHelper.SIDEBAR_BG);
        logoPanel.setPreferredSize(new Dimension(280, 100));

        try {
            JLabel logoLabel = new JLabel() {
                @Override
                protected void paintComponent(Graphics g) {
                    try {
                        java.net.URL logoUrl = getClass().getResource("/logo.png");
                        if (logoUrl != null) {
                            Image img = javax.imageio.ImageIO.read(logoUrl);
                            Graphics2D g2 = (Graphics2D) g.create();
                            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                            
                            int w = getWidth();
                            int h = (w * img.getHeight(null)) / img.getWidth(null);
                            if (h > getHeight()) {
                                h = getHeight();
                                w = (h * img.getWidth(null)) / img.getHeight(null);
                            }
                            g2.drawImage(img, (getWidth() - w) / 2, (getHeight() - h) / 2, w, h, null);
                            g2.dispose();
                        }
                    } catch (Exception e) {}
                }
            };
            logoPanel.add(logoLabel, "w 180!, h 60!, center");
        } catch (Exception e) {
            JLabel logoText = new JLabel("BAVYA");
            logoText.setFont(new Font("Segoe UI", Font.BOLD, 22));
            logoText.setForeground(UIHelper.PRIMARY);
            logoPanel.add(logoText, "center");
        }
        sidebar.add(logoPanel, "growx, h 100!");

        // Navigation Items
        JPanel tabsPanel = new JPanel(new MigLayout("ins 0, wrap, gap 0", "[grow]", "[]"));
        tabsPanel.setOpaque(false);
        ButtonGroup navGroup = new ButtonGroup();
        JToggleButton firstBtn = null;

        JPanel reportsSubMenu = new JPanel(new MigLayout("ins 0, wrap, gap 0, hidemode 3", "[grow]", "[]"));
        reportsSubMenu.setOpaque(false);
        reportsSubMenu.setVisible(false);

        for (Object[] item : NAV_ITEMS) {
            String label = (String) item[0];
            String iconPath = (String) item[1];
            String[] subItems = (String[]) item[2];

            if (label.startsWith("FOLDER:")) {
                String folderName = label.substring(7);
                JPanel subMenu = new JPanel(new MigLayout("ins 0, wrap, gap 0, hidemode 3", "[grow]", "[]"));
                subMenu.setOpaque(false);
                subMenu.setVisible(false);

                FlatSVGIcon rightIcon = new FlatSVGIcon("icons/chevron_right.svg", 16, 16);
                FlatSVGIcon downIcon = new FlatSVGIcon("icons/chevron_down.svg", 16, 16);
                rightIcon.setColorFilter(new FlatSVGIcon.ColorFilter(c -> new Color(0x94A3B8)));
                downIcon.setColorFilter(new FlatSVGIcon.ColorFilter(c -> UIHelper.PRIMARY));

                JToggleButton folderBtn = createNavButton(folderName, iconPath);
                folderBtn.putClientProperty("JButton.trailingIcon", rightIcon);
                navGroup.add(folderBtn);
                tabsPanel.add(folderBtn, "growx");

                for (String sub : subItems) {
                    if ("Settings".equals(sub) && "Operator".equals(role)) continue;
                    
                    JToggleButton subBtn = createSubNavButton(sub);
                    navGroup.add(subBtn);
                    subMenu.add(subBtn, "growx");
                    
                    subBtn.addActionListener(e -> {
                        if ("Daily".equals(sub) || "Monthly".equals(sub) || "Leave Report".equals(sub)) {
                            cardLayout.show(contentPanel, "Reports");
                            pageTitle.setText("Reports - " + sub);
                            if (reportsPanel != null) reportsPanel.setActiveTab(sub);
                        } else {
                            cardLayout.show(contentPanel, sub);
                            pageTitle.setText(sub);
                        }
                    });
                }
                tabsPanel.add(subMenu, "growx, hidemode 3");
                
                folderBtn.addActionListener(e -> {
                    boolean visible = !subMenu.isVisible();
                    subMenu.setVisible(visible);
                    folderBtn.putClientProperty("JButton.trailingIcon", visible ? downIcon : rightIcon);
                });
            } else {
                JToggleButton btn = createNavButton(label, iconPath);
                navGroup.add(btn);
                tabsPanel.add(btn, "growx");
                if (firstBtn == null) firstBtn = btn;
            }
        }
        JScrollPane navScroll = new JScrollPane(tabsPanel);
        navScroll.setBorder(null);
        navScroll.setOpaque(false);
        navScroll.getViewport().setOpaque(false);
        navScroll.getVerticalScrollBar().setUnitIncrement(16);
        navScroll.getVerticalScrollBar().putClientProperty("JScrollBar.showButtons", false);
        navScroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        
        sidebar.add(navScroll, "grow, pushy");
        add(sidebar, "grow");

        // Make logo clickable
        logoPanel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        final JToggleButton dashBtn = firstBtn;
        logoPanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (dashBtn != null)
                    dashBtn.setSelected(true);
            }
        });

        // ----- MAIN AREA -----
        JPanel mainArea = new JPanel(new MigLayout("ins 0, gap 0, wrap, fill", "[grow]", "[52!][grow][]"));
        mainArea.setBackground(UIHelper.BG_MAIN);

        // Header
        JPanel header = new JPanel(new MigLayout("ins 0 24 0 24, aligny center", "[grow] [] []", "[grow]"));
        header.setBackground(Color.WHITE);
        header.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, UIHelper.BORDER));

        pageTitle = new JLabel("Dashboard");
        pageTitle.setFont(new Font("Segoe UI", Font.BOLD, 20));
        pageTitle.setForeground(UIHelper.PRIMARY);
        header.add(pageTitle, "growx");

        JLabel clockLbl = new JLabel();
        clockLbl.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        clockLbl.setForeground(UIHelper.TEXT_LIGHT);
        header.add(clockLbl);

        // User Info & Logout
        JPanel userBox = new JPanel(new MigLayout("ins 0, gap 10", "[] 5 [] 15 []"));
        userBox.setOpaque(false);
        
        JLabel userLbl = new JLabel(username);
        userLbl.setFont(new Font("Segoe UI", Font.BOLD, 13));
        userLbl.setForeground(UIHelper.PRIMARY);
        
        FlatSVGIcon uIcon = new FlatSVGIcon("icons/user.svg", 20, 20);
        uIcon.setColorFilter(new FlatSVGIcon.ColorFilter(c -> UIHelper.PRIMARY));
        JLabel userIconLbl = new JLabel(uIcon);
        
        FlatSVGIcon lIcon = new FlatSVGIcon("icons/logout.svg", 20, 20);
        lIcon.setColorFilter(new FlatSVGIcon.ColorFilter(c -> UIHelper.DANGER));
        JButton logoutBtn = new JButton(lIcon);
        logoutBtn.setContentAreaFilled(false);
        logoutBtn.setBorderPainted(false);
        logoutBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        logoutBtn.setToolTipText("Logout Securely");
        logoutBtn.addActionListener(e -> {
            if (UIHelper.confirm(this, "Logout", "Are you sure you want to log out securely?")) {
                dispose();
                new LoginWindow().setVisible(true);
            }
        });
        
        userBox.add(userLbl);
        userBox.add(userIconLbl);
        userBox.add(logoutBtn);
        DashboardPanel dashboard = new DashboardPanel();
        
        JButton syncNowBtn = UIHelper.makeButton("Sync Devices", UIHelper.ACCENT);
        syncNowBtn.setIcon(new FlatSVGIcon("icons/sync.svg", 16, 16));
        syncNowBtn.setToolTipText("Sync all biometric devices now");
        syncNowBtn.addActionListener(e -> {
            syncNowBtn.setEnabled(false);
            syncNowBtn.setText("Syncing...");
            SyncService.forceUpdateToday(() -> {
                syncNowBtn.setEnabled(true);
                syncNowBtn.setText("Sync Devices");
                dashboard.publicLoadTable();
                JOptionPane.showMessageDialog(this, "Manual synchronization completed successfully.");
            });
        });
        header.add(syncNowBtn, "gapleft 16");
        header.add(userBox, "gapleft 16");

        Timer t = new Timer(1000, e -> {
            clockLbl.setText(java.time.LocalDateTime.now()
                    .format(java.time.format.DateTimeFormatter.ofPattern("EEE, dd MMM yyyy  HH:mm:ss")));
        });
        t.start();

        mainArea.add(header, "growx");

        // Content
        cardLayout = new CardLayout();
        contentPanel = new JPanel(cardLayout);
        contentPanel.setOpaque(false);

        // Footer for Sync Status
        JPanel footer = new JPanel(new MigLayout("ins 4 24 4 24", "[grow]"));
        footer.setBackground(new Color(0xf8fafc));
        footer.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, UIHelper.BORDER));
        JLabel statusLbl = new JLabel("System Ready");
        statusLbl.setFont(new Font("Segoe UI", Font.ITALIC, 11));
        statusLbl.setForeground(UIHelper.TEXT_LIGHT);
        footer.add(statusLbl, "growx");

        com.bhspl.service.SyncService.setStatusListener(msg -> SwingUtilities.invokeLater(() -> statusLbl.setText("Sync: " + msg)));

        contentPanel.add(dashboard, "Dashboard");
        contentPanel.add(new EmployeePanel(), "Employee Master");
        contentPanel.add(new AttendancePanel(), "Attendance");
        contentPanel.add(new RawPunchLogPanel(), "Raw Punch Log");
        contentPanel.add(new LeavePanel(), "Leave Manager");
        contentPanel.add(new ODRequestPanel(), "OD Requests");
        contentPanel.add(new LeavePolicyPanel(), "Leave Policy");
        contentPanel.add(new LeaveBalancePanel(), "Leave Balance");
        contentPanel.add(new HolidayPanel(), "Holidays");
        
        contentPanel.add(new DevicePanel(), "Device Manager");
        contentPanel.add(new DepartmentPanel(), "Departments");
        contentPanel.add(new DesignationPanel(), "Designations");
        contentPanel.add(new ShiftPanel(), "Shifts");
        contentPanel.add(new WeeklyOffPanel(), "Weekly Off");
        
        contentPanel.add(new UserManagementPanel(), "User Management");
        contentPanel.add(new BackupPanel(), "DB Backup");
        contentPanel.add(new AboutPanel(), "About");

        reportsPanel = new ReportsPanel(false);
        contentPanel.add(reportsPanel, "Reports");
        if (!"Operator".equals(role)) {
            contentPanel.add(new SettingsPanel(), "Settings");
        }

        mainArea.add(contentPanel, "grow, push");
        mainArea.add(footer, "growx");
        add(mainArea, "grow, push");

        if (firstBtn != null)
            firstBtn.setSelected(true);
    }

    private JComponent createSeparator(String title) {
        JPanel p = new JPanel(new MigLayout("ins 15 15 5 15, fillx", "[grow] [] [grow]", "[]"));
        p.setOpaque(false);
        JLabel l = new JLabel("— " + title + " —");
        l.setFont(new Font("Segoe UI", Font.BOLD, 10));
        l.setForeground(new Color(0x64748B));
        p.add(l, "center");
        return p;
    }

    private JToggleButton createNavButton(String name, String iconPath) {
        FlatSVGIcon icon = new FlatSVGIcon("icons/" + iconPath, 18, 18);
        JToggleButton btn = new JToggleButton(name, icon);
        btn.setIconTextGap(15);
        btn.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        btn.setForeground(UIHelper.TEXT_LIGHT);
        btn.setBackground(UIHelper.SIDEBAR_BG);
        btn.setHorizontalAlignment(SwingConstants.LEFT);
        btn.setBorder(new EmptyBorder(14, 24, 14, 10));
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        btn.addItemListener(e -> {
            boolean selected = e.getStateChange() == ItemEvent.SELECTED;
            btn.setBackground(selected ? UIHelper.SIDEBAR_SEL : UIHelper.SIDEBAR_BG);
            btn.setForeground(selected ? UIHelper.SIDEBAR_TEXT_SEL : UIHelper.TEXT_LIGHT);
            btn.setFont(selected ? new Font("Segoe UI", Font.BOLD, 16) : new Font("Segoe UI", Font.PLAIN, 16));
            
            if (selected) {
                btn.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 5, 0, 0, UIHelper.PRIMARY),
                    new EmptyBorder(14, 19, 14, 10)
                ));
            } else {
                btn.setBorder(new EmptyBorder(14, 24, 14, 10));
            }

            if (selected) {
                if (!name.contains("\u25B6") && !name.contains("\u25BC")) {
                    cardLayout.show(contentPanel, name);
                    pageTitle.setText(name);
                }
            }
        });

        btn.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                if (!btn.isSelected())
                    btn.setBackground(UIHelper.SIDEBAR_HOVER);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                if (!btn.isSelected())
                    btn.setBackground(UIHelper.SIDEBAR_BG);
            }
        });

        return btn;
    }

    private JToggleButton createSubNavButton(String name) {
        JToggleButton btn = new JToggleButton(name);
        btn.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        btn.setForeground(UIHelper.TEXT_LIGHT);
        btn.setBackground(UIHelper.SIDEBAR_BG);
        btn.setHorizontalAlignment(SwingConstants.LEFT);
        btn.setBorder(new EmptyBorder(10, 45, 10, 10));
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        btn.addItemListener(e -> {
            boolean selected = e.getStateChange() == ItemEvent.SELECTED;
            btn.setBackground(selected ? UIHelper.SIDEBAR_SEL : UIHelper.SIDEBAR_BG);
            btn.setForeground(selected ? UIHelper.SIDEBAR_TEXT_SEL : UIHelper.TEXT_LIGHT);
            btn.setFont(selected ? new Font("Segoe UI", Font.BOLD, 14) : new Font("Segoe UI", Font.PLAIN, 14));
            
            if (selected) {
                btn.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 4, 0, 0, UIHelper.PRIMARY),
                    new EmptyBorder(10, 41, 10, 10)
                ));
            } else {
                btn.setBorder(new EmptyBorder(10, 45, 10, 10));
            }
        });
        
        return btn;
    }

}
