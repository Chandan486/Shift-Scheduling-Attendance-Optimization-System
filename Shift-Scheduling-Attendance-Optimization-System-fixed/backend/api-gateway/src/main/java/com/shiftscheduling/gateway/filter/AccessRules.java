package com.shiftscheduling.gateway.filter;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Role rules applied by the gateway to every authenticated request. Pure Java (no framework types),
 * so the rules can be unit-tested on their own.
 */
public final class AccessRules {

    private static final Set<String> MANAGERS = Set.of("ADMIN", "HR");
    private static final Set<String> ADMIN_ONLY = Set.of("ADMIN");
    private static final Set<String> NOBODY = Set.of();

    /** Access rule. method null = any method. roles null = any authenticated user. */
    private record Rule(String method, String path, boolean exact, Set<String> roles) {
        boolean matches(String requestMethod, String requestPath) {
            if (method != null && !method.equals(requestMethod)) return false;
            if (exact) return requestPath.equals(path);
            return requestPath.equals(path) || requestPath.startsWith(path + "/");
        }
    }

    /** Ordered: more specific rules first. Anything not matched is available to any authenticated user. */
    private static final List<Rule> RULES = List.of(
            // Notifications are created only service-to-service, never from a user's request.
            new Rule("POST", "/api/notifications", false, NOBODY),
            // User accounts: list/activate/deactivate are ADMIN only. Creating accounts: HR may create
            // EMPLOYEE accounts only; the auth-service enforces that using X-User-Role.
            new Rule(null, "/api/auth/users", false, ADMIN_ONLY),
            new Rule(null, "/api/auth/register", false, MANAGERS),
            // Company-wide leave dates (attendance-service reads these directly, not via the gateway).
            new Rule(null, "/api/leaves/approved", false, MANAGERS),
            new Rule(null, "/api/leaves/check", false, MANAGERS),
            // Employees: active list (used by attendance screens) and own-record lookup are open to all;
            // everything else about employee records is HR/ADMIN.
            new Rule("GET", "/api/employees/all", false, null),
            new Rule("GET", "/api/employees/user", false, null),
            new Rule(null, "/api/employees", false, MANAGERS),
            // Departments and skills: readable by all (shift screens for employees), changed by managers only.
            new Rule("GET", "/api/departments", false, null),
            new Rule("GET", "/api/skills", false, null),
            new Rule(null, "/api/departments", false, MANAGERS),
            new Rule(null, "/api/skills", false, MANAGERS),
            // Schedules (drafts, generation, publishing) are manager-only.
            new Rule(null, "/api/schedules", false, MANAGERS),
            // Shifts: an employee sees only their own assignments; shift definitions are manager-only.
            new Rule("GET", "/api/shifts/assignments/my", true, null),
            new Rule(null, "/api/shifts", false, MANAGERS),
            // Attendance reports and exports.
            new Rule(null, "/api/attendance/overview", false, MANAGERS),
            new Rule(null, "/api/attendance/export", false, MANAGERS)
    );

    /** /api/attendance/{id} and /api/attendance/{id}/correct are manager-only; check-in/out and history are self-checked in the service. */
    private static final Pattern ATTENDANCE_RECORD = Pattern.compile("^/api/attendance/\\d+(/correct)?$");

    private AccessRules() {}

    /** True when a user with {@code role} may call {@code method path} through the gateway. */
    public static boolean isAllowed(String method, String path, String role) {
        if (role == null) return false;
        String r = role.toUpperCase();
        if (path.startsWith("/api/attendance/") && ATTENDANCE_RECORD.matcher(path).matches()) {
            return MANAGERS.contains(r);
        }
        for (Rule rule : RULES) {
            if (rule.matches(method, path)) {
                return rule.roles() == null || rule.roles().contains(r);
            }
        }
        return true;
    }
}
