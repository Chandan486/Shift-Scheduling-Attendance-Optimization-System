package com.shiftscheduling.notification.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record SchedulePublishedRequest(
    @NotEmpty List<Long> userIds,
    @NotNull Long scheduleId,
    String scheduleName
) {}
