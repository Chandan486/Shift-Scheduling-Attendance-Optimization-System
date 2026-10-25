package com.shiftscheduling.attendance.overview;

public record OverviewSummary(int present, int absent, int onLeave, int notScheduled, int total) {}
