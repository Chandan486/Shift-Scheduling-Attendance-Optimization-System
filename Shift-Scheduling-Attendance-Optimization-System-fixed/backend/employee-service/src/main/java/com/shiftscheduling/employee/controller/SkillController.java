package com.shiftscheduling.employee.controller;

import com.shiftscheduling.employee.dto.*;
import com.shiftscheduling.employee.service.EmployeeService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/skills")
public class SkillController {
    private final EmployeeService service;
    public SkillController(EmployeeService service) { this.service = service; }

    @PostMapping
    public ResponseEntity<SkillResponse> create(@Valid @RequestBody SkillRequest req) {
        return ResponseEntity.ok(service.createSkill(req));
    }
    @GetMapping
    public ResponseEntity<List<SkillResponse>> getAll() { return ResponseEntity.ok(service.getAllSkills()); }
    @PutMapping("/{id}")
    public ResponseEntity<SkillResponse> update(@PathVariable Long id, @Valid @RequestBody SkillRequest req) {
        return ResponseEntity.ok(service.updateSkill(id, req));
    }
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.deleteSkill(id); return ResponseEntity.noContent().build();
    }
}
