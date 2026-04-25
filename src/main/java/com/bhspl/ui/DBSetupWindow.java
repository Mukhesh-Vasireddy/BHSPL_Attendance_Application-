package com.bhspl.ui;

import com.bhspl.core.Config;
import com.bhspl.util.UIHelper;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;

public class DBSetupWindow extends JFrame {

    private JTextField hostField;
    private JTextField portField;
    private JTextField dbField;
    private JTextField userField;
    private JPasswordField passField;
    private JLabel statusLabel;

    public DBSetupWindow() {
        setTitle("BHSPL Attendance - Database Setup");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setResizable(false);
        setSize(520, 640);
        UIHelper.centerWindow(this, 520, 640);
        
        getContentPane().setBackground(UIHelper.BG_MAIN);
        setLayout(new MigLayout("ins 0, fill, wrap", "[grow, center]", "[grow, center]"));

        buildUI();
    }

    private void buildUI() {
        // Main Container
        JPanel card = new JPanel(new MigLayout("ins 40, wrap, gapy 12", "[grow, fill]", "[] 8 [] 24 [] 24 [grow] 24 [] 24 []"));
        card.setBackground(Color.WHITE);
        card.setBorder(UIHelper.createCardBorder());

        // Header
        JLabel logo = new JLabel("Database Configuration");
        logo.setFont(new Font("Segoe UI", Font.BOLD, 24));
        logo.setForeground(UIHelper.PRIMARY);
        logo.setHorizontalAlignment(SwingConstants.CENTER);

        JLabel subtitle = new JLabel("Initialize your system connection");
        subtitle.setFont(UIHelper.FNT_MEDIUM);
        subtitle.setForeground(new Color(0x64748b));
        subtitle.setHorizontalAlignment(SwingConstants.CENTER);

        // Form Pane
        JPanel form = new JPanel(new MigLayout("ins 0, wrap 2, gap 16", "[right][grow, fill]"));
        form.setOpaque(false);

        String[] labels = {"Host Address", "Port Number", "Database Name", "Service User", "Service Password"};
        JTextField[] fields = new JTextField[5];
        fields[0] = new JTextField("localhost");
        fields[1] = new JTextField("3306");
        fields[2] = new JTextField("bhspl_attendance");
        fields[3] = new JTextField("root");
        fields[4] = new JPasswordField();

        hostField = fields[0]; portField = fields[1];
        dbField = fields[2]; userField = fields[3]; passField = (JPasswordField)fields[4];

        for (int i = 0; i < labels.length; i++) {
            JLabel lbl = new JLabel(labels[i]);
            lbl.setFont(UIHelper.FNT_BOLD);
            form.add(lbl);
            fields[i].setFont(UIHelper.FNT_MAIN);
            fields[i].setPreferredSize(new Dimension(0, 36));
            form.add(fields[i]);
        }

        statusLabel = new JLabel("Ready to configure connection", SwingConstants.CENTER);
        statusLabel.setFont(UIHelper.FNT_MEDIUM);
        statusLabel.setForeground(new Color(0x64748b));

        JPanel btnPanel = new JPanel(new MigLayout("ins 0, gap 12", "[grow, fill][grow, fill]"));
        btnPanel.setOpaque(false);

        JButton connectBtn = UIHelper.makeButton("Save & Connect", UIHelper.SUCCESS);
        connectBtn.setFont(new Font("Segoe UI", Font.BOLD, 14));
        connectBtn.addActionListener(this::onConnect);

        JButton exitBtn = UIHelper.makeButton("Exit Setup", UIHelper.DANGER);
        exitBtn.setFont(new Font("Segoe UI", Font.BOLD, 14));
        exitBtn.addActionListener(e -> System.exit(0));

        btnPanel.add(connectBtn, "h 42!");
        btnPanel.add(exitBtn, "h 42!");

        card.add(logo);
        card.add(subtitle);
        card.add(new JSeparator(), "gaptop 8, gapbottom 8");
        card.add(form, "grow");
        card.add(statusLabel, "gaptop 12");
        card.add(btnPanel, "gaptop 12");

        add(card, "width 420!");
    }

    private void onConnect(ActionEvent evt) {
        String host = hostField.getText().trim();
        String port = portField.getText().trim();
        String dbName = dbField.getText().trim();
        String user = userField.getText().trim();
        String pass = new String(passField.getPassword()).trim();

        statusLabel.setForeground(UIHelper.PRIMARY);
        statusLabel.setText("Establishing connection...");
        
        SwingWorker<Boolean, Void> worker = new SwingWorker<>() {
            @Override
            protected Boolean doInBackground() {
                try {
                    String setupUrl = String.format("jdbc:mysql://%s:%s/?useSSL=false&allowPublicKeyRetrieval=true", host, port);
                    java.sql.Connection tmpConn = java.sql.DriverManager.getConnection(setupUrl, user, pass);
                    java.sql.Statement stmt = tmpConn.createStatement();
                    stmt.executeUpdate("CREATE DATABASE IF NOT EXISTS `" + dbName + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
                    stmt.close();
                    tmpConn.close();
                } catch (Exception e) {
                    return false;
                }
                try {
                    return com.bhspl.db.DatabaseManager.getInstance().connect(host, port, user, pass, dbName);
                } catch (java.sql.SQLException e) {
                    return false;
                }
            }

            @Override
            protected void done() {
                try {
                    if (get()) {
                        Config.saveDbConfig(host, port, dbName, user, pass);
                        statusLabel.setForeground(UIHelper.SUCCESS);
                        statusLabel.setText("Verified! Redirecting to login...");
                        
                        Timer t = new Timer(800, e -> {
                            dispose();
                            new LoginWindow().setVisible(true);
                        });
                        t.setRepeats(false);
                        t.start();
                    } else {
                        statusLabel.setForeground(UIHelper.DANGER);
                        statusLabel.setText("Connection failed. Check settings.");
                    }
                } catch (Exception ex) {
                    statusLabel.setForeground(UIHelper.DANGER);
                    statusLabel.setText("Fault: " + ex.getMessage());
                }
            }
        };
        worker.execute();
    }
}
