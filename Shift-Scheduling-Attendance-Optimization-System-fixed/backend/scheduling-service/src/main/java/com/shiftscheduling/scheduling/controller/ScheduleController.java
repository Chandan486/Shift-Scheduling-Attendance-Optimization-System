package com.shiftscheduling.scheduling.controller;

import com.shiftscheduling.scheduling.dto.*;
import com.shiftscheduling.scheduling.engine.SchedulingInput;
import com.shiftscheduling.scheduling.service.SchedulingService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/schedules")
public class ScheduleController {

    private final SchedulingService service;

    public ScheduleController(SchedulingService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<ScheduleResponse> create(@Valid @RequestBody ScheduleRequest req) {
        return ResponseEntity.ok(service.createSchedule(req));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ScheduleResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(service.getSchedule(id));
    }

    @GetMapping
    public ResponseEntity<List<ScheduleResponse>> getAll(@RequestParam(required = false) String status) {
        if (status != null && !status.isBlank()) {
            return ResponseEntity.ok(service.getSchedulesByStatus(status));
        }
        return ResponseEntity.ok(service.getAllSchedules());
    }

    @PostMapping("/generate")
    public ResponseEntity<ScheduleGenerationResult> generate(
            @Valid @RequestBody ScheduleGenerationRequest req,
            @RequestBody(required = false) Map<String, Object> body) {
        // In a full microservice setup, employees and leaves would be fetched via Feign clients
        // from employee-service and leave-service. For now, accept them as part of the request
        // or use empty lists as placeholders for inter-service communication.
        List<SchedulingInput.EmployeeData> employees = List.of();
        List<SchedulingInput.LeaveData> leaves = List.of();
        return ResponseEntity.ok(service.generateSchedule(req, employees, leaves));
    }

    @PostMapping("/generate-with-data")
    public ResponseEntity<ScheduleGenerationResult> generateWithData(
            @Valid @RequestBody GenerateWithDataRequest req) {
        return ResponseEntity.ok(service.generateSchedule(req.schedule(), req.employees(), req.leaves()));
    }

    @PostMapping("/{id}/publish")
    public ResponseEntity<ScheduleResponse> publish(@PathVariable Long id,
                                                     @RequestHeader(value = "X-User-Role", defaultValue = "") String userRole) {
        return ResponseEntity.ok(service.publishSchedule(id, userRole));
    }

    @GetMapping("/{id}/validate")
    public ResponseEntity<List<String>> validate(@PathVariable Long id) {
        return ResponseEntity.ok(service.validateSchedule(id));
    }

    @GetMapping("/assignments/by-date")
    public ResponseEntity<List<AssignmentResponse>> getPublishedAssignmentsByDate(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(service.getPublishedAssignmentsForDate(date));
    }

    @GetMapping("/{id}/assignments")
    public ResponseEntity<List<AssignmentResponse>> getAssignments(@PathVariable Long id) {
        return ResponseEntity.ok(service.getAssignmentsForSchedule(id));
    }

    @GetMapping("/my-schedule")
    public ResponseEntity<List<AssignmentResponse>> getMySchedule(
            @RequestHeader(value = "X-Employee-Id", defaultValue = "0") Long employeeId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(service.getEmployeeSchedule(employeeId, startDate, endDate));
    }

    @GetMapping("/employee/{employeeId}")
    public ResponseEntity<List<AssignmentResponse>> getEmployeeSchedule(
            @PathVariable Long employeeId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(service.getEmployeeSchedule(employeeId, startDate, endDate));
    }
}
