package com.shiftscheduling.employee.repository;
import com.shiftscheduling.employee.entity.Skill;
import org.springframework.data.jpa.repository.JpaRepository;
public interface SkillRepository extends JpaRepository<Skill, Long> {
    boolean existsByName(String name);
}
