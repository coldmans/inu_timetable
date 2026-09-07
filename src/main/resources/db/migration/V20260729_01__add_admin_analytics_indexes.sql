-- 관리자 분석 대시보드의 기간별 신규가입·실제 저장 집계를 지원한다.
CREATE INDEX IF NOT EXISTS idx_users_created_at
    ON users (created_at);

CREATE INDEX IF NOT EXISTS idx_user_timetables_added_at
    ON user_timetables (added_at);

CREATE INDEX IF NOT EXISTS idx_wishlist_items_added_at
    ON wishlist_items (added_at);
