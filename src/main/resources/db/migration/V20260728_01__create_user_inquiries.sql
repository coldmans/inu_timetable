-- 사이트 내 문의 테이블.
-- 기존에는 인스타그램 DM 링크로 문의를 받았으나, 앱 안에서 직접 문의를 남기고
-- 관리자가 admin 페이지에서 확인/처리할 수 있도록 저장한다.
-- 비로그인 문의도 허용하므로 user_id 는 NULL 가능, 탈퇴 시에는 문의만 남기고 연결을 끊는다.
CREATE TABLE IF NOT EXISTS user_inquiries (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    content VARCHAR(1000) NOT NULL,
    contact VARCHAR(100),
    created_at TIMESTAMP NOT NULL,
    resolved_at TIMESTAMP
);

-- 관리자 문의 목록 조회(미처리 필터 + 최신순)를 커버하는 인덱스.
CREATE INDEX IF NOT EXISTS idx_user_inquiries_resolved_created
    ON user_inquiries (resolved_at, created_at DESC);
