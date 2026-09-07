# 인천대 시간표 마법사 이용 지표 리포트

- 생성 시각: 2026-08-01 14:26 KST
- 데이터 기준: 운영 PostgreSQL DB의 `users`, `wishlist_items`, `user_timetables` 테이블
- 접속 방식: GCP Secret Manager의 현재 운영 DB 접속 정보를 사용한 read-only session

## 요약

| 지표 | 값 |
|---|---:|
| 현재 보존된 `users` 행 | 3,596 |
| 활성 계정 | 3,582 |
| 탈퇴 계정 | 14 |
| 활성·비테스트 계정 | 3,554 |
| 저장 행동이 1회 이상 남은 활성·비테스트 사용자 | 3,371 |
| 활성·비테스트 계정 대비 저장 행동 사용자 비율 | 94.9% |
| 시간표 저장 행 | 20,853 |
| 위시리스트 저장 행 | 8,649 |
| 저장 행 합계 | 29,502 |
| 현재 보존된 최소/최대 사용자 ID | 448 / 4,045 |
| 누적 가입 ID 이정표 | 4,000번대 돌파 (`users_id_seq = 4,045`) |

테스트 계정은 이전 리포트와 같은 조건으로 제외했습니다.

- `username = '202101681'`
- `lower(username) LIKE '%test%'`
- `lower(username) LIKE '%gaia%'`

## 누적 가입 ID 4천 돌파와 현재 계정 수

운영 DB의 `users_id_seq`와 `MAX(users.id)`는 모두 4,045입니다. 그러나 현재 보존된
`users` 행은 3,596개이고, 활성·비테스트 계정은 3,554개입니다.

현재 보존된 ID 범위는 448부터 4,045까지이며 이 범위 안에서 누락된 ID는 2개입니다.
운영자 확인에 따르면 ID 1부터 447까지의 과거 행은 운영 DB 초기화 때 제거됐고 sequence는
이어졌습니다. 따라서 4,045는 `누적 가입 ID가 4천 번대를 돌파했다`는 운영 이정표의
근거로 사용합니다. 다만 현재 DB만으로 초기화 전에 제거된 행의 속성이나 테스트 계정 여부를
복원할 수 없으므로, 최대 ID를 현재 보유 사용자 수로 해석하지 않습니다.

외부 문서에는 다음처럼 누적 이정표와 현재 규모를 함께 적습니다.

- 누적 이정표: `DB 초기화 전 이력을 포함해 가입 ID 4,000번대 돌파`
- 현재 계정 규모: `활성·비테스트 계정 3,554개`
- 저장 행동 사용자: `3,371명`

## 산출 SQL

운영 접속에서는 connection을 read-only, autocommit으로 설정한 뒤 아래 집계만 수행했습니다.

```sql
select count(*) as users_rows,
       min(id) as min_id,
       max(id) as max_id,
       count(*) filter (where status = 'ACTIVE') as active_rows,
       count(*) filter (where status = 'WITHDRAWN') as withdrawn_rows
from users;

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
```

## 해석 범위

- 현재 사용자 수는 테이블에 남아 있는 계정 기준입니다. 누적 4천 이정표는 운영자가 확인한
  DB 초기화 이력과 sequence 값에 근거하며, 제거된 행의 상세 속성은 복원하지 않습니다.
- 저장 행동은 현재 남아 있는 시간표·위시리스트 행 기준이며, 검색·상세 조회·조합 생성
  이벤트 횟수와는 다른 지표입니다.
- 이 수치는 2026-08-01의 스냅샷입니다. 이후 가입·탈퇴·저장 변경에 따라 달라집니다.
