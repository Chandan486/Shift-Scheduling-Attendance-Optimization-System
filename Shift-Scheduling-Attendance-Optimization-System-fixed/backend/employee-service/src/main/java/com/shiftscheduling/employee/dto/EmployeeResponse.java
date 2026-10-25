package com.shiftscheduling.employee.dto;
import java.time.LocalDateTime;
import java.util.Set;
public record EmployeeResponse(
    Long id, Long userId, String fullName, String email, String phone,
    Long departmentId, String departmentName, String position,
    Double maxHoursPerWeek, Integer maxShiftsPerWeek, Double minRestHours,
    Set<SkillResponse> skills, boolean active, LocalDateTime createdAt
) {}
