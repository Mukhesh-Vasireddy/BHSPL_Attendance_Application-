package com.bhspl.ui;

import com.bhspl.db.DatabaseManager;
import com.bhspl.util.UIHelper;
import javax.swing.border.EmptyBorder;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.sql.SQLException;
import java.util.Map;

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
        setLayout(new MigLayout("ins 0, gap 0, fill", "[500!]0[grow]", "[grow]"));

        buildUI();
    }

    private void buildUI() {
        // ----- LEFT SIDE: BRANDING -----
        UIHelper.GradientPanel branding = new UIHelper.GradientPanel(new Color(0x8E0E6C), new Color(0xD8005A));
        branding.setLayout(new MigLayout("ins 60, fill, wrap", "[center]", "[] 60 [] push []"));

        // Logo Container with White Background for visibility
        JPanel logoContainer = new JPanel(new MigLayout("ins 20", "[center]", "[center]"));
        logoContainer.setBackground(Color.WHITE);
        logoContainer.setBorder(UIHelper.createCardBorder());

        JLabel logo = new JLabel();
        try {
            java.net.URL logoUrl = getClass().getResource("/logo.png");
            if (logoUrl != null) {
                ImageIcon icon = new ImageIcon(logoUrl);
                Image img = icon.getImage();
                if (img != null) {
                    int targetWidth = 300;
                    int targetHeight = (targetWidth * img.getHeight(null)) / img.getWidth(null);
                    Image scaled = img.getScaledInstance(targetWidth, targetHeight, Image.SCALE_SMOOTH);
                    logo.setIcon(new ImageIcon(scaled));
                }
            } else {
                logo.setText("BAVYA");
            }
        } catch (Exception e) {
            logo.setText("BAVYA");
        }
        if (logo.getText() != null && !logo.getText().isEmpty()) {
            logo.setFont(new Font("Segoe UI", Font.BOLD, 72));
            logo.setForeground(UIHelper.PRIMARY);
        }
        logoContainer.add(logo);
        branding.add(logoContainer, "w 380!");

        JLabel title = new JLabel("<html><center>Enterprise Attendance<br>Management Portal</center></html>");
        title.setFont(new Font("Segoe UI", Font.BOLD, 32));
        title.setForeground(Color.WHITE);
        title.setHorizontalAlignment(SwingConstants.CENTER);
        branding.add(title);

        JLabel techInfo = new JLabel("<html><center>Secured biometric synchronization<br>Real-time enterprise reporting</center></html>");
        techInfo.setFont(new Font("Segoe UI", Font.PLAIN, 18));
        techInfo.setForeground(new Color(0xC7D2FE));
        techInfo.setHorizontalAlignment(SwingConstants.CENTER);
        branding.add(techInfo, "gapbottom 40");

        add(branding, "grow");

        // ----- RIGHT SIDE: LOGIN FORM -----
        JPanel rightSide = new JPanel(new MigLayout("ins 0, fill, wrap", "[center]", "[center]"));
        rightSide.setBackground(Color.WHITE);

        UIHelper.RoundedPanel formCard = new UIHelper.RoundedPanel(24);
        formCard.setLayout(new MigLayout("ins 50, wrap, gapy 15", "[grow, fill]", "[] 5 [] 50 [] 5 [] 25 [] 5 [] 50 []"));
        formCard.setBackground(Color.WHITE);
        formCard.setPreferredSize(new Dimension(500, 650));
        formCard.setBorderColor(new Color(0xF1F5F9));
        
        JLabel welcome = new JLabel("Welcome Back");
        welcome.setFont(new Font("Segoe UI", Font.BOLD, 36));
        welcome.setForeground(UIHelper.TEXT_DARK);
        
        JLabel subwelcome = new JLabel("Sign in to your account to continue");
        subwelcome.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        subwelcome.setForeground(UIHelper.TEXT_LIGHT);

        JLabel userLabel = new JLabel("USERNAME");
        userLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        userLabel.setForeground(UIHelper.TEXT_LIGHT);
        
        userField = new JTextField();
        userField.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        userField.putClientProperty("JTextField.placeholderText", "e.g. admin_bavya");

        JLabel passLabel = new JLabel("PASSWORD");
        passLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        passLabel.setForeground(UIHelper.TEXT_LIGHT);
        
        passField = new JPasswordField();
        passField.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        passField.putClientProperty("JTextField.placeholderText", "••••••••");
        passField.putClientProperty("JTextField.showRevealButton", true);

        statusLabel = new JLabel("Secure Authentication Required", SwingConstants.CENTER);
        statusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        statusLabel.setForeground(UIHelper.TEXT_LIGHT);

        JButton loginBtn = UIHelper.makeButton("Sign In", UIHelper.PRIMARY);
        loginBtn.setFont(new Font("Segoe UI", Font.BOLD, 18));
        loginBtn.addActionListener(this::onLogin);

        formCard.add(welcome);
        formCard.add(subwelcome);
        formCard.add(userLabel, "gaptop 20");
        formCard.add(userField, "h 54!");
        formCard.add(passLabel, "gaptop 15");
        formCard.add(passField, "h 54!");
        formCard.add(statusLabel, "gaptop 15");
        formCard.add(loginBtn, "h 60!, gaptop 30");

        rightSide.add(formCard);
        add(rightSide, "grow");

        getRootPane().setDefaultButton(loginBtn);
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
                username, hash
            );

            if (user != null) {
                if ("Inactive".equals(user.get("status"))) {
                    statusLabel.setForeground(UIHelper.DANGER);
                    statusLabel.setText("Account is currently disabled.");
                    return;
                }
                statusLabel.setForeground(UIHelper.SUCCESS);
                statusLabel.setText("Verified! Redirecting...");

                db.execute("UPDATE users SET last_login=NOW() WHERE id=?", user.get("id"));

                Timer t = new Timer(600, evt -> {
                    try {
                        dispose();
                        new MainApp((String) user.get("username"), (String) user.get("role")).setVisible(true);
                    } catch (Throwable ex) {
                        ex.printStackTrace();
                        try (java.io.FileWriter fw = new java.io.FileWriter("app_crash.log", true)) {
                            fw.write("\n--- Crash at " + new java.util.Date() + " ---\n");
                            ex.printStackTrace(new java.io.PrintWriter(fw));
                        } catch (Exception ignored) {}
                        
                        JOptionPane.showMessageDialog(null, 
                            "CRITICAL ERROR (Logged in app_crash.log):\n" + ex.toString(),
                            "Launch Error", JOptionPane.ERROR_MESSAGE);
                        System.exit(1);
                    }
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
