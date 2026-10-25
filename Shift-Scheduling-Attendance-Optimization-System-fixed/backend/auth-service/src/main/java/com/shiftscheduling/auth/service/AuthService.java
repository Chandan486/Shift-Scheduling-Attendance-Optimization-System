package com.shiftscheduling.auth.service;

import com.shiftscheduling.auth.dto.*;
import com.shiftscheduling.auth.entity.Role;
import com.shiftscheduling.auth.entity.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.shiftscheduling.auth.repository.UserRepository;
import com.shiftscheduling.auth.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.username())
                .orElseThrow(() -> new RuntimeException("Invalid username or password"));

        if (!user.isActive()) {
            throw new RuntimeException("Account is deactivated");
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new RuntimeException("Invalid username or password");
        }

        String token = jwtService.generateToken(user.getId(), user.getUsername(), user.getRole().name(), user.getFullName());
        return new LoginResponse(token, user.getUsername(), user.getFullName(), user.getRole().name(), user.getId());
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new RuntimeException("Username already taken");
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new RuntimeException("Email already registered");
        }

        Role role;
        try {
            role = Role.valueOf(request.role().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Invalid role. Allowed: ADMIN, HR, EMPLOYEE");
        }

        User user = new User();
        user.setUsername(request.username());
        user.setEmail(request.email());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setFullName(request.fullName());
        user.setRole(role);

        user = userRepository.save(user);
        return new UserResponse(user.getId(), user.getUsername(), user.getEmail(),
                user.getFullName(), user.getRole().name(), user.isActive(), user.getCreatedAt());
    }

    public TokenValidationResponse validateToken(String token) {
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        if (jwtService.validateToken(token)) {
            return new TokenValidationResponse(true,
                    jwtService.getUserIdFromToken(token),
                    jwtService.getUsernameFromToken(token),
                    jwtService.getRoleFromToken(token));
        }
        return new TokenValidationResponse(false, null, null, null);
    }

    public UserResponse getUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return new UserResponse(user.getId(), user.getUsername(), user.getEmail(),
                user.getFullName(), user.getRole().name(), user.isActive(), user.getCreatedAt());
    }

    public List<UserResponse> getAllUsers() {
        return userRepository.findAll().stream()
                .map(u -> new UserResponse(u.getId(), u.getUsername(), u.getEmail(),
                        u.getFullName(), u.getRole().name(), u.isActive(), u.getCreatedAt()))
                .collect(Collectors.toList());
    }

    @Transactional
    public UserResponse setUserActive(Long id, boolean active, String currentUsername) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!active) {
            // Prevent deactivating master admin
            if (user.getUsername().equals("admin")) {
                throw new RuntimeException("Cannot deactivate the master admin account");
            }
            // Prevent self-deactivation
            if (user.getUsername().equals(currentUsername)) {
                throw new RuntimeException("Cannot deactivate your own account");
            }
            // Prevent deactivating last active admin
            if (user.getRole() == Role.ADMIN) {
                long activeAdminCount = userRepository.countByRoleAndActive(Role.ADMIN, true);
                if (activeAdminCount <= 1) {
                    throw new RuntimeException("Cannot deactivate the last active admin account");
                }
            }
        }

        user.setActive(active);
        user = userRepository.save(user);
        return new UserResponse(user.getId(), user.getUsername(), user.getEmail(),
                user.getFullName(), user.getRole().name(), user.isActive(), user.getCreatedAt());
    }
}
