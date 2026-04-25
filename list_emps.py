import json
import mysql.connector

try:
    db = mysql.connector.connect(host='localhost',user='root',password='user',database='bhspl_attendance')
    c = db.cursor(dictionary=True)
    c.execute("SELECT emp_id, emp_name, device_enroll_id FROM employees")
    emps = c.fetchall()
    print(json.dumps(emps, indent=2))
except Exception as e:
    print(e)
