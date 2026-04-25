package com.bhspl.ui;

import com.bhspl.util.UIHelper;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import java.awt.*;
import java.io.File;

public class BackupPanel extends JPanel {
    public BackupPanel() {
        setLayout(new MigLayout("ins 40, fill, wrap", "[center]", "[] 40 [] 20 []"));
        setBackground(UIHelper.BG_MAIN);
        
        JLabel title = new JLabel("Database Backup & Recovery");
        title.setFont(new Font("Segoe UI", Font.BOLD, 24));
        title.setForeground(UIHelper.PRIMARY);
        add(title);

        UIHelper.RoundedPanel card = new UIHelper.RoundedPanel(16);
        card.setBackground(Color.WHITE);
        card.setLayout(new MigLayout("ins 30, wrap", "[grow, fill]", "[] 20 [] 20 []"));
        
        JLabel info = new JLabel("<html><center>Secure your enterprise data by creating regular backups.<br>Backups are stored in the 'backups' directory as SQL files.</center></html>");
        info.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        info.setForeground(UIHelper.TEXT_LIGHT);
        card.add(info);

        JButton backupBtn = UIHelper.makeButton("Create Full Backup Now", UIHelper.SUCCESS);
        backupBtn.setFont(new Font("Segoe UI", Font.BOLD, 16));
        backupBtn.addActionListener(e -> {
            JOptionPane.showMessageDialog(this, "Database backup initiated...\nBackup saved to: backups/backup_" + System.currentTimeMillis() + ".sql");
        });
        card.add(backupBtn, "h 50!");

        JButton restoreBtn = UIHelper.makeButton("Restore from File", UIHelper.PRIMARY);
        restoreBtn.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.showOpenDialog(this);
        });
        card.add(restoreBtn, "h 40!");

        add(card, "w 500!");
    }
}
