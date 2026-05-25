package com.bhspl.service;

import com.bhspl.db.DatabaseManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

@Component
public class LeaveCreditScheduler {

    @Autowired
    private LeaveService leaveService;

    /**
     * Scheduled task running every day at 12:05 AM to automatically process
     * monthly/yearly leave credits based on active leave policies.
     */
    @Scheduled(cron = "0 5 0 * * ?")
    public void runAutomaticLeaveCredits() {
        try {
            DatabaseManager db = DatabaseManager.getInstance();
            System.out.println("LeaveCreditScheduler: Running automated credit check...");

            LocalDate today = LocalDate.now();
            int curY = today.getYear();
            String monthStr = String.format("%02d", today.getMonthValue());

            // Fetch active leave policies
            List<Map<String, Object>> policies = db.query("SELECT * FROM leave_policy WHERE status='Active'");
            
            // Fetch all active employees
            List<Map<String, Object>> emps = db.query("SELECT emp_id, DOJ, gender FROM employees WHERE status='Active'");

            for (Map<String, Object> emp : emps) {
                String empId = emp.get("emp_id").toString();
                String dojStr = DatabaseManager.str(emp, "DOJ");
                String gender = DatabaseManager.str(emp, "gender");
                LocalDate doj = null;
                if (!dojStr.isEmpty()) {
                    try {
                        doj = LocalDate.parse(dojStr);
                    } catch (Exception ex) {}
                }

                for (Map<String, Object> policy : policies) {
                    String leaveType = policy.get("leave_type").toString();
                    String creditMethod = policy.get("credit_method").toString();
                    double daysPerYear = DatabaseManager.dbl(policy, "days_per_year");
                    String applicableGender = DatabaseManager.str(policy, "applicable_gender");
                    int minServiceDays = DatabaseManager.num(policy, "min_service_days");

                    // 1. Gender Applicability Check
                    if (!"All".equalsIgnoreCase(applicableGender) && !applicableGender.equalsIgnoreCase(gender)) {
                        continue;
                    }

                    // 2. Minimum Service Days Check
                    if (minServiceDays > 0 && doj != null) {
                        long daysBetween = ChronoUnit.DAYS.between(doj, today);
                        if (daysBetween < minServiceDays) {
                            continue; // Employee hasn't completed minimum service requirement
                        }
                    }

                    // 3. Process Credits based on Method
                    if ("Monthly".equalsIgnoreCase(creditMethod)) {
                        String refId = "Auto-Credit-" + curY + "-" + monthStr;
                        
                        // Check if already credited for this month
                        long count = db.queryLong(
                                "SELECT COUNT(*) FROM leave_transactions WHERE emp_id=? AND leave_type=? AND year=? AND reference_id=?",
                                empId, leaveType, curY, refId);
                        
                        if (count == 0) {
                            double monthlyAmount = daysPerYear / 12.0;
                            // Round to 1 decimal place
                            monthlyAmount = Math.round(monthlyAmount * 10.0) / 10.0;

                            if (monthlyAmount > 0) {
                                leaveService.creditLeaveSingle(empId, leaveType, curY, monthlyAmount, refId, "Automatic monthly credit based on policy");
                                System.out.println("LeaveCreditScheduler: Credited " + monthlyAmount + " CL/SL to employee: " + empId + " for " + curY + "-" + monthStr);
                            }
                        }
                    } else if ("Yearly".equalsIgnoreCase(creditMethod)) {
                        String refId = "Auto-Credit-" + curY;

                        // Check if already credited for this year
                        long count = db.queryLong(
                                "SELECT COUNT(*) FROM leave_transactions WHERE emp_id=? AND leave_type=? AND year=? AND reference_id=?",
                                empId, leaveType, curY, refId);
                        
                        if (count == 0) {
                            if (daysPerYear > 0) {
                                leaveService.creditLeaveSingle(empId, leaveType, curY, daysPerYear, refId, "Automatic yearly credit based on policy");
                                System.out.println("LeaveCreditScheduler: Credited " + daysPerYear + " EL to employee: " + empId + " for year " + curY);
                            }
                        }
                    }
                }
            }
            System.out.println("LeaveCreditScheduler: Completed automated credit check.");
        } catch (SQLException e) {
            System.err.println("LeaveCreditScheduler SQL Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
