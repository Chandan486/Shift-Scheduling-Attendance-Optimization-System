package com.shiftscheduling.leave.security;

/**
 * The caller, as verified by the gateway and forwarded in X-User-Id / X-User-Role / X-User-Name.
 * Request bodies never supply identity.
 */
public record RequestUser(Long userId, String role, String name) {

    public boolean isAdmin() { return "ADMIN".equalsIgnoreCase(role); }
    public boolean isHr() { return "HR".equalsIgnoreCase(role); }
    public boolean isAdminOrHr() { return isAdmin() || isHr(); }
}
