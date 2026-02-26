package com.pricealert.alertapi;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Generates valid JWT tokens for integration tests using HMAC-SHA256.
 */
public final class JwtTestUtil {

    private JwtTestUtil() {}

    public static String generateToken(String userId, String secret) {
        var header =
                Base64.getUrlEncoder()
                        .withoutPadding()
                        .encodeToString(
                                "{\"alg\":\"HS256\",\"typ\":\"JWT\"}"
                                        .getBytes(StandardCharsets.UTF_8));
        var payload =
                Base64.getUrlEncoder()
                        .withoutPadding()
                        .encodeToString(
                                ("{\"sub\":\""
                                                + userId
                                                + "\",\"iat\":1700000000,\"exp\":9999999999}")
                                        .getBytes(StandardCharsets.UTF_8));
        var signature = hmacSha256(header + "." + payload, secret);
        return header + "." + payload + "." + signature;
    }

    private static String hmacSha256(String data, String secret) {
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            var hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to compute HMAC", e);
        }
    }
}
