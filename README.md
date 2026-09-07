# INU Timetable

인천대학교 학생을 위한 과목 검색, 위시리스트, 시간표 저장 및 자동 조합 서비스의 Spring Boot 백엔드입니다.

## Current Capabilities

### 학생 기능

- 운영 설정에서 현재 학기를 조회하고, 관리자가 학기 전환을 반영하는 동적 학기 흐름
- 학기·학과·교수·학수번호·학년·이수구분·학점·요일·시간대 기반 과목 검색과 페이지네이션
- 위시리스트의 필수/선택 과목, 우선순위, 공강 요일, 목표 학점을 반영한 시간표 조합
- 목표 학점을 생략하면 충돌 없이 가능한 최대 학점을 찾는 선택 학점 조합
- 개인 시간표 저장·삭제, 메모, 위시리스트 관리
- 과목 업데이트 이력 공개 조회와 시간 변경·폐강 영향 사용자 알림
- 한 수업 안에서 시간 구간별 강의실이 달라지는 `schedule_room_segments` 지원
- 앱 내 문의 접수와 공개 FAQ 조회

### 관리자 기능

- 현재 학기 변경, 과목 CRUD, 관리자 계정 변경
- 강의계획서 JSON 또는 종합강의시간표 Excel의 변경점·before/after·사용자 영향 검토
- 선택 과목만 반영하고, 미리보기 이후 DB 변경 여부와 저장 결과를 재검증하는 안전한 import
- 문의 목록·미처리 필터·처리 완료, 공개 FAQ 생성·수정·삭제
- 기간별 이벤트·사용자·검색·학과 지표를 보는 관리자 분석 API
- 관리자 변경 요청 감사 로그, DB 기반 로그인 제한, PostgreSQL 기반 작업 잠금

관리자 과목 파일 반영 절차와 API 계약은
[`docs/admin-subject-import.md`](docs/admin-subject-import.md)에 정리되어 있습니다.

## Architecture

| 영역 | 현재 구성 |
|---|---|
| Backend | Java 17, Spring Boot 3.5.4, Spring Data JPA, Spring Security |
| Database | Supabase PostgreSQL, HikariCP, Flyway |
| Session | Spring Session JDBC 공유 세션 |
| Cache | 인스턴스별 Caffeine L1 + Memorystore Redis L2, DB version 기반 무효화 |
| Import | 통합정보시스템 JSON, Apache POI Excel, PDF/Excel Gemini 파싱 경로 |
| Runtime | GCP Cloud Run, 최대 3개 인스턴스, 인스턴스 동시성 40 |
| Frontend | Vercel의 React/Vite SPA |
| CI/CD | GitHub Actions OIDC -> Cloud Run source deploy 및 단계적 트래픽 전환 |
| Metrics | Actuator/Micrometer Prometheus endpoint, 로컬 Prometheus/Grafana 구성 |

배포 구조는 [`docs/architecture/deployment-diagram.mmd`](docs/architecture/deployment-diagram.mmd),
운영 지표의 범위는 [`docs/observability.md`](docs/observability.md)를 참고하세요.

## Portfolio Evidence

아래 수치는 실시간 현재값이 아니라 각 보고서가 기록된 시점의 스냅샷 또는 통제된 테스트 결과입니다.

| 근거 시점 | 검증된 결과 | Evidence |
|---|---|---|
| 2026-08-01 운영 DB read-only 집계 | DB 초기화 전 이력을 포함한 가입 ID 4천 번대 돌파, 현재 `users` 3,596행, 활성·비테스트 계정 3,554개, 저장 행동 사용자 3,371명 | `reports/usage-report-2026-08-01.md` |
| 2026-04-29 운영 DB 리포트 | 테스트 계정 제외 가입자 2,660명 중 2,512명이 저장 행동 수행, 저장 행동 21,292건 | `reports/usage-report-2026-04-29.md` |
| 2026-06-12 운영 DB 스냅샷 | `users` 2,686행, `user_timetables` 14,668행, `wishlist_items` 6,701행 | `reports/course-data-snapshot-2026-06-12/README.md` |
| 2026-01-02 200 VU 혼합 부하 테스트 | p95 30초 -> 386ms, 실패율 25.79% -> 0% | `PERFORMANCE_TEST_REPORT.md` |
| 2026-06-14 로컬 조합 벤치마크 | 위시리스트 30개 케이스 p95 348.72ms -> 9.39ms | `reports/combination-performance/README.md` |
| 2026-07-27~28 Cloud Run 읽기 벤치마크 | Caffeine L1 + Redis L2가 Caffeine 기준선과 유사한 처리 성능을 유지하며 공유 캐시·장애 격리 제공 | `reports/performance/2026-07-27-redis-shared-cache/README.md` |

