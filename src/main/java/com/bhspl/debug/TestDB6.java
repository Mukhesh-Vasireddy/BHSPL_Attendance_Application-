package com.bhspl.debug;

import com.bhspl.util.AttendanceCalculator;
import com.bhspl.util.AttendanceCalculator.Metrics;
import java.time.LocalDateTime;
import java.util.*;

public class TestDB6 {
    public static void main(String[] args) {
        List<Map<String, Object>> punches = new ArrayList<>();
        
        // 09:51 AM to 01:16 PM
        // 01:19 PM to 04:15 PM
        // 04:21 PM to 06:19 PM
        addPunch(punches, "2026-06-03T09:51:00", 0); // IN
        addPunch(punches, "2026-06-03T13:16:00", 1); // OUT
        addPunch(punches, "2026-06-03T13:19:00", 0); // IN
        addPunch(punches, "2026-06-03T16:15:00", 1); // OUT
        addPunch(punches, "2026-06-03T16:21:00", 0); // IN
        addPunch(punches, "2026-06-03T18:19:00", 1); // OUT

        Metrics m = new Metrics();
        AttendanceCalculator.calculateFromPunches(punches, null, m);
        
        long breakMins = (long) Math.round(m.breakHours * 60);
        long hr = breakMins / 60;
        long min = breakMins % 60;
        
        System.out.println("First IN: " + m.firstIn);
        System.out.println("Last OUT: " + m.lastOut);
        System.out.println("Break Time (Mins): " + breakMins + " (" + hr + "h " + min + "m)");
        System.out.println("Net Working Time (Hours): " + m.workHours);
    }
    
    private static void addPunch(List<Map<String, Object>> punches, String time, int type) {
        Map<String, Object> p = new HashMap<>();
        p.put("time", LocalDateTime.parse(time));
        p.put("type", type);
        punches.add(p);
    }
}
