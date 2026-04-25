package com.bhspl.ui;

import com.bhspl.db.DatabaseManager;
import com.bhspl.util.UIHelper;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import java.awt.*;

public class ProfilePanel extends JPanel {

    private final String username;
    private final String role;

    public ProfilePanel(String username, String role) {
        this.username = username;
        this.role = role;
        
        setBackground(UIHelper.BG_MAIN);
        setLayout(new MigLayout("ins 40, align center", "[400!]", "[]"));
        buildUI();
    }

    private void buildUI() {
        JPanel scrollContent = new JPanel(new MigLayout("ins 0, wrap, align center, gapy 24", "[400!]", "[] []"));
        scrollContent.setOpaque(false);

        // Profile Card
        JPanel card = new JPanel(new MigLayout("ins 30, wrap, align center", "[grow, center]", "[] 20 [] 5 [] 30 []"));
        card.setBackground(Color.WHITE);
        card.setBorder(UIHelper.createCardBorder());

        JLabel avatar = new JLabel("");
        avatar.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 72));
        avatar.setForeground(UIHelper.PRIMARY);
        card.add(avatar);

        JLabel nameLbl = new JLabel(username);
        nameLbl.setFont(new Font("Segoe UI", Font.BOLD, 24));
        nameLbl.setForeground(UIHelper.TEXT_DARK);
        card.add(nameLbl);

        JLabel roleLbl = new JLabel(role.toUpperCase() + " ACCOUNT");
        roleLbl.setFont(new Font("Segoe UI", Font.BOLD, 12));
        roleLbl.setForeground(UIHelper.TEXT_LIGHT);
        card.add(roleLbl);

        JButton logoutBtn = UIHelper.makeButton("Log Out Securely", UIHelper.DANGER);
        logoutBtn.addActionListener(e -> logout());
        card.add(logoutBtn, "growx, h 40!");
        
        scrollContent.add(card, "growx");

        // System Info Card
        scrollContent.add(systemInfoSection(), "growx");

        add(scrollContent);
    }

    private JPanel systemInfoSection() {
        JPanel panel = new JPanel(new MigLayout("ins 24, wrap, gap 15", "[grow]", "[] 10 []"));
        panel.setBackground(Color.WHITE);
        panel.setBorder(UIHelper.createCardBorder());

        JLabel title = new JLabel("System Information");
        title.setFont(new Font("Segoe UI", Font.BOLD, 16));
        title.setForeground(UIHelper.PRIMARY);
        panel.add(title);

        JPanel info = new JPanel(new MigLayout("ins 0, wrap, gapy 12", "[grow]", "[]"));
        info.setOpaque(false);

        info.add(infoRow("System Date", java.time.LocalDate.now().toString()), "growx");
        info.add(infoRow("Operating System", System.getProperty("os.name")), "growx");
        info.add(infoRow("Java Version", System.getProperty("java.version")), "growx");

        panel.add(info, "growx");
        return panel;
    }

    private JPanel infoRow(String key, String value) {
        JPanel row = new JPanel(new MigLayout("ins 0, gap 20", "[130!] [grow]"));
        row.setOpaque(false);
        JLabel k = new JLabel(key);
        k.setFont(new Font("Segoe UI", Font.BOLD, 12));
        k.setForeground(UIHelper.TEXT_LIGHT);
        JLabel v = new JLabel(value);
        v.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        v.setForeground(UIHelper.TEXT_DARK);
        row.add(k);
        row.add(v, "left");
        return row;
    }
    
    private void logout() {
        int confirm = JOptionPane.showConfirmDialog(this, "Are you sure you want to logout?", "Logout", JOptionPane.YES_NO_OPTION);
        if (confirm == JOptionPane.YES_OPTION) {
            DatabaseManager.getInstance().close();
            JFrame parent = (JFrame) SwingUtilities.getWindowAncestor(this);
            if (parent != null) {
                parent.dispose();
            }
            new LoginWindow().setVisible(true);
        }
    }
}
