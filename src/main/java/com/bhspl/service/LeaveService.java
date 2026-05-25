package com.bhspl.service;

import com.bhspl.db.DatabaseManager;
import org.springframework.stereotype.Service;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

@Service
public class LeaveService {

    /**
     * Deducts leave balance for a approved leave request.
     */
    public synchronized void deductBalance(String empId, String leaveType, int year, double amount, String referenceId, String remarks) {
        try {
            DatabaseManager db = DatabaseManager.getInstance();
            
            // Check if already deducted to prevent duplicate deductions
            long exists = db.queryLong("SELECT COUNT(*) FROM leave_transactions WHERE emp_id=? AND leave_type=? AND year=? AND transaction_type='Deduction' AND reference_id=?", 
                    empId, leaveType, year, referenceId);
            if (exists > 0) {
                System.out.println("LeaveService: Leave request " + referenceId + " is already deducted.");
                return;
            }

            // Update or insert leave_balance
            int rows = db.execute(
                    "UPDATE leave_balance SET used = used + ?, closing_bal = closing_bal - ? WHERE emp_id=? AND leave_type=? AND year=?",
                    amount, amount, empId, leaveType, year);
            if (rows == 0) {
                db.execute(
                        "INSERT INTO leave_balance (emp_id, leave_type, year, opening_bal, credited, carry_fwd, used, lapsed, closing_bal) VALUES (?, ?, ?, 0, 0, 0, ?, 0, ?)",
                        empId, leaveType, year, amount, -amount);
            }

            // Record transaction log
            db.execute(
                    "INSERT INTO leave_transactions (emp_id, leave_type, year, transaction_type, amount, reference_id, remarks) VALUES (?, ?, ?, 'Deduction', ?, ?, ?)",
                    empId, leaveType, year, "Deduction", -amount, referenceId, remarks);
            
            System.out.println("LeaveService: Successfully deducted " + amount + " days for employee " + empId + " (Leave ID: " + referenceId + ")");
        } catch (SQLException e) {
            System.err.println("LeaveService Error in deductBalance: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Reverses a previous leave deduction (e.g. if leave is rejected or cancelled).
     */
    public synchronized void reverseDeduction(String empId, String leaveType, int year, double amount, String referenceId) {
        try {
            DatabaseManager db = DatabaseManager.getInstance();
            
            // Check if deduction exists to reverse
            long exists = db.queryLong("SELECT COUNT(*) FROM leave_transactions WHERE emp_id=? AND leave_type=? AND year=? AND transaction_type='Deduction' AND reference_id=?", 
                    empId, leaveType, year, referenceId);
            if (exists == 0) {
                System.out.println("LeaveService: No deduction found for request " + referenceId + " to reverse.");
                return;
            }

            // Reverse update leave_balance
            db.execute(
                    "UPDATE leave_balance SET used = used - ?, closing_bal = closing_bal + ? WHERE emp_id=? AND leave_type=? AND year=?",
                    amount, amount, empId, leaveType, year);

            // Record reversal transaction log (or delete deduction)
            db.execute(
                    "DELETE FROM leave_transactions WHERE emp_id=? AND leave_type=? AND year=? AND transaction_type='Deduction' AND reference_id=?",
                    empId, leaveType, year, referenceId);
            
            // Clean up: If all fields in leave_balance are 0, delete the row entirely to prevent showing empty/zero balance rows
            db.execute(
                    "DELETE FROM leave_balance WHERE emp_id=? AND leave_type=? AND year=? AND opening_bal=0 AND credited=0 AND carry_fwd=0 AND used=0 AND lapsed=0 AND closing_bal=0",
                    empId, leaveType, year);
            
            System.out.println("LeaveService: Successfully reversed leave deduction and cleaned up empty balance rows for employee " + empId + " (Leave ID: " + referenceId + ")");
        } catch (SQLException e) {
            System.err.println("LeaveService Error in reverseDeduction: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Credits leaves in bulk by department or individual employee.
     */
    public synchronized void creditLeavesBulk(String dept, String empId, String type, double amount, String remarks) {
        try {
            DatabaseManager db = DatabaseManager.getInstance();
            int curY = java.time.LocalDate.now().getYear();
            
            // Build target employee query
            String query = "SELECT emp_id FROM employees WHERE status='Active'";
            Object[] params;
            if (empId != null && !"All".equals(empId)) {
                query += " AND emp_id = ?";
                params = new Object[]{empId};
            } else if (dept != null && !"All".equals(dept)) {
                query += " AND department = ?";
                params = new Object[]{dept};
            } else {
                params = new Object[]{};
            }

            List<Map<String, Object>> emps = db.query(query, params);
            System.out.println("LeaveService: Bulk crediting " + amount + " days of " + type + " to " + emps.size() + " employees.");

            for (Map<String, Object> e : emps) {
                String targetId = e.get("emp_id").toString();
                
                // Update closing_bal
                int rows = db.execute(
                        "UPDATE leave_balance SET credited = credited + ?, closing_bal = closing_bal + ? WHERE emp_id=? AND leave_type=? AND year=?",
                        amount, amount, targetId, type, curY);
                if (rows == 0) {
                    db.execute(
                            "INSERT INTO leave_balance (emp_id, leave_type, year, opening_bal, credited, carry_fwd, used, lapsed, closing_bal) VALUES (?, ?, ?, 0, ?, 0, 0, 0, ?)",
                            targetId, type, curY, amount, amount);
                }

                // Record transaction log
                db.execute(
                        "INSERT INTO leave_transactions (emp_id, leave_type, year, transaction_type, amount, reference_id, remarks) VALUES (?, ?, ?, 'Credit', ?, 'Bulk Credit', ?)",
                        targetId, type, curY, "Credit", amount, remarks);
            }
        } catch (SQLException e) {
            System.err.println("LeaveService Error in creditLeavesBulk: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Credits a single leave balance and registers a transaction.
     */
    public synchronized void creditLeaveSingle(String empId, String leaveType, int year, double amount, String referenceId, String remarks) {
        try {
            DatabaseManager db = DatabaseManager.getInstance();
            int rows = db.execute(
                    "UPDATE leave_balance SET credited = credited + ?, closing_bal = closing_bal + ? WHERE emp_id=? AND leave_type=? AND year=?",
                    amount, amount, empId, leaveType, year);
            if (rows == 0) {
                db.execute(
                        "INSERT INTO leave_balance (emp_id, leave_type, year, opening_bal, credited, carry_fwd, used, lapsed, closing_bal) VALUES (?, ?, ?, 0, ?, 0, 0, 0, ?)",
                        empId, leaveType, year, amount, amount);
            }
            db.execute(
                    "INSERT INTO leave_transactions (emp_id, leave_type, year, transaction_type, amount, reference_id, remarks) VALUES (?, ?, ?, 'Credit', ?, ?, ?)",
                    empId, leaveType, year, "Credit", amount, referenceId, remarks);
        } catch (SQLException e) {
            System.err.println("LeaveService Error in creditLeaveSingle: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Executes the Year-End process by carrying forward active balances and lapsing excess.
     */
    public synchronized void processYearEnd(int sourceYear, int targetYear) {
        try {
            DatabaseManager db = DatabaseManager.getInstance();
            System.out.println("LeaveService: Starting Year-End Process from " + sourceYear + " to " + targetYear);

            // Fetch active leave policies
            List<Map<String, Object>> policies = db.query("SELECT * FROM leave_policy WHERE status='Active'");
            
            // Fetch all active employees
            List<Map<String, Object>> emps = db.query("SELECT emp_id FROM employees WHERE status='Active'");

            for (Map<String, Object> emp : emps) {
                String empId = emp.get("emp_id").toString();

                for (Map<String, Object> policy : policies) {
                    String leaveType = policy.get("leave_type").toString();
                    boolean carryFwdRule = DatabaseManager.num(policy, "carry_forward") == 1;
                    double maxCarryRule = DatabaseManager.dbl(policy, "max_carry");

                    // Fetch closing balance of source year
                    Map<String, Object> balRow = db.queryOne(
                            "SELECT closing_bal FROM leave_balance WHERE emp_id=? AND leave_type=? AND year=?",
                            empId, leaveType, sourceYear);
                    
                    double closingBal = (balRow != null) ? DatabaseManager.dbl(balRow, "closing_bal") : 0.0;
                    if (closingBal <= 0) continue; // Nothing to process

                    double carryAmount = 0.0;
                    double lapseAmount = 0.0;

                    if (carryFwdRule) {
                        carryAmount = Math.min(closingBal, maxCarryRule);
                        lapseAmount = closingBal - carryAmount;
                    } else {
                        lapseAmount = closingBal;
                    }

                    // 1. Update source year balance (lapsed & closing_bal)
                    db.execute(
                            "UPDATE leave_balance SET lapsed = lapsed + ?, closing_bal = ? WHERE emp_id=? AND leave_type=? AND year=?",
                            lapseAmount, carryAmount, empId, leaveType, sourceYear);

                    // Record lapse transaction in source year if any
                    if (lapseAmount > 0) {
                        db.execute(
                                "INSERT INTO leave_transactions (emp_id, leave_type, year, transaction_type, amount, reference_id, remarks) VALUES (?, ?, ?, 'Lapse', ?, 'Year-End', ?)",
                                empId, leaveType, sourceYear, "Lapse", -lapseAmount, "Lapsed during year-end process");
                    }

                    // 2. Set up target year balance
                    int rows = db.execute(
                            "UPDATE leave_balance SET carry_fwd = ?, closing_bal = opening_bal + credited + ? - used - lapsed WHERE emp_id=? AND leave_type=? AND year=?",
                            carryAmount, carryAmount, empId, leaveType, targetYear);
                    if (rows == 0) {
                        db.execute(
                                "INSERT INTO leave_balance (emp_id, leave_type, year, opening_bal, credited, carry_fwd, used, lapsed, closing_bal) VALUES (?, ?, ?, 0, 0, ?, 0, 0, ?)",
                                empId, leaveType, targetYear, carryAmount, carryAmount);
                    }

                    // Record carry-forward transaction in target year if any
                    if (carryAmount > 0) {
                        db.execute(
                                "INSERT INTO leave_transactions (emp_id, leave_type, year, transaction_type, amount, reference_id, remarks) VALUES (?, ?, ?, 'Carry-Forward', ?, 'Year-End', ?)",
                                empId, leaveType, targetYear, "Carry-Forward", carryAmount, "Carried forward from " + sourceYear);
                    }
                }
            }
            System.out.println("LeaveService: Year-End transition completed successfully.");
        } catch (SQLException e) {
            System.err.println("LeaveService Error in processYearEnd: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
