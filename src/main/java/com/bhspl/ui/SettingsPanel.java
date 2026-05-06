package com.bhspl.ui;
 
 import com.bhspl.util.UIHelper;
 import net.miginfocom.swing.MigLayout;
 
 import javax.swing.*;
 import java.awt.*;
 
 /**
  * System Settings Panel.
  * Focuses strictly on Application Infrastructure and Environment details.
  * (User Management and Database Config have been moved to their respective modules).
  */
 public class SettingsPanel extends JPanel {
 
     public SettingsPanel() {
         setBackground(UIHelper.BG_MAIN);
         setLayout(new MigLayout("ins 24, wrap, gap 0, fill", "[grow]", "[] 24 [grow]"));
         buildUI();
     }
 
     private void buildUI() {
         JLabel title = new JLabel("System Information");
         title.setFont(UIHelper.FNT_TITLE);
         title.setForeground(UIHelper.PRIMARY);
         add(title, "growx");
 
         // Infrastructure Card
         JPanel card = new JPanel(new MigLayout("ins 40, wrap 2, gap 20 16", "[right] 25 [left]", "[] 20 []"));
         card.setBackground(Color.WHITE);
         card.setBorder(UIHelper.createCardBorder());
 
         JLabel header = new JLabel("Application Infrastructure Profile");
         header.setFont(UIHelper.FNT_BOLD);
         header.setForeground(UIHelper.PRIMARY);
         card.add(header, "span 2, center, gapbottom 15");
 
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
             lbl.setFont(new Font("Segoe UI", Font.BOLD, 14));
             lbl.setForeground(UIHelper.TEXT_LIGHT);
             
             JLabel val = new JLabel(row[1]);
             val.setFont(new Font("Segoe UI Semibold", Font.PLAIN, 15));
             val.setForeground(UIHelper.TEXT_DARK);
             
             if (row[1].contains("Running")) val.setForeground(UIHelper.SUCCESS);
 
             card.add(lbl);
             card.add(val);
         }
 
         add(card, "center, width 650!");
     }
 }
