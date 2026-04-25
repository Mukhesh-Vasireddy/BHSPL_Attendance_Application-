package com.bhspl;

import com.bhspl.core.Config;
import com.bhspl.ui.DBSetupWindow;
import com.bhspl.ui.LoginWindow;
import com.formdev.flatlaf.FlatLightLaf;

import javax.swing.*;
import java.awt.Color;
import java.util.Map;

public class Main {
    public static void main(String[] args) {
        // Setup modern UI look and feel (Enterprise Styling)
        try {
            // Load custom "CSS" properties
            com.formdev.flatlaf.FlatLaf.registerCustomDefaultsSource("enterprise");
            
            // Set the base look and feel
            UIManager.setLookAndFeel(new com.formdev.flatlaf.FlatLightLaf());
            
            // Smooth scrolling for a premium feel
            UIManager.put("ScrollPane.smoothScrolling", true);
            
        } catch (Exception ex) {
            System.err.println("Failed to initialize Enterprise Theme: " + ex.getMessage());
        }

        SwingUtilities.invokeLater(() -> {
            new SwingWorker<Boolean, Void>() {
                @Override
                protected Boolean doInBackground() {
                    Map<String, String> config = Config.loadDbConfig();
                    if (config == null) return false;

                    try {
                        return com.bhspl.db.DatabaseManager.getInstance().connect(
                            config.get("host"), 
                            config.get("port"), 
                            config.get("user"), 
                            config.get("password"), 
                            config.get("database")
                        );
                    } catch (Exception e) {
                        return false;
                    }
                }

                @Override
                protected void done() {
                    try {
                        if (get()) {
                            com.bhspl.service.SyncService.start();
                            new LoginWindow().setVisible(true);
                        } else {
                            new DBSetupWindow().setVisible(true);
                        }
                    } catch (Exception e) {
                        new DBSetupWindow().setVisible(true);
                    }
                }
            }.execute();
        });
    }
}
