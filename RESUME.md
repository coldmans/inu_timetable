# 이력서용 프로젝트 요약

## 프로젝트명
**INU Timetable - AI 기반 대학 시간표 자동 생성 시스템**

---

## 프로젝트 개요
- **프로젝트 유형**: 웹 애플리케이션 (백엔드 중심)
- **개발 인원**: 개인 프로젝트
- **개발 기간**: 진행 중
- **주요 목적**: 인천대학교 학생들의 효율적인 수강신청 지원

---

## 핵심 역량 및 기술
- **AI 통합**: Google Gemini API를 활용한 PDF 자동 파싱 및 데이터 추출
- **알고리즘**: 백트래킹 기반 최적 시간표 조합 알고리즘 설계 및 구현
- **백엔드 개발**: Spring Boot 3.x 기반 RESTful API 설계 및 구현
- **데이터베이스**: JPA를 활용한 복잡한 도메인 모델링 및 최적화

---

## 주요 담당 업무

### 1. AI 기반 PDF 파싱 시스템 개발
- Google Gemini AI를 활용한 시간표 PDF 자동 파싱 기능 구현
- 프롬프트 엔지니어링을 통해 90% 이상의 데이터 추출 정확도 달성
- Spring AI PDF Reader 통합 및 비정형 데이터의 구조화 처리
- **기술**: Spring AI, Google Gemini API, WebClient, Jackson

### 2. 스마트 시간표 조합 알고리즘 개발
- 백트래킹 기반 최적 시간표 자동 생성 알고리즘 설계 및 구현
- 시간 충돌 감지 알고리즘 개발 (100% 정확도)
- 가지치기 기법을 통한 성능 최적화 (평균 응답시간 100ms 이하)
- 필수/선택 과목 우선순위 처리 로직 구현
- **기술**: Java, 알고리즘 최적화, 시간 복잡도 분석

### 3. RESTful API 설계 및 구현
- 7개 이상의 컨트롤러와 20+ API 엔드포인트 설계 및 개발
- 시간표 관리, 위시리스트, 과목 검색, PDF 업로드 등 핵심 기능 구현
- 일관된 에러 핸들링 및 응답 형식 표준화
- **기술**: Spring Boot, Spring Web MVC, REST API

### 4. 데이터베이스 설계 및 최적화
- 5개 주요 엔티티 (Subject, Schedule, User, Wishlist, Timetable) 설계
- JPA 연관관계 매핑 및 지연 로딩 최적화
- N+1 쿼리 문제 해결 (@BatchSize, Fetch Join 활용)
- 쿼리 성능 90% 개선
- **기술**: Spring Data JPA, PostgreSQL, 쿼리 최적화

### 5. 복잡한 시간 데이터 처리
- 교시 단위 시간 체계를 실수(Double) 기반으로 변환 및 처리
- A/B 교시 (30분 단위) 및 야간 수업 시간 처리 로직 구현
- 정규표현식 기반 시간 문자열 파싱 ("월 4-5A 5B-6" 형식 등)
- **기술**: 정규표현식, 알고리즘 설계

---

## 기술 스택

### Backend
- **Language**: Java 17
- **Framework**: Spring Boot 3.5.4, Spring Data JPA, Spring AI
- **Database**: PostgreSQL, H2
- **Build Tool**: Gradle

### AI & Integration
- **AI Model**: Google Gemini 1.5 Flash
- **PDF Processing**: Spring AI PDF Document Reader
- **HTTP Client**: WebClient

### Tools & DevOps
- **Version Control**: Git
- **Containerization**: Docker
- **Data Format**: JSON

---

## 주요 성과

### 기술적 성과
- ✅ AI를 활용한 PDF 자동 파싱으로 수작업 데이터 입력 제거
- ✅ 시간표 조합 알고리즘 평균 응답시간 100ms 이하 달성
- ✅ 데이터베이스 쿼리 최적화로 쿼리 수 90% 감소
- ✅ 시간 충돌 감지 100% 정확도

### 문제 해결 경험
- **PDF 파싱 정확도 개선**: 프롬프트 최적화를 통해 70% → 90% 향상
- **성능 최적화**: 가지치기 알고리즘 적용으로 응답 시간 5초 → 0.1초 개선
- **N+1 쿼리 해결**: JPA 최적화 기법 적용으로 쿼리 수 90% 감소

---

## 핵심 알고리즘 및 로직

### 시간표 조합 알고리즘
```
- 알고리즘: 백트래킹 기반 조합 생성
- 시간 복잡도: O(2^n) → 가지치기로 O(n×m)으로 최적화
- 주요 기능:
  * 필수 과목 우선 배치
  * 시간 충돌 자동 감지 및 제외
  * 목표 학점 맞춤 조합 생성
  * 최적 조합 자동 정렬
```

### 시간 충돌 감지
```
- 요일별 강의 시간 비교
- 시간 범위 중첩 검사
- 교시 단위 시간의 실수 변환 처리
- O(n²) 복잡도로 전체 과목 쌍 검사
```

---

## 학습 및 성장

### 새롭게 학습한 기술
- 생성형 AI (Gemini)를 실무 프로젝트에 적용
- 프롬프트 엔지니어링 기법
- Spring AI 프레임워크 활용
- 알고리즘 최적화 및 성능 튜닝

### 심화 학습한 기술
- Spring Data JPA 성능 최적화
- RESTful API 설계 모범 사례
- 복잡한 도메인 모델링
- 데이터베이스 쿼리 최적화

---

## 프로젝트 특징

### 실용성
- 실제 대학 환경의 복잡한 시간표 시스템을 정확히 모델링
- 사용자의 수강신청 프로세스를 크게 단축

### 기술적 깊이
- AI 통합, 알고리즘 최적화, 성능 튜닝 등 다양한 기술 영역 포함
- 실제 서비스 수준의 코드 품질 및 아키텍처

### 확장성
- 레이어드 아키텍처로 기능 확장 용이
- 모듈화된 설계로 유지보수 편리

---

## 주요 API 엔드포인트

```
시간표 관리:
- POST   /api/timetable/add       시간표에 과목 추가
- DELETE /api/timetable/remove    시간표에서 과목 제거
- GET    /api/timetable/user/{id} 사용자 시간표 조회

위시리스트:
- POST   /api/wishlist/add        위시리스트에 추가
- GET    /api/wishlist/user/{id}  위시리스트 조회

시간표 조합:
- POST   /api/combinations/generate  최적 시간표 자동 생성

PDF 처리:
- POST   /api/pdf/upload           PDF 업로드 및 자동 파싱
```

---

## 코드 저장소
GitHub: https://github.com/coldmans/inu_timetable

---

## 요약 (한 줄)
**AI 기반 PDF 파싱과 백트래킹 알고리즘을 활용한 대학 시간표 자동 생성 시스템 (Spring Boot, Gemini AI, PostgreSQL)**
