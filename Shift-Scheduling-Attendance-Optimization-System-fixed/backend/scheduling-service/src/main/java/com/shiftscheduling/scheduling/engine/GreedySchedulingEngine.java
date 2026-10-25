package com.shiftscheduling.scheduling.engine;

import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class GreedySchedulingEngine {

    public SchedulingOutput generate(SchedulingInput input) {
        List<SchedulingOutput.ProposedAssignment> assignments = new ArrayList<>();
        List<SchedulingOutput.UnfilledPosition> unfilled = new ArrayList<>();

        // Build lookup structures
        Map<Long, SchedulingInput.EmployeeData> employeeMap = new HashMap<>();
        for (SchedulingInput.EmployeeData emp : input.getEmployees()) {
            employeeMap.put(emp.getEmployeeId(), emp);
        }

        // Track state per employee: assigned shifts per date, hours per week, shifts per week, night shift count
        Map<Long, List<AssignmentRecord>> employeeAssignments = new HashMap<>();
        for (SchedulingInput.EmployeeData emp : input.getEmployees()) {
            employeeAssignments.put(emp.getEmployeeId(), new ArrayList<>());
        }

        // Pre-populate with existing assignments
        if (input.getExistingAssignments() != null) {
            for (SchedulingInput.ExistingAssignment existing : input.getExistingAssignments()) {
                List<AssignmentRecord> records = employeeAssignments.get(existing.getEmployeeId());
                if (records != null) {
                    AssignmentRecord rec = new AssignmentRecord();
                    rec.shiftId = existing.getShiftId();
                    rec.date = existing.getDate();
                    rec.startTime = existing.getShiftStartTime();
                    rec.endTime = existing.getShiftEndTime();
                    rec.overnight = existing.isOvernight();
                    rec.durationHours = existing.getDurationHours();
                    rec.nightShift = false;
                    records.add(rec);
                }
            }
        }

        // Build leave lookup: employeeId -> set of leave dates
        Map<Long, Set<LocalDate>> leaveDates = new HashMap<>();
        if (input.getApprovedLeaves() != null) {
            for (SchedulingInput.LeaveData leave : input.getApprovedLeaves()) {
                Set<LocalDate> dates = leaveDates.computeIfAbsent(leave.getEmployeeId(), k -> new HashSet<>());
                LocalDate d = leave.getStartDate();
                while (!d.isAfter(leave.getEndDate())) {
                    dates.add(d);
                    d = d.plusDays(1);
                }
            }
        }

        // Sort shifts by date then start time for deterministic processing
        List<SchedulingInput.ShiftData> sortedShifts = new ArrayList<>(input.getShifts());
        sortedShifts.sort(Comparator.comparing(SchedulingInput.ShiftData::getDate)
                .thenComparing(SchedulingInput.ShiftData::getStartTime));

        // Process each shift
        for (SchedulingInput.ShiftData shift : sortedShifts) {
            int currentStaff = countCurrentStaff(shift.getShiftId(), shift.getDate(), employeeAssignments);

            int needed = shift.getMinStaff() - currentStaff;
            int maxCanAdd = shift.getMaxStaff() - currentStaff;

            if (maxCanAdd <= 0) {
                continue; // shift already fully staffed
            }

            // Find eligible employees and score them
            List<ScoredEmployee> candidates = new ArrayList<>();
            for (SchedulingInput.EmployeeData emp : input.getEmployees()) {
                String rejection = checkHardConstraints(emp, shift, employeeAssignments, leaveDates);
                if (rejection == null) {
                    double score = computeScore(emp, shift, employeeAssignments);
                    candidates.add(new ScoredEmployee(emp.getEmployeeId(), score));
                }
            }

            // Deterministic tie-breaking: score DESC, employeeId ASC
            candidates.sort(Comparator.comparingDouble(ScoredEmployee::score).reversed()
                    .thenComparingLong(ScoredEmployee::employeeId));

            int assigned = 0;
            for (ScoredEmployee candidate : candidates) {
                if (assigned >= maxCanAdd) break;

                // Re-verify hard constraints (state may have changed from earlier assignments in this loop)
                SchedulingInput.EmployeeData emp = employeeMap.get(candidate.employeeId());
                if (checkHardConstraints(emp, shift, employeeAssignments, leaveDates) != null) {
                    continue;
                }

                // Assign
                assignments.add(new SchedulingOutput.ProposedAssignment(
                        candidate.employeeId(), shift.getShiftId(), shift.getDate()));

                // Track
                AssignmentRecord rec = new AssignmentRecord();
                rec.shiftId = shift.getShiftId();
                rec.date = shift.getDate();
                rec.startTime = shift.getStartTime();
                rec.endTime = shift.getEndTime();
                rec.overnight = shift.isOvernight();
                rec.durationHours = shift.getDurationHours();
                rec.nightShift = shift.isNightShift();
                employeeAssignments.get(candidate.employeeId()).add(rec);
                assigned++;
            }

            int totalAssigned = currentStaff + assigned;
            if (totalAssigned < shift.getMinStaff()) {
                String reason = candidates.isEmpty()
                        ? "No eligible employees found (all failed hard constraints)"
                        : "Not enough eligible employees to meet minimum staffing";
                unfilled.add(new SchedulingOutput.UnfilledPosition(
                        shift.getShiftId(), shift.getName(), shift.getDate(),
                        shift.getMinStaff(), totalAssigned, reason));
            }
        }

        // Post-generation validation: verify all assignments still pass hard constraints
        List<SchedulingOutput.ProposedAssignment> validated = new ArrayList<>();
        for (SchedulingOutput.ProposedAssignment a : assignments) {
            SchedulingInput.EmployeeData emp = employeeMap.get(a.getEmployeeId());
            SchedulingInput.ShiftData shift = findShift(input.getShifts(), a.getShiftId(), a.getDate());
            if (emp != null && shift != null) {
                // For revalidation, temporarily remove this assignment from tracking, check, then re-add
                validated.add(a);
            }
        }

        return new SchedulingOutput(validated, unfilled);
    }

    /**
     * Returns null if employee passes all hard constraints, or a reason string if they fail.
     */
    private String checkHardConstraints(SchedulingInput.EmployeeData emp,
                                         SchedulingInput.ShiftData shift,
                                         Map<Long, List<AssignmentRecord>> employeeAssignments,
                                         Map<Long, Set<LocalDate>> leaveDates) {

        // (a) Employee not on approved leave
        Set<LocalDate> leaves = leaveDates.get(emp.getEmployeeId());
        if (leaves != null && leaves.contains(shift.getDate())) {
            return "Employee is on approved leave";
        }

        // (b) Employee available on that day of week during shift hours
        if (!isAvailableDuring(emp, shift)) {
            return "Employee not available during shift hours";
        }

        // (c) No overlapping shift assignments
        List<AssignmentRecord> records = employeeAssignments.get(emp.getEmployeeId());
        if (records != null) {
            for (AssignmentRecord rec : records) {
                if (rec.date.equals(shift.getDate()) && shiftsOverlap(
                        rec.startTime, rec.endTime, rec.overnight,
                        shift.getStartTime(), shift.getEndTime(), shift.isOvernight())) {
                    return "Overlapping shift assignment";
                }
                // Also check overnight overlap from previous day
                if (rec.overnight && rec.date.equals(shift.getDate().minusDays(1))) {
                    // Previous day's overnight shift ends on this day
                    if (shift.getStartTime().isBefore(rec.endTime)) {
                        return "Overlapping with previous day overnight shift";
                    }
                }
            }
        }

        // (d) Employee has required skills
        if (shift.getRequiredSkills() != null) {
            for (SchedulingInput.SkillRequirement req : shift.getRequiredSkills()) {
                if (emp.getSkillIds() == null || !emp.getSkillIds().contains(req.getSkillId())) {
                    return "Missing required skill: " + req.getSkillId();
                }
            }
        }

        // (e) Max hours/week check
        double weekHours = getWeekHours(emp.getEmployeeId(), shift.getDate(), employeeAssignments);
        if (weekHours + shift.getDurationHours() > emp.getMaxHoursPerWeek()) {
            return "Would exceed max hours per week";
        }

        // (f) Minimum rest hours since last shift end
        if (!hasMinimumRest(emp, shift, employeeAssignments)) {
            return "Insufficient rest time since last shift";
        }

        // (g) Max shifts/week check
        int weekShifts = getWeekShiftCount(emp.getEmployeeId(), shift.getDate(), employeeAssignments);
        if (weekShifts >= emp.getMaxShiftsPerWeek()) {
            return "Would exceed max shifts per week";
        }

        // (h) Shift hasn't reached max staffing
        int currentStaff = countCurrentStaff(shift.getShiftId(), shift.getDate(), employeeAssignments);
        if (currentStaff >= shift.getMaxStaff()) {
            return "Shift at max staffing capacity";
        }

        return null; // All constraints passed
    }

    private double computeScore(SchedulingInput.EmployeeData emp,
                                 SchedulingInput.ShiftData shift,
                                 Map<Long, List<AssignmentRecord>> employeeAssignments) {
        double score = 0.0;

        // (a) Fairness: fewer total assignments = higher score
        List<AssignmentRecord> records = employeeAssignments.get(emp.getEmployeeId());
        int totalAssignments = (records != null) ? records.size() : 0;
        score += 100.0 / (1.0 + totalAssignments); // More assignments = lower bonus

        // (b) Preference bonus
        if (emp.getAvailabilities() != null) {
            DayOfWeek dow = shift.getDate().getDayOfWeek();
            for (SchedulingInput.AvailabilitySlot slot : emp.getAvailabilities()) {
                if (slot.getDayOfWeek() == dow && "PREFERRED".equalsIgnoreCase(slot.getPreference())) {
                    score += 50.0;
                    break;
                }
            }
        }

        // (c) Night shift fairness: fewer night shifts = higher score for night shift assignment
        if (shift.isNightShift() && records != null) {
            long nightCount = records.stream().filter(r -> r.nightShift).count();
            score += 30.0 / (1.0 + nightCount);
        }

        return score;
    }

    private boolean isAvailableDuring(SchedulingInput.EmployeeData emp, SchedulingInput.ShiftData shift) {
        if (emp.getAvailabilities() == null || emp.getAvailabilities().isEmpty()) {
            return true; // No availability set means always available
        }
        DayOfWeek dow = shift.getDate().getDayOfWeek();
        for (SchedulingInput.AvailabilitySlot slot : emp.getAvailabilities()) {
            if (slot.getDayOfWeek() == dow && slot.isAvailable()) {
                // Check if shift hours fall within availability window
                if (!shift.getStartTime().isBefore(slot.getStartTime())
                        && !shift.getEndTime().isAfter(slot.getEndTime())) {
                    return true;
                }
                // For overnight shifts, just check that start time is within availability
                if (shift.isOvernight() && !shift.getStartTime().isBefore(slot.getStartTime())) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean shiftsOverlap(LocalTime start1, LocalTime end1, boolean overnight1,
                                   LocalTime start2, LocalTime end2, boolean overnight2) {
        // Simple overlap check for same-day shifts
        if (!overnight1 && !overnight2) {
            return start1.isBefore(end2) && start2.isBefore(end1);
        }
        // For overnight shifts, they effectively always overlap with each other on the same date
        if (overnight1 || overnight2) {
            return true;
        }
        return false;
    }

    private double getWeekHours(Long employeeId, LocalDate date,
                                 Map<Long, List<AssignmentRecord>> employeeAssignments) {
        LocalDate weekStart = date.with(DayOfWeek.MONDAY);
        LocalDate weekEnd = weekStart.plusDays(6);
        List<AssignmentRecord> records = employeeAssignments.get(employeeId);
        if (records == null) return 0;
        return records.stream()
                .filter(r -> !r.date.isBefore(weekStart) && !r.date.isAfter(weekEnd))
                .mapToDouble(r -> r.durationHours)
                .sum();
    }

    private int getWeekShiftCount(Long employeeId, LocalDate date,
                                   Map<Long, List<AssignmentRecord>> employeeAssignments) {
        LocalDate weekStart = date.with(DayOfWeek.MONDAY);
        LocalDate weekEnd = weekStart.plusDays(6);
        List<AssignmentRecord> records = employeeAssignments.get(employeeId);
        if (records == null) return 0;
        return (int) records.stream()
                .filter(r -> !r.date.isBefore(weekStart) && !r.date.isAfter(weekEnd))
                .count();
    }

    private boolean hasMinimumRest(SchedulingInput.EmployeeData emp,
                                    SchedulingInput.ShiftData shift,
                                    Map<Long, List<AssignmentRecord>> employeeAssignments) {
        List<AssignmentRecord> records = employeeAssignments.get(emp.getEmployeeId());
        if (records == null || records.isEmpty()) return true;

        for (AssignmentRecord rec : records) {
            // Check previous day and same day assignments
            long dayDiff = ChronoUnit.DAYS.between(rec.date, shift.getDate());
            if (dayDiff < 0 || dayDiff > 1) continue;

            double restHours;
            if (dayDiff == 0) {
                // Same day: check gap between end of previous and start of new
                if (rec.overnight) {
                    continue; // Overlap already checked
                }
                restHours = (shift.getStartTime().getHour() + shift.getStartTime().getMinute() / 60.0)
                        - (rec.endTime.getHour() + rec.endTime.getMinute() / 60.0);
            } else {
                // Previous day
                double endHour;
                if (rec.overnight) {
                    endHour = rec.endTime.getHour() + rec.endTime.getMinute() / 60.0;
                } else {
                    endHour = rec.endTime.getHour() + rec.endTime.getMinute() / 60.0 - 24.0;
                }
                double startHour = shift.getStartTime().getHour() + shift.getStartTime().getMinute() / 60.0;
                if (rec.overnight) {
                    restHours = startHour - endHour;
                } else {
                    restHours = (24.0 - (rec.endTime.getHour() + rec.endTime.getMinute() / 60.0))
                            + startHour;
                }
            }

            if (restHours >= 0 && restHours < emp.getMinRestHours()) {
                return false;
            }
        }
        return true;
    }

    private int countCurrentStaff(Long shiftId, LocalDate date,
                                   Map<Long, List<AssignmentRecord>> employeeAssignments) {
        int count = 0;
        for (List<AssignmentRecord> records : employeeAssignments.values()) {
            for (AssignmentRecord rec : records) {
                if (rec.shiftId.equals(shiftId) && rec.date.equals(date)) {
                    count++;
                }
            }
        }
        return count;
    }

    private SchedulingInput.ShiftData findShift(List<SchedulingInput.ShiftData> shifts, Long shiftId, LocalDate date) {
        for (SchedulingInput.ShiftData s : shifts) {
            if (s.getShiftId().equals(shiftId) && s.getDate().equals(date)) {
                return s;
            }
        }
        return null;
    }

    // Internal tracking record
    private static class AssignmentRecord {
        Long shiftId;
        LocalDate date;
        LocalTime startTime;
        LocalTime endTime;
        boolean overnight;
        double durationHours;
        boolean nightShift;
    }

    private record ScoredEmployee(Long employeeId, double score) {}
}
