package com.bhspl.ui;

import com.bhspl.db.ConfigManager;
import com.bhspl.util.UIHelper;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * Dialog to configure Cloud Synchronization parameters.
 */
public class CloudSettingsForm extends JDialog {
    private final Runnable callback;
    private JCheckBox enabledCheck;
    private JTextField urlField;
    private JTextField keyField;
    private JTextField retryField;
    private JTextField batchField;

    public CloudSettingsForm(JFrame parent, Runnable callback) {
        super(parent, "Cloud Sync Settings", true);
        this.callback = callback;
        setUndecorated(true);
        setSize(480, 460);
        UIHelper.centerWindow(this, 480, 460);
        buildUI();
        loadData();
        setVisible(true);
    }

    private void buildUI() {
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(UIHelper.BG_CARD);
        root.setBorder(BorderFactory.createLineBorder(UIHelper.BORDER, 1));

        // Header with gradient
        UIHelper.GradientPanel header = new UIHelper.GradientPanel(UIHelper.PRIMARY, UIHelper.SECONDARY);
        header.setPreferredSize(new Dimension(0, 60));
        header.setLayout(new BorderLayout());
        header.setBorder(new EmptyBorder(0, 20, 0, 10));

        JLabel title = new JLabel("Cloud Synchronization Setup");
        title.setFont(UIHelper.FNT_TITLE.deriveFont(16f));
        title.setForeground(Color.WHITE);
        
        try {
            com.formdev.flatlaf.extras.FlatSVGIcon hIcon = new com.formdev.flatlaf.extras.FlatSVGIcon("icons/settings.svg", 24, 24);
            hIcon.setColorFilter(new com.formdev.flatlaf.extras.FlatSVGIcon.ColorFilter(c -> Color.WHITE));
            title.setIcon(hIcon);
            title.setIconTextGap(12);
        } catch (Exception ignored) {}
        header.add(title, BorderLayout.WEST);

        JButton closeBtn = new JButton();
        try {
            com.formdev.flatlaf.extras.FlatSVGIcon cIcon = new com.formdev.flatlaf.extras.FlatSVGIcon("icons/x.svg", 16, 16);
            cIcon.setColorFilter(new com.formdev.flatlaf.extras.FlatSVGIcon.ColorFilter(c -> Color.WHITE));
            closeBtn.setIcon(cIcon);
        } catch (Exception e) {
            closeBtn.setText("X");
        }
        closeBtn.setBorderPainted(false);
        closeBtn.setContentAreaFilled(false);
        closeBtn.setFocusPainted(false);
        closeBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        closeBtn.addActionListener(e -> dispose());
        header.add(closeBtn, BorderLayout.EAST);

        root.add(header, BorderLayout.NORTH);

        // Body with MigLayout
        JPanel form = new JPanel(new MigLayout("ins 30, wrap 2, gapy 16, fillx", "[150!]15[grow]"));
        form.setBackground(UIHelper.BG_CARD);

        // Enabled checkbox
        form.add(new JLabel(""), ""); // spacer
        enabledCheck = new JCheckBox("Enable Real-time Cloud Sync");
        enabledCheck.setFont(UIHelper.FNT_BOLD);
        enabledCheck.setForeground(UIHelper.TEXT_DARK);
        enabledCheck.setOpaque(false);
        form.add(enabledCheck, "growx");

        // Endpoint URL field
        JLabel urlLbl = new JLabel("API Endpoint URL:");
        urlLbl.setFont(UIHelper.FNT_BOLD);
        urlLbl.setForeground(UIHelper.TEXT_DARK);
        form.add(urlLbl);

        urlField = new JTextField();
        urlField.setFont(UIHelper.FNT_MAIN);
        urlField.setPreferredSize(new Dimension(0, 36));
        urlField.putClientProperty("JTextField.placeholderText", "https://api.bhspl.com/sync");
        form.add(urlField, "growx");

        // API Key/Token field
        JLabel keyLbl = new JLabel("Secure API Key/Token:");
        keyLbl.setFont(UIHelper.FNT_BOLD);
        keyLbl.setForeground(UIHelper.TEXT_DARK);
        form.add(keyLbl);

        keyField = new JTextField();
        keyField.setFont(UIHelper.FNT_MAIN);
        keyField.setPreferredSize(new Dimension(0, 36));
        keyField.putClientProperty("JTextField.placeholderText", "Enter bearer token...");
        form.add(keyField, "growx");

        // Retry Interval field
        JLabel retryLbl = new JLabel("Retry Interval (sec):");
        retryLbl.setFont(UIHelper.FNT_BOLD);
        retryLbl.setForeground(UIHelper.TEXT_DARK);
        form.add(retryLbl);

        retryField = new JTextField();
        retryField.setFont(UIHelper.FNT_MAIN);
        retryField.setPreferredSize(new Dimension(0, 36));
        retryField.putClientProperty("JTextField.placeholderText", "30");
        form.add(retryField, "growx");

        // Batch Size field
        JLabel batchLbl = new JLabel("Sync Batch Size:");
        batchLbl.setFont(UIHelper.FNT_BOLD);
        batchLbl.setForeground(UIHelper.TEXT_DARK);
        form.add(batchLbl);

        batchField = new JTextField();
        batchField.setFont(UIHelper.FNT_MAIN);
        batchField.setPreferredSize(new Dimension(0, 36));
        batchField.putClientProperty("JTextField.placeholderText", "100");
        form.add(batchField, "growx");

        root.add(form, BorderLayout.CENTER);

        // Footer buttons
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 15, 20));
        footer.setBackground(UIHelper.BG_CARD);
        footer.setBorder(new EmptyBorder(0, 0, 10, 10));

        JButton cancelBtn = UIHelper.makeButton("Cancel", new Color(0x64748B));
        cancelBtn.addActionListener(e -> dispose());
        footer.add(cancelBtn);

        JButton saveBtn = UIHelper.makeButton("Save Config", UIHelper.PRIMARY);
        saveBtn.addActionListener(e -> save());
        footer.add(saveBtn);

        root.add(footer, BorderLayout.SOUTH);
        setContentPane(root);
    }

    private void loadData() {
        boolean enabled = "true".equalsIgnoreCase(ConfigManager.getProperty("cloud_sync_enabled", "false"));
        enabledCheck.setSelected(enabled);
        urlField.setText(ConfigManager.getProperty("cloud_sync_api_url", "http://103.174.161.68:9002/api/sync/cloud/sync-logs"));
        keyField.setText(ConfigManager.getProperty("cloud_sync_api_key", ""));
        retryField.setText(ConfigManager.getProperty("cloud_sync_retry_interval", "30"));
        batchField.setText(ConfigManager.getProperty("cloud_sync_batch_size", "100"));
    }

    private void save() {
        String url = urlField.getText().trim();
        String key = keyField.getText().trim();
        String retry = retryField.getText().trim();
        String batch = batchField.getText().trim();
        boolean enabled = enabledCheck.isSelected();

        if (enabled && url.isEmpty()) {
            UIHelper.showError(this, "API Endpoint URL is required when cloud sync is enabled.");
            return;
        }

        // Validate retry and batch size
        try {
            int rInt = Integer.parseInt(retry);
            if (rInt <= 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            UIHelper.showError(this, "Retry Interval must be a positive integer.");
            return;
        }

        try {
            int bInt = Integer.parseInt(batch);
            if (bInt <= 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            UIHelper.showError(this, "Sync Batch Size must be a positive integer.");
            return;
        }

        try {
            ConfigManager.setProperty("cloud_sync_enabled", String.valueOf(enabled));
            ConfigManager.setProperty("cloud_sync_api_url", url);
            ConfigManager.setProperty("cloud_sync_api_key", key);
            ConfigManager.setProperty("cloud_sync_retry_interval", retry);
            ConfigManager.setProperty("cloud_sync_batch_size", batch);

            // Re-apply settings to CloudSyncService
            if (enabled) {
                com.bhspl.service.CloudSyncService.stop();
                com.bhspl.service.CloudSyncService.start();
                com.bhspl.service.CloudSyncService.triggerSync();
            } else {
                com.bhspl.service.CloudSyncService.stop();
            }

            UIHelper.showSuccess(this, "Cloud configuration saved successfully!");
            if (callback != null) {
                callback.run();
            }
            dispose();
        } catch (Exception e) {
            UIHelper.showError(this, "Failed to save configuration: " + e.getMessage());
        }
    }
}
