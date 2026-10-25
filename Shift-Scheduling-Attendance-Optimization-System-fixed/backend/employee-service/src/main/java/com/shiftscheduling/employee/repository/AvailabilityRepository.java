package com.shiftscheduling.employee.repository;
import com.shiftscheduling.employee.entity.Availability;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.DayOfWeek;
import java.util.List;
import java.util.Optional;
public interface AvailabilityRepository extends JpaRepository<Availability, Long> {
    List<Availability> findByEmployeeId(Long employeeId);
    Optional<Availability> findByEmployeeIdAndDayOfWeek(Long employeeId, DayOfWeek dayOfWeek);
    void deleteByEmployeeId(Long employeeId);
}
