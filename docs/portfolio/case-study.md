# Case Study: INU Timetable

## Summary

INU 시간표는 인천대학교 학생이 학기별 과목을 검색하고, 위시리스트에서 필수·선택 과목을 정한 뒤 시간표 조합을 만들고 저장하는 서비스입니다. 사용 규모는 ID 최댓값이나 실시간 추정치가 아니라 2026-08-01 운영 DB read-only 집계와 이전 dated report로 확인했습니다.

## Problem

기능이 동작하는 초기 서비스에서 운영 가능한 서비스로 넘어가며 다음 문제를 해결해야 했습니다.

| 영역 | 문제 |
|---|---|
| 데이터 변경 | 새 학기 파일의 추가·변경·폐강을 바로 반영하면 사용자 시간표와 위시리스트 영향을 놓칠 수 있음 |
| 학기 전환 | 학기가 코드에 고정되면 재배포 없이 다음 학기로 전환하기 어려움 |
| 조합 | 위시리스트가 커질수록 재귀 중 반복 충돌 검사가 급격히 비싸짐 |
| 성능 | 동시 요청에서 DB connection starvation과 Supabase pooler 호환 문제가 발생 |
| 보안/확장 | client `userId` 신뢰, legacy password, 인스턴스별 세션·login limit은 다중 인스턴스에서 안전하지 않음 |
| 배포 | 새 버전을 즉시 100% 노출하면 health 외의 API·인증·캐시 회귀를 승격 전에 찾기 어려움 |
| 운영 | 문의, FAQ, 제품 분석, 과목 변경 이력과 사용자 공지가 분리되어 있었음 |

## Actions

### 1. 학기와 학생 기능을 운영 설정으로 연결

- `app_settings`의 현재 학기를 공개 조회하고 관리자가 변경하도록 했습니다.
- 과목 조회는 학기, 학과, 교수, 학수번호, 학점, 요일과 시간 구간을 함께 필터링합니다.
- 위시리스트를 필수/선택으로 나누고 목표 학점 또는 가능한 최대 학점, 공강 요일을 반영합니다.
- 한 수업의 강의실이 교시별로 바뀌는 경우 `schedule_room_segments`로 보존합니다.

### 2. 과목 import를 검토 가능한 workflow로 전환

- 통합정보시스템 JSON과 `.xlsx`를 현재 학기 DB와 비교해 변경 유형, field diff, before/after를 저장합니다.
- 선택한 학수번호 기준으로 시간표·위시리스트 사용자와 예상 충돌을 미리 계산합니다.
- apply 시 PostgreSQL advisory lock, plan row lock, before snapshot stale 검사, 정책 version 검사를 수행합니다.
- 반영 뒤 값을 다시 읽어 계획의 after와 일치하지 않으면 전체 transaction을 rollback합니다.
- 시간 변경·폐강으로 충돌하는 사용자 시간표를 정리하고 알림과 과목 업데이트 로그를 남깁니다.

### 3. 성능 병목을 원인별로 분리

- 2026-01-02 k6 혼합 부하 보고서에서 HikariCP pool과 `prepareThreshold=0`, URL encoding 문제를 순차적으로 검증했습니다.
- 시간표 조합은 과목 시간을 `BitSet` mask로 바꾸고 재귀 중 `intersects`로 충돌을 검사했습니다.
- 운영 과목 조회는 Caffeine L1 + private Redis L2를 사용합니다. Redis 장애는 PostgreSQL loader와 L1으로 fail-open하며 DB version poll/publish로 인스턴스 간 무효화를 전파합니다.
- 2026-07-27~28 Redis 실험은 큰 속도 향상이 아니라 Caffeine 기준선과 유사한 성능을 유지하면서 공유 L2와 장애 격리를 얻은 결과로 판단했습니다.

### 4. 인증과 공유 상태 보강

- Spring Security session principal과 `UserAccessGuard`로 private API의 사용자 소유권을 검증했습니다.
- legacy SHA-256 password는 성공 로그인 때 BCrypt로 lazy migration합니다.
- 운영은 Spring Session JDBC를 사용해 최대 3개 Cloud Run 인스턴스가 session을 공유합니다.
- 사용자·관리자 login limit과 공개 문의 limit을 PostgreSQL `login_rate_limits`에 namespace별로 저장합니다.
- 관리자 계정은 최초 bootstrap 후 DB의 BCrypt hash와 credential version을 정본으로 사용해 이전 session을 무효화합니다.

