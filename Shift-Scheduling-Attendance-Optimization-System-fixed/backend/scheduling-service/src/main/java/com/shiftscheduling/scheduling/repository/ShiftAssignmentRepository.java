package com.shiftscheduling.scheduling.repository;

import com.shiftscheduling.scheduling.entity.ShiftAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface ShiftAssignmentRepository extends JpaRepository<ShiftAssignment, Long> {

    List<ShiftAssignment> findByShift_IdOrderByEmployeeNameAsc(Long shiftId);

    /** Other assignments of one employee on one date (used for double-booking checks). */
    List<ShiftAssignment> findByEmployeeIdAndShift_Date(Long employeeId, LocalDate date);

    List<ShiftAssignment> findByUserIdAndShift_DateBetweenOrderByShift_DateAscShift_StartTimeAsc(
            Long userId, LocalDate start, LocalDate end);
}
