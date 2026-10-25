package com.shiftscheduling.scheduling.repository;

import com.shiftscheduling.scheduling.entity.Shift;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface ShiftRepository extends JpaRepository<Shift, Long> {

    List<Shift> findByDate(LocalDate date);

    List<Shift> findByDateBetween(LocalDate startDate, LocalDate endDate);

    List<Shift> findByDepartmentId(Long departmentId);

    List<Shift> findByDepartmentIdAndDateBetween(Long departmentId, LocalDate startDate, LocalDate endDate);

    @Query("SELECT s FROM Shift s WHERE s.date BETWEEN :start AND :end ORDER BY s.date, s.startTime")
    List<Shift> findShiftsInRange(@Param("start") LocalDate start, @Param("end") LocalDate end);
}
