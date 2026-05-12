package com.bhspl.ui;

import com.bhspl.util.UIHelper;
import com.formdev.flatlaf.extras.FlatSVGIcon;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import java.awt.*;

/**
 * Modern Corporate About Page for BHSPL.
 * Clean, light-themed, and aligned with the enterprise dashboard aesthetic.
 */
public class AboutPanel extends JPanel {

    public AboutPanel() {
        setLayout(new MigLayout("ins 0, fill", "[grow]", "[grow]"));
        setBackground(UIHelper.BG_MAIN);
        buildUI();
    }

    private void buildUI() {
        // Main Container
        JPanel container = new JPanel(new MigLayout("ins 40, fill", "[grow]", "[grow]"));
        container.setOpaque(false);

        // Corporate Info Card
        JPanel card = new JPanel(new MigLayout("ins 0, wrap, fillx", "[grow, fill]", "[] [grow] []"));
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createLineBorder(UIHelper.BORDER, 1, true));

        // 1. Header Bar (Brand Purple)
        JPanel header = new JPanel(new MigLayout("ins 20 30, gap 20", "[] [grow]", "[]"));
        header.setBackground(UIHelper.PRIMARY);

        try {
            FlatSVGIcon logoIcon = new FlatSVGIcon("icons/biometric.svg", 48, 48);
            logoIcon.setColorFilter(new FlatSVGIcon.ColorFilter(c -> Color.WHITE));
            header.add(new JLabel(logoIcon));
        } catch (Exception ignored) {
        }

        JPanel titleCont = new JPanel(new MigLayout("ins 0, wrap", "[grow]"));
        titleCont.setOpaque(false);

        JLabel title = new JLabel("BHSPL Attendance Management System");
        title.setFont(new Font("Segoe UI", Font.BOLD, 22));
        title.setForeground(Color.WHITE);
        titleCont.add(title);

        JLabel version = new JLabel("Enterprise Edition | Version 2.0.42");
        version.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        version.setForeground(new Color(0xE0E7FF));
        titleCont.add(version);

        header.add(titleCont);
        card.add(header, "growx");

        // 2. Content Area
        JPanel content = new JPanel(new MigLayout("ins 30 40 30 40, wrap, gapy 25", "[grow, fill]"));
        content.setOpaque(false);

        // Info Sections
        content.add(createInfoSection("Company Information", new String[][] {
                { "Proprietor", "Bavya Health Services Pvt Ltd", "building.svg" },
                { "Headquarters", "Vijayawada, Andhra Pradesh, India", "map_marker.svg" }
        }));

        content.add(createInfoSection("Support & Contact", new String[][] {
                { "Email Support", "mukhesh.vasireddy@bhspl.in", "email.svg" },
                { "Web Portal", "www.bhspl.in", "web.svg" },
                { "Developer", "Mukhesh Vasireddy", "user.svg" }
        }));

        card.add(content, "grow");

        // 3. Footer
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 15));
        footer.setBackground(new Color(0xF8FAFC));
        footer.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, UIHelper.BORDER));

        JLabel copy = new JLabel("© 2026 BHSPL. All Rights Reserved. Licensed for Enterprise Use.");
        copy.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        copy.setForeground(UIHelper.TEXT_MUTE);
        footer.add(copy);

        card.add(footer, "growx");

        container.add(card, "center, w 700!");
        add(container, "grow");
    }

    private JPanel createInfoSection(String sectionTitle, String[][] rows) {
        JPanel section = new JPanel(new MigLayout("ins 0, wrap", "[grow, fill]"));
        section.setOpaque(false);

        JLabel sTitle = new JLabel(sectionTitle.toUpperCase());
        sTitle.setFont(new Font("Segoe UI", Font.BOLD, 12));
        sTitle.setForeground(UIHelper.PRIMARY);
        sTitle.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, UIHelper.BORDER));
        section.add(sTitle, "gapbottom 10");

        for (String[] row : rows) {
            JPanel rowPanel = new JPanel(new MigLayout("ins 5 0, gap 15", "[] []", "[]"));
            rowPanel.setOpaque(false);

            try {
                FlatSVGIcon icon = new FlatSVGIcon("icons/" + row[2], 18, 18);
                icon.setColorFilter(new FlatSVGIcon.ColorFilter(c -> UIHelper.TEXT_LIGHT));
                rowPanel.add(new JLabel(icon));
            } catch (Exception ignored) {
            }

            JLabel label = new JLabel(row[0] + ":");
            label.setFont(new Font("Segoe UI Semibold", Font.PLAIN, 14));
            label.setForeground(UIHelper.TEXT_LIGHT);
            label.setPreferredSize(new Dimension(120, 20));
            rowPanel.add(label);

            JLabel value = new JLabel(row[1]);
            value.setFont(new Font("Segoe UI", Font.BOLD, 14));
            value.setForeground(UIHelper.TEXT_DARK);
            rowPanel.add(value);

            section.add(rowPanel);
        }

        return section;
    }
}
