# INU Timetable (인천대학교 시간표 관리 시스템)

## 📌 프로젝트 개요
인천대학교 학생들을 위한 지능형 시간표 관리 시스템입니다. AI를 활용한 PDF 파싱 기술과 자동 시간표 조합 알고리즘을 통해 학생들의 수강신청을 효율적으로 지원합니다.

## 🎯 주요 기능

### 1. AI 기반 PDF 파싱
- **Gemini AI 통합**: Google Gemini API를 활용한 강의 시간표 PDF 자동 파싱
- **정확한 데이터 추출**: 과목명, 학점, 교수명, 강의 시간, 강의실 등 세부 정보 자동 추출
- **다양한 형식 지원**: 여러 형태의 PDF 시간표 문서 처리 가능
- **배치 처리**: 여러 페이지를 효율적으로 처리하는 배치 파싱 기능

### 2. 스마트 시간표 조합
- **자동 조합 생성**: 위시리스트 기반 최적 시간표 자동 생성
- **시간 충돌 감지**: 강의 시간 겹침 자동 확인 및 제외
- **목표 학점 맞춤**: 설정한 목표 학점에 맞는 조합 우선 제공
- **필수/선택 과목 구분**: 필수 과목 우선 배치 후 선택 과목 조합
- **다중 조합 제시**: 여러 가능한 시간표 옵션 제공

### 3. 위시리스트 관리
- **과목 저장**: 관심 과목을 위시리스트에 추가
- **학기별 관리**: 학기별로 위시리스트 구분 관리
- **우선순위 설정**: 과목별 우선순위 지정
- **필수 과목 표시**: 반드시 들어야 하는 과목 마킹

### 4. 개인 시간표 관리
- **시간표 생성**: 선택한 과목으로 개인 시간표 구성
- **메모 기능**: 과목별 메모 추가 가능
- **학기별 조회**: 학기별 시간표 확인
- **시간표 수정**: 과목 추가/삭제 자유롭게 가능

### 5. 과목 검색 및 조회
- **다양한 필터**: 과목명, 교수명, 학과, 이수구분 등으로 검색
- **상세 정보 제공**: 과목별 강의 시간, 학점, 교수, 강의실 등 상세 정보

## 🛠 기술 스택

### Backend
- **Java 17**: 최신 LTS 버전 사용
- **Spring Boot 3.5.4**: 현대적인 웹 애플리케이션 프레임워크
- **Spring Data JPA**: ORM을 통한 효율적인 데이터 관리
- **Spring AI**: AI 모델 통합을 위한 프레임워크
- **Lombok**: 보일러플레이트 코드 감소

### AI & External APIs
- **Google Gemini 1.5 Flash**: PDF 파싱 및 데이터 추출
- **Spring AI PDF Document Reader**: PDF 문서 처리

### Database
- **PostgreSQL**: 프로덕션 환경 데이터베이스
- **H2 Database**: 개발 및 테스트 환경

### 빌드 & 배포
- **Gradle**: 의존성 관리 및 빌드 도구
- **Docker**: 컨테이너화를 통한 배포 표준화

## 🏗 시스템 아키텍처

### 핵심 컴포넌트
```
┌─────────────────────────────────────────────────────────┐
│                    Client Layer                         │
│              (RESTful API Consumer)                     │
└─────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────┐
│                  Controller Layer                       │
│  - TimetableController                                  │
│  - WishlistController                                   │
│  - PdfParseController                                   │
│  - SubjectController                                    │
└─────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────┐
│                   Service Layer                         │
│  - TimetableCombinationService (조합 알고리즘)           │
│  - PdfParseService (AI 파싱)                            │
│  - WishlistService                                      │
│  - TimetableService                                     │
└─────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────┐
│                 Repository Layer                        │
│  - SubjectRepository                                    │
│  - UserRepository                                       │
│  - WishlistRepository                                   │
│  - UserTimetableRepository                              │
└─────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────┐
│                   Database                              │
│              PostgreSQL / H2                            │
└─────────────────────────────────────────────────────────┘
```

## 💡 핵심 알고리즘

### 시간표 조합 알고리즘
```java
// 백트래킹 기반 조합 생성
1. 필수 과목들의 시간 충돌 여부 확인
2. 필수 과목을 기본으로 포함
3. 선택 과목들을 재귀적으로 추가하며 조합 생성
4. 각 조합에서 시간 충돌 검사
5. 목표 학점 범위(±3학점) 내의 조합만 수집
6. 목표 학점에 가까운 순으로 정렬
```

