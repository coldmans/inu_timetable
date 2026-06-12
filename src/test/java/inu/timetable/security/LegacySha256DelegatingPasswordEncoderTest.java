package inu.timetable.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class LegacySha256DelegatingPasswordEncoderTest {

    private final LegacySha256DelegatingPasswordEncoder passwordEncoder =
            new LegacySha256DelegatingPasswordEncoder(new BCryptPasswordEncoder(4));

    @Test
    void matchesLegacySha256PasswordAndRequestsUpgrade() {
        String legacyHash = sha256("password123");

        assertThat(passwordEncoder.matches("password123", legacyHash)).isTrue();
        assertThat(passwordEncoder.upgradeEncoding(legacyHash)).isTrue();
    }

    @Test
    void matchesUppercaseLegacySha256Password() {
        String legacyHash = sha256("password123").toUpperCase(Locale.ROOT);

        assertThat(passwordEncoder.matches("password123", legacyHash)).isTrue();
    }

    @Test
    void rejectsNullRawPassword() {
        String legacyHash = sha256("password123");

        assertThat(passwordEncoder.matches(null, legacyHash)).isFalse();
    }

    @Test
    void encodesAndMatchesBcryptPassword() {
        String encodedPassword = passwordEncoder.encode("password123");

        assertThat(encodedPassword).startsWith("$2");
        assertThat(passwordEncoder.matches("password123", encodedPassword)).isTrue();
    }

    private String sha256(String value) {
        try {
            byte[] hashedBytes = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte hashedByte : hashedBytes) {
                builder.append(String.format("%02x", hashedByte));
            }
            return builder.toString();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
