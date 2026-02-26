package com.pricealert.alertapi.application.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtProperties jwtProperties;
    private final StringRedisTemplate redisTemplate;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        var authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        var token = authHeader.substring(7);
        try {
            var claims = validateAndExtractClaims(token);
            var jti = claims.jti();
            if (jti != null && Boolean.TRUE.equals(redisTemplate.hasKey("blacklist:" + jti))) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Token revoked");
                return;
            }
            var auth = new UsernamePasswordAuthenticationToken(claims.sub(), null, List.of());
            SecurityContextHolder.getContext().setAuthentication(auth);
        } catch (Exception e) {
            log.warn("Invalid JWT token: {}", e.getMessage());
        }

        filterChain.doFilter(request, response);
    }

    public JwtClaims validateAndExtractClaims(String token) {
        var parts = token.split("\\.");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Invalid JWT format");
        }

        var headerPayload = parts[0] + "." + parts[1];
        var expectedSignature = hmacSha256(headerPayload, jwtProperties.secret());
        if (!expectedSignature.equals(parts[2])) {
            throw new IllegalArgumentException("Invalid JWT signature");
        }

        var payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        var sub = extractClaim(payload, "sub");
        var jti = extractOptionalClaim(payload, "jti");
        var exp = extractOptionalClaim(payload, "exp");
        return new JwtClaims(sub, jti, exp != null ? Long.parseLong(exp) : null);
    }

    private String hmacSha256(String data, String secret) {
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            var hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to compute HMAC", e);
        }
    }

    private String extractClaim(String payload, String claim) {
        var key = "\"" + claim + "\"";
        var idx = payload.indexOf(key);
        if (idx < 0) {
            throw new IllegalArgumentException("Missing claim: " + claim);
        }
        return extractValueAt(payload, idx);
    }

    private String extractOptionalClaim(String payload, String claim) {
        var key = "\"" + claim + "\"";
        var idx = payload.indexOf(key);
        if (idx < 0) {
            return null;
        }
        return extractValueAt(payload, idx);
    }

    private String extractValueAt(String payload, int keyIdx) {
        var valueStart = payload.indexOf(':', keyIdx) + 1;
        var trimmed = payload.substring(valueStart).trim();
        if (trimmed.startsWith("\"")) {
            var end = trimmed.indexOf('"', 1);
            return trimmed.substring(1, end);
        }
        var end =
                Math.min(
                        trimmed.indexOf(',') >= 0 ? trimmed.indexOf(',') : trimmed.length(),
                        trimmed.indexOf('}') >= 0 ? trimmed.indexOf('}') : trimmed.length());
        return trimmed.substring(0, end).trim();
    }
}
