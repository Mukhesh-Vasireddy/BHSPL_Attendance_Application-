import json
import mysql.connector

try:
    db = mysql.connector.connect(host='localhost',user='root',password='user',database='bhspl_attendance')
    c = db.cursor(dictionary=True)
    c.execute("SELECT * FROM raw_logs WHERE emp_id='15241' AND punch_time >= '2026-04-21 00:00:00' ORDER BY punch_time DESC")
    logs = c.fetchall()
    print("Sridhar Logs Today:")
    for l in logs:
        print(f"Time: {l['punch_time']} | Synced: {l['synced']}")
except Exception as e:
    print(e)
