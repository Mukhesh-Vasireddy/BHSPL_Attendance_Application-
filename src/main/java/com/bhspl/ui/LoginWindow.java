package com.bhspl.ui;

import com.bhspl.db.DatabaseManager;
import com.bhspl.util.UIHelper;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.sql.SQLException;
import java.util.Map;

/**
 * Modern Enterprise Login UI with high-fidelity split layout.
 * Optimized for professional enterprise look with muted brand colors.
 */
public class LoginWindow extends JFrame {

    private JTextField userField;
    private JPasswordField passField;
    private JLabel statusLabel;

    public LoginWindow() {
        setTitle("BHSPL Attendance - Dedicated Login Portal");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setResizable(true);
        setSize(1280, 800);
        UIHelper.centerWindow(this, 1280, 800);

        getContentPane().setBackground(Color.WHITE);
        // Immersive Branding: Unified gradient background across the entire window
        UIHelper.BrandGradientPanel mainPanel = new UIHelper.BrandGradientPanel(0.15f, true);
        mainPanel.setLayout(new MigLayout("fill, ins 0", "[40%, fill]0[60%, fill]", "[fill]"));
        setContentPane(mainPanel);

        buildUI();
    }

    private void buildUI() {
        // ----- LEFT SIDE: BRANDING CONTENT (Transparent) -----
        JPanel leftContent = new JPanel(new MigLayout("fill, ins 0", "[center]", "[center]"));
        leftContent.setOpaque(false);

        // Use a tight vertical layout for the icon and text
        JPanel iconContainer = new JPanel(new MigLayout("ins 0, wrap, gapy 12", "[center]", "[] 20 [] 10 [] 5 []"));
        iconContainer.setOpaque(false);

        // Biometric Fingerprint Icon - 160px Fixed (Proportional & Sharp)
        JLabel bioIcon = new JLabel();
        bioIcon.setIcon(new com.formdev.flatlaf.extras.FlatSVGIcon("icons/biometric.svg", 160, 160));
        iconContainer.add(bioIcon, "w 160!, h 160!, align center, gapbottom 12");

        // Main Title - Uppercase with letter spacing
        JLabel leftTitle = new JLabel("ATTENDANCE HUB");
        leftTitle.setFont(new Font("Segoe UI", Font.BOLD, 26));
        leftTitle.setForeground(Color.WHITE);
        leftTitle.putClientProperty("FlatLaf.style", "letterSpacing: 2.5");
        iconContainer.add(leftTitle);

        // Subtitle - Balanced layout
        JLabel subtitle = new JLabel("Real-time workforce tracking");
        subtitle.setFont(new Font("Segoe UI", Font.PLAIN, 15));
        subtitle.setForeground(new Color(255, 255, 255, 180)); // 70% opacity white
        iconContainer.add(subtitle);

        leftContent.add(iconContainer, "align right, gapright 40, pad 0 0 50 0"); // Shifted slightly towards the login card
        add(leftContent);

        // ----- RIGHT SIDE: LOGIN CARD & FORM (Transparent container) -----
        JPanel rightSide = new JPanel(new MigLayout("ins 0, fill, wrap", "[center]", "[center]"));
        rightSide.setOpaque(false);

        UIHelper.RoundedPanel loginCard = new UIHelper.RoundedPanel(32);
        loginCard.setLayout(new MigLayout("ins 60, wrap, gapy 12, fillx", "[grow, fill]"));
        loginCard.setBackground(Color.WHITE);
        loginCard.setBorderColor(new Color(0xE2E8F0));

        // 1. High-Resolution BAVYA Logo (BICUBIC Rendering)
        JLabel logo = new JLabel() {
            @Override
            protected void paintComponent(Graphics g) {
                try {
                    java.net.URL logoUrl = LoginWindow.class.getResource("/logo.png");
                    if (logoUrl != null) {
                        Image img = javax.imageio.ImageIO.read(logoUrl);
                        Graphics2D g2 = (Graphics2D) g.create();
                        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                                RenderingHints.VALUE_INTERPOLATION_BICUBIC);
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
                    } else {
                        renderFallback(g);
                    }
                } catch (Exception e) {
                    renderFallback(g);
                }
            }

            private void renderFallback(Graphics g) {
                g.setFont(new Font("Segoe UI", Font.BOLD, 48));
                g.setColor(new Color(0xBE185D));
                g.drawString("BAVYA", 20, 60);
            }
        };
        loginCard.add(logo, "w 280!, h 100!, align center, gapbottom 30");

