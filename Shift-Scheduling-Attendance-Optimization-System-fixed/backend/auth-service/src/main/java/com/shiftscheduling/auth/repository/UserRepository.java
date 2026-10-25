package com.shiftscheduling.auth.repository;

import com.shiftscheduling.auth.entity.Role;
import com.shiftscheduling.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
    long countByRoleAndActive(Role role, boolean active);
}
