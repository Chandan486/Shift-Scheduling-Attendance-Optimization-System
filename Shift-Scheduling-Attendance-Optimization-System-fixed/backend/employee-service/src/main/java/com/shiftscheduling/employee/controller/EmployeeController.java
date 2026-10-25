package com.shiftscheduling.employee.controller;

import com.shiftscheduling.employee.dto.*;
import com.shiftscheduling.employee.service.EmployeeService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/employees")
public class EmployeeController {

    private final EmployeeService service;

    public EmployeeController(EmployeeService service) { this.service = service; }

    @PostMapping
    public ResponseEntity<EmployeeResponse> create(@Valid @RequestBody EmployeeRequest req) {
        return ResponseEntity.ok(service.createEmployee(req));
    }

    @PutMapping("/{id}")
    public ResponseEntity<EmployeeResponse> update(@PathVariable Long id, @Valid @RequestBody EmployeeRequest req) {
        return ResponseEntity.ok(service.updateEmployee(id, req));
    }

    @GetMapping("/{id}")
    public ResponseEntity<EmployeeResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(service.getEmployee(id));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<EmployeeResponse> getByUser(@PathVariable Long userId) {
        return ResponseEntity.ok(service.getByUserId(userId));
    }

    @GetMapping
    public ResponseEntity<Page<EmployeeResponse>> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search) {
        PageRequest pr = PageRequest.of(page, size, Sort.by("fullName"));
        if (search != null && !search.isBlank()) {
            return ResponseEntity.ok(service.searchEmployees(search, pr));
        }
        return ResponseEntity.ok(service.getAllEmployees(pr));
    }

    @GetMapping("/all")
    public ResponseEntity<List<EmployeeResponse>> getAllActive() {
        return ResponseEntity.ok(service.getAllActiveEmployees());
    }

    @GetMapping("/department/{deptId}")
    public ResponseEntity<List<EmployeeResponse>> getByDept(@PathVariable Long deptId) {
        return ResponseEntity.ok(service.getByDepartment(deptId));
    }

    @GetMapping("/skill/{skillId}")
    public ResponseEntity<List<EmployeeResponse>> getBySkill(@PathVariable Long skillId) {
        return ResponseEntity.ok(service.getBySkill(skillId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivate(@PathVariable Long id) {
        service.deactivateEmployee(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/availability")
    public ResponseEntity<List<AvailabilityResponse>> setAvailability(
            @PathVariable Long id, @Valid @RequestBody List<AvailabilityRequest> reqs) {
        return ResponseEntity.ok(service.setAvailability(id, reqs));
    }

    @GetMapping("/{id}/availability")
    public ResponseEntity<List<AvailabilityResponse>> getAvailability(@PathVariable Long id) {
        return ResponseEntity.ok(service.getAvailability(id));
    }
}
