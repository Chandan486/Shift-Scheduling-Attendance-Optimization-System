package com.shiftscheduling.employee.service;

import com.shiftscheduling.employee.dto.*;
import com.shiftscheduling.employee.entity.*;
import com.shiftscheduling.employee.repository.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class EmployeeService {

    private final EmployeeRepository employeeRepo;
    private final DepartmentRepository deptRepo;
    private final SkillRepository skillRepo;
    private final AvailabilityRepository availRepo;

    public EmployeeService(EmployeeRepository employeeRepo, DepartmentRepository deptRepo,
                           SkillRepository skillRepo, AvailabilityRepository availRepo) {
        this.employeeRepo = employeeRepo;
        this.deptRepo = deptRepo;
        this.skillRepo = skillRepo;
        this.availRepo = availRepo;
    }

    @Transactional
    public EmployeeResponse createEmployee(EmployeeRequest req) {
        Employee emp = new Employee();
        emp.setUserId(req.userId());
        emp.setFullName(req.fullName());
        emp.setEmail(req.email());
        emp.setPhone(req.phone());
        emp.setPosition(req.position());
        if (req.maxHoursPerWeek() != null) emp.setMaxHoursPerWeek(req.maxHoursPerWeek());
        if (req.maxShiftsPerWeek() != null) emp.setMaxShiftsPerWeek(req.maxShiftsPerWeek());
        if (req.minRestHours() != null) emp.setMinRestHours(req.minRestHours());
        if (req.departmentId() != null) {
            emp.setDepartment(deptRepo.findById(req.departmentId())
                    .orElseThrow(() -> new RuntimeException("Department not found")));
        }
        if (req.skillIds() != null && !req.skillIds().isEmpty()) {
            emp.setSkills(new HashSet<>(skillRepo.findAllById(req.skillIds())));
        }
        return toResponse(employeeRepo.save(emp));
    }

    @Transactional
    public EmployeeResponse updateEmployee(Long id, EmployeeRequest req) {
        Employee emp = employeeRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Employee not found"));
        emp.setFullName(req.fullName());
        emp.setEmail(req.email());
        emp.setPhone(req.phone());
        emp.setPosition(req.position());
        if (req.maxHoursPerWeek() != null) emp.setMaxHoursPerWeek(req.maxHoursPerWeek());
        if (req.maxShiftsPerWeek() != null) emp.setMaxShiftsPerWeek(req.maxShiftsPerWeek());
        if (req.minRestHours() != null) emp.setMinRestHours(req.minRestHours());
        if (req.departmentId() != null) {
            emp.setDepartment(deptRepo.findById(req.departmentId())
                    .orElseThrow(() -> new RuntimeException("Department not found")));
        }
        if (req.skillIds() != null) {
            emp.setSkills(new HashSet<>(skillRepo.findAllById(req.skillIds())));
        }
        return toResponse(employeeRepo.save(emp));
    }

    public EmployeeResponse getEmployee(Long id) {
        return toResponse(employeeRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Employee not found")));
    }

    public EmployeeResponse getByUserId(Long userId) {
        return toResponse(employeeRepo.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("Employee not found for user")));
    }

    public Page<EmployeeResponse> getAllEmployees(Pageable pageable) {
        return employeeRepo.findByActiveTrue(pageable).map(this::toResponse);
    }

    public Page<EmployeeResponse> searchEmployees(String search, Pageable pageable) {
        return employeeRepo.searchByName(search, pageable).map(this::toResponse);
    }

    public List<EmployeeResponse> getByDepartment(Long deptId) {
        return employeeRepo.findByDepartmentIdAndActiveTrue(deptId).stream().map(this::toResponse).toList();
    }

    public List<EmployeeResponse> getBySkill(Long skillId) {
        return employeeRepo.findBySkillId(skillId).stream().map(this::toResponse).toList();
    }

    public List<EmployeeResponse> getAllActiveEmployees() {
        return employeeRepo.findByActiveTrue().stream().map(this::toResponse).toList();
    }

    @Transactional
    public void deactivateEmployee(Long id) {
        Employee emp = employeeRepo.findById(id).orElseThrow(() -> new RuntimeException("Employee not found"));
        emp.setActive(false);
        employeeRepo.save(emp);
    }

    // Department CRUD
    public DepartmentResponse createDepartment(DepartmentRequest req) {
        if (deptRepo.existsByName(req.name())) throw new RuntimeException("Department name already exists");
        Department d = new Department();
        d.setName(req.name());
        d.setDescription(req.description());
        d = deptRepo.save(d);
        return new DepartmentResponse(d.getId(), d.getName(), d.getDescription(), 0);
    }

    public List<DepartmentResponse> getAllDepartments() {
        return deptRepo.findAll().stream()
                .map(d -> new DepartmentResponse(d.getId(), d.getName(), d.getDescription(), d.getEmployees().size()))
                .toList();
    }

    public DepartmentResponse updateDepartment(Long id, DepartmentRequest req) {
        Department d = deptRepo.findById(id).orElseThrow(() -> new RuntimeException("Department not found"));
        d.setName(req.name());
        d.setDescription(req.description());
        d = deptRepo.save(d);
        return new DepartmentResponse(d.getId(), d.getName(), d.getDescription(), d.getEmployees().size());
    }

    public void deleteDepartment(Long id) { deptRepo.deleteById(id); }

    // Skill CRUD
    public SkillResponse createSkill(SkillRequest req) {
        if (skillRepo.existsByName(req.name())) throw new RuntimeException("Skill name already exists");
        Skill s = new Skill();
        s.setName(req.name());
        s.setDescription(req.description());
        s = skillRepo.save(s);
        return new SkillResponse(s.getId(), s.getName(), s.getDescription());
    }

    public List<SkillResponse> getAllSkills() {
        return skillRepo.findAll().stream().map(s -> new SkillResponse(s.getId(), s.getName(), s.getDescription())).toList();
    }

    public SkillResponse updateSkill(Long id, SkillRequest req) {
        Skill s = skillRepo.findById(id).orElseThrow(() -> new RuntimeException("Skill not found"));
        s.setName(req.name());
        s.setDescription(req.description());
        s = skillRepo.save(s);
        return new SkillResponse(s.getId(), s.getName(), s.getDescription());
    }

    public void deleteSkill(Long id) { skillRepo.deleteById(id); }

    // Availability
    @Transactional
    public List<AvailabilityResponse> setAvailability(Long employeeId, List<AvailabilityRequest> reqs) {
        Employee emp = employeeRepo.findById(employeeId).orElseThrow(() -> new RuntimeException("Employee not found"));
        availRepo.deleteByEmployeeId(employeeId);
        List<Availability> avails = reqs.stream().map(r -> {
            Availability a = new Availability();
            a.setEmployee(emp);
            a.setDayOfWeek(r.dayOfWeek());
            a.setStartTime(r.startTime());
            a.setEndTime(r.endTime());
            a.setAvailable(r.available());
            a.setPreference(r.preference());
            return a;
        }).toList();
        return availRepo.saveAll(avails).stream().map(this::toAvailResponse).toList();
    }

    public List<AvailabilityResponse> getAvailability(Long employeeId) {
        return availRepo.findByEmployeeId(employeeId).stream().map(this::toAvailResponse).toList();
    }

    private EmployeeResponse toResponse(Employee emp) {
        Set<SkillResponse> skills = emp.getSkills().stream()
                .map(s -> new SkillResponse(s.getId(), s.getName(), s.getDescription()))
                .collect(Collectors.toSet());
        return new EmployeeResponse(emp.getId(), emp.getUserId(), emp.getFullName(), emp.getEmail(),
                emp.getPhone(), emp.getDepartment() != null ? emp.getDepartment().getId() : null,
                emp.getDepartment() != null ? emp.getDepartment().getName() : null,
                emp.getPosition(), emp.getMaxHoursPerWeek(), emp.getMaxShiftsPerWeek(),
                emp.getMinRestHours(), skills, emp.isActive(), emp.getCreatedAt());
    }

    private AvailabilityResponse toAvailResponse(Availability a) {
        return new AvailabilityResponse(a.getId(), a.getEmployee().getId(), a.getDayOfWeek(),
                a.getStartTime(), a.getEndTime(), a.isAvailable(), a.getPreference());
    }
}
