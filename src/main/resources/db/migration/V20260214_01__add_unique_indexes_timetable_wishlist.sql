-- 중복 삽입 방지: 사용자+과목+학기 유니크 인덱스
DELETE FROM user_timetables ut
USING (
    SELECT id,
           ROW_NUMBER() OVER (
               PARTITION BY user_id, subject_id, semester
               ORDER BY added_at ASC NULLS LAST, id ASC
           ) AS duplicate_order
    FROM user_timetables
) duplicated
WHERE ut.id = duplicated.id
  AND duplicated.duplicate_order > 1;

DELETE FROM wishlist_items wi
USING (
    SELECT id,
           ROW_NUMBER() OVER (
               PARTITION BY user_id, subject_id, semester
               ORDER BY is_required DESC NULLS LAST, priority ASC NULLS LAST, added_at ASC NULLS LAST, id ASC
           ) AS duplicate_order
    FROM wishlist_items
) duplicated
WHERE wi.id = duplicated.id
  AND duplicated.duplicate_order > 1;

CREATE UNIQUE INDEX IF NOT EXISTS uk_user_timetable_user_subject_semester
    ON user_timetables (user_id, subject_id, semester);

CREATE UNIQUE INDEX IF NOT EXISTS uk_wishlist_item_user_subject_semester
    ON wishlist_items (user_id, subject_id, semester);
