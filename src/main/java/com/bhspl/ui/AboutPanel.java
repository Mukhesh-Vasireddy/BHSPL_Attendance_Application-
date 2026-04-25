package com.bhspl.ui;

import com.bhspl.util.UIHelper;
import com.formdev.flatlaf.extras.FlatSVGIcon;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import java.awt.*;

public class AboutPanel extends JPanel {
    public AboutPanel() {
        setLayout(new MigLayout("ins 0, fill, wrap", "[grow]", "[35%!] [grow]"));
        setBackground(new Color(0x0F172A)); // Dark Blue Header Background

        // Header Section
        JPanel header = new JPanel(new MigLayout("ins 0, fill, wrap", "[center]", "push [] 20 [] 10 [] push"));
        header.setOpaque(false);

        FlatSVGIcon buildingIcon = new FlatSVGIcon("icons/building.svg", 80, 80);
        buildingIcon.setColorFilter(new FlatSVGIcon.ColorFilter(c -> Color.WHITE));
        header.add(new JLabel(buildingIcon));

        JLabel title = new JLabel("BHSPL ATTENDANCE APP");
        title.setFont(new Font("Segoe UI", Font.BOLD, 32));
        title.setForeground(Color.WHITE);
        header.add(title);

        JLabel subtitle = new JLabel("Biometric Attendance Management System");
        subtitle.setFont(new Font("Segoe UI", Font.PLAIN, 18));
        subtitle.setForeground(new Color(0x94A3B8));
        header.add(subtitle);

        add(header, "grow");

        // Content Section
        JPanel contentArea = new JPanel(new MigLayout("ins 40, center, wrap", "[center]", "[]"));
        contentArea.setBackground(Color.WHITE);

        UIHelper.RoundedPanel card = new UIHelper.RoundedPanel(16);
        card.setBackground(Color.WHITE);
        card.setBorderColor(new Color(0xE2E8F0));
        card.setLayout(new MigLayout("ins 40, wrap 2, gap 25 20", "[shrink] 30 [grow, left]", "[]"));

        addInfoRow(card, "Company", "Bavya Health Services Pvt Ltd", "icons/home.svg");
        addInfoRow(card, "Location", "Vijayawada", "icons/map_marker.svg");
        addInfoRow(card, "Website", "www.bhspl.in", "icons/web.svg");

        card.add(new JSeparator(), "span 2, growx, gaptop 10, gapbottom 10");

        addInfoRow(card, "Developed by", "Mukhesh Vasireddy", "icons/user.svg");
        addInfoRow(card, "Email", "mukhesh.vasireddy@bhspl.in", "icons/email.svg");

        card.add(new JSeparator(), "span 2, growx, gaptop 10, gapbottom 10");

        addInfoRow(card, "Copyright", "Bavya Health Services Pvt Ltd", "icons/copyright.svg");
        addInfoRow(card, "Version", "2.0 | 2026", "icons/star.svg");

        contentArea.add(card, "w 650!");
        add(contentArea, "grow");
    }

    private void addInfoRow(JPanel parent, String label, String value, String iconPath) {
        JPanel labelSide = new JPanel(new MigLayout("ins 0, gap 12", "[] []", "[]"));
        labelSide.setOpaque(false);

        FlatSVGIcon icon = new FlatSVGIcon(iconPath, 18, 18);
        icon.setColorFilter(new FlatSVGIcon.ColorFilter(c -> new Color(0x1E293B)));
        labelSide.add(new JLabel(icon));

        JLabel lbl = new JLabel(label);
        lbl.setFont(new Font("Segoe UI", Font.BOLD, 15));
        lbl.setForeground(new Color(0x334155));
        labelSide.add(lbl);

        parent.add(labelSide);

        JLabel val = new JLabel(value);
        val.setFont(new Font("Segoe UI", Font.PLAIN, 15));
        val.setForeground(new Color(0x475569));
        parent.add(val);
    }
}
