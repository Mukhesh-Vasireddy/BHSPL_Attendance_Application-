package com.bhspl.util;

import com.bhspl.db.DatabaseManager;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
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
        public double breakHours = 0;
        public LocalDateTime firstIn = null;
        public LocalDateTime lastOut = null;
        public String exceptions = ""; // New field to track punch anomalies
        public java.util.List<Map<String, Object>> breakIntervals = new java.util.ArrayList<>();
    }

    public static void calculateFromPunches(List<Map<String, Object>> punches, Map<String, Object> shift, Metrics m) {
        if (punches == null || punches.isEmpty()) return;

        // Ensure chronological sorting
        List<Map<String, Object>> sortedPunches = new java.util.ArrayList<>(punches);
        sortedPunches.sort((p1, p2) -> ((LocalDateTime) p1.get("time")).compareTo((LocalDateTime) p2.get("time")));

        // 1. Filter duplicates (within 10 seconds)
        List<Map<String, Object>> filtered = new java.util.ArrayList<>();
        LocalDateTime lastTime = null;
        int lastType = -1;
        
        for (Map<String, Object> p : sortedPunches) {
            LocalDateTime t = (LocalDateTime) p.get("time");
            int type = (int) p.get("type");
            
            if (lastTime != null) {
                long diff = Math.abs(Duration.between(lastTime, t).getSeconds());
                if (diff < 10 && type == lastType) {
                    continue; // skip duplicate within 10s
                }
            }
            filtered.add(p);
            lastTime = t;
            lastType = type;
        }

        if (filtered.isEmpty()) return;

        List<String> exceptionsList = new java.util.ArrayList<>();

        // First IN and Last OUT
        m.firstIn = (LocalDateTime) filtered.get(0).get("time");
        m.lastOut = (LocalDateTime) filtered.get(filtered.size() - 1).get("time");

        // 2. Break Calculation
        long breakSeconds = 0;
        LocalDateTime breakStartTime = null;

        boolean hasExplicitTypes = false;
        for (Map<String, Object> p : filtered) {
            int type = (int) p.get("type");
            if (type != 0) {
                hasExplicitTypes = true;
                break;
            }
        }

        if (hasExplicitTypes) {
            // State-Based Break Calculation (when explicit types exist)
            for (int i = 0; i < filtered.size() - 1; i++) {
                Map<String, Object> p = filtered.get(i);
                LocalDateTime t = (LocalDateTime) p.get("time");
                int type = (int) p.get("type");

                if (type != 0 && breakStartTime == null) {
                    breakStartTime = t; // Start of break
                } else if (type == 0 && breakStartTime != null) {
                    long gapSecs = Duration.between(breakStartTime, t).getSeconds();
                    if (gapSecs > 0) {
                        breakSeconds += gapSecs;
                        Map<String, Object> interval = new java.util.HashMap<>();
                        interval.put("start", breakStartTime);
                        interval.put("end", t);
                        interval.put("duration", gapSecs);
                        m.breakIntervals.add(interval);
                    }
                    breakStartTime = null; // End of break
                }
            }
        } else {
            // Alternating Punch Sequence (when all punches are type = 0)
            // Punch 1 to Punch 2 = Work, Punch 2 to Punch 3 = Break, Punch 3 to Punch 4 = Work, etc.
            // Under this pattern: Break intervals are between odd indices and their subsequent even indices.
            for (int i = 1; i < filtered.size() - 1; i += 2) {
                LocalDateTime startBreak = (LocalDateTime) filtered.get(i).get("time");
                LocalDateTime endBreak = (LocalDateTime) filtered.get(i + 1).get("time");
                long gapSecs = Duration.between(startBreak, endBreak).getSeconds();
                if (gapSecs > 0) {
                    breakSeconds += gapSecs;
                    Map<String, Object> interval = new java.util.HashMap<>();
                    interval.put("start", startBreak);
                    interval.put("end", endBreak);
                    interval.put("duration", gapSecs);
                    m.breakIntervals.add(interval);
                }
            }
        }
        
        m.breakHours = breakSeconds / 3600.0;

        // 3. Net Working Hours Calculation
        long totalSeconds = Duration.between(m.firstIn, m.lastOut).getSeconds();
        if (totalSeconds <= 0) {
            m.workHours = 0;
            m.duration = 0;
            calculateShiftMetrics(m, shift);
            return;
        }

        long netSeconds = totalSeconds - breakSeconds;
        if (netSeconds < 0) netSeconds = 0;

        m.workHours = netSeconds / 3600.0;
        m.duration = totalSeconds / 3600.0;

        if (hasExplicitTypes) {
            if (breakStartTime != null) {
                exceptionsList.add("Missing Return Punch");
            }
        } else {
            if (filtered.size() % 2 != 0) {
                exceptionsList.add("Unpaired Punches");
            }
        }
        
        if (m.lastOut.toLocalDate().equals(m.firstIn.toLocalDate()) && m.workHours > 14.0) {
            m.workHours = 14.0;
            exceptionsList.add("Abnormal Work Duration");
        }

        m.exceptions = String.join(", ", exceptionsList);

        calculateShiftMetrics(m, shift);
    }

    private static void calculateShiftMetrics(Metrics m, Map<String, Object> shift) {
        if (m.firstIn == null) return;
        
        // Shift Parameters
        LocalTime schedIn = LocalTime.of(9, 0);
        LocalTime schedOut = LocalTime.of(18, 0);
        int grace = 5;
        double otThreshold = 9.0;

        if (shift != null) {
            try {
                Object st = shift.get("start_time");
                Object et = shift.get("end_time");
                
                schedIn = parseLocalTime(st, schedIn);
                schedOut = parseLocalTime(et, schedOut);
                
                grace = DatabaseManager.num(shift, "grace_mins");
                otThreshold = DatabaseManager.dbl(shift, "overtime_after");
                if (otThreshold <= 0) otThreshold = DatabaseManager.dbl(shift, "work_hours");
                if (otThreshold <= 0) otThreshold = 9.0;
            } catch (Exception e) {
                System.err.println("AttendanceCalculator: Shift parse error: " + e.getMessage());
            }
        }
        
        // Auto-deduction rule removed per user request

        // 1. Overnight Shift Handling
        boolean isOvernight = schedOut.isBefore(schedIn);
        LocalDateTime expectedStart;
        LocalDateTime expectedEnd;

        if (isOvernight) {
            // If the punch in is after midnight but before shift end, they punched in the next day
            if (m.firstIn.toLocalTime().isBefore(schedOut) || m.firstIn.toLocalTime().isBefore(LocalTime.of(12, 0))) {
                expectedStart = m.firstIn.toLocalDate().minusDays(1).atTime(schedIn);
                expectedEnd = m.firstIn.toLocalDate().atTime(schedOut);
            } else {
                // They punched in the same day evening
                expectedStart = m.firstIn.toLocalDate().atTime(schedIn);
                expectedEnd = m.firstIn.toLocalDate().plusDays(1).atTime(schedOut);
            }
        } else {
            expectedStart = m.firstIn.toLocalDate().atTime(schedIn);
            expectedEnd = m.firstIn.toLocalDate().atTime(schedOut);
        }

        // 2. Lateness
        if (m.firstIn.isAfter(expectedStart.plusMinutes(grace))) {
            m.lateMins = (int) Duration.between(expectedStart.plusMinutes(grace), m.firstIn).toMinutes();
            m.status = "Late";
        }

        // 3. Early Leaving and Overtime
        if (m.lastOut != null && !m.lastOut.equals(m.firstIn)) {
            if (m.workHours > otThreshold) {
                m.overtime = m.workHours - otThreshold;
            }

            if (m.lastOut.isBefore(expectedEnd)) {
                m.earlyMins = (int) Duration.between(m.lastOut, expectedEnd).toMinutes();
            }
        }
    }

    /**
     * Calculates metrics for a single session based on shift rules.
     */
    public static Metrics calculate(LocalDateTime in, LocalDateTime out, Map<String, Object> shift) {
        Metrics m = new Metrics();
        if (in == null) return m;
        m.firstIn = in;
        m.lastOut = out;

        if (out != null) {
            if (out.isBefore(in)) {
                m.workHours = 0;
                m.duration = 0;
            } else {
                double duration = Duration.between(in, out).toMinutes() / 60.0;
                if (out.toLocalDate().equals(in.toLocalDate()) && duration > 14.0) {
                    duration = 14.0;
                }
                m.duration = duration;
                m.workHours = duration;
            }
        }
        
        calculateShiftMetrics(m, shift);
        return m;
    }

    public static LocalTime parseLocalTime(Object val, LocalTime defaultTime) {
        if (val == null) return defaultTime;
        if (val instanceof java.sql.Time) {
            return ((java.sql.Time) val).toLocalTime();
        }
        if (val instanceof java.time.LocalTime) {
            return (java.time.LocalTime) val;
        }
        String s = val.toString().trim();
        if (s.isEmpty()) return defaultTime;
        try {
            boolean isPm = s.toUpperCase().contains("PM");
            boolean isAm = s.toUpperCase().contains("AM");
            s = s.toUpperCase().replace("AM", "").replace("PM", "").trim();
            
            // Handle HH:mm:ss or HH:mm format
            String[] parts = s.split(":");
            if (parts.length >= 2) {
                int hour = Integer.parseInt(parts[0]);
                int min = Integer.parseInt(parts[1]);
                if (isPm && hour < 12) hour += 12;
                if (isAm && hour == 12) hour = 0;
                return LocalTime.of(hour, min);
            }
        } catch (Exception e) {
            System.err.println("Error parsing time '" + val + "': " + e.getMessage());
        }
        return defaultTime;
    }

    /**
     * Formats decimal hours into HH:mm format.
     */
    public static String formatDuration(double decimalHours) {
        if (decimalHours <= 0) return "00:00:00";
        int totalSeconds = (int) Math.round(decimalHours * 3600);
        int hrs = totalSeconds / 3600;
        int mins = (totalSeconds % 3600) / 60;
        int secs = totalSeconds % 60;
        return String.format("%02d:%02d:%02d", hrs, mins, secs);
    }
}
