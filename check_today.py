import json
import mysql.connector

try:
    db = mysql.connector.connect(host='localhost',user='root',password='user',database='bhspl_attendance')
    c = db.cursor(dictionary=True)
    c.execute("SELECT * FROM raw_logs WHERE emp_id='15241' AND DATE(punch_time) = CURDATE() ORDER BY punch_time DESC")
    print("Sridhar Today:", json.dumps(c.fetchall(), default=str, indent=2))
    
    # Check for any names containing Yash
    c.execute("SELECT emp_id, emp_name FROM employees WHERE emp_name LIKE '%Yash%'")
    print("Yashwanth Search:", json.dumps(c.fetchall(), indent=2))
except Exception as e:
    print(e)
