# 인천대 시간표 마법사 이용 지표 리포트

- 생성 시각: 2026-09-02 00:32 KST
- 데이터 기준: 운영 PostgreSQL DB의 `users`, `wishlist_items`, `user_timetables`, `user_activity_daily` 테이블
- 접속 방식: GCP Secret Manager의 현재 운영 DB 접속 정보를 사용한 read-only session
- 비고: 2026-2 학기 수강신청 시즌(8월 말) 이후 첫 스냅샷. 직전 리포트는 `usage-report-2026-08-01.md`.

## 요약

| 지표 | 값 |
|---|---:|
| 현재 보존된 `users` 행 | 4,216 |
| 활성 계정 | 4,185 |
| 탈퇴 계정 | 31 |
| 활성·비테스트 계정 | 4,156 |
| 저장 행동이 1회 이상 남은 활성·비테스트 사용자 | 3,947 |
| 활성·비테스트 계정 대비 저장 행동 사용자 비율 | 95.0% |
| 시간표 저장 행 | 25,764 |
| 위시리스트 저장 행 | 10,532 |
| 저장 행 합계 | 36,296 |
| 최근 30일 활동 사용자(MAU, KST) | 946 |
| 현재 보존된 최소/최대 사용자 ID | 448 / 4,665 |
| 누적 가입 ID 이정표 | `users_id_seq = 4,665` |

테스트 계정은 이전 리포트와 같은 조건으로 제외했습니다.

- `username = '202101681'`
- `lower(username) LIKE '%test%'`
- `lower(username) LIKE '%gaia%'`

## 직전 리포트 대비 변화 (2026-08-01 → 2026-09-02)

| 지표 | 08-01 | 09-02 | 증감 |
|---|---:|---:|---:|
| `users` 행 | 3,596 | 4,216 | +620 |
| 활성·비테스트 계정 | 3,554 | 4,156 | +602 |
| 저장 행 합계 | 29,502 | 36,296 | +6,794 |

8월 말 2026-2 수강신청 기간의 가입·저장 증가가 반영된 스냅샷입니다. 8월 1일 대비
현재 보존된 사용자 행은 17.2%, 활성·비테스트 계정은 16.9%, 저장 행은 23.0%
증가했습니다. 활성·비테스트 계정 중 현재 저장 행동이 남은 사용자 비율은 95.0%입니다.

## 산출 SQL

운영 접속에서는 connection을 read-only, autocommit으로 설정한 뒤 아래와 동등한 집계를
단일 SQL statement로 실행해 주요 수치를 같은 DB snapshot에서 계산했습니다.

```sql
-- 1) 사용자 행/상태/ID 범위
select count(*) as users_rows,
       min(id) as min_id,
       max(id) as max_id,
       count(*) filter (where status = 'ACTIVE') as active_rows,
       count(*) filter (where status = 'WITHDRAWN') as withdrawn_rows
from users;

-- 2) 활성·비테스트 + 저장 행동 사용자
with eligible as (
    select id
    from users
    where status = 'ACTIVE'
      and deleted_at is null
      and not (
          username = '202101681'
          or lower(username) like '%test%'
          or lower(username) like '%gaia%'
      )
), saved_users as (
    select user_id from user_timetables
    union
    select user_id from wishlist_items
)
select count(*) as active_non_test_users,
       count(*) filter (where saved_users.user_id is not null) as saved_users
from eligible
left join saved_users on saved_users.user_id = eligible.id;

-- 3) 저장 행 수
with eligible as (
    select id
    from users
    where status = 'ACTIVE'
      and deleted_at is null
      and not (
          username = '202101681'
          or lower(username) like '%test%'
          or lower(username) like '%gaia%'
      )
)
select (select count(*)
        from user_timetables t
        join eligible e on e.id = t.user_id) as timetable_rows,
       (select count(*)
        from wishlist_items w
        join eligible e on e.id = w.user_id) as wishlist_rows;

-- 4) MAU — 오늘 포함 최근 30일, KST
with bounds as (
    select (current_timestamp at time zone 'Asia/Seoul')::date as today
)
select count(distinct user_id) as mau
from user_activity_daily, bounds
where activity_date between today - 29 and today;

-- 5) 시퀀스 이정표
select last_value as seq_value from users_id_seq;
```

## 검증 및 해석 범위

- 집계 시점에 `default_transaction_read_only`와 `transaction_read_only`가 모두 `on`인지 확인했고,
  사용자 상태 합계(활성 4,185 + 탈퇴 31)는 전체 `users` 행 4,216과 일치했습니다.
- 저장 행동 사용자는 `EXISTS`를 사용한 독립 집계로도 3,947명이었고, 저장 테이블의
  `user_id`가 `NULL`인 행은 없었습니다.
- DB session 시간대는 UTC였습니다. 원본 템플릿의 `current_date` 기반 식은 같은 시점에
  1,269명을 반환했지만, KST 날짜를 명시한 최근 30일 MAU 946명은 운영 Prometheus
  `inu.users.mau` 게이지 946과 일치했습니다. SQL에는 시작일과 종료일을 모두 명시해
  미래 날짜가 집계되지 않게 했습니다.
- 현재 `UserActivityService`는 business date를 `Asia/Seoul`로 명시하고 오늘부터 29일 전까지
  양끝을 포함해 집계합니다. 운영 게이지와 KST SQL의 일치도 별도로 확인했습니다.
- 현재 사용자 수는 테이블에 남아 있는 계정 기준입니다. 누적 이정표는 DB 초기화 이력과
  sequence 값에 근거하며, 제거된 행의 상세 속성은 복원하지 않습니다.
- 활동 사용자는 회원가입·로그인 인증 성공 또는 인증된 `/api/**` 요청이
  `user_activity_daily`에 기록된 최근 30일 distinct user입니다.
- 저장 행동은 현재 남아 있는 시간표·위시리스트 행 기준이며, 검색·상세 조회·조합 생성
  이벤트 횟수와는 다른 지표입니다.
- 이 수치는 2026-09-02 00:32 KST의 스냅샷입니다. 이후 가입·탈퇴·저장 변경에 따라
  달라집니다.
