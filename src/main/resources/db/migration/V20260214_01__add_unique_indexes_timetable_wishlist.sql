-- 중복 삽입 방지: 사용자+과목+학기 유니크 인덱스
CREATE UNIQUE INDEX IF NOT EXISTS uk_user_timetable_user_subject_semester
    ON user_timetables (user_id, subject_id, semester);

CREATE UNIQUE INDEX IF NOT EXISTS uk_wishlist_item_user_subject_semester
    ON wishlist_items (user_id, subject_id, semester);
