package com.shiftscheduling.leave.repository;

import com.shiftscheduling.leave.entity.LeaveBalance;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LeaveBalanceRepository extends JpaRepository<LeaveBalance, Long> {

    Optional<LeaveBalance> findByEmployeeIdAndYear(Long employeeId, int year);

    /** Locks the balance row while an approval deducts from it, so usage can never be double-counted. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM LeaveBalance b WHERE b.employeeId = :employeeId AND b.year = :year")
    Optional<LeaveBalance> findByEmployeeIdAndYearForUpdate(@Param("employeeId") Long employeeId, @Param("year") int year);
}
