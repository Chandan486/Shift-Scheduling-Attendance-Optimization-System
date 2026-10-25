package com.shiftscheduling.attendance.controller;

import com.shiftscheduling.attendance.dto.*;
import com.shiftscheduling.attendance.overview.ForbiddenException;
import com.shiftscheduling.attendance.service.AttendanceService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/attendance")
public class AttendanceController {

    private final AttendanceService service;

    public AttendanceController(AttendanceService service) {
        this.service = service;
    }

    @PostMapping("/check-in")
    public ResponseEntity<AttendanceResponse> checkIn(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @Valid @RequestBody CheckInRequest req) {
        assertSelfOrManager(userId, role, req.employeeId());
        return ResponseEntity.ok(service.checkIn(req));
    }

    @PostMapping("/check-out")
    public ResponseEntity<AttendanceResponse> checkOut(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @Valid @RequestBody CheckOutRequest req) {
        assertSelfOrManager(userId, role, req.employeeId());
        return ResponseEntity.ok(service.checkOut(req));
    }

    @GetMapping("/history")
    public ResponseEntity<Page<AttendanceResponse>> getHistory(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestParam Long employeeId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        assertSelfOrManager(userId, role, employeeId);
        PageRequest pr = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "date"));
        return ResponseEntity.ok(service.getAttendanceHistory(employeeId, startDate, endDate, pr));
    }

    @GetMapping("/date/{date}")
    public ResponseEntity<List<AttendanceResponse>> getByDate(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(service.getAttendanceByDate(date));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AttendanceResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(service.getAttendanceById(id));
    }

    @PostMapping("/{id}/correct")
    public ResponseEntity<CorrectionResponse> correct(
            @PathVariable Long id, @Valid @RequestBody CorrectionRequest req) {
        return ResponseEntity.ok(service.correctAttendance(id, req));
    }

    @GetMapping("/export/csv")
    public ResponseEntity<AttendanceReportResponse> exportCsv(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(service.exportCsv(startDate, endDate));
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP", "service", "attendance-service"));
    }

    /**
     * Employees may act only on their own attendance (the frontend sends the logged-in user id as
     * employeeId). HR and ADMIN may act on anyone. Fails closed when the identity headers are missing.
     */
    private static void assertSelfOrManager(Long userId, String role, Long employeeId) {
        String r = role == null ? "" : role.toUpperCase();
        if ("ADMIN".equals(r) || "HR".equals(r)) {
            return;
        }
        if (userId == null || employeeId == null || !userId.equals(employeeId)) {
            throw new ForbiddenException("You can only record or view your own attendance");
        }
    }
}
