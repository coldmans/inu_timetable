# Course Data Snapshot (course-data-snapshot-2026-06-12)

- Generated at: `2026-06-12T05:50:43+00:00`
- Source: production PostgreSQL via read-only session
- Privacy guard: no usernames, passwords, raw user IDs, emails, or per-user event rows exported.
- Note: subject IDs are internal course record IDs, retained only to join exported course tables.

## Source Counts

| table | rows |
|---|---:|
| subjects | 2,894 |
| schedules | 3,682 |
| wishlist_items | 6,701 |
| user_timetables | 14,668 |
| users | 2,686 |

## Exported Files

- `subjects.csv`: 2,894 rows
- `schedules.csv`: 3,682 rows
- `subject_usage_summary.csv`: 2,894 rows
- `department_usage_summary.csv`: 94 rows
- `subject_type_usage_summary.csv`: 9 rows
- `time_slot_usage_summary.csv`: 255 rows
- `daily_usage_summary.csv`: 44 rows
- `subject_pair_cooccurrence.csv`: 1,000 rows
- `audience_by_subject_grade.csv`: 2,339 rows
- `audience_by_subject_major.csv`: 982 rows
- `course-data-snapshot-2026-06-12.xlsx`: same tables bundled into one workbook

## Top Subjects By Saved Actions

| rank | subject_id | subject | professor | wishlist | timetable | total | users |
|---:|---:|---|---|---:|---:|---:|---:|
| 1 | 9300 | 대중매체속바이오테크놀로지 | 예정용 | 43 | 94 | 137 | 121 |
| 2 | 9155 | MBTI로찾아가는나의책 | 박상원 | 50 | 73 | 123 | 108 |
| 3 | 9207 | 영화속으로들어간클래식음악 | 김상림 | 40 | 83 | 123 | 107 |
| 4 | 9376 | 예술과친구하기 | 이계원 | 25 | 63 | 88 | 83 |
| 5 | 9206 | 영화속바이러스의이해 | 예정용 | 39 | 40 | 79 | 71 |
| 6 | 9199 | 심리학의이해 | 서신화 | 31 | 47 | 78 | 71 |
| 7 | 9201 | 심리학의이해 | 서신화 | 28 | 49 | 77 | 69 |
| 8 | 9327 | 배움특강 | 학생취업처 | 26 | 40 | 66 | 58 |
| 9 | 9374 | 영화로배우는독성학개론 | 예정용 | 19 | 45 | 64 | 59 |
| 10 | 9200 | 심리학의이해 | 박준성 | 28 | 35 | 63 | 57 |

## Schema Snapshot

| table | columns |
|---|---|
| schedules | `end_time, start_time, id, subject_id, day_of_week` |
| subjects | `credits, grade, is_night, id, class_method, department, professor, subject_name, subject_type` |
| user_timetables | `id, added_at, memo, semester, subject_id` |
| users | `id, created_at, grade, major` |
| wishlist_items | `id, added_at, priority, semester, subject_id, is_required` |

## Usage Ideas

- Course recommendation: start from `subject_pair_cooccurrence.csv`.
- Popular course ranking: use `subject_usage_summary.csv` with wishlist/timetable counts.
- Demand by department/type/time: use the grouped summary files.
- UX copy and filter defaults: use top departments, subject types, and busy time slots.

## Caveats

- Current production `subjects` may not yet have `course_code`, `semester`, or `active`; missing optional columns are exported as blank/default values.
- Time-slot demand duplicates a subject's demand across each scheduled block; interpret it as demand touching that slot, not unique users per slot.
- Audience distribution files suppress small groups (`>=3` by grade, `>=5` by major) and remain aggregate-only.
