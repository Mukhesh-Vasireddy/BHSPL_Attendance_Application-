package com.bhspl;

import com.bhspl.db.DatabaseManager;
import java.util.*;

public class CheckColumns {
    public static void main(String[] args) {
        try {
            DatabaseManager db = DatabaseManager.getInstance();
            List<Map<String, Object>> policies = db.query("SELECT * FROM leave_policy LIMIT 1");
            if (!policies.isEmpty()) {
                System.out.println("Columns found in first row:");
                for (String col : policies.get(0).keySet()) {
                    System.out.println("- " + col);
                }
            } else {
                System.out.println("No policies found. Checking metadata...");
                // Note: DatabaseManager doesn't expose metadata directly, but we can try a dummy query
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
