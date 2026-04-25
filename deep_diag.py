import json
import mysql.connector

try:
    db = mysql.connector.connect(host='localhost',user='root',password='user',database='bhspl_attendance')
    c = db.cursor(dictionary=True)
    
    print("--- Searching for Yashwanth ---")
    c.execute("SELECT emp_id, emp_name, device_enroll_id FROM employees WHERE emp_name LIKE '%Yash%' OR emp_name LIKE '%Yash%'")
    print(json.dumps(c.fetchall(), indent=2))
    
    print("\n--- Latest 20 Raw Logs (Any ID) ---")
    c.execute("SELECT * FROM raw_logs ORDER BY punch_time DESC LIMIT 20")
    print(json.dumps(c.fetchall(), default=str, indent=2))
    
    print("\n--- Latest 20 Attendance Records (Processed) ---")
    c.execute("SELECT a.*, e.emp_name FROM attendance a LEFT JOIN employees e ON a.emp_id = e.emp_id ORDER BY punch_date DESC, in_time DESC LIMIT 20")
    print(json.dumps(c.fetchall(), default=str, indent=2))

except Exception as e:
    print(e)
