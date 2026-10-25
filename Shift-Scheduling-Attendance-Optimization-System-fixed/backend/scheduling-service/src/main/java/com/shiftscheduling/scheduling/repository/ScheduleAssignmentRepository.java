package com.shiftscheduling.scheduling.repository;

import com.shiftscheduling.scheduling.entity.ScheduleAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface ScheduleAssignmentRepository extends JpaRepository<ScheduleAssignment, Long> {

    List<ScheduleAssignment> findByScheduleId(Long scheduleId);

    List<ScheduleAssignment> findByEmployeeId(Long employeeId);

    List<ScheduleAssignment> findByEmployeeIdAndDateBetween(Long employeeId, LocalDate startDate, LocalDate endDate);

    List<ScheduleAssignment> findByScheduleIdAndDate(Long scheduleId, LocalDate date);

    List<ScheduleAssignment> findByScheduleIdAndEmployeeId(Long scheduleId, Long employeeId);

    @Query("SELECT a FROM ScheduleAssignment a WHERE a.employeeId = :empId AND a.date = :date AND a.status <> 'CANCELLED'")
    List<ScheduleAssignment> findActiveByEmployeeAndDate(@Param("empId") Long employeeId, @Param("date") LocalDate date);

    @Query("SELECT a FROM ScheduleAssignment a WHERE a.schedule.id = :scheduleId AND a.date BETWEEN :start AND :end ORDER BY a.date, a.shift.startTime")
    List<ScheduleAssignment> findByScheduleAndDateRange(@Param("scheduleId") Long scheduleId, @Param("start") LocalDate start, @Param("end") LocalDate end);

    @Query("SELECT COUNT(a) FROM ScheduleAssignment a WHERE a.shift.id = :shiftId AND a.date = :date AND a.status <> 'CANCELLED'")
    int countActiveByShiftAndDate(@Param("shiftId") Long shiftId, @Param("date") LocalDate date);

    /**
     * All non-cancelled assignments on a date that belong to PUBLISHED schedules.
     * Shift and schedule are fetched in the same query to avoid N+1 lookups.
     */
    @Query("SELECT a FROM ScheduleAssignment a JOIN FETCH a.shift s JOIN FETCH a.schedule sc "
            + "WHERE a.date = :date "
            + "AND a.status <> com.shiftscheduling.scheduling.entity.AssignmentStatus.CANCELLED "
            + "AND sc.status = com.shiftscheduling.scheduling.entity.ScheduleStatus.PUBLISHED")
    List<ScheduleAssignment> findPublishedActiveByDate(@Param("date") LocalDate date);
}
