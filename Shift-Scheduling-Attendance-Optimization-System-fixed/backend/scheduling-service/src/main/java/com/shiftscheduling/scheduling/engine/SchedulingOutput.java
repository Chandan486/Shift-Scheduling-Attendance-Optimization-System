package com.shiftscheduling.scheduling.engine;

import java.time.LocalDate;
import java.util.List;

public class SchedulingOutput {

    private List<ProposedAssignment> assignments;
    private List<UnfilledPosition> unfilledPositions;

    public SchedulingOutput() {}

    public SchedulingOutput(List<ProposedAssignment> assignments, List<UnfilledPosition> unfilledPositions) {
        this.assignments = assignments;
        this.unfilledPositions = unfilledPositions;
    }

    public List<ProposedAssignment> getAssignments() { return assignments; }
    public void setAssignments(List<ProposedAssignment> assignments) { this.assignments = assignments; }
    public List<UnfilledPosition> getUnfilledPositions() { return unfilledPositions; }
    public void setUnfilledPositions(List<UnfilledPosition> unfilledPositions) { this.unfilledPositions = unfilledPositions; }

    public static class ProposedAssignment {
        private Long employeeId;
        private Long shiftId;
        private LocalDate date;

        public ProposedAssignment() {}
        public ProposedAssignment(Long employeeId, Long shiftId, LocalDate date) {
            this.employeeId = employeeId;
            this.shiftId = shiftId;
            this.date = date;
        }

        public Long getEmployeeId() { return employeeId; }
        public void setEmployeeId(Long employeeId) { this.employeeId = employeeId; }
        public Long getShiftId() { return shiftId; }
        public void setShiftId(Long shiftId) { this.shiftId = shiftId; }
        public LocalDate getDate() { return date; }
        public void setDate(LocalDate date) { this.date = date; }
    }

    public static class UnfilledPosition {
        private Long shiftId;
        private String shiftName;
        private LocalDate date;
        private int requiredCount;
        private int assignedCount;
        private String reason;

        public UnfilledPosition() {}
        public UnfilledPosition(Long shiftId, String shiftName, LocalDate date,
                                int requiredCount, int assignedCount, String reason) {
            this.shiftId = shiftId;
            this.shiftName = shiftName;
            this.date = date;
            this.requiredCount = requiredCount;
            this.assignedCount = assignedCount;
            this.reason = reason;
        }

        public Long getShiftId() { return shiftId; }
        public void setShiftId(Long shiftId) { this.shiftId = shiftId; }
        public String getShiftName() { return shiftName; }
        public void setShiftName(String shiftName) { this.shiftName = shiftName; }
        public LocalDate getDate() { return date; }
        public void setDate(LocalDate date) { this.date = date; }
        public int getRequiredCount() { return requiredCount; }
        public void setRequiredCount(int requiredCount) { this.requiredCount = requiredCount; }
        public int getAssignedCount() { return assignedCount; }
        public void setAssignedCount(int assignedCount) { this.assignedCount = assignedCount; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }
}
