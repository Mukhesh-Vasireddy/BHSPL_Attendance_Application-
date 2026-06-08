package com.bhspl.debug;

import com.bhspl.db.DatabaseManager;
import com.bhspl.util.AttendanceCalculator;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TestLateCount2 {
    public static void main(String[] args) {
        try {
            Map<String, String> config = com.bhspl.core.Config.loadDbConfig();
            DatabaseManager db = DatabaseManager.getInstance();
            db.connect(config.get("host"), config.get("port"), config.get("user"), config.get("password"), config.get("database"));

            // 1. Get Employee Shift
            Map<String, Object> emp = db.queryOne("SELECT * FROM employees WHERE emp_id = '15576'");
            String shiftName = (String) emp.get("shift");
            System.out.println("Employee Shift Name: " + shiftName);

            // 2. Get Shift
            Map<String, Object> shift = db.queryOne("SELECT * FROM shifts WHERE shift_name = ?", shiftName);
            System.out.println("Shift Details: " + shift);

            // 3. Get Punches
            List<Map<String, Object>> punches = db.query("SELECT punch_time, punch_type FROM raw_logs WHERE emp_id = '15576' AND DATE(punch_time) = CURDATE() ORDER BY punch_time");
            System.out.println("Punches from DB: " + punches);

            List<Map<String, Object>> list = new ArrayList<>();
            for (Map<String, Object> m : punches) {
                Object o = m.get("punch_time");
                int type = m.get("punch_type") != null ? ((Number) m.get("punch_type")).intValue() : 0;
                LocalDateTime ldt;
                if (o instanceof LocalDateTime) ldt = (LocalDateTime) o;
                else if (o instanceof Timestamp) ldt = ((Timestamp) o).toLocalDateTime();
                else ldt = LocalDateTime.parse(o.toString().replace(" ", "T").split("\\.")[0]);

                Map<String, Object> pMap = new HashMap<>();
                pMap.put("time", ldt);
                pMap.put("type", type);
                list.add(pMap);
            }

            // 4. Calculate
            AttendanceCalculator.Metrics met = new AttendanceCalculator.Metrics();
            AttendanceCalculator.calculateFromPunches(list, shift, met);
            System.out.println("FirstIn: " + met.firstIn);
            System.out.println("LastOut: " + met.lastOut);
            System.out.println("Calculated Status: " + met.status);
            System.out.println("Late Mins: " + met.lateMins);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