### 5. 운영 기능을 제품 안으로 통합

- 과목 update log와 영향 사용자 notification을 추가했습니다.
- 비로그인도 가능한 앱 내 문의, 관리자 미처리 필터와 처리 완료, 관리형 공개 FAQ를 추가했습니다.
- client event를 저장하고 관리자 summary/dashboard API로 기간별 이벤트·검색·사용자 흐름을 조회합니다.

### 6. Cloud Run 후보 기반 배포

- PR은 `clean test bootJar`를 통과해야 합니다.
- main 배포는 GitHub OIDC로 GCP에 인증하고 `gcloud run deploy --source . --no-traffic`으로 후보를 만듭니다.
- 후보에서 health, 과목 조회, 캐시 metric, admin login/CSRF와 비인증 차단을 검증합니다.
- 현재 rollout phase는 10%, 50%, 100% 순서로 승격하고 검증 실패 시 배포 전에 기록한 revision으로 traffic을 돌립니다.
- 운영 schema는 Flyway migration과 Hibernate `ddl-auto=validate`를 사용합니다.

## Impact

| 근거 | Before | After | 날짜/범위 |
|---|---:|---:|---|
| DB/search 혼합 부하 p95 | 30s | 386ms | 2026-01-02 보고서, 5분 30초, 최대 200 VU |
| DB/search 혼합 부하 실패율 | 25.79% | 0% | 같은 보고서가 기록한 client 결과 |
| 30개 위시리스트 조합 p95 | 348.72ms | 9.39ms | 2026-06-14 로컬 H2 격리 벤치 |
| 누적 가입 ID | - | 4천 번대 돌파 | 2026-08-01 sequence 4,045, 운영자 확인 DB 초기화 전 이력 포함 |
| 활성·비테스트 계정 | - | 3,554개 | 2026-08-01 운영 DB read-only 집계 |
| 저장 행동 사용자 | - | 3,371명 | 같은 2026-08-01 집계 |
| 저장 행 | - | 29,502개 | 같은 2026-08-01 집계의 시간표 20,853행 + 위시리스트 8,649행 |
| 저장 행동 사용자 | - | 2,512명 | 2026-04-29, 테스트 계정 제외 운영 DB report |
| 저장 행동 | - | 21,292건 | 같은 2026-04-29 report |
| Redis 공유 캐시 | Caffeine 기준선 | 처리량 -2.3%, 평균 +4.1%, p95 -2.1%, p99 -1.4% | 2026-07-27~28 읽기 전용 Cloud Run benchmark |

수치는 서로 다른 날짜와 시나리오의 결과이므로 하나의 현재 production baseline으로 합치지 않습니다.

## Trade-offs

- 세션과 login limit을 PostgreSQL에 둬 다중 인스턴스 일관성을 얻었지만, 인증 경로가 DB 가용성에 의존합니다.
- Redis는 재생성 가능한 과목 조회 cache에만 쓰고 session이나 원장 데이터는 저장하지 않습니다.
- Caffeine L1의 즉시 일관성을 위해 DB version polling을 유지합니다. Redis Pub/Sub로 전환하기 전에는 poll/publish를 끄지 않습니다.
- zero-traffic 후보도 공유 DB에 연결해 Flyway를 실행할 수 있으므로 migration은 이전/새 revision 모두와 호환되는 forward-only 변경이어야 합니다.
- 로컬 Prometheus/Grafana 구성이 있지만 저장소 자체가 hosted production dashboard를 배포하지는 않습니다.

## What I Would Explain In An Interview

- 기능 수보다 실제 사용자 데이터에 영향을 주는 변경을 preview와 사후 검증으로 통제한 과정을 설명합니다.
- 386ms, 9.39ms, Redis 수치는 날짜와 workload가 다른 별도 증거로 제시합니다.
- 사용자 ID sequence 4,045와 현재 활성·비테스트 계정 3,554개를 구분해 설명합니다.
- Redis를 선택한 이유는 latency 과장이 아니라 scale-out 시 공유 cache reuse와 장애 격리입니다.
- Cloud Run 배포는 이미지 재시작이 아니라 비트래픽 후보에서 API·인증·cache 계약을 확인한 뒤 traffic을 단계적으로 옮기는 구조입니다.
