package com.shiftscheduling.scheduling.repository;

import com.shiftscheduling.scheduling.entity.Schedule;
import com.shiftscheduling.scheduling.entity.ScheduleStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface ScheduleRepository extends JpaRepository<Schedule, Long> {

    List<Schedule> findByStatus(ScheduleStatus status);

    List<Schedule> findByCreatedBy(Long createdBy);

    List<Schedule> findByStartDateLessThanEqualAndEndDateGreaterThanEqual(LocalDate endDate, LocalDate startDate);

    List<Schedule> findAllByOrderByCreatedAtDesc();
}
