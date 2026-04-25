package com.bhspl.util;

import com.bhspl.db.DatabaseManager;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;

/**
 * Utility class to centralize attendance calculations (work hours, OT, lateness).
 */
public class AttendanceCalculator {

    public static class Metrics {
        public double duration = 0;
        public double workHours = 0;
        public double overtime = 0;
        public int lateMins = 0;
        public int earlyMins = 0;
        public String status = "Present";
    }

    /**
     * Calculates metrics for a single session based on shift rules.
     */
    public static Metrics calculate(LocalDateTime in, LocalDateTime out, Map<String, Object> shift) {
        Metrics m = new Metrics();
        if (in == null) return m;

        // Shift Parameters
        LocalTime schedIn = LocalTime.of(9, 0);
        LocalTime schedOut = LocalTime.of(18, 0);
        int grace = 5;
        double otThreshold = 9.0;
        double breakHrs = 0.5;

        if (shift != null) {
            try {
                Object st = shift.get("start_time");
                Object et = shift.get("end_time");
                if (st != null && et != null) {
                    if (st instanceof java.sql.Time) {
                        schedIn = ((java.sql.Time) st).toLocalTime();
                    } else {
                        schedIn = LocalTime.parse(st.toString().substring(0, 5));
                    }
                    
                    if (et instanceof java.sql.Time) {
                        schedOut = ((java.sql.Time) et).toLocalTime();
                    } else {
                        schedOut = LocalTime.parse(et.toString().substring(0, 5));
                    }
                    
                    grace = DatabaseManager.num(shift, "grace_mins");
                    otThreshold = DatabaseManager.dbl(shift, "overtime_after");
                    if (otThreshold <= 0) otThreshold = DatabaseManager.dbl(shift, "work_hours");
                    if (otThreshold <= 0) otThreshold = 9.0;
                    breakHrs = DatabaseManager.num(shift, "break_mins") / 60.0;
                }
            } catch (Exception e) {
                System.err.println("AttendanceCalculator: Shift parse error: " + e.getMessage());
            }
        }

        // 1. Lateness
        if (in.toLocalTime().isAfter(schedIn.plusMinutes(grace))) {
            m.lateMins = (int) Duration.between(schedIn, in.toLocalTime()).toMinutes();
            m.status = "Late";
        }

        if (out != null) {
            // 2. Work Hours (Subtotal)
            if (out.isBefore(in)) {
                // Sanity: OUT before IN
                m.workHours = 0;
                m.duration = 0;
            } else {
                double duration = Duration.between(in, out).toMinutes() / 60.0;
                
                // Sanity: Cap duration to 14 hours for same-day shifts to avoid pairing errors
                if (out.toLocalDate().equals(in.toLocalDate()) && duration > 14.0) {
                    duration = 14.0;
                }
                
                m.duration = duration;
                m.workHours = duration;
            }

            // 4. Overtime
            if (m.workHours > otThreshold) {
                m.overtime = m.workHours - otThreshold;
            }

            // 5. Early Out
            if (out.toLocalTime().isBefore(schedOut) && out.toLocalDate().equals(in.toLocalDate())) {
                m.earlyMins = (int) Duration.between(out.toLocalTime(), schedOut).toMinutes();
            }
        }
        return m;
    }

    /**
     * Formats decimal hours into HH:mm format.
     */
    public static String formatDuration(double decimalHours) {
        if (decimalHours <= 0) return "00:00";
        int totalMins = (int) Math.round(decimalHours * 60);
        int hrs = totalMins / 60;
        int mins = totalMins % 60;
        return String.format("%02d:%02d", hrs, mins);
    }
}
