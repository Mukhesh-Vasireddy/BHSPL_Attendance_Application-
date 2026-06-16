package com.bhspl.ui;

import com.bhspl.db.ConfigManager;
import com.bhspl.util.UIHelper;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import java.awt.*;

/**
 * System Settings Panel.
 * Focuses strictly on Application Infrastructure and Environment details,
 * and handles Cloud Synchronization setup.
 */
public class SettingsPanel extends JPanel {

    public SettingsPanel() {
        setBackground(UIHelper.BG_MAIN);
        setLayout(new MigLayout("ins 24, wrap, gap 0, fill", "[grow]", "[] 24 [grow]"));
        buildUI();
    }

    private void buildUI() {
        removeAll();

        JLabel title = new JLabel("System Information & Settings");
        title.setFont(UIHelper.FNT_TITLE);
        title.setForeground(UIHelper.PRIMARY);
        add(title, "growx, gapbottom 15");

        // Container panel to hold both cards stacked
        JPanel container = new JPanel(new MigLayout("ins 0, wrap, gapy 20, fillx", "[grow]"));
        container.setOpaque(false);

        // 1. Infrastructure Card
        JPanel card = new JPanel(new MigLayout("ins 30, wrap 2, gap 20 12", "[right] 25 [left]", "[] 15 []"));
        card.setBackground(Color.WHITE);
        card.setBorder(UIHelper.createCardBorder());

        JLabel header = new JLabel("Application Infrastructure Profile");
        header.setFont(UIHelper.FNT_BOLD);
        header.setForeground(UIHelper.PRIMARY);
        card.add(header, "span 2, center, gapbottom 10");

        String[][] info = {
            {"Application Name", "BHSPL Attendance Management System"},
            {"Software Version", "2.0.42 (Enterprise)"},
            {"Runtime Env", "Java 17 (OpenJDK)"},
            {"Core Framework", "Spring Boot 3.x / Swing UI"},
            {"Persistence", "MySQL 8.0 (Connector/J)"},
            {"SDK Interface", "ZKTeco Native Library (Java)"},
            {"ADMS Protocol", com.bhspl.service.PushService.isRunning() ? "Active (Listening)" : "Inactive"},
            {"ADMS Port", String.valueOf(com.bhspl.service.PushService.getPort())},
            {"Module Status", "Running / Connected"}
        };

        for (String[] row : info) {
            JLabel lbl = new JLabel(row[0] + ":");
            lbl.setFont(new Font("Segoe UI", Font.BOLD, 13));
            lbl.setForeground(UIHelper.TEXT_LIGHT);
            
            JLabel val = new JLabel(row[1]);
            val.setFont(new Font("Segoe UI Semibold", Font.PLAIN, 14));
            val.setForeground(UIHelper.TEXT_DARK);
            
            if (row[1].contains("Running") || row[1].contains("Active")) {
                val.setForeground(UIHelper.SUCCESS);
            }

            card.add(lbl);
            card.add(val);
        }
        container.add(card, "center, width 650!");

        // 2. Cloud Sync Card
        JPanel cloudCard = new JPanel(new MigLayout("ins 30, wrap 2, gap 20 12", "[right] 25 [left]", "[] 15 []"));
        cloudCard.setBackground(Color.WHITE);
        cloudCard.setBorder(UIHelper.createCardBorder());

        JLabel cloudHeader = new JLabel("Cloud Synchronization Profile");
        cloudHeader.setFont(UIHelper.FNT_BOLD);
        cloudHeader.setForeground(UIHelper.PRIMARY);
        cloudCard.add(cloudHeader, "span 2, center, gapbottom 10");

        boolean cloudEnabled = "true".equalsIgnoreCase(ConfigManager.getProperty("cloud_sync_enabled", "false"));
        String cloudUrl = ConfigManager.getProperty("cloud_sync_api_url", "http://103.174.161.68:9002/api/sync/cloud/sync-logs");
        String cloudApiKey = ConfigManager.getProperty("cloud_sync_api_key", "");

        String statusStr = cloudEnabled ? "Enabled (Real-time Active)" : "Disabled";
        String urlStr = cloudUrl.isEmpty() ? "Not Configured" : cloudUrl;
        String authStr = cloudApiKey.isEmpty() ? "Not Configured" : "Configured (Bearer Token)";

        String retryStr = ConfigManager.getProperty("cloud_sync_retry_interval", "30") + " seconds";
        String batchStr = ConfigManager.getProperty("cloud_sync_batch_size", "100") + " logs";

        String[][] cloudInfo = {
            {"Sync Status", statusStr},
            {"Cloud Endpoint", urlStr},
            {"API Authorization", authStr},
            {"Retry Interval", retryStr},
            {"Batch Size", batchStr}
        };

        for (String[] row : cloudInfo) {
            JLabel lbl = new JLabel(row[0] + ":");
            lbl.setFont(new Font("Segoe UI", Font.BOLD, 13));
            lbl.setForeground(UIHelper.TEXT_LIGHT);
            
            JLabel val = new JLabel(row[1]);
            val.setFont(new Font("Segoe UI Semibold", Font.PLAIN, 14));
            val.setForeground(UIHelper.TEXT_DARK);
            
            if (row[0].equals("Sync Status")) {
                if (cloudEnabled) {
                    val.setForeground(UIHelper.SUCCESS);
                } else {
                    val.setForeground(UIHelper.TEXT_LIGHT);
                }
            }

            cloudCard.add(lbl);
            cloudCard.add(val);
        }

        JButton configBtn = UIHelper.makeButton("Configure Cloud Sync", UIHelper.PRIMARY);
        configBtn.addActionListener(e -> {
            JFrame frame = (JFrame) SwingUtilities.getWindowAncestor(SettingsPanel.this);
            new CloudSettingsForm(frame, this::buildUI);
        });
        cloudCard.add(configBtn, "span 2, center, gaptop 10");

        container.add(cloudCard, "center, width 650!");

        add(container, "center, grow");

        revalidate();
        repaint();
    }
}
