package com.shiftscheduling.leave.dto;

import com.shiftscheduling.leave.entity.LeaveStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Decision body. The reviewer is never named here; it comes from the gateway headers. */
public record LeaveApprovalRequest(
    @NotNull LeaveStatus status,
    @Size(max = 500) String reviewNotes
) {}
