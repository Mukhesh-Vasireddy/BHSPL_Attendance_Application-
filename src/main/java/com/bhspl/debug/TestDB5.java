package com.bhspl.debug;

import com.bhspl.util.AttendanceCalculator;
import com.bhspl.util.AttendanceCalculator.Metrics;
import java.time.LocalDateTime;
import java.util.*;

public class TestDB5 {
    public static void main(String[] args) {
        List<Map<String, Object>> punches = new ArrayList<>();
        
        // Example from User Requirements:
        // 09:00 AM - IN (type 0)
        // 01:00 PM - OUT (type 1)
        // 01:30 PM - IN (type 0)
        // 04:00 PM - OUT (type 1)
        // 04:15 PM - IN (type 0)
        // 06:00 PM - OUT (type 1)
        
        // Let's also add duplicate noise
        addPunch(punches, "2026-06-03T09:00:00", 0);
        addPunch(punches, "2026-06-03T09:01:00", 0); // Duplicate IN (should be ignored)
        addPunch(punches, "2026-06-03T13:00:00", 1);
        addPunch(punches, "2026-06-03T13:30:00", 0);
        addPunch(punches, "2026-06-03T16:00:00", 1);
        addPunch(punches, "2026-06-03T16:15:00", 0);
        addPunch(punches, "2026-06-03T18:00:00", 1);
        addPunch(punches, "2026-06-03T18:01:00", 1); // Duplicate OUT (should be ignored)

        Metrics m = new Metrics();
        AttendanceCalculator.calculateFromPunches(punches, null, m);
        
        System.out.println("First IN: " + m.firstIn);
        System.out.println("Last OUT: " + m.lastOut);
        System.out.println("Total Duration (Hours): " + m.duration);
        System.out.println("Break Time (Hours): " + m.breakHours);
        System.out.println("Net Working Time (Hours): " + m.workHours);
        System.out.println("Break Time (Mins): " + (m.breakHours * 60));
        System.out.println("Exceptions: " + m.exceptions);
    }
    
    private static void addPunch(List<Map<String, Object>> punches, String time, int type) {
        Map<String, Object> p = new HashMap<>();
        p.put("time", LocalDateTime.parse(time));
        p.put("type", type);
        punches.add(p);
    }
}
