package com.bhspl;

import com.bhspl.core.Config;

import javax.swing.*;
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
                            com.bhspl.service.PushService.start();
                            
                            // Start Java Web Portal (Spring Boot) on port 8080
                            com.bhspl.web.WebApplication.start(args);

                            // new LoginWindow().setVisible(true);
                        } else {
                            // new DBSetupWindow().setVisible(true);
                        }
                    } catch (Exception e) {
                        // new DBSetupWindow().setVisible(true);
                    }
                }
            }.execute();
        });

        // Keep the main thread alive so the Web Portal and Background Services don't close
        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            System.err.println("Main thread interrupted");
        }
    }
}
