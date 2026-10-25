package com.shiftscheduling.scheduling.engine;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

public class SchedulingInput {

    private List<ShiftData> shifts;
    private List<EmployeeData> employees;
    private List<LeaveData> approvedLeaves;
    private List<ExistingAssignment> existingAssignments;

    public SchedulingInput() {}

    public SchedulingInput(List<ShiftData> shifts, List<EmployeeData> employees,
                           List<LeaveData> approvedLeaves, List<ExistingAssignment> existingAssignments) {
        this.shifts = shifts;
        this.employees = employees;
        this.approvedLeaves = approvedLeaves;
        this.existingAssignments = existingAssignments;
    }

    public List<ShiftData> getShifts() { return shifts; }
    public void setShifts(List<ShiftData> shifts) { this.shifts = shifts; }
    public List<EmployeeData> getEmployees() { return employees; }
    public void setEmployees(List<EmployeeData> employees) { this.employees = employees; }
    public List<LeaveData> getApprovedLeaves() { return approvedLeaves; }
    public void setApprovedLeaves(List<LeaveData> approvedLeaves) { this.approvedLeaves = approvedLeaves; }
    public List<ExistingAssignment> getExistingAssignments() { return existingAssignments; }
    public void setExistingAssignments(List<ExistingAssignment> existingAssignments) { this.existingAssignments = existingAssignments; }

    // --- Nested data classes ---

    public static class ShiftData {
        private Long shiftId;
        private String name;
        private Long departmentId;
        private LocalDate date;
        private LocalTime startTime;
        private LocalTime endTime;
        private boolean overnight;
        private boolean nightShift;
        private int minStaff;
        private int maxStaff;
        private List<SkillRequirement> requiredSkills;

        public Long getShiftId() { return shiftId; }
        public void setShiftId(Long shiftId) { this.shiftId = shiftId; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public Long getDepartmentId() { return departmentId; }
        public void setDepartmentId(Long departmentId) { this.departmentId = departmentId; }
        public LocalDate getDate() { return date; }
        public void setDate(LocalDate date) { this.date = date; }
        public LocalTime getStartTime() { return startTime; }
        public void setStartTime(LocalTime startTime) { this.startTime = startTime; }
        public LocalTime getEndTime() { return endTime; }
        public void setEndTime(LocalTime endTime) { this.endTime = endTime; }
        public boolean isOvernight() { return overnight; }
        public void setOvernight(boolean overnight) { this.overnight = overnight; }
        public boolean isNightShift() { return nightShift; }
        public void setNightShift(boolean nightShift) { this.nightShift = nightShift; }
        public int getMinStaff() { return minStaff; }
        public void setMinStaff(int minStaff) { this.minStaff = minStaff; }
        public int getMaxStaff() { return maxStaff; }
        public void setMaxStaff(int maxStaff) { this.maxStaff = maxStaff; }
        public List<SkillRequirement> getRequiredSkills() { return requiredSkills; }
        public void setRequiredSkills(List<SkillRequirement> requiredSkills) { this.requiredSkills = requiredSkills; }

        public double getDurationHours() {
            double hours;
            if (overnight) {
                hours = (24.0 - startTime.getHour() - startTime.getMinute() / 60.0)
                        + endTime.getHour() + endTime.getMinute() / 60.0;
            } else {
                hours = (endTime.getHour() + endTime.getMinute() / 60.0)
                        - (startTime.getHour() + startTime.getMinute() / 60.0);
            }
            return Math.max(hours, 0);
        }
    }

    public static class SkillRequirement {
        private Long skillId;
        private int requiredCount;

        public SkillRequirement() {}
        public SkillRequirement(Long skillId, int requiredCount) {
            this.skillId = skillId;
            this.requiredCount = requiredCount;
        }

        public Long getSkillId() { return skillId; }
        public void setSkillId(Long skillId) { this.skillId = skillId; }
        public int getRequiredCount() { return requiredCount; }
        public void setRequiredCount(int requiredCount) { this.requiredCount = requiredCount; }
    }

    public static class EmployeeData {
        private Long employeeId;
        private String fullName;
        private Long departmentId;
        private Set<Long> skillIds;
        private double maxHoursPerWeek;
        private int maxShiftsPerWeek;
        private double minRestHours;
        private List<AvailabilitySlot> availabilities;

