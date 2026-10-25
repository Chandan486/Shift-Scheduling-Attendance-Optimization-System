package com.shiftscheduling.scheduling.service;

import com.shiftscheduling.scheduling.dto.*;
import com.shiftscheduling.scheduling.engine.*;
import com.shiftscheduling.scheduling.entity.*;
import com.shiftscheduling.scheduling.repository.*;
import com.shiftscheduling.scheduling.exception.ApiException;
import com.shiftscheduling.scheduling.security.RequestUser;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class SchedulingService {

    private final ShiftRepository shiftRepo;
    private final ShiftRequirementRepository shiftReqRepo;
    private final ScheduleRepository scheduleRepo;
    private final ScheduleAssignmentRepository assignmentRepo;
    private final GreedySchedulingEngine schedulingEngine;
    private final ShiftAssignmentService shiftAssignments;

    public SchedulingService(ShiftRepository shiftRepo,
                             ShiftRequirementRepository shiftReqRepo,
                             ScheduleRepository scheduleRepo,
                             ScheduleAssignmentRepository assignmentRepo,
                             GreedySchedulingEngine schedulingEngine,
                             ShiftAssignmentService shiftAssignments) {
        this.shiftRepo = shiftRepo;
        this.shiftReqRepo = shiftReqRepo;
        this.scheduleRepo = scheduleRepo;
        this.assignmentRepo = assignmentRepo;
        this.schedulingEngine = schedulingEngine;
        this.shiftAssignments = shiftAssignments;
    }

    // ========== Shift CRUD ==========

    @Transactional
    public ShiftResponse createShift(ShiftRequest req, RequestUser actor) {
        validateShift(req, true);

        Shift shift = new Shift();
        shift.setName(req.name());
        shift.setDepartmentId(req.departmentId());
        shift.setStartTime(req.startTime());
        shift.setEndTime(req.endTime());
        shift.setDate(req.date());
        shift.setOvernight(req.overnight());
        shift.setMaxStaff(req.maxStaff());
        shift.setMinStaff(req.minStaff());
        shift.setNightShift(req.nightShift());

        shift = shiftRepo.save(shift);

        if (req.requirements() != null) {
            for (ShiftRequirementRequest rr : req.requirements()) {
                ShiftRequirement sr = new ShiftRequirement();
                sr.setShift(shift);
                sr.setSkillId(rr.skillId());
                sr.setRequiredCount(rr.requiredCount());
                shift.getRequirements().add(sr);
            }
            shift = shiftRepo.save(shift);
        }

        // Employees chosen on the form are assigned in the same transaction; notifications queue with them.
        shiftAssignments.assignNew(shift, req.employeeIds(), actor);

        return toShiftResponse(shift);
    }

    @Transactional
    public ShiftResponse updateShift(Long id, ShiftRequest req, RequestUser actor) {
        validateShift(req, false);

        Shift shift = shiftRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Shift not found"));
        shift.setName(req.name());
        shift.setDepartmentId(req.departmentId());
        shift.setStartTime(req.startTime());
        shift.setEndTime(req.endTime());
        shift.setDate(req.date());
        shift.setOvernight(req.overnight());
        shift.setMaxStaff(req.maxStaff());
        shift.setMinStaff(req.minStaff());
        shift.setNightShift(req.nightShift());

        shift.getRequirements().clear();
        if (req.requirements() != null) {
            for (ShiftRequirementRequest rr : req.requirements()) {
                ShiftRequirement sr = new ShiftRequirement();
                sr.setShift(shift);
                sr.setSkillId(rr.skillId());
                sr.setRequiredCount(rr.requiredCount());
                shift.getRequirements().add(sr);
            }
        }

        Shift saved = shiftRepo.save(shift);
        if (req.employeeIds() != null) {
            shiftAssignments.sync(saved, req.employeeIds(), actor);
        } else {
            // No new list: the current assignments must still fit the edited date, times and department.
            shiftAssignments.revalidateExisting(saved);
        }
        return toShiftResponse(saved);
    }

    /** Assignments of one shift (HR/ADMIN views). */
    public List<ShiftAssignmentResponse> getShiftAssignments(Long shiftId) {
        if (!shiftRepo.existsById(shiftId)) {
            throw new RuntimeException("Shift not found");
        }
        return shiftAssignments.forShift(shiftId);
    }

    /** Shifts assigned to one user between two dates (the employee's own schedule). */
    public List<ShiftAssignmentResponse> getAssignedShiftsForUser(Long userId, LocalDate start, LocalDate end) {
        return shiftAssignments.forUser(userId, start, end);
    }

    private void validateShift(ShiftRequest req, boolean isNew) {
        if (req.startTime().equals(req.endTime())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Start time and end time cannot be the same");
        }
        if (!req.overnight() && !req.endTime().isAfter(req.startTime())) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "End time must be after start time. Tick 'Overnight Shift' if the shift runs past midnight");
        }
        if (req.maxStaff() < req.minStaff()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Max staff cannot be less than min staff");
        }
        if (isNew && req.date().isBefore(LocalDate.now())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Shift date cannot be in the past");
        }
    }

    public ShiftResponse getShift(Long id) {
        return toShiftResponse(shiftRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Shift not found")));
    }

    public List<ShiftResponse> getShiftsByDateRange(LocalDate startDate, LocalDate endDate) {
        return shiftRepo.findShiftsInRange(startDate, endDate).stream()
                .map(this::toShiftResponse).toList();
    }

    public List<ShiftResponse> getShiftsByDepartment(Long departmentId) {
        return shiftRepo.findByDepartmentId(departmentId).stream()
                .map(this::toShiftResponse).toList();
    }

    public List<ShiftResponse> getShiftsByDepartmentAndDateRange(Long departmentId, LocalDate start, LocalDate end) {
        return shiftRepo.findByDepartmentIdAndDateBetween(departmentId, start, end).stream()
                .map(this::toShiftResponse).toList();
    }

    public List<ShiftResponse> getAllShifts() {
        return shiftRepo.findAll().stream().map(this::toShiftResponse).toList();
    }

    @Transactional
    public void deleteShift(Long id) {
        if (!shiftRepo.existsById(id)) {
            throw new RuntimeException("Shift not found");
        }
        shiftRepo.deleteById(id);
    }

    // ========== Schedule CRUD ==========

    @Transactional
    public ScheduleResponse createSchedule(ScheduleRequest req) {
        if (req.endDate().isBefore(req.startDate())) {
            throw new RuntimeException("End date must be after start date");
        }
        Schedule schedule = new Schedule();
        schedule.setName(req.name());
        schedule.setStartDate(req.startDate());
        schedule.setEndDate(req.endDate());
        schedule.setCreatedBy(req.createdBy());
        schedule.setNotes(req.notes());
        schedule.setStatus(ScheduleStatus.DRAFT);
        return toScheduleResponse(scheduleRepo.save(schedule));
    }

    public ScheduleResponse getSchedule(Long id) {
        return toScheduleResponse(scheduleRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Schedule not found")));
    }

    public List<ScheduleResponse> getAllSchedules() {
        return scheduleRepo.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toScheduleResponse).toList();
    }

    public List<ScheduleResponse> getSchedulesByStatus(String status) {
        ScheduleStatus s = ScheduleStatus.valueOf(status.toUpperCase());
        return scheduleRepo.findByStatus(s).stream()
                .map(this::toScheduleResponse).toList();
    }

    // ========== Schedule Generation ==========

    @Transactional
    public ScheduleGenerationResult generateSchedule(ScheduleGenerationRequest req,
                                                      List<SchedulingInput.EmployeeData> employees,
                                                      List<SchedulingInput.LeaveData> approvedLeaves) {
        // Create the schedule
        Schedule schedule = new Schedule();
        schedule.setName("Auto-generated " + req.startDate() + " to " + req.endDate());
        schedule.setStartDate(req.startDate());
        schedule.setEndDate(req.endDate());
        schedule.setCreatedBy(0L); // System-generated
        schedule.setStatus(ScheduleStatus.DRAFT);
        schedule = scheduleRepo.save(schedule);

        // Find shifts in range
        List<Shift> shifts;
        if (req.departmentId() != null) {
            shifts = shiftRepo.findByDepartmentIdAndDateBetween(req.departmentId(), req.startDate(), req.endDate());
        } else {
            shifts = shiftRepo.findShiftsInRange(req.startDate(), req.endDate());
        }

        if (shifts.isEmpty()) {
            return new ScheduleGenerationResult(schedule.getId(), 0, List.of(), List.of());
        }

        // Convert to engine input
        List<SchedulingInput.ShiftData> shiftDataList = shifts.stream()
                .map(this::toShiftData).toList();

        // Get existing assignments for the schedule's date range
        List<SchedulingInput.ExistingAssignment> existingAssignments = List.of();

        SchedulingInput input = new SchedulingInput(shiftDataList, employees, approvedLeaves, existingAssignments);

        // Run the engine
        SchedulingOutput output = schedulingEngine.generate(input);

        // Save assignments
        Map<Long, Shift> shiftMap = shifts.stream().collect(Collectors.toMap(Shift::getId, s -> s));
        List<ScheduleAssignment> savedAssignments = new ArrayList<>();

        for (SchedulingOutput.ProposedAssignment proposed : output.getAssignments()) {
            Shift shift = shiftMap.get(proposed.getShiftId());
            if (shift == null) continue;

            ScheduleAssignment assignment = new ScheduleAssignment();
            assignment.setSchedule(schedule);
            assignment.setShift(shift);
            assignment.setEmployeeId(proposed.getEmployeeId());
            assignment.setDate(proposed.getDate());
            assignment.setStatus(AssignmentStatus.ASSIGNED);
            savedAssignments.add(assignmentRepo.save(assignment));
        }

        // Build response
        List<AssignmentResponse> assignmentResponses = savedAssignments.stream()
                .map(this::toAssignmentResponse).toList();

        List<UnfilledPositionResponse> unfilledResponses = output.getUnfilledPositions().stream()
                .map(u -> new UnfilledPositionResponse(u.getShiftId(), u.getShiftName(), u.getDate(),
                        u.getRequiredCount(), u.getAssignedCount(), u.getReason()))
                .toList();

        return new ScheduleGenerationResult(schedule.getId(), assignmentResponses.size(),
                assignmentResponses, unfilledResponses);
    }

    // ========== Schedule Validation ==========

    public List<String> validateSchedule(Long scheduleId) {
        Schedule schedule = scheduleRepo.findById(scheduleId)
                .orElseThrow(() -> new RuntimeException("Schedule not found"));
        List<ScheduleAssignment> assignments = assignmentRepo.findByScheduleId(scheduleId);
        List<String> issues = new ArrayList<>();

        // Check min staffing per shift per date
        Map<String, List<ScheduleAssignment>> shiftDateGroups = assignments.stream()
                .filter(a -> a.getStatus() != AssignmentStatus.CANCELLED)
                .collect(Collectors.groupingBy(a -> a.getShift().getId() + "_" + a.getDate()));

        Set<Long> shiftIds = assignments.stream().map(a -> a.getShift().getId()).collect(Collectors.toSet());
        Map<Long, Shift> shiftMap = shiftRepo.findAllById(shiftIds).stream()
                .collect(Collectors.toMap(Shift::getId, s -> s));

        for (Map.Entry<String, List<ScheduleAssignment>> entry : shiftDateGroups.entrySet()) {
            ScheduleAssignment first = entry.getValue().get(0);
            Shift shift = shiftMap.get(first.getShift().getId());
            if (shift != null && entry.getValue().size() < shift.getMinStaff()) {
                issues.add("Shift '" + shift.getName() + "' on " + first.getDate()
                        + " has " + entry.getValue().size() + " staff but needs " + shift.getMinStaff());
            }
        }

        // Check for double-booked employees
        Map<String, List<ScheduleAssignment>> empDateGroups = assignments.stream()
                .filter(a -> a.getStatus() != AssignmentStatus.CANCELLED)
                .collect(Collectors.groupingBy(a -> a.getEmployeeId() + "_" + a.getDate()));

        for (Map.Entry<String, List<ScheduleAssignment>> entry : empDateGroups.entrySet()) {
            List<ScheduleAssignment> dayAssignments = entry.getValue();
            for (int i = 0; i < dayAssignments.size(); i++) {
                for (int j = i + 1; j < dayAssignments.size(); j++) {
                    Shift s1 = dayAssignments.get(i).getShift();
                    Shift s2 = dayAssignments.get(j).getShift();
                    if (shiftsOverlap(s1, s2)) {
                        issues.add("Employee " + dayAssignments.get(i).getEmployeeId()
                                + " has overlapping shifts on " + dayAssignments.get(i).getDate()
                                + ": '" + s1.getName() + "' and '" + s2.getName() + "'");
                    }
                }
            }
        }

        return issues;
    }

    // ========== Publish Schedule ==========

    @Transactional
    public ScheduleResponse publishSchedule(Long scheduleId, String userRole) {
        if (!"ADMIN".equalsIgnoreCase(userRole) && !"MANAGER".equalsIgnoreCase(userRole)) {
            throw new RuntimeException("Only ADMIN or MANAGER can publish schedules");
        }

        Schedule schedule = scheduleRepo.findById(scheduleId)
                .orElseThrow(() -> new RuntimeException("Schedule not found"));

        if (schedule.getStatus() != ScheduleStatus.DRAFT) {
            throw new RuntimeException("Only DRAFT schedules can be published. Current status: " + schedule.getStatus());
        }

        schedule.setStatus(ScheduleStatus.PUBLISHED);
        schedule.setPublishedAt(LocalDateTime.now());
        return toScheduleResponse(scheduleRepo.save(schedule));
    }

    // ========== Employee Schedule ==========

    public List<AssignmentResponse> getEmployeeSchedule(Long employeeId, LocalDate startDate, LocalDate endDate) {
        return assignmentRepo.findByEmployeeIdAndDateBetween(employeeId, startDate, endDate).stream()
                .map(this::toAssignmentResponse).toList();
    }

    /** Published, non-cancelled assignments for one date (used by the attendance overview report). */
    @Transactional(readOnly = true)
    public List<AssignmentResponse> getPublishedAssignmentsForDate(LocalDate date) {
        return assignmentRepo.findPublishedActiveByDate(date).stream()
                .map(this::toAssignmentResponse).toList();
    }

    public List<AssignmentResponse> getAssignmentsForSchedule(Long scheduleId) {
        return assignmentRepo.findByScheduleId(scheduleId).stream()
                .map(this::toAssignmentResponse).toList();
    }

    // ========== Helpers ==========

    private boolean shiftsOverlap(Shift s1, Shift s2) {
        if (!s1.isOvernight() && !s2.isOvernight()) {
            return s1.getStartTime().isBefore(s2.getEndTime()) && s2.getStartTime().isBefore(s1.getEndTime());
        }
        return true; // Overnight shifts on same day always overlap
    }

    private SchedulingInput.ShiftData toShiftData(Shift shift) {
        SchedulingInput.ShiftData data = new SchedulingInput.ShiftData();
        data.setShiftId(shift.getId());
        data.setName(shift.getName());
        data.setDepartmentId(shift.getDepartmentId());
        data.setDate(shift.getDate());
        data.setStartTime(shift.getStartTime());
        data.setEndTime(shift.getEndTime());
        data.setOvernight(shift.isOvernight());
        data.setNightShift(shift.isNightShift());
        data.setMinStaff(shift.getMinStaff());
        data.setMaxStaff(shift.getMaxStaff());
        if (shift.getRequirements() != null) {
            data.setRequiredSkills(shift.getRequirements().stream()
                    .map(r -> new SchedulingInput.SkillRequirement(r.getSkillId(), r.getRequiredCount()))
                    .toList());
        }
        return data;
    }

    private ShiftResponse toShiftResponse(Shift shift) {
        List<ShiftRequirementResponse> reqs = shift.getRequirements().stream()
                .map(r -> new ShiftRequirementResponse(r.getId(), r.getSkillId(), r.getRequiredCount()))
                .toList();
        return new ShiftResponse(shift.getId(), shift.getName(), shift.getDepartmentId(),
                shift.getStartTime(), shift.getEndTime(), shift.getDate(),
                shift.isOvernight(), shift.getMaxStaff(), shift.getMinStaff(),
                shift.isNightShift(), reqs, shift.getCreatedAt());
    }

    private ScheduleResponse toScheduleResponse(Schedule schedule) {
        int total = schedule.getAssignments() != null ? schedule.getAssignments().size() : 0;
        return new ScheduleResponse(schedule.getId(), schedule.getName(),
                schedule.getStartDate(), schedule.getEndDate(),
                schedule.getStatus().name(), schedule.getCreatedBy(),
                schedule.getNotes(), total, schedule.getCreatedAt(),
                schedule.getUpdatedAt(), schedule.getPublishedAt());
    }

    private AssignmentResponse toAssignmentResponse(ScheduleAssignment a) {
        Shift shift = a.getShift();
        return new AssignmentResponse(a.getId(), a.getSchedule().getId(),
                shift.getId(), shift.getName(), shift.getStartTime(), shift.getEndTime(),
                a.getEmployeeId(), a.getDate(), a.getStatus().name(),
                a.getNotes(), a.getAssignedAt());
    }
}
