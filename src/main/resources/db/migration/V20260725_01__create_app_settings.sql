-- 애플리케이션 전역 설정 키-값 저장 테이블.
-- 현재 학기(current_semester) 등 운영자가 전환하는 설정을 보관한다.
CREATE TABLE IF NOT EXISTS app_settings (
    setting_key   VARCHAR(64) PRIMARY KEY,
    setting_value VARCHAR(255) NOT NULL,
    updated_at    TIMESTAMP NOT NULL
);
