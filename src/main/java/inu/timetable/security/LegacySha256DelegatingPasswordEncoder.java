package inu.timetable.security;

import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.regex.Pattern;

public class LegacySha256DelegatingPasswordEncoder implements PasswordEncoder {

    private static final Pattern SHA256_HEX_PATTERN = Pattern.compile("^[a-fA-F0-9]{64}$");

    private final PasswordEncoder bcryptPasswordEncoder;

    public LegacySha256DelegatingPasswordEncoder(PasswordEncoder bcryptPasswordEncoder) {
        this.bcryptPasswordEncoder = bcryptPasswordEncoder;
    }

    @Override
    public String encode(CharSequence rawPassword) {
        return bcryptPasswordEncoder.encode(rawPassword);
    }

    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        if (encodedPassword == null) {
            return false;
        }
        if (isLegacySha256(encodedPassword)) {
            return MessageDigest.isEqual(
                    sha256(rawPassword).getBytes(StandardCharsets.UTF_8),
                    encodedPassword.getBytes(StandardCharsets.UTF_8));
        }
        return bcryptPasswordEncoder.matches(rawPassword, encodedPassword);
    }

    @Override
    public boolean upgradeEncoding(String encodedPassword) {
        return encodedPassword == null
                || isLegacySha256(encodedPassword)
                || bcryptPasswordEncoder.upgradeEncoding(encodedPassword);
    }

    private boolean isLegacySha256(String encodedPassword) {
        return SHA256_HEX_PATTERN.matcher(encodedPassword).matches();
    }

    private String sha256(CharSequence rawPassword) {
        try {
            byte[] hashedBytes = MessageDigest.getInstance("SHA-256")
                    .digest(rawPassword.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte hashedByte : hashedBytes) {
                builder.append(String.format("%02x", hashedByte));
            }
            return builder.toString();
        } catch (Exception exception) {
            throw new IllegalStateException("비밀번호 해시 검증 실패", exception);
        }
    }
}
