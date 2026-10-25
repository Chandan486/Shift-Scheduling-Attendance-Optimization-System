package com.shiftscheduling.auth.config;

import com.shiftscheduling.auth.entity.Role;
import com.shiftscheduling.auth.entity.User;
import com.shiftscheduling.auth.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

/**
 * Makes sure the demo logins exist on EVERY start (not only on an empty database), so the
 * documented credentials keep working after restarts, volume changes or upgrades.
 *
 * Default logins:  admin / admin123,  hr001 / hrpass123,  employee1..3 / emp123
 *
 * - Missing demo users are created; existing users are never deleted (except the old 2-char "hr").
 * - If DEMO_RESET_PASSWORDS=true (default in docker-compose for this demo), the demo accounts'
 *   passwords are set back to the values above and the accounts are re-activated.
 *   Set it to false for any real deployment.
 */
@Configuration
public class DataInitializer {

    /** Default HR login (5 characters). It replaces the old 2-character "hr" login. */
    static final String DEFAULT_HR_USERNAME = "hr001";
    private static final String LEGACY_HR_USERNAME = "hr";

    private record Demo(String username, String email, String password, String fullName, Role role) {}

    private static final List<Demo> DEMO_USERS = List.of(
            new Demo("admin", "admin@shiftscheduling.com", "admin123", "System Administrator", Role.ADMIN),
            new Demo(DEFAULT_HR_USERNAME, "hr@shiftscheduling.com", "hrpass123", "HR Manager", Role.HR),
            new Demo("employee1", "employee1@shiftscheduling.com", "emp123", "John Doe", Role.EMPLOYEE),
            new Demo("employee2", "employee2@shiftscheduling.com", "emp123", "Jane Smith", Role.EMPLOYEE),
            new Demo("employee3", "employee3@shiftscheduling.com", "emp123", "Bob Wilson", Role.EMPLOYEE));

    @Bean
    public CommandLineRunner initData(UserRepository repo, PasswordEncoder encoder,
                                      @Value("${demo.reset-passwords:false}") boolean resetPasswords) {
        return args -> {
            removeLegacyHrUser(repo);
            for (Demo d : DEMO_USERS) {
                ensureUser(repo, encoder, d, resetPasswords);
            }
            System.out.println("Demo users verified (reset passwords: " + resetPasswords + ")");
        };
    }

    /** Old databases: the 2-character demo HR user is removed (its email is freed for hr001). */
    private void removeLegacyHrUser(UserRepository repo) {
        repo.findByUsername(LEGACY_HR_USERNAME)
                .filter(u -> u.getRole() == Role.HR)
                .ifPresent(old -> {
                    repo.delete(old);
                    repo.flush();
                });
    }

    private void ensureUser(UserRepository repo, PasswordEncoder encoder, Demo d, boolean resetPasswords) {
        User user = repo.findByUsername(d.username()).orElse(null);
        if (user == null) {
            if (repo.existsByEmail(d.email())) {
                // email already used by another account; keep the demo user creatable
                user = new User();
                user.setEmail(d.username() + "@shiftscheduling.com");
            } else {
                user = new User();
                user.setEmail(d.email());
            }
            user.setUsername(d.username());
            user.setPasswordHash(encoder.encode(d.password()));
            user.setFullName(d.fullName());
            user.setRole(d.role());
            repo.save(user);
        } else if (resetPasswords) {
            user.setPasswordHash(encoder.encode(d.password()));
            user.setActive(true);
            repo.save(user);
        }
    }
}
