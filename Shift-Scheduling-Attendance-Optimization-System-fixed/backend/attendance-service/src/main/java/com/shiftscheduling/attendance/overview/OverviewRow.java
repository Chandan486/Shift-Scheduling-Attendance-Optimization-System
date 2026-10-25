package com.shiftscheduling.attendance.overview;

import java.time.LocalDateTime;

/**
 * One employee's classification for the selected date.
 * checkInTime/checkOutTime/attendanceStatus are null unless the employee is PRESENT.
 */
public record OverviewRow(
        Long employeeId,
        String employeeName,
        Long departmentId,
        String department,
        OverviewStatus status,
        LocalDateTime checkInTime,
        LocalDateTime checkOutTime,
        String attendanceStatus,
        boolean incomplete,
        String shiftName,
        String leaveType
) {}
