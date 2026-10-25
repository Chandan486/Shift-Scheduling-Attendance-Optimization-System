package com.shiftscheduling.leave.repository;

import com.shiftscheduling.leave.entity.LeaveRequest;
import com.shiftscheduling.leave.entity.LeaveStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, Long> {

    /** Row lock so two reviewers cannot decide the same request at once. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM LeaveRequest r WHERE r.id = :id")
    Optional<LeaveRequest> findByIdForUpdate(@Param("id") Long id);

    Page<LeaveRequest> findByEmployeeId(Long employeeId, Pageable pageable);

    List<LeaveRequest> findByStatus(LeaveStatus status);

    List<LeaveRequest> findByEmployeeIdAndStatusAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
            Long employeeId, LeaveStatus status, LocalDate endDate, LocalDate startDate);

    /** Overlap check for new applications: any pending or approved request covering part of the range. */
    List<LeaveRequest> findByEmployeeIdAndStatusInAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
            Long employeeId, Collection<LeaveStatus> statuses, LocalDate endDate, LocalDate startDate);

    List<LeaveRequest> findByStartDateBetween(LocalDate start, LocalDate end);

    List<LeaveRequest> findByStatusAndStartDateLessThanEqualAndEndDateGreaterThanEqual(
            LeaveStatus status, LocalDate rangeEnd, LocalDate rangeStart);
}
