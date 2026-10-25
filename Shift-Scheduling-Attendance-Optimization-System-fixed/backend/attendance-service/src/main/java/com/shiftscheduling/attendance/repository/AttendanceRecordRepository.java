package com.shiftscheduling.attendance.repository;

import com.shiftscheduling.attendance.entity.AttendanceRecord;
import com.shiftscheduling.attendance.entity.AttendanceStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface AttendanceRecordRepository extends JpaRepository<AttendanceRecord, Long> {

    Optional<AttendanceRecord> findByEmployeeIdAndDate(Long employeeId, LocalDate date);

    List<AttendanceRecord> findByEmployeeIdAndDateBetween(Long employeeId, LocalDate startDate, LocalDate endDate);

    Page<AttendanceRecord> findByEmployeeIdAndDateBetween(Long employeeId, LocalDate startDate, LocalDate endDate, Pageable pageable);

    List<AttendanceRecord> findByDate(LocalDate date);

    boolean existsByEmployeeIdAndDateAndStatusIn(Long employeeId, LocalDate date, Collection<AttendanceStatus> statuses);

    Optional<AttendanceRecord> findByEmployeeIdAndDateAndStatus(Long employeeId, LocalDate date, AttendanceStatus status);

    List<AttendanceRecord> findByDateBetween(LocalDate startDate, LocalDate endDate);
}
