package inu.timetable.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@Configuration
public class AdminSecurityConfig {

    @Bean
    public BCryptPasswordEncoder adminPasswordEncoder(
            @Value("${admin.bcrypt-strength:12}") int strength) {
        return new BCryptPasswordEncoder(strength);
    }
}
