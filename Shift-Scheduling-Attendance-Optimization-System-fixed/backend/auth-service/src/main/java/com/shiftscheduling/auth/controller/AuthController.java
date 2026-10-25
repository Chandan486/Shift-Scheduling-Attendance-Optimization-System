package com.shiftscheduling.auth.controller;

import com.shiftscheduling.auth.dto.*;
import com.shiftscheduling.auth.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    /**
     * ADMIN may create accounts with any role. HR may create EMPLOYEE accounts only (it creates the login
     * for each new employee record). Without this check any signed-in user could create an ADMIN account.
     */
    @PostMapping("/register")
    @PreAuthorize("hasAnyRole('ADMIN','HR')")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {
        String caller = currentRole();
        String requested = request.role() == null ? "" : request.role().trim().toUpperCase();
        if ("HR".equals(caller) && !"EMPLOYEE".equals(requested)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "HR users can only create EMPLOYEE accounts"));
        }
        return ResponseEntity.ok(authService.register(request));
    }

    private static String currentRole() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return "";
        return auth.getAuthorities().stream()
                .map(a -> a.getAuthority())
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.substring(5))
                .findFirst().orElse("");
    }

    @GetMapping("/validate")
    public ResponseEntity<TokenValidationResponse> validate(@RequestHeader("Authorization") String token) {
        return ResponseEntity.ok(authService.validateToken(token));
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> getCurrentUser(@RequestHeader("Authorization") String token) {
        String t = token.startsWith("Bearer ") ? token.substring(7) : token;
        var validation = authService.validateToken(t);
        if (!validation.valid()) {
            return ResponseEntity.status(401).build();
        }
        return ResponseEntity.ok(authService.getUserById(validation.userId()));
    }

    @GetMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<UserResponse>> getAllUsers() {
        return ResponseEntity.ok(authService.getAllUsers());
    }

    @PatchMapping("/users/{id}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponse> deactivateUser(@PathVariable Long id) {
        String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
        return ResponseEntity.ok(authService.setUserActive(id, false, currentUsername));
    }

    @PatchMapping("/users/{id}/activate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponse> activateUser(@PathVariable Long id) {
        String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
        return ResponseEntity.ok(authService.setUserActive(id, true, currentUsername));
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Auth Service is running");
    }
}
