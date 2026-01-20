# 인천대 시간표 마법사

<br>

## 프로젝트 소개

저번 학기에 만들어서 **400명 넘는 학우분들이 써주신** 시간표 조합 서비스입니다.

<br>

## 주요 기능

### 📚 과목 관리
- **다양한 파일 형식 지원**
  - PDF 파싱 (Gemini AI 활용)
  - Excel 파싱 (.xlsx 형식)
- 과목명/교수명 검색
- 학년/학과/이수구분/야간수업 필터링
- 요일/시간대 필터

### 🗓️ 시간표 자동 조합
시간표 짜는데 몇 시간씩 쓰지 말고 그냥 조건만 입력하면 됩니다.
- 목표 학점 설정하면 가능한 조합 전부 생성
- 시간 겹치는 거 자동으로 거름
- 위시리스트 기반 조합 생성

### 위시리스트
듣고 싶은 과목 담아두면 나중에 조합 뽑을 때 우선으로 넣어줌

<br>

## 기술 스택

### Backend
- Java 17
- Spring Boot 3.5.4
- Spring Data JPA
- PostgreSQL (Supabase)
- HikariCP
- Apache POI (Excel 파싱)

### AI
- Google Gemini API (PDF 파싱용)

### 성능 테스트
- k6 (Grafana Labs)

<br>

## 성능 최적화 과정
좀 버벅이는 느낌이 불편해서 각종 api들을 최적화 했습니다

### 테스트 환경
- 동시 접속자 200명 시뮬레이션
- 5분 30초 동안 지속적인 부하 발생
- 과목 조회/검색 + 시간표 조합 생성

### 문제 발견
```
평균 응답시간: 14.5초
실패율: 25.79%
```

DB 연결을 5개만 열어놔서 200명이 몰리면 대부분 30초 기다리다가 타임아웃 걸렸음

### 해결 과정

**1차 시도 - Connection Pool 늘림**
```yaml
hikari:
  maximum-pool-size: 30  # 5 → 30
  minimum-idle: 10       # 2 → 10
```
결과: 평균 응답시간 **14.5초 → 162ms** (90배 개선)
근데 여전히 실패율 17.98%...

**2차 시도 - Supabase 연결 문제 해결**
- Prepared Statement 충돌 발견 (`prepareThreshold=0` 설정)
- 한글 검색어 URL 인코딩 안 돼있던 거 수정

결과: 실패율 **0%** 달성 ✅

**3차 시도 - DB 인덱스 추가**
```java
@Index(name = "idx_subject_name", columnList = "subjectName")
@Index(name = "idx_professor", columnList = "professor")
@Index(name = "idx_department", columnList = "department")
@Index(name = "idx_search_filter", columnList = "subjectName, grade, department")
// ... 총 7개 인덱스
```
결과: 과목 조회 성능 **17% 추가 개선**

### 최종 결과
| 지표 | 개선 전 | 개선 후 |
|------|---------|---------|
| 평균 응답시간 | 14.5초 | 200ms |
| p95 | 30초 | 380ms |
| 실패율 | 25.79% | 0% |
| 처리량 | 6.9 req/s | 50.5 req/s |

자세한 내용은 [성능 테스트 보고서](PERFORMANCE_TEST_REPORT.md) 참고

<br>

## API 엔드포인트

### 파일 업로드 (수강편람 데이터 입력)
```
POST /api/pdf/upload            # PDF 업로드 & AI 파싱 (Gemini)
POST /api/excel/upload          # Excel 업로드 & 파싱 (.xlsx)
```

### 과목 관리
```
GET  /api/subjects              # 과목 목록 (페이지네이션)
GET  /api/subjects/search       # 과목명 검색
GET  /api/subjects/filter       # 조건별 필터링
GET  /api/subjects/count        # 전체 과목 수
```

### 시간표
```
POST /api/timetable-combination/generate  # 시간표 조합 생성
GET  /api/timetable                       # 내 시간표 조회
```

### 위시리스트
```
GET    /api/wishlist        # 위시리스트 조회
POST   /api/wishlist        # 과목 추가
DELETE /api/wishlist/{id}   # 과목 제거
```

<br>

## 로컬 실행

### 1. 환경 변수 설정

**1단계: .env 파일 생성** (터미널에서 실행)
```bash
cp .env.example .env
```

**2단계: .env 파일 편집** (자신의 환경에 맞게 수정)
```text
DB_URL=jdbc:postgresql://your-db-host:port/database?sslmode=require&prepareThreshold=0
DB_USERNAME=your_database_username
DB_PASSWORD=your_database_password
GEMINI_API_KEY=your_gemini_api_key
```

### 2. 실행 프로필 선택
```bash
# 개발 모드 (기본값, SQL 로깅 활성화)
./gradlew bootRun

# 프로덕션 모드 (SQL 로깅 비활성화)
SPRING_PROFILE=prod ./gradlew bootRun
```

서버는 `http://localhost:8080`에서 실행됩니다.

#### Swagger API 문서
- Swagger UI: http://localhost:8080/swagger-ui.html
- API Docs: http://localhost:8080/v3/api-docs

<br>

## 성능 테스트 실행

```bash
# k6 설치 (Mac)
brew install k6

# 로컬 환경 테스트
k6 run load-test.js

# 다른 환경 테스트 (예: 스테이징)
k6 run -e BASE_URL=https://staging.example.com load-test.js
```




