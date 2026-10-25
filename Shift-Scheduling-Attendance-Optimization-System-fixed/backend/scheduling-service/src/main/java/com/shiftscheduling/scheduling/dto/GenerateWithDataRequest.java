package com.shiftscheduling.scheduling.dto;

import com.shiftscheduling.scheduling.engine.SchedulingInput;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record GenerateWithDataRequest(
    @Valid @NotNull ScheduleGenerationRequest schedule,
    List<SchedulingInput.EmployeeData> employees,
    List<SchedulingInput.LeaveData> leaves
) {}
