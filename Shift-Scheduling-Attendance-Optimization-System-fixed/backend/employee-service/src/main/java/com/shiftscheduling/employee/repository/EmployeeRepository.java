package com.shiftscheduling.employee.repository;
import com.shiftscheduling.employee.entity.Employee;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;
public interface EmployeeRepository extends JpaRepository<Employee, Long> {
    Optional<Employee> findByUserId(Long userId);
    Optional<Employee> findByEmail(String email);
    Page<Employee> findByActiveTrue(Pageable pageable);
    List<Employee> findByDepartmentIdAndActiveTrue(Long departmentId);
    @Query("SELECT e FROM Employee e WHERE e.active = true AND LOWER(e.fullName) LIKE LOWER(CONCAT('%', :search, '%'))")
    Page<Employee> searchByName(String search, Pageable pageable);
    @Query("SELECT e FROM Employee e JOIN e.skills s WHERE s.id = :skillId AND e.active = true")
    List<Employee> findBySkillId(Long skillId);
    List<Employee> findByActiveTrue();
}
