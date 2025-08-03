package inu.timetable.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class DatabaseInitializer {
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    @Bean
    public ApplicationRunner initDatabase() {
        return args -> {
            try {
                // is_required 컬럼이 없으면 추가
                jdbcTemplate.execute("ALTER TABLE wishlist_items ADD COLUMN IF NOT EXISTS is_required BOOLEAN DEFAULT false");
                System.out.println(">>> [DB] Added is_required column to wishlist_items table");
            } catch (Exception e) {
                System.out.println(">>> [DB] Column might already exist or error occurred: " + e.getMessage());
            }
        };
    }
}