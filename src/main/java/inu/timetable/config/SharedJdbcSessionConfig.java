package inu.timetable.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.session.jdbc.config.annotation.web.http.EnableJdbcHttpSession;
import org.springframework.session.config.SessionRepositoryCustomizer;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

import java.time.Duration;

@Configuration
@ConditionalOnProperty(name = "session.shared.enabled", havingValue = "true")
@EnableJdbcHttpSession(
        maxInactiveIntervalInSeconds = 1800,
        tableName = "SPRING_SESSION",
        cleanupCron = "0 * * * * *")
public class SharedJdbcSessionConfig {

    @Bean
    public CookieSerializer sessionCookieSerializer(
            @Value("${server.servlet.session.cookie.path:/}") String cookiePath,
            @Value("${server.servlet.session.cookie.secure:false}") boolean secure,
            @Value("${server.servlet.session.cookie.same-site:lax}") String sameSite) {
        DefaultCookieSerializer serializer = new DefaultCookieSerializer();
        serializer.setCookieName("SESSION");
        serializer.setCookiePath(cookiePath);
        serializer.setUseHttpOnlyCookie(true);
        serializer.setUseSecureCookie(secure);
        serializer.setSameSite(sameSite);
        return serializer;
    }

    @Bean
    public SessionRepositoryCustomizer<JdbcIndexedSessionRepository> sessionTimeoutCustomizer(
            @Value("${server.servlet.session.timeout:30m}") Duration timeout) {
        return repository -> repository.setDefaultMaxInactiveInterval(timeout);
    }
}