### 시간 충돌 감지
```java
// 효율적인 시간 겹침 검사
1. 같은 요일의 강의만 비교
2. 시작/종료 시간을 실수(double)로 변환
3. 시간 범위 중첩 여부 계산
4. O(n²) 복잡도로 모든 과목 쌍 검사
```

## 📊 데이터 모델

### 주요 엔티티
- **Subject**: 과목 정보 (과목명, 학점, 교수, 이수구분 등)
- **Schedule**: 강의 시간 정보 (요일, 시작시간, 종료시간)
- **User**: 사용자 정보
- **WishlistItem**: 위시리스트 항목 (우선순위, 필수 여부 포함)
- **UserTimetable**: 사용자 시간표 (메모, 학기 정보 포함)

### 주요 열거형
- **SubjectType**: 전심, 전핵, 심교, 핵교, 일선, 기교, 전기, 군사학, 교직
- **ClassMethod**: ONLINE, OFFLINE, BLENDED

## 🚀 실행 방법

### 필수 요구사항
- Java 17 이상
- Gradle
- PostgreSQL (프로덕션) 또는 H2 (개발)
- Google Gemini API Key

### 실행
```bash
# 의존성 설치
./gradlew build

# 애플리케이션 실행
./gradlew bootRun

# Docker 실행
docker build -t inu-timetable .
docker run -p 8080:8080 inu-timetable
```

### 환경 변수 설정
```properties
# application.properties
gemini.api.key=YOUR_GEMINI_API_KEY
spring.datasource.url=jdbc:postgresql://localhost:5432/timetable
spring.datasource.username=YOUR_USERNAME
spring.datasource.password=YOUR_PASSWORD
```

## 📈 성능 최적화

- **배치 사이즈 최적화**: Hibernate 배치 처리로 N+1 쿼리 문제 해결
- **지연 로딩**: 연관 엔티티의 효율적인 로딩
- **인덱싱**: 주요 검색 필드에 대한 데이터베이스 인덱스
- **조합 생성 제한**: 최대 조합 수 제한으로 성능 보장

## 🔒 보안

- **입력 검증**: Spring Validation을 통한 데이터 검증
- **SQL Injection 방지**: JPA를 통한 안전한 쿼리 실행
- **CORS 설정**: 허용된 도메인만 접근 가능

## 📝 API 엔드포인트

### 시간표 관리
- `POST /api/timetable/add` - 시간표에 과목 추가
- `DELETE /api/timetable/remove` - 시간표에서 과목 제거
- `GET /api/timetable/user/{userId}` - 사용자 시간표 조회
- `PUT /api/timetable/memo` - 과목 메모 업데이트
- `DELETE /api/timetable/clear` - 시간표 전체 삭제

### 위시리스트 관리
- `POST /api/wishlist/add` - 위시리스트에 과목 추가
- `DELETE /api/wishlist/remove` - 위시리스트에서 과목 제거
- `GET /api/wishlist/user/{userId}` - 사용자 위시리스트 조회
- `PUT /api/wishlist/priority` - 우선순위 업데이트
- `PUT /api/wishlist/required` - 필수 과목 설정

### 시간표 조합
- `POST /api/combinations/generate` - 최적 시간표 조합 생성

### PDF 파싱
- `POST /api/pdf/upload` - PDF 업로드 및 파싱

### 과목 검색
- `GET /api/subjects/search` - 과목 검색

## 🎓 학습 포인트

### 기술적 성과
1. **AI 통합**: 최신 생성형 AI를 실무 프로젝트에 적용
2. **알고리즘 구현**: 백트래킹을 활용한 조합 최적화 알고리즘
3. **Spring Boot 심화**: JPA, Validation, RESTful API 설계
4. **데이터 모델링**: 복잡한 도메인을 효과적으로 모델링

### 문제 해결
1. **PDF 파싱 정확도**: AI 프롬프트 엔지니어링을 통한 높은 정확도 달성
2. **성능 최적화**: 조합 알고리즘의 시간 복잡도 최적화
3. **시간 표현**: 교시 단위의 복잡한 시간 체계를 효율적으로 표현

## 👥 기여

이 프로젝트는 인천대학교 학생들의 수강신청을 돕기 위해 개발되었습니다.

## 📄 라이센스

이 프로젝트는 교육 목적으로 개발되었습니다.