        // 2. Welcome Header
        JLabel welcome = new JLabel("Welcome Back");
        welcome.setFont(new Font("Segoe UI", Font.BOLD, 36));
        welcome.setForeground(new Color(0x0F172A));
        loginCard.add(welcome, "align center, gapbottom 40");

        // 3. Form Fields
        loginCard.add(lbl("USERNAME"));
        userField = tf("e.g. admin_bavya");
        loginCard.add(userField, "h 54!");

        loginCard.add(lbl("PASSWORD"), "gaptop 10");
        passField = new JPasswordField();
        passField.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        passField.putClientProperty("JTextField.placeholderText", "••••••••");
        
        // Custom Eye Toggle Button for Show Password
        JToggleButton revealBtn = new JToggleButton(new com.formdev.flatlaf.extras.FlatSVGIcon("icons/eye.svg", 18, 18));
        revealBtn.setBorder(BorderFactory.createEmptyBorder(0, 5, 0, 10));
        revealBtn.setContentAreaFilled(false);
        revealBtn.setFocusable(false);
        revealBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        revealBtn.addActionListener(e -> {
            char echo = revealBtn.isSelected() ? (char) 0 : '•';
            passField.setEchoChar(echo);
        });
        passField.putClientProperty("JTextField.trailingComponent", revealBtn);
        
        loginCard.add(passField, "h 54!");

        statusLabel = new JLabel(" ", SwingConstants.CENTER);
        statusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        loginCard.add(statusLabel, "gaptop 10, h 20!");

        // 4. Sign In Button
        JButton loginBtn = UIHelper.makeButton("Sign In", new Color(0xBE185D));
        loginBtn.setFont(new Font("Segoe UI", Font.BOLD, 18));
        loginBtn.addActionListener(this::onLogin);
        loginCard.add(loginBtn, "h 60!, gaptop 20");

        rightSide.add(loginCard, "wmin 400, wmax 520, hmin 600, hmax 720");
        add(rightSide, "grow");

        getRootPane().setDefaultButton(loginBtn);
    }

    private JLabel lbl(String text) {
        JLabel l = new JLabel(text);
        l.setFont(new Font("Segoe UI", Font.BOLD, 12));
        l.setForeground(new Color(0x475569));
        return l;
    }

    private JTextField tf(String placeholder) {
        JTextField f = new JTextField();
        f.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        f.putClientProperty("JTextField.placeholderText", placeholder);
        return f;
    }

    private void onLogin(ActionEvent e) {
        String username = userField.getText().trim();
        String password = new String(passField.getPassword()).trim();

        if (username.isEmpty() || password.isEmpty()) {
            statusLabel.setForeground(UIHelper.DANGER);
            statusLabel.setText("Required: Username & Password");
            return;
        }

        DatabaseManager db = DatabaseManager.getInstance();
        String hash = db.hashPw(password);

        try {
            Map<String, Object> user = db.fetchOne(
                    "SELECT * FROM users WHERE username=? AND password_hash=?",
                    username, hash);

            if (user != null) {
                if ("Inactive".equals(user.get("status"))) {
                    statusLabel.setForeground(UIHelper.DANGER);
                    statusLabel.setText("Account is disabled.");
                    return;
                }
                statusLabel.setForeground(UIHelper.SUCCESS);
                statusLabel.setText("Verified! Redirecting...");

                db.execute("UPDATE users SET last_login=NOW() WHERE id=?", user.get("id"));

                Timer t = new Timer(600, evt -> {
                    dispose();
                    new MainApp((String) user.get("username"), (String) user.get("role")).setVisible(true);
                });
                t.setRepeats(false);
                t.start();
            } else {
                statusLabel.setForeground(UIHelper.DANGER);
                statusLabel.setText("Invalid credentials provided.");
            }
        } catch (SQLException ex) {
            statusLabel.setForeground(UIHelper.DANGER);
            statusLabel.setText("System Fault: " + ex.getMessage());
        }
    }
}
