-- 공식 엑셀 반영 이력(공개 일지) 테이블.
-- admin 이 OfficialSubjectImportService.apply 로 반영할 때마다 1행씩 기록한다.
CREATE TABLE IF NOT EXISTS subject_update_logs (
    id BIGSERIAL PRIMARY KEY,
    applied_at TIMESTAMP NOT NULL,
    semester VARCHAR(20) NOT NULL,
    source_format VARCHAR(32),
    added_count INT NOT NULL,
    modified_count INT NOT NULL,
    removed_count INT NOT NULL
);

-- 유저 1회성 알림 테이블.
-- 과목 시간 변경으로 시간표에서 자동 제거된 경우 등 유저에게 알릴 내용을 보관한다.
-- read_at 이 NULL 이면 미읽음, 읽음 처리 시 일괄로 read_at 을 채운다.
CREATE TABLE IF NOT EXISTS user_notifications (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    message VARCHAR(500) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    read_at TIMESTAMP
);

-- 미읽음 알림 조회(user_id + read_at IS NULL)를 커버하는 인덱스.
CREATE INDEX IF NOT EXISTS idx_user_notifications_unread
    ON user_notifications (user_id, read_at);