        public Long getEmployeeId() { return employeeId; }
        public void setEmployeeId(Long employeeId) { this.employeeId = employeeId; }
        public String getFullName() { return fullName; }
        public void setFullName(String fullName) { this.fullName = fullName; }
        public Long getDepartmentId() { return departmentId; }
        public void setDepartmentId(Long departmentId) { this.departmentId = departmentId; }
        public Set<Long> getSkillIds() { return skillIds; }
        public void setSkillIds(Set<Long> skillIds) { this.skillIds = skillIds; }
        public double getMaxHoursPerWeek() { return maxHoursPerWeek; }
        public void setMaxHoursPerWeek(double maxHoursPerWeek) { this.maxHoursPerWeek = maxHoursPerWeek; }
        public int getMaxShiftsPerWeek() { return maxShiftsPerWeek; }
        public void setMaxShiftsPerWeek(int maxShiftsPerWeek) { this.maxShiftsPerWeek = maxShiftsPerWeek; }
        public double getMinRestHours() { return minRestHours; }
        public void setMinRestHours(double minRestHours) { this.minRestHours = minRestHours; }
        public List<AvailabilitySlot> getAvailabilities() { return availabilities; }
        public void setAvailabilities(List<AvailabilitySlot> availabilities) { this.availabilities = availabilities; }
    }

    public static class AvailabilitySlot {
        private DayOfWeek dayOfWeek;
        private LocalTime startTime;
        private LocalTime endTime;
        private boolean available;
        private String preference; // PREFERRED, NEUTRAL, AVOID

        public DayOfWeek getDayOfWeek() { return dayOfWeek; }
        public void setDayOfWeek(DayOfWeek dayOfWeek) { this.dayOfWeek = dayOfWeek; }
        public LocalTime getStartTime() { return startTime; }
        public void setStartTime(LocalTime startTime) { this.startTime = startTime; }
        public LocalTime getEndTime() { return endTime; }
        public void setEndTime(LocalTime endTime) { this.endTime = endTime; }
        public boolean isAvailable() { return available; }
        public void setAvailable(boolean available) { this.available = available; }
        public String getPreference() { return preference; }
        public void setPreference(String preference) { this.preference = preference; }
    }

    public static class LeaveData {
        private Long employeeId;
        private LocalDate startDate;
        private LocalDate endDate;

        public LeaveData() {}
        public LeaveData(Long employeeId, LocalDate startDate, LocalDate endDate) {
            this.employeeId = employeeId;
            this.startDate = startDate;
            this.endDate = endDate;
        }

        public Long getEmployeeId() { return employeeId; }
        public void setEmployeeId(Long employeeId) { this.employeeId = employeeId; }
        public LocalDate getStartDate() { return startDate; }
        public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
        public LocalDate getEndDate() { return endDate; }
        public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    }

    public static class ExistingAssignment {
        private Long employeeId;
        private Long shiftId;
        private LocalDate date;
        private LocalTime shiftStartTime;
        private LocalTime shiftEndTime;
        private boolean overnight;
        private double durationHours;

        public Long getEmployeeId() { return employeeId; }
        public void setEmployeeId(Long employeeId) { this.employeeId = employeeId; }
        public Long getShiftId() { return shiftId; }
        public void setShiftId(Long shiftId) { this.shiftId = shiftId; }
        public LocalDate getDate() { return date; }
        public void setDate(LocalDate date) { this.date = date; }
        public LocalTime getShiftStartTime() { return shiftStartTime; }
        public void setShiftStartTime(LocalTime shiftStartTime) { this.shiftStartTime = shiftStartTime; }
        public LocalTime getShiftEndTime() { return shiftEndTime; }
        public void setShiftEndTime(LocalTime shiftEndTime) { this.shiftEndTime = shiftEndTime; }
        public boolean isOvernight() { return overnight; }
        public void setOvernight(boolean overnight) { this.overnight = overnight; }
        public double getDurationHours() { return durationHours; }
        public void setDurationHours(double durationHours) { this.durationHours = durationHours; }
    }
}
