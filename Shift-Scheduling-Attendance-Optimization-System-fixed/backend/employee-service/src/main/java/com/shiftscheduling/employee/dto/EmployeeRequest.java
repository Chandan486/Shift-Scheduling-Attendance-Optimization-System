package com.shiftscheduling.employee.dto;
import jakarta.validation.constraints.*;
import java.util.Set;
public record EmployeeRequest(
    @NotNull(message = "User ID is required") Long userId,

    @NotBlank(message = "Full name is required")
    @Size(min = 2, max = 100, message = "Full name must be 2-100 characters")
    String fullName,

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    String email,

    @Pattern(regexp = "^$|^[6-9][0-9]{9}$", message = "Phone must be a valid 10-digit Indian mobile number")
    String phone,

    @NotNull(message = "Department is required") Long departmentId,

    @NotBlank(message = "Position is required") String position,

    @Min(value = 1, message = "Max hours per week must be at least 1")
    @Max(value = 80, message = "Max hours per week cannot exceed 80")
    Double maxHoursPerWeek,

    @Min(value = 1, message = "Max shifts per week must be at least 1")
    @Max(value = 7, message = "Max shifts per week cannot exceed 7")
    Integer maxShiftsPerWeek,

    @Min(value = 4, message = "Min rest hours must be at least 4")
    @Max(value = 24, message = "Min rest hours cannot exceed 24")
    Double minRestHours,

    Set<Long> skillIds
) {}
