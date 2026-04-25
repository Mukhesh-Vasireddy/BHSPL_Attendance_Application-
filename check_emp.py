import json
import mysql.connector

db = mysql.connector.connect(host='localhost',user='root',password='user',database='bhspl_attendance')
c = db.cursor(dictionary=True)
c.execute("SELECT emp_id, emp_name, device_enroll_id FROM employees WHERE emp_name LIKE '%Sri%' OR emp_name LIKE '%Yash%'")
print(json.dumps(c.fetchall(), default=str, indent=2))
