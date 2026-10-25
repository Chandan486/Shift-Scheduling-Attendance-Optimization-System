package com.shiftscheduling.leave.controller;

import com.shiftscheduling.leave.dto.*;
import com.shiftscheduling.leave.exception.ApiException;
import com.shiftscheduling.leave.security.RequestUser;
import com.shiftscheduling.leave.service.LeaveService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/leaves")
public class LeaveController {

    private final LeaveService leaveService;

    public LeaveController(LeaveService leaveService) {
        this.leaveService = leaveService;
    }

    @PostMapping
    public ResponseEntity<LeaveResponse> applyLeave(
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-User-Role") String role,
            @RequestHeader(value = "X-User-Name", required = false) String encodedName,
            @Valid @RequestBody LeaveRequestDto dto) {
        return ResponseEntity.ok(leaveService.applyLeave(dto, caller(userId, role, encodedName)));
    }

    /** Body: { "status": "APPROVED" | "REJECTED", "reviewNotes": "..." } (reason required when rejecting). */
    @PutMapping("/{id}/review")
    public ResponseEntity<LeaveResponse> reviewLeave(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-User-Role") String role,
            @RequestHeader(value = "X-User-Name", required = false) String encodedName,
            @Valid @RequestBody LeaveApprovalRequest approval) {
        return ResponseEntity.ok(leaveService.decide(id, approval, caller(userId, role, encodedName)));
    }

    /** Requests the caller is allowed to decide right now. Empty for EMPLOYEE callers. */
    @GetMapping("/pending")
    public ResponseEntity<List<LeaveResponse>> getPendingLeaves(
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-User-Role") String role,
            @RequestHeader(value = "X-User-Name", required = false) String encodedName) {
        return ResponseEntity.ok(leaveService.getPendingLeavesFor(caller(userId, role, encodedName)));
    }

    @GetMapping("/employee/{employeeId}")
    public ResponseEntity<Page<LeaveResponse>> getLeavesByEmployee(
            @PathVariable Long employeeId,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-User-Role") String role,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        RequestUser caller = caller(userId, role, null);
        assertSelfOrAdminHr(caller, employeeId);
        return ResponseEntity.ok(leaveService.getLeavesByEmployee(
                employeeId, PageRequest.of(page, Math.min(size, 100), Sort.by("createdAt").descending())));
    }

    /** Used internally by attendance-service (no caller headers); not exposed to end users. */
    @GetMapping("/approved")
    public ResponseEntity<List<LeaveResponse>> getApprovedLeaves(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(leaveService.getApprovedLeaves(startDate, endDate));
    }

    @GetMapping("/balance/{employeeId}")
    public ResponseEntity<LeaveBalanceResponse> getLeaveBalance(
            @PathVariable Long employeeId,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-User-Role") String role,
            @RequestParam(required = false) Integer year) {
        RequestUser caller = caller(userId, role, null);
        assertSelfOrAdminHr(caller, employeeId);
        int targetYear = year != null ? year : LocalDate.now().getYear();
        return ResponseEntity.ok(leaveService.getLeaveBalance(employeeId, targetYear));
    }

    @GetMapping("/check")
    public ResponseEntity<Map<String, Boolean>> hasApprovedLeave(
            @RequestParam Long employeeId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        boolean hasLeave = leaveService.hasApprovedLeave(employeeId, date);
        return ResponseEntity.ok(Map.of("hasApprovedLeave", hasLeave));
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP", "service", "leave-service"));
    }

    // ---------- helpers ----------

    private static RequestUser caller(Long userId, String role, String encodedName) {
        String name = null;
        if (encodedName != null && !encodedName.isBlank()) {
            name = URLDecoder.decode(encodedName, StandardCharsets.UTF_8);
        }
        return new RequestUser(userId, role == null ? null : role.toUpperCase(), name);
    }

    private static void assertSelfOrAdminHr(RequestUser caller, Long employeeId) {
        if (!caller.userId().equals(employeeId) && !caller.isAdminOrHr()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "You can only view your own leave records");
        }
    }
}