2026-08-01 기준 사용자 ID sequence와 `MAX(users.id)`는 4,045입니다. 운영자 확인상
ID 1~447은 DB 초기화 때 제거됐으므로 누적 가입 ID 4천 돌파의 근거로 사용하되, 현재
보존된 `users` 3,596행 및 활성·비테스트 3,554개와는 구분합니다.

면접용 증거 묶음은 [`docs/portfolio/README.md`](docs/portfolio/README.md)에 있습니다.

## Local Setup

### 1. 환경 변수

```bash
cp .env.example .env
```

필수 기본값은 다음과 같습니다.

```text
DB_URL=jdbc:postgresql://your-db-host:port/database?sslmode=require&prepareThreshold=0
DB_USERNAME=your_database_username
DB_PASSWORD=your_database_password
GEMINI_API_KEY=your_gemini_api_key
```

관리자 로그인까지 확인하려면 `.env.example`의 `ADMIN_USERNAME`과
`ADMIN_PASSWORD_HASH`도 설정합니다. 평문 `ADMIN_PASSWORD`는 이전 배포와의 호환용
fallback입니다.

기본 활성 프로파일은 `dev`입니다. 다른 프로파일을 선택할 때 사용하는 표준 변수명은
`SPRING_PROFILES_ACTIVE`입니다.

### 2. 데이터베이스 전제 조건

이 저장소의 `db/migration`은 기존 운영 스키마 위에 쌓인 증분 Flyway migration입니다.
이미 스키마와 Flyway baseline이 준비된 PostgreSQL에서는 그대로 실행할 수 있지만, 빈 DB는
자동으로 핵심 테이블을 만들지 않습니다. 새 DB나 복구 환경을 만들 때는
`src/main/resources/db/baseline/V1__baseline_core_schema.sql`의 경고와 절차를 읽고,
실제 운영 스키마와 대조한 baseline을 먼저 적용해야 합니다.

### 3. 실행과 검증

```bash
./gradlew bootRun
```

