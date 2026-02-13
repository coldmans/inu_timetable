-- INU Timetable / Wishlist 중복 방지용 유니크 제약 추가
-- PostgreSQL 기준

BEGIN;

-- 1) 기존 중복 데이터 정리 (가장 오래된 1개만 남기고 제거)
WITH ranked_timetable AS (
  SELECT id,
         ROW_NUMBER() OVER (
           PARTITION BY user_id, subject_id, semester
           ORDER BY added_at ASC NULLS LAST, id ASC
         ) AS rn
  FROM user_timetables
)
DELETE FROM user_timetables ut
USING ranked_timetable rt
WHERE ut.id = rt.id
  AND rt.rn > 1;

WITH ranked_wishlist AS (
  SELECT id,
         ROW_NUMBER() OVER (
           PARTITION BY user_id, subject_id, semester
           ORDER BY added_at ASC NULLS LAST, id ASC
         ) AS rn
  FROM wishlist_items
)
DELETE FROM wishlist_items w
USING ranked_wishlist rw
WHERE w.id = rw.id
  AND rw.rn > 1;

-- 2) 유니크 제약 추가
ALTER TABLE user_timetables
  ADD CONSTRAINT uk_user_timetable_user_subject_semester
  UNIQUE (user_id, subject_id, semester);

ALTER TABLE wishlist_items
  ADD CONSTRAINT uk_wishlist_user_subject_semester
  UNIQUE (user_id, subject_id, semester);

COMMIT;
