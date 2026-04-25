package com.bhspl.model;

/**
 * POJO representing a shift configuration loaded from the DB.
 */
public class ShiftInfo {
    public String shiftName;
    public String startTime;   // "HH:mm"
    public String endTime;     // "HH:mm"
    public int    graceMins;
    public int    breakMins;
    public double overtimeAfter;
    public int    minPresentMins;
    public int    halfDayMins;
    public String weeklyOff1;
    public String weeklyOff2;

    public ShiftInfo() {
        this.shiftName       = "General";
        this.startTime       = "09:00";
        this.endTime         = "18:00";
        this.graceMins       = 5;
        this.breakMins       = 30;
        this.overtimeAfter   = 9.0;
        this.minPresentMins  = 480;
        this.halfDayMins     = 240;
        this.weeklyOff1      = "Sunday";
        this.weeklyOff2      = "None";
    }
}