서버 기본 주소는 `http://localhost:8080`입니다. 로컬 API 문서는 다음에서 확인합니다.

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`

운영 프로파일을 로컬에서 명시적으로 검증할 때는 운영용 DB·쿠키·Redis 설정을 먼저 준비한 뒤 실행합니다.

```bash
SPRING_PROFILES_ACTIVE=prod ./gradlew bootRun
```

PR과 같은 검증 명령은 다음과 같습니다.

```bash
./gradlew clean test bootJar --no-daemon
```

## API Overview

아래는 대표 경로입니다. 사용자 전용 API는 세션 인증이 필요하고, 상태를 바꾸는 브라우저 요청은
`GET /api/auth/csrf`에서 받은 토큰을 사용합니다.

### 인증과 설정

```http
POST   /api/auth/register
POST   /api/auth/login
GET    /api/auth/me
PATCH  /api/auth/me
GET    /api/auth/csrf
POST   /api/auth/logout
GET    /api/settings/current-semester
PUT    /admin/api/settings/current-semester
```

### 과목과 업데이트 알림

```http
GET  /api/subjects?page=0&size=20
GET  /api/subjects/search?keyword=데이터
GET  /api/subjects/search/professor?keyword=홍길동
GET  /api/subjects/filter?semester=2026-2&page=0&size=20
GET  /api/subjects/departments?semester=2026-2
GET  /api/subjects/count
GET  /api/subjects/update-logs?limit=20
GET  /api/notifications/unread
POST /api/notifications/read
```

### 시간표와 위시리스트

```http
POST   /api/timetable-combination/generate
GET    /api/timetable-combination/stats/{userId}?semester=2026-2&targetCredits=18
GET    /api/timetable/user/{userId}?semester=2026-2
POST   /api/timetable/add
PUT    /api/timetable/memo
DELETE /api/timetable/remove?userId={userId}&subjectId={subjectId}
DELETE /api/timetable/clear?userId={userId}&semester=2026-2
GET    /api/wishlist/user/{userId}?semester=2026-2
POST   /api/wishlist/add
PUT    /api/wishlist/priority
PUT    /api/wishlist/required
DELETE /api/wishlist/remove?userId={userId}&subjectId={subjectId}
```

### 문의, FAQ, 분석

```http
POST   /api/inquiries
GET    /api/inquiries/faqs
GET    /admin/api/inquiries?page=0&size=20&unresolvedOnly=true
POST   /admin/api/inquiries/{id}/resolve
GET    /admin/api/inquiry-faqs
POST   /admin/api/inquiry-faqs
PUT    /admin/api/inquiry-faqs/{id}
DELETE /admin/api/inquiry-faqs/{id}
POST   /api/events
GET    /admin/api/analytics/summary?days=14
GET    /admin/api/analytics/dashboard?range=today
```

### 관리자 과목 검토

```http
POST /admin/api/subject-import-plans
GET  /admin/api/subject-import-plans/{planId}
POST /admin/api/subject-import-plans/{planId}/impact
POST /admin/api/subject-import-plans/{planId}/apply
```

## Production Deployment

`.github/workflows/docker-image.yml`은 `main`의 실행 코드 변경 또는 수동 실행 시 다음 순서로 배포합니다.

1. `./gradlew clean test bootJar`
2. GitHub OIDC Workload Identity로 Google Cloud 인증
3. 현재 100% 트래픽 리비전 기록
4. `gcloud run deploy --source . --no-traffic`으로 태그된 후보 리비전 생성
5. 후보 URL에서 health, 과목 API, 인증 경계, 캐시 metric smoke test
6. 현재 설정처럼 `SESSION_BRIDGE_MODE=off`이면 10% -> 50% -> 100%로 단계 승격
7. 각 승격 뒤 공개 서비스 검증 실패 시 직전 리비전으로 100% rollback

현재 저장소에 선언된 운영 rollout 설정은 다음과 같습니다. 비밀번호와 DB 접속 정보는
Secret Manager에서 주입되며 문서에 기록하지 않습니다.

| 설정 | 값 |
|---|---:|
| Cloud Run region | `asia-northeast3` |
| CPU / memory | 1 vCPU / 1 GiB |
| Min / max instances | 0 / 3 |
| Concurrency | 40 |
| Request timeout | 300초 |
| JDBC pool per instance | maximum 10 / minimum idle 2 |
| Shared session | Spring Session JDBC enabled |
| Session bridge | off |
| Subject cache | Caffeine L1 + Redis L2 |
| Cache invalidation compatibility | DB version poll/publish enabled |
| Schema | Flyway enabled, production Hibernate `ddl-auto=validate` |

PR은 별도 `.github/workflows/pull-request-validation.yml`에서
`./gradlew clean test bootJar --no-daemon`을 통과해야 합니다. 상세 검증과 rollback 절차는
[`docs/portfolio/operations-runbook.md`](docs/portfolio/operations-runbook.md)를 참고하세요.

## Performance and Observability

성능 숫자는 테스트 날짜와 시나리오를 함께 인용해야 합니다. 특히 Redis 도입 결과는 큰 속도 향상이
아니라 다중 인스턴스의 공유 L2와 장애 격리를 얻으면서 기준선과 유사한 처리 성능을 유지한 결과입니다.

- 성능 근거: [`docs/portfolio/performance-evidence.md`](docs/portfolio/performance-evidence.md)
- 관측 지표와 로컬 대시보드: [`docs/observability.md`](docs/observability.md)
