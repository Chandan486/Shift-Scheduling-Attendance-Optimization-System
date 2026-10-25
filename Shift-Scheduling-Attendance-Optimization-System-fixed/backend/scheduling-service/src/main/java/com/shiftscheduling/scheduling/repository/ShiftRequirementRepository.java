package com.shiftscheduling.scheduling.repository;

import com.shiftscheduling.scheduling.entity.ShiftRequirement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ShiftRequirementRepository extends JpaRepository<ShiftRequirement, Long> {

    List<ShiftRequirement> findByShiftId(Long shiftId);

    void deleteByShiftId(Long shiftId);
}
