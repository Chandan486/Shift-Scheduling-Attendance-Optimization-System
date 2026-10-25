package com.shiftscheduling.attendance.overview;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * Admin/HR date-wise attendance overview.
 * The API gateway verifies the JWT and overwrites X-User-Role with the role from the token;
 * this controller re-checks that role as a second layer.
 */
@RestController
@RequestMapping("/api/attendance/overview")
public class AttendanceOverviewController {

    private final AttendanceOverviewService service;

    public AttendanceOverviewController(AttendanceOverviewService service) {
        this.service = service;
    }

    @GetMapping("/summary")
    public ResponseEntity<OverviewSummary> summary(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) String search) {
        requireAdminOrHr(role);
        return ResponseEntity.ok(service.summary(date, departmentId, search));
    }

    @GetMapping("/employees")
    public ResponseEntity<OverviewResponse> employees(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        requireAdminOrHr(role);
        return ResponseEntity.ok(service.overview(date, AttendanceOverviewService.parseStatus(status),
                departmentId, search, page, size));
    }

    @GetMapping(value = "/export", produces = "text/csv;charset=UTF-8")
    public ResponseEntity<String> export(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) String search) {
        requireAdminOrHr(role);
        OverviewStatus parsed = AttendanceOverviewService.parseStatus(status);
        List<OverviewRow> rows = service.export(date, parsed, departmentId, search);
        String suffix = parsed == null ? "all" : parsed.name().toLowerCase();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"attendance_" + date + "_" + suffix + ".csv\"")
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(OverviewCsvWriter.write(date, rows));
    }

    private static void requireAdminOrHr(String role) {
        if (role == null || !(role.equalsIgnoreCase("ADMIN") || role.equalsIgnoreCase("HR"))) {
            throw new ForbiddenException("Only ADMIN or HR users can view attendance reports");
        }
    }
}
