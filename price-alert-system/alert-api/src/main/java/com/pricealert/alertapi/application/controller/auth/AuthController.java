package com.pricealert.alertapi.application.controller.auth;

import com.pricealert.alertapi.application.security.JwtAuthenticationFilter;
import com.pricealert.alertapi.application.security.JwtClaims;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;

@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final StringRedisTemplate redisTemplate;

    @DeleteMapping("/logout")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        var authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.badRequest().build();
        }

        var token = authHeader.substring(7);
        var claims = jwtAuthenticationFilter.validateAndExtractClaims(token);

        if (claims.jti() == null) {
            log.warn("Logout called with token missing jti claim for user {}", claims.sub());
            return ResponseEntity.badRequest().build();
        }

        var ttl = claims.exp() != null
                ? Duration.ofSeconds(Math.max(0, claims.exp() - Instant.now().getEpochSecond()))
                : Duration.ofHours(1);

        redisTemplate.opsForValue().set("blacklist:" + claims.jti(), "1", ttl);
        log.info("Token revoked for user {} (jti={}, ttl={}s)", claims.sub(), claims.jti(), ttl.toSeconds());

        return ResponseEntity.noContent().build();
    }
}
