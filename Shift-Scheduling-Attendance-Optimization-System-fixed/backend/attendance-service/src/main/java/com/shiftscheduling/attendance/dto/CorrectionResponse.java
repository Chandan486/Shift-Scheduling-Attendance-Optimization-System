package com.shiftscheduling.attendance.dto;

import java.time.LocalDateTime;

public record CorrectionResponse(
    Long id,
    Long attendanceRecordId,
    Long correctedBy,
    LocalDateTime previousCheckIn,
    LocalDateTime previousCheckOut,
    LocalDateTime newCheckIn,
    LocalDateTime newCheckOut,
    String reason,
    LocalDateTime createdAt
) {}
