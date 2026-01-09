package inu.timetable.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("INU Timetable API")
                        .version("v1.0.0")
                        .description("인천대학교 시간표 관리 시스템 API 문서")
                        .contact(new Contact()
                                .name("INU Timetable Team")
                                .email("support@example.com")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:8080")
                                .description("로컬 개발 서버"),
                        new Server()
                                .url("https://api.example.com")
                                .description("프로덕션 서버")
                ));
    }
}
