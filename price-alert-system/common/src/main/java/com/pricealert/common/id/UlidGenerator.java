package com.pricealert.common.id;

import java.security.SecureRandom;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Generates ULIDs (Universally Unique Lexicographically Sortable Identifiers).
 * Format: 10-char timestamp (48-bit ms since epoch) + 16-char randomness (80-bit).
 * Total: 26-char Crockford Base32 string.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class UlidGenerator {

    private static final char[] ENCODING = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    public static String generate() {
        long timestamp = Instant.now().toEpochMilli();
        byte[] randomness = new byte[10];
        RANDOM.nextBytes(randomness);
        return encode(timestamp, randomness);
    }

    private static String encode(long timestamp, byte[] randomness) {
        char[] chars = new char[26];

        // Encode timestamp (48 bits -> 10 chars in Crockford Base32)
        chars[0] = ENCODING[(int) ((timestamp >>> 45) & 0x1F)];
        chars[1] = ENCODING[(int) ((timestamp >>> 40) & 0x1F)];
        chars[2] = ENCODING[(int) ((timestamp >>> 35) & 0x1F)];
        chars[3] = ENCODING[(int) ((timestamp >>> 30) & 0x1F)];
        chars[4] = ENCODING[(int) ((timestamp >>> 25) & 0x1F)];
        chars[5] = ENCODING[(int) ((timestamp >>> 20) & 0x1F)];
        chars[6] = ENCODING[(int) ((timestamp >>> 15) & 0x1F)];
        chars[7] = ENCODING[(int) ((timestamp >>> 10) & 0x1F)];
        chars[8] = ENCODING[(int) ((timestamp >>> 5) & 0x1F)];
        chars[9] = ENCODING[(int) (timestamp & 0x1F)];

        // Encode randomness (80 bits -> 16 chars in Crockford Base32)
        chars[10] = ENCODING[((randomness[0] & 0xFF) >>> 3)];
        chars[11] = ENCODING[((randomness[0] & 0x07) << 2) | ((randomness[1] & 0xFF) >>> 6)];
        chars[12] = ENCODING[((randomness[1] & 0x3E) >>> 1)];
        chars[13] = ENCODING[((randomness[1] & 0x01) << 4) | ((randomness[2] & 0xFF) >>> 4)];
        chars[14] = ENCODING[((randomness[2] & 0x0F) << 1) | ((randomness[3] & 0xFF) >>> 7)];
        chars[15] = ENCODING[((randomness[3] & 0x7C) >>> 2)];
        chars[16] = ENCODING[((randomness[3] & 0x03) << 3) | ((randomness[4] & 0xFF) >>> 5)];
        chars[17] = ENCODING[(randomness[4] & 0x1F)];
        chars[18] = ENCODING[((randomness[5] & 0xFF) >>> 3)];
        chars[19] = ENCODING[((randomness[5] & 0x07) << 2) | ((randomness[6] & 0xFF) >>> 6)];
        chars[20] = ENCODING[((randomness[6] & 0x3E) >>> 1)];
        chars[21] = ENCODING[((randomness[6] & 0x01) << 4) | ((randomness[7] & 0xFF) >>> 4)];
        chars[22] = ENCODING[((randomness[7] & 0x0F) << 1) | ((randomness[8] & 0xFF) >>> 7)];
        chars[23] = ENCODING[((randomness[8] & 0x7C) >>> 2)];
        chars[24] = ENCODING[((randomness[8] & 0x03) << 3) | ((randomness[9] & 0xFF) >>> 5)];
        chars[25] = ENCODING[(randomness[9] & 0x1F)];

        return new String(chars);
    }
}
