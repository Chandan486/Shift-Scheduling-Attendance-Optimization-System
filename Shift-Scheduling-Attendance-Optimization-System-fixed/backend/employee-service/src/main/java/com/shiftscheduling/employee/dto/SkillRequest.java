package com.shiftscheduling.employee.dto;
import jakarta.validation.constraints.NotBlank;
public record SkillRequest(@NotBlank String name, String description) {}
