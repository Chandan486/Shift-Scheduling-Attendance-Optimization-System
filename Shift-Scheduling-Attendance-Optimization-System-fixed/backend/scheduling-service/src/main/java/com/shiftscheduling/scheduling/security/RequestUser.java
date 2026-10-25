package com.shiftscheduling.scheduling.security;

import org.springframework.http.HttpStatus;
import com.shiftscheduling.scheduling.exception.ApiException;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/** Caller identity forwarded by the gateway (X-User-Id / X-User-Role / X-User-Name). */
public record RequestUser(Long userId, String role, String name) {

    public static RequestUser of(Long userId, String role, String encodedName) {
        String decoded = null;
        if (encodedName != null && !encodedName.isBlank()) {
            decoded = URLDecoder.decode(encodedName, StandardCharsets.UTF_8);
        }
        return new RequestUser(userId, role == null ? null : role.toUpperCase(), decoded);
    }

    public boolean isAdminOrHr() {
        return "ADMIN".equals(role) || "HR".equals(role);
    }

    public void requireAdminOrHr() {
        if (!isAdminOrHr()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Only HR or ADMIN users can manage shift assignments");
        }
    }

    public String displayName() {
        return name != null && !name.isBlank() ? name : "HR/Admin";
    }
}
