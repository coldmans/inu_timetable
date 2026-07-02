# INU Timetable

인천대학교 학생을 위한 시간표 검색, 위시리스트, 자동 시간표 조합 서비스의 Spring Boot 백엔드입니다.

## Portfolio Summary

이 프로젝트는 단순 과제용 API가 아니라 실제 학생들이 사용한 시간표 서비스의 운영 백엔드입니다. 운영 snapshot 기준 과목 2,894개, 사용자 2,686명, 시간표 추가 14,668건, 위시리스트 6,701건의 데이터를 다뤘고, 이후 보안, 성능, 배포, 관측 가능성을 단계적으로 개선했습니다.

| Area | Portfolio Story | Evidence |
|---|---|---|
| Usage | 테스트 계정 제외 가입자 2,660명 중 2,512명이 저장 행동 수행 | `reports/usage-report-2026-04-29.md` |
| Performance | DB/search p95 30s -> 386ms, failure rate 25.79% -> 0% | `PERFORMANCE_TEST_REPORT.md` |
| Algorithm | 시간표 조합 30개 케이스 p95 348.72ms -> 9.39ms | `reports/combination-performance/README.md` |
| Security | 세션 인증, IDOR 방어, CSRF, BCrypt lazy migration, login rate limit | `src/test/java/inu/timetable/controller/UserSecurityIntegrationTest.java` |
| Admin Safety | 관리자 route 분리, CSRF, audit log, import lock | `src/test/java/inu/timetable/controller/AdminEndpointSeparationTest.java` |
| Operations | Flyway `ddl-auto=validate`, nginx blue-green deploy, rollback path | `scripts/deploy-blue-green.sh` |
| Observability | Prometheus/Grafana, p95/p99, DAU/MAU, registered/active user gauges | `docs/observability.md` |

Portfolio packet: [`docs/portfolio/README.md`](docs/portfolio/README.md)

## Portfolio Highlights

- 1인 개발로 시작한 실사용 서비스이며, 수강신청/시간표 편성 기간에 사용이 집중된 학생용 서비스입니다.
- Gemini API로 수강편람 PDF를 구조화 데이터로 변환하고, Java/Spring 로직으로 과목/교수/이수구분/시간표 데이터를 정리했습니다.
- k6 부하 테스트로 병목을 확인한 뒤 HikariCP connection pool, PostgreSQL prepared statement 설정, 검색 인덱스를 개선했습니다.
- 200명 동시 사용자 시나리오에서 평균 응답시간을 14.5초에서 약 200ms로 낮추고 실패율을 25.79%에서 0%로 줄였습니다.
- Spring Security 기반 세션 인증, CSRF, 사용자 소유권 검증, BCrypt 전환, 관리자 감사 로그를 적용했습니다.
- GitHub Actions, Docker, nginx blue-green, Flyway validate로 운영 배포 안정성을 높였습니다.

## My Role

- Spring Boot 백엔드 API 설계 및 구현
- JPA 엔티티/리포지토리/서비스 계층 구현
- PDF/Excel 기반 과목 데이터 입력 파이프라인 구현
- 과목 검색, 필터링, 위시리스트, 시간표 조합 API 구현
- k6 성능 테스트, 병목 분석, DB/connection 설정 개선
- Swagger/OpenAPI 문서화와 로컬 실행 문서 관리

## Tech Stack

| Area | Stack |
|---|---|
| Backend | Java 17, Spring Boot 3.5.4, Spring Data JPA |
| Database | PostgreSQL, Supabase, HikariCP |
| AI/Data Import | Google Gemini API, Apache POI |
| Test/Performance | JUnit 5, k6 |
| Docs/Infra | Swagger/OpenAPI, Docker, GitHub Actions, Flyway, Prometheus, Grafana |

## Features

### Subject Data Management

- 수강편람 PDF 업로드 및 Gemini 기반 파싱
- Excel `.xlsx` 파일 파싱
- 과목명, 교수명, 학년, 학과, 이수구분, 야간수업 필터링
- 요일/시간대 기반 검색

### Timetable Combination

- 목표 학점 기반 가능한 시간표 조합 생성
- 같은 시간대 수업 충돌 자동 제외
- 위시리스트 기반 우선 조합 생성
- 공강 요일과 필수 과목 조건 반영

### User Timetable

- 과목 위시리스트 저장/삭제
- 개인 시간표 저장/삭제
- 과목 메모와 시간표 조회 API

