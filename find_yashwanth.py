import json
import mysql.connector

try:
    db = mysql.connector.connect(host='localhost',user='root',password='user',database='bhspl_attendance')
    c = db.cursor(dictionary=True)
    
    # 1. Any employees containing 'Yash' anywhere?
    c.execute("SELECT emp_id, emp_name FROM employees WHERE emp_name LIKE '%Yash%'")
    print("Employees with 'Yash':", c.fetchall())
    
    # 2. Latest raw logs for IDs NOT in employees table
    c.execute("""
        SELECT r.emp_id, COUNT(*) as count, MAX(r.punch_time) as last_punch 
        FROM raw_logs r 
        LEFT JOIN employees e ON r.emp_id = e.emp_id 
        WHERE e.emp_id IS NULL 
        GROUP BY r.emp_id 
        ORDER BY last_punch DESC 
        LIMIT 20
    """)
    print("\nUnknown IDs in Raw Logs:", json.dumps(c.fetchall(), default=str, indent=2))

except Exception as e:
    print(e)
