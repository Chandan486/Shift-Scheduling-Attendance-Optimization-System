package com.shiftscheduling.gateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Single entry point for all user traffic.
 *
 *  1. Public paths pass through.
 *  2. Every other request needs a valid Bearer token (HS-signed, verified here).
 *  3. Role rules (below) are applied from the verified token, in order; the first matching rule wins.
 *  4. Identity is forwarded to services as X-User-Id / X-User-Role / X-User-Name. Any client-supplied
 *     X-User-* header is removed first, so services can trust them *when traffic comes through here*.
 *
 * Services are NOT published to the host network in docker-compose.yml, so this gateway is the only
 * route in from outside the Docker network.
 */
@Component
public class JwtAuthFilter implements GlobalFilter, Ordered {

    private static final Set<String> MANAGERS = Set.of("ADMIN", "HR");
    private static final Set<String> ADMIN_ONLY = Set.of("ADMIN");
    private static final Set<String> NOBODY = Set.of();

    /** /api/notifications/user/{id}[/...] : a user may only read their own feed. */
    private static final Pattern NOTIFICATION_USER_PATH = Pattern.compile("^/api/notifications/user/(\\d+)(/.*)?$");

    private static final List<String> PUBLIC_PATHS = List.of(
            "/api/auth/login", "/api/auth/health",
            "/v3/api-docs", "/swagger-ui", "/eureka");

    private final SecretKey signingKey;

    public JwtAuthFilter(@Value("${jwt.secret:shift-scheduling-dev-secret-key-change-in-production-2024}") String secret) {
        String padded = secret;
        while (padded.getBytes(StandardCharsets.UTF_8).length < 64) {
            padded = padded + secret;
        }
        this.signingKey = Keys.hmacShaKeyFor(padded.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        String method = exchange.getRequest().getMethod() == null ? "" : exchange.getRequest().getMethod().name();

        for (String pub : PUBLIC_PATHS) {
            if (path.startsWith(pub)) {
                return chain.filter(exchange);
            }
        }

        // Paths that could be read differently by the gateway and by Spring MVC are refused outright
        // (encoded characters, path parameters, traversal, double slashes).
        if (path.contains("%") || path.contains(";") || path.contains("..") || path.contains("//") || path.contains("\\")) {
            exchange.getResponse().setStatusCode(HttpStatus.BAD_REQUEST);
            return exchange.getResponse().setComplete();
        }

        String authHeader = exchange.getRequest().getHeaders().getFirst("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }

        try {
            String token = authHeader.substring(7);
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            String role = String.valueOf(claims.get("role")).toUpperCase();
            String displayName = Objects.toString(claims.get("fullName"), claims.getSubject());

            if (!AccessRules.isAllowed(method, path, role)) {
                return forbidden(exchange);
            }

            // Notification feeds: a user may only read their own.
            Matcher feed = NOTIFICATION_USER_PATH.matcher(path);
            if (feed.matches() && !feed.group(1).equals(String.valueOf(claims.get("userId")))) {
                return forbidden(exchange);
            }

            ServerHttpRequest modifiedRequest = exchange.getRequest().mutate()
                    .headers(h -> {
                        h.remove("X-User-Id");
                        h.remove("X-User-Role");
                        h.remove("X-Username");
                        h.remove("X-User-Name");
                        h.add("X-User-Id", String.valueOf(claims.get("userId")));
                        h.add("X-User-Role", role);
                        h.add("X-Username", claims.getSubject());
                        // Header values must be ASCII, so the display name is URL-encoded (services decode it).
                        h.add("X-User-Name", URLEncoder.encode(displayName, StandardCharsets.UTF_8));
                    })
                    .build();

            return chain.filter(exchange.mutate().request(modifiedRequest).build());
        } catch (Exception e) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }
    }

    private static Mono<Void> forbidden(ServerWebExchange exchange) {
        exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
        return exchange.getResponse().setComplete();
    }

    @Override
    public int getOrder() {
        return -1;
    }
}