### Admin/Data Operations

- 관리자용 과목 등록/수정/삭제 API
- 공식 강의시간표 가져오기 preview/apply 흐름
- 중복 요청 방지와 관리자 접근 가드

## Performance Tuning

### Test Scenario

- 200 concurrent virtual users
- 5m 30s continuous load
- subject list/search/filter + timetable combination generation

### Initial Bottleneck

```text
Average response time: 14.5s
Failure rate: 25.79%
```

The main bottleneck was database connection starvation. With a small connection pool, many requests waited until timeout under concurrent load.

### Fixes

1. Increased HikariCP connection pool size.
2. Disabled PostgreSQL prepared statement behavior that conflicted with the Supabase connection path using `prepareThreshold=0`.
3. Fixed Korean search query encoding.
4. Added indexes for common subject search and filter paths.

```java
@Index(name = "idx_subject_name", columnList = "subjectName")
@Index(name = "idx_professor", columnList = "professor")
@Index(name = "idx_department", columnList = "department")
@Index(name = "idx_search_filter", columnList = "subjectName, grade, department")
```

### Result

| Metric | Before | After |
|---|---:|---:|
| Average response time | 14.5s | 200ms |
| p95 | 30s | 380ms |
| Failure rate | 25.79% | 0% |
| Throughput | 6.9 req/s | 50.5 req/s |

See [PERFORMANCE_TEST_REPORT.md](PERFORMANCE_TEST_REPORT.md) for details.

## API Overview

### Admin Subject Import

```http
POST /admin/api/pdf/upload
POST /admin/api/excel/upload
POST /admin/api/subjects/import/preview
POST /admin/api/subjects/import/apply
```

### Subject Search

```http
GET /api/subjects
GET /api/subjects/search
GET /api/subjects/filter
GET /api/subjects/count
```

### Timetable

```http
POST /api/timetable-combination/generate
GET  /api/timetable
POST /api/timetable/add
DELETE /api/timetable/remove
```

### Wishlist

```http
GET    /api/wishlist/user/{userId}
POST   /api/wishlist/add
DELETE /api/wishlist/remove
POST   /api/wishlist/priority
```

## Local Setup

### Environment

```bash
cp .env.example .env
```

```text
DB_URL=jdbc:postgresql://your-db-host:port/database?sslmode=require&prepareThreshold=0
DB_USERNAME=your_database_username
DB_PASSWORD=your_database_password
GEMINI_API_KEY=your_gemini_api_key
```

### Run

```bash
./gradlew bootRun
```

Production profile:

```bash
SPRING_PROFILE=prod ./gradlew bootRun
```

The server runs on `http://localhost:8080`.

### API Docs

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- API Docs: `http://localhost:8080/v3/api-docs`

## Production Deployment

Production deployments are handled by GitHub Actions in `.github/workflows/docker-image.yml`.

- Docker image: `${DOCKERHUB_USERNAME}/inutable:latest`
- Public backend port: `8080`
- Reverse proxy: nginx
- App containers: `inu-backend-blue` on `127.0.0.1:8081` and `inu-backend-green` on `127.0.0.1:8082`

Required GitHub Actions secrets:

- `OCI_HOST`: OCI instance public IP
- `OCI_USER`: SSH user, usually `ubuntu`
- `OCI_SSH_KEY`: private key matching an authorized public key on the OCI instance
- `DOCKERHUB_USERNAME`, `DOCKERHUB_TOKEN`
- `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`
- `GEMINI_KEY`
- `ADMIN_USERNAME`, `ADMIN_PASSWORD_HASH` or `ADMIN_PASSWORD`

The deployment script starts the inactive color first, waits for `/actuator/health`, switches nginx to the healthy container, and then removes the previous container. The first migration from the old single-container deployment may require stopping the legacy `inu-backend` container once so nginx can bind port `8080`; subsequent deployments switch through nginx without stopping the active app first.

Production schema changes are managed by Flyway. The prod profile uses Hibernate `ddl-auto=validate`, and the deployment script explicitly sets:

```text
SPRING_JPA_HIBERNATE_DDL_AUTO=validate
SPRING_FLYWAY_ENABLED=true
SPRING_FLYWAY_BASELINE_ON_MIGRATE=true
```

Before the first Flyway-enabled production deployment, take a database backup and verify that the existing schema can pass the migration history baseline.

## Performance Test

```bash
brew install k6
k6 run load-test.js
```
