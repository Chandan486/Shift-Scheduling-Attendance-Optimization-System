package com.shiftscheduling.scheduling.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDate;

public record ScheduleRequest(
    @NotBlank @Size(max = 200) String name,
    @NotNull LocalDate startDate,
    @NotNull LocalDate endDate,
    @NotNull Long createdBy,
    String notes
) {}
