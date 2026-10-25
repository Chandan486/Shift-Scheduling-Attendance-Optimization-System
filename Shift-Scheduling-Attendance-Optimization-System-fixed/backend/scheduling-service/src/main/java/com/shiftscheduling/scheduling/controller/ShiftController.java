package com.shiftscheduling.scheduling.controller;

import com.shiftscheduling.scheduling.dto.ShiftAssignmentResponse;
import com.shiftscheduling.scheduling.dto.ShiftRequest;
import com.shiftscheduling.scheduling.dto.ShiftResponse;
import com.shiftscheduling.scheduling.exception.ApiException;
import com.shiftscheduling.scheduling.security.RequestUser;
import com.shiftscheduling.scheduling.service.SchedulingService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/shifts")
public class ShiftController {

    private final SchedulingService service;

    public ShiftController(SchedulingService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<ShiftResponse> create(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-User-Name", required = false) String encodedName,
            @Valid @RequestBody ShiftRequest req) {
        RequestUser actor = RequestUser.of(userId, role, encodedName);
        actor.requireAdminOrHr();
        return ResponseEntity.ok(service.createShift(req, actor));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ShiftResponse> update(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestHeader(value = "X-User-Name", required = false) String encodedName,
            @Valid @RequestBody ShiftRequest req) {
        RequestUser actor = RequestUser.of(userId, role, encodedName);
        actor.requireAdminOrHr();
        return ResponseEntity.ok(service.updateShift(id, req, actor));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ShiftResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(service.getShift(id));
    }

    @GetMapping
    public ResponseEntity<List<ShiftResponse>> getAll(
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        if (departmentId != null && startDate != null && endDate != null) {
            return ResponseEntity.ok(service.getShiftsByDepartmentAndDateRange(departmentId, startDate, endDate));
        }
        if (departmentId != null) {
            return ResponseEntity.ok(service.getShiftsByDepartment(departmentId));
        }
        if (startDate != null && endDate != null) {
            return ResponseEntity.ok(service.getShiftsByDateRange(startDate, endDate));
        }
        return ResponseEntity.ok(service.getAllShifts());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        RequestUser.of(userId, role, null).requireAdminOrHr();
        service.deleteShift(id);
        return ResponseEntity.noContent().build();
    }

    /** Employees assigned to this shift (HR and ADMIN only). */
    @GetMapping("/{id}/assignments")
    public ResponseEntity<List<ShiftAssignmentResponse>> assignments(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        RequestUser.of(userId, role, null).requireAdminOrHr();
        return ResponseEntity.ok(service.getShiftAssignments(id));
    }

    /** The logged-in user's own assigned shifts between two dates. */
    @GetMapping("/assignments/my")
    public ResponseEntity<List<ShiftAssignmentResponse>> myAssignments(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        if (userId == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Sign in to view your shifts");
        }
        if (endDate.isBefore(startDate)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "End date cannot be before start date");
        }
        return ResponseEntity.ok(service.getAssignedShiftsForUser(userId, startDate, endDate));
    }
}
