package com.shiftscheduling.leave.dto;

public record LeaveBalanceResponse(
    Long id,
    Long employeeId,
    int annualLeave,
    int sickLeave,
    int personalLeave,
    int annualUsed,
    int sickUsed,
    int personalUsed,
    int annualRemaining,
    int sickRemaining,
    int personalRemaining,
    int year
) {}
