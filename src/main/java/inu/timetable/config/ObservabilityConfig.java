package inu.timetable.config;

import inu.timetable.enums.UserStatus;
import inu.timetable.repository.UserRepository;
import inu.timetable.service.UserActivityService;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ObservabilityConfig {

    @Bean
    public MeterBinder userMetrics(UserRepository userRepository, UserActivityService userActivityService) {
        return registry -> {
            Gauge.builder("inu.users.registered", userRepository, UserRepository::count)
                    .description("누적 가입 사용자 수")
                    .register(registry);

            Gauge.builder("inu.users.active", userRepository, repository -> repository.countByStatus(UserStatus.ACTIVE))
                    .description("회원탈퇴를 제외한 활성 사용자 수")
                    .register(registry);

            Gauge.builder("inu.users.dau", userActivityService, UserActivityService::countDau)
                    .description("오늘 API를 사용한 고유 사용자 수")
                    .register(registry);

            Gauge.builder("inu.users.mau", userActivityService, UserActivityService::countMau)
                    .description("최근 30일 API를 사용한 고유 사용자 수")
                    .register(registry);
        };
    }
}
