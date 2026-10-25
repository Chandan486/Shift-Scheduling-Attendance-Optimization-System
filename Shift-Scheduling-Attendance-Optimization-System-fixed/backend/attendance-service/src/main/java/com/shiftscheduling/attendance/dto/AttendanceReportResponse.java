package com.shiftscheduling.attendance.dto;

public record AttendanceReportResponse(
    String csvContent,
    String filename,
    int totalRecords
) {}
