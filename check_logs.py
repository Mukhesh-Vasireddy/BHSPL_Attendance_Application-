import json
import mysql.connector

db = mysql.connector.connect(host='localhost',user='root',password='user',database='bhspl_attendance')
c = db.cursor(dictionary=True)
c.execute("SELECT * FROM raw_logs WHERE emp_id='15241' ORDER BY punch_time DESC LIMIT 10")
print("Sridhar Raw Logs:", json.dumps(c.fetchall(), default=str, indent=2))
c.execute("SELECT * FROM raw_logs ORDER BY punch_time DESC LIMIT 5")
print("Latest 5 Raw Logs:", json.dumps(c.fetchall(), default=str, indent=2))
