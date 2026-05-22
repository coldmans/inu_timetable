# INU Timetable

인천대학교 학생을 위한 시간표 검색, 위시리스트, 자동 시간표 조합 서비스의 Spring Boot 백엔드입니다.

## Portfolio Highlights

- 1인 개발로 시작한 실사용 서비스이며, 공개 README 기준 400명 이상의 학우가 사용한 시간표 조합 서비스입니다.
- Gemini API로 수강편람 PDF를 구조화 데이터로 변환하고, Java/Spring 로직으로 과목/교수/이수구분/시간표 데이터를 정리했습니다.
- k6 부하 테스트로 병목을 확인한 뒤 HikariCP connection pool, PostgreSQL prepared statement 설정, 검색 인덱스를 개선했습니다.
- 200명 동시 사용자 시나리오에서 평균 응답시간을 14.5초에서 약 200ms로 낮추고 실패율을 25.79%에서 0%로 줄였습니다.

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
| Docs/Infra | Swagger/OpenAPI, Dockerfile |

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

### Subject Import

```http
POST /api/pdf/upload
POST /api/excel/upload
POST /api/subjects/import/preview
POST /api/subjects/import/apply
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

## Performance Test

```bash
brew install k6
k6 run load-test.js
```
