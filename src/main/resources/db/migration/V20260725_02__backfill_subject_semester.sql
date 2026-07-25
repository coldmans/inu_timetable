-- 학기 구분 도입 이전에 등록되어 학기 정보가 없는 과목을 2026-1 학기로 백필한다.
UPDATE subjects SET semester = '2026-1' WHERE semester IS NULL;
