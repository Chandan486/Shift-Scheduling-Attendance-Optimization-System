package com.shiftscheduling.attendance.overview;

import java.time.LocalDate;
import java.util.List;

public record OverviewResponse(
        LocalDate date,
        boolean today,
        OverviewSummary summary,
        List<OverviewRow> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {}
