# 인천대 시간표 마법사 발표자료 원천 문서

이 문서는 Gemini 또는 NotebookLM에 그대로 넣기 위한 단일 텍스트 자료다. 발표자료를 만들 때 필요한 서비스 기획 의도, 현재 구현 방식, 데이터 지표, 성능 개선 과정, 해석상 주의사항을 한 파일에 모았다.

작성 시각: 2026-04-29 KST
대상 프로젝트: 인천대 시간표 마법사, `inu_timetable`
저장소 기준 경로: `/Users/coldmans/Documents/GitHub/inu_timetable`
핵심 참고 파일: `README.md`, `PERFORMANCE_TEST_REPORT.md`, `reports/usage-report-2026-04-29.md`, `src/main/java/inu/timetable/**`

## 1. 프로젝트 한 줄 요약

인천대 시간표 마법사는 학생이 수강편람/종합강의시간표 데이터를 기반으로 과목을 검색하고, 위시리스트에 담고, 목표 학점과 공강 조건에 맞는 시간표 조합을 자동으로 만들어보는 서비스다.

발표에서 쓸 수 있는 한 문장:

> 시간표를 짜기 위해 여러 과목을 직접 대조하던 과정을, 과목 검색-위시리스트-자동 조합-시간표 저장 흐름으로 바꾼 인천대 학생용 시간표 조합 서비스입니다.

## 2. 문제 정의와 기획 의도

학생이 시간표를 짤 때 겪는 문제는 단순히 과목을 찾는 것에 그치지 않는다. 과목명, 교수명, 학년, 학과, 이수구분, 요일, 시간대, 야간 여부, 학점 등을 동시에 고려해야 하고, 선택한 과목끼리 시간이 겹치지 않는지도 직접 확인해야 한다. 특히 수강신청 또는 시간표 업데이트 시점에는 짧은 시간 안에 여러 대안을 비교해야 하므로 부담이 커진다.

이 프로젝트의 기획 의도는 다음과 같다.

1. 과목 정보를 한 곳에서 검색하고 필터링하게 한다.
2. 관심 과목을 위시리스트에 모아두게 한다.
3. 사용자가 원하는 목표 학점과 공강 요일 조건을 입력하면 가능한 시간표 조합을 자동으로 만들어준다.
4. 마음에 드는 과목 조합을 실제 내 시간표로 저장하게 한다.
5. 수강편람 데이터 입력과 업데이트도 관리자가 반복 가능하게 만든다.

즉, 이 서비스는 단순 과목 검색기가 아니라, 시간표 편성 과정 전체를 지원하는 도구다.

## 3. 운영 맥락

사용자 확인에 따르면 2026-02-04 오후 11시 30분경 에브리타임 시간표가 업데이트되었다. 현재 리포트는 일 단위 집계이므로 23:30 직후의 분 단위 피크를 직접 증명하지는 못한다. 다만 가입과 저장 행동이 2026-02-01부터 2026-02-04까지 매우 집중되어 있고, 2026-02-05부터 급감하기 때문에 수강신청/시간표 편성 시점과 서비스 사용량이 맞물렸다고 해석할 수 있다.

발표에서는 다음처럼 말하는 것이 안전하다.

> 2월 4일 밤 에타 시간표 업데이트 시점과 맞물려, 2월 1일부터 4일까지 가입과 저장 행동이 집중적으로 발생했습니다. 일 단위 로그 기준으로 초기 4일에 전체 가입자의 96.8%, 저장 행동의 98.0%가 몰렸습니다.

## 4. 현재 서비스 흐름

일반 사용자 흐름은 다음과 같다.

1. 회원가입 또는 로그인한다.
2. 과목명, 교수명, 학년, 학과, 이수구분, 요일, 시간대 등의 조건으로 과목을 검색한다.
3. 듣고 싶은 과목을 위시리스트에 담는다.
4. 위시리스트 안에서 우선순위와 필수 포함 여부를 조정한다.
5. 목표 학점과 공강 요일을 입력해 시간표 조합을 생성한다.
6. 생성된 조합 중 마음에 드는 과목을 실제 시간표에 저장한다.
7. 저장된 시간표에서 과목을 제거하거나 메모를 남길 수 있다.

관리자/운영자 흐름은 다음과 같다.

1. 관리자 로그인으로 세션과 CSRF 토큰을 얻는다.
2. PDF 또는 Excel 파일을 업로드해 과목 데이터를 입력한다.
3. 공식 Excel import는 preview 단계에서 추가/수정/비활성화 대상을 미리 확인한다.
4. apply 단계에서 과목코드와 학기를 기준으로 과목을 추가 또는 수정한다.
5. 누락된 과목은 옵션에 따라 `active=false` 처리할 수 있다.
6. 같은 import 작업이 동시에 실행되지 않도록 운영 락이 걸려 있다.

## 5. 핵심 기능

### 과목 데이터 입력

과목 데이터는 PDF 또는 Excel 기반으로 입력된다. README 기준으로 PDF 파싱에는 Gemini API를 활용하고, Excel 파싱에는 Apache POI가 사용된다. 현재 코드에는 공식 Excel import의 preview/apply 흐름도 포함되어 있어, 데이터 변경 전 차이를 확인하고 적용할 수 있다.

관련 코드:

- `src/main/java/inu/timetable/controller/PdfParseController.java`
- `src/main/java/inu/timetable/service/PdfParseService.java`
- `src/main/java/inu/timetable/service/ExcelParseService.java`
- `src/main/java/inu/timetable/service/OfficialSubjectImportService.java`

### 과목 검색과 필터링

과목 조회 API는 페이지네이션을 지원한다. 검색은 과목명과 교수명 기준으로 가능하고, 필터는 학과, 학년, 요일, 시간대, 이수구분, 야간 여부, 학점 등을 받을 수 있다.

특징적인 구현은 `/api/subjects/filter`의 2단계 조회 방식이다.

1. 필터 조건에 맞는 과목 ID를 먼저 페이지네이션으로 조회한다.
2. 조회된 ID 목록으로 과목 상세 정보와 시간표 정보를 함께 가져온다.

이 방식은 과목과 스케줄을 바로 조인해 페이지네이션할 때 생길 수 있는 중복/페이지 왜곡 문제를 줄이기 위한 구조다.

관련 코드:

- `src/main/java/inu/timetable/controller/SubjectController.java`
- `src/main/java/inu/timetable/repository/SubjectRepository.java`

### 위시리스트

위시리스트는 사용자가 관심 있는 과목을 학기별로 저장하는 기능이다. 각 위시리스트 항목에는 우선순위와 필수 포함 여부가 있다.

위시리스트가 중요한 이유는 자동 시간표 조합의 입력 데이터가 되기 때문이다. 사용자가 모든 과목을 대상으로 조합을 만들면 경우의 수가 너무 커지므로, 먼저 관심 과목을 모으고 그 범위 안에서 조합을 만든다.

관련 코드:

- `src/main/java/inu/timetable/controller/WishlistController.java`
- `src/main/java/inu/timetable/service/WishlistService.java`
- `src/main/java/inu/timetable/entity/WishlistItem.java`

### 자동 시간표 조합

자동 조합은 위시리스트 기반으로 동작한다.

동작 방식:

1. 사용자 ID와 학기를 기준으로 위시리스트를 불러온다.
2. 같은 과목명이 여러 개 있으면 중복을 줄이기 위해 과목명 기준으로 한 번만 선택한다.
3. 필수 과목과 선택 과목을 나눈다.
4. 필수 과목끼리 시간이 겹치거나 공강 요일 조건을 위반하면 조합 생성을 중단한다.
5. 선택 과목을 재귀적으로 추가하면서 목표 학점 ±3 범위의 조합을 만든다.
6. 각 조합에 대해 시간표 충돌 여부와 공강 요일 조건을 검사한다.
7. 목표 학점에 가까운 순서로 정렬하고, `maxCombinations`만큼 반환한다.

자동 조합 결과에는 총 학점, 과목 수, 이수구분 분포, 요일별 수업 수, 실제 공강 요일 같은 통계도 함께 만들 수 있다.

관련 코드:

- `src/main/java/inu/timetable/controller/TimetableCombinationController.java`
- `src/main/java/inu/timetable/service/TimetableCombinationService.java`

### 시간표 저장

사용자가 특정 과목을 실제 시간표에 추가할 때 서버는 이미 저장된 과목과 새 과목의 요일/시간을 비교한다. 시간이 겹치면 저장하지 않고 에러를 반환한다. 시간표에는 학기와 메모도 저장된다.

관련 코드:

- `src/main/java/inu/timetable/controller/TimetableController.java`
- `src/main/java/inu/timetable/service/TimetableService.java`
- `src/main/java/inu/timetable/entity/UserTimetable.java`

## 6. 백엔드 기술 구조

기술 스택:

- Java 17
- Spring Boot 3.5.4
- Spring Data JPA
- PostgreSQL, Supabase
- HikariCP
- Flyway
- Apache POI
- Google Gemini API, PDF 파싱용
- Springdoc OpenAPI, Swagger
- Actuator, Prometheus
- k6, 부하 테스트

주요 설정:

- 운영 DB는 PostgreSQL을 사용한다.
- Supabase Pooler와 Hibernate Prepared Statement 충돌을 피하기 위해 JDBC URL에 `prepareThreshold=0`을 사용한다.
- HikariCP connection pool은 `maximum-pool-size: 30`, `minimum-idle: 10`으로 설정되어 있다.
- JPA에는 batch fetch size와 batch insert/update 설정이 있다.
- Flyway로 중복 방지 인덱스와 과목 공식 키 관련 마이그레이션을 관리한다.
- Actuator와 Prometheus endpoint를 열어 모니터링 기반을 마련했다.

## 7. 주요 데이터 모델

### User

사용자 계정이다.

저장 필드:

- `id`
- `username`
- `password`
- `nickname`
- `grade`
- `major`
- `created_at`

일반 사용자 인증은 자체 SHA-256 해시 비교 방식이다. 발표에서는 MVP형 사용자 인증이라고 설명하는 것이 정확하다. 관리자 인증은 별도 구현으로 BCrypt, 세션, CSRF, 로그인 실패 제한을 사용한다.

### Subject

과목 원장이다.

저장 필드:

- `id`
- `course_code`
- `semester`
- `active`
- `subject_name`
- `credits`
- `professor`
- `is_night`
- `subject_type`
- `class_method`
- `grade`
- `department`

검색 성능을 위해 과목명, 교수명, 학과, 학년, 이수구분, 야간 여부, 복합 검색 조건 등에 인덱스가 정의되어 있다.

### Schedule

과목의 시간 정보를 저장한다.

저장 필드:

- `id`
- `subject_id`
- `day_of_week`
- `start_time`
- `end_time`

### WishlistItem

사용자가 관심 과목으로 저장한 항목이다.

저장 필드:

- `id`
- `user_id`
- `subject_id`
- `semester`
- `priority`
- `is_required`
- `added_at`

### UserTimetable

사용자가 실제 시간표에 추가한 과목이다.

저장 필드:

- `id`
- `user_id`
- `subject_id`
- `semester`
- `added_at`
- `memo`

### 중복 방지 정책

Flyway 마이그레이션으로 다음 유니크 인덱스를 둔다.

- `user_timetables`: `user_id + subject_id + semester`
- `wishlist_items`: `user_id + subject_id + semester`
- `subjects`: `semester + course_code`, 단 null 제외 조건부 unique

이 덕분에 같은 사용자가 같은 학기에 같은 과목을 중복 저장하는 문제를 DB 수준에서도 막을 수 있다.

## 8. API 구조 요약

인증:

- `POST /api/auth/register`: 사용자 회원가입
- `POST /api/auth/login`: 사용자 로그인

과목:

- `GET /api/subjects`: 과목 목록, 페이지네이션
- `GET /api/subjects/count`: 활성 과목 수
- `GET /api/subjects/search`: 과목명 검색
- `GET /api/subjects/search/professor`: 교수명 검색
- `GET /api/subjects/filter`: 조건 필터
- `GET /api/subjects/departments`: 학과 목록
- `GET /api/subjects/grades`: 학년 목록

위시리스트:

- `POST /api/wishlist/add`: 위시리스트 추가
- `DELETE /api/wishlist/remove`: 위시리스트 제거
- `GET /api/wishlist/user/{userId}`: 사용자 위시리스트 조회
- `PUT /api/wishlist/priority`: 우선순위 변경
- `PUT /api/wishlist/required`: 필수 과목 여부 변경

시간표:

- `POST /api/timetable/add`: 내 시간표에 과목 추가
- `DELETE /api/timetable/remove`: 내 시간표에서 과목 제거
- `GET /api/timetable/user/{userId}`: 내 시간표 조회
- `PUT /api/timetable/memo`: 시간표 과목 메모 변경
- `DELETE /api/timetable/clear`: 시간표 전체 삭제

자동 조합:

- `POST /api/timetable-combination/generate`: 위시리스트 기반 시간표 조합 생성
- `GET /api/timetable-combination/stats/{userId}`: 조합 가능 여부/간단 통계 조회

관리자:

- `POST /api/admin/auth/login`: 관리자 로그인
- `GET /api/admin/auth/me`: 관리자 로그인 상태 확인
- `POST /api/admin/auth/logout`: 관리자 로그아웃
- `POST /api/subjects/import/preview`: 공식 Excel import 미리보기
- `POST /api/subjects/import/apply`: 공식 Excel import 적용
- `POST /api/pdf/upload`: PDF 업로드 및 파싱
- `POST /api/excel/upload`: Excel 업로드 및 파싱

## 9. 실제 이용 지표 산출 기준

지표 기준일: 2026-04-29 KST
데이터 기준: 운영 PostgreSQL DB의 `users`, `wishlist_items`, `user_timetables`, `subjects` 테이블
출처 파일: `reports/usage-report-2026-04-29.md`

테스트 계정은 제외했다.

제외 조건:

- `username = '202101681'`
- `lower(username) LIKE '%test%'`
- `lower(username) LIKE '%gaia%'`

위 조건에 해당하는 테스트 계정은 25개였고, 이 계정들이 남긴 저장 행동 88건을 제외했다.

현재 DB에는 검색 클릭, 필터 사용, 조합 생성 버튼 클릭, 페이지 방문, 체류 시간, 재방문 같은 이벤트 로그가 별도로 저장되어 있지 않다. 따라서 이번 리포트에서 "실제 사용자"는 가입 후 아래 행동 중 하나라도 남긴 사용자로 정의했다.

- 위시리스트에 과목 담기
- 내 시간표에 과목 추가

따라서 이 수치는 "저장 행동 기준의 실사용 전환율"이다. 검색만 하고 저장하지 않은 사용자, 비로그인 방문자, 페이지 방문자는 포함되지 않는다.

## 10. 전체 이용 지표

핵심 수치:

- 총 가입자: 2,660명
- 실제 사용자: 2,512명
- 실제 사용률: 94.4%
- 총 저장 행동: 21,292건
- 시간표 과목 추가: 14,672건
- 시간표 추가 사용자: 2,420명
- 위시리스트 담기: 6,620건
- 위시리스트 사용자: 1,044명
- 활성 사용자당 평균 저장 행동: 8.48건
- 가입 기간: 2026-02-01부터 2026-03-23까지
- 저장 행동 발생 기간: 2026-02-01부터 2026-04-21까지

발표용 문장:

> 테스트 계정을 제외하고 총 2,660명이 가입했고, 이 중 2,512명인 94.4%가 실제로 과목을 담거나 시간표에 추가했습니다. 총 저장 행동은 21,292건이었고, 시간표 과목 추가가 14,672건으로 위시리스트 6,620건보다 약 2.2배 많았습니다.

## 11. 학년별 이용 지표

| 학년 | 가입자 | 실제 사용자 | 사용률 | 위시리스트 | 시간표 추가 | 저장 행동 합계 | 활성 사용자당 평균 |
|---:|---:|---:|---:|---:|---:|---:|---:|
| 1 | 586 | 512 | 87.4% | 1,729 | 2,957 | 4,686 | 9.15 |
| 2 | 852 | 811 | 95.2% | 1,988 | 5,223 | 7,211 | 8.89 |
| 3 | 659 | 644 | 97.7% | 1,606 | 3,770 | 5,376 | 8.35 |
| 4 | 563 | 545 | 96.8% | 1,297 | 2,722 | 4,019 | 7.37 |

해석:

- 가입자 수와 저장 행동 수 모두 2학년이 가장 많다.
- 사용률은 3학년이 97.7%로 가장 높다.
- 1학년도 테스트 계정 제외 후 586명 가입, 512명 실제 사용으로 충분히 큰 이용층을 만들었다.

발표용 문장:

> 학년별로는 2학년이 가입자 852명, 저장 행동 7,211건으로 가장 많이 사용했습니다. 사용률은 3학년이 97.7%로 가장 높았습니다.

## 12. 전공별 이용 지표

전공별 사용량 Top 10:

| 전공 | 가입자 | 실제 사용자 | 사용률 | 위시리스트 | 시간표 추가 | 저장 행동 합계 | 활성 사용자당 평균 |
|---|---:|---:|---:|---:|---:|---:|---:|
| 컴퓨터공학부 | 225 | 199 | 88.4% | 526 | 1,122 | 1,648 | 8.28 |
| 무역학부 | 117 | 110 | 94.0% | 293 | 605 | 898 | 8.16 |
| 기계공학과 | 110 | 95 | 86.4% | 267 | 552 | 819 | 8.62 |
| 경영학부 | 113 | 107 | 94.7% | 268 | 548 | 816 | 7.63 |
| 전기공학과 | 83 | 75 | 90.4% | 208 | 496 | 704 | 9.39 |
| 디자인학부 | 66 | 63 | 95.5% | 275 | 425 | 700 | 11.11 |
| 전자공학과 | 78 | 77 | 98.7% | 248 | 396 | 644 | 8.36 |
| 스포츠과학부 | 75 | 73 | 97.3% | 148 | 483 | 631 | 8.64 |
| 산업경영공학과 | 67 | 66 | 98.5% | 239 | 350 | 589 | 8.92 |
| 정보통신공학과 | 74 | 71 | 95.9% | 161 | 421 | 582 | 8.20 |

해석:

- 컴퓨터공학부가 가입자 225명, 실제 사용자 199명, 저장 행동 1,648건으로 사용량 1위다.
- 디자인학부는 활성 사용자당 평균 저장 행동이 11.11건으로 상위 전공 중 사용 깊이가 높다.
- 공학계열뿐 아니라 무역학부, 경영학부, 스포츠과학부에서도 사용량이 크게 나왔다.

발표용 문장:

> 전공별로는 컴퓨터공학부가 저장 행동 1,648건으로 가장 많이 사용했습니다. 다만 무역학부, 경영학부, 스포츠과학부처럼 비공학계열에서도 높은 사용량이 나와, 특정 전공만을 위한 서비스가 아니라 학교 전체 시간표 문제를 해결한 서비스였다고 볼 수 있습니다.

## 13. 전공별 사용률 Top 10

가입자 수가 30명 이상인 전공만 비교했다.

| 전공 | 가입자 | 실제 사용자 | 사용률 | 저장 행동 합계 |
|---|---:|---:|---:|---:|
| 법학부 | 70 | 70 | 100.0% | 551 |
| 세무회계학과 | 44 | 44 | 100.0% | 360 |
| 도시행정학과 | 39 | 39 | 100.0% | 280 |
| 국어국문학과 | 37 | 37 | 100.0% | 327 |
| 바이오-로봇시스템공학과 | 37 | 37 | 100.0% | 295 |
| 패션산업학과 | 36 | 36 | 100.0% | 268 |
| 환경공학전공 | 33 | 33 | 100.0% | 294 |
| 전자공학과 | 78 | 77 | 98.7% | 644 |
| 산업경영공학과 | 67 | 66 | 98.5% | 589 |
| 경제학과 | 66 | 65 | 98.5% | 514 |

주의:

사용률 Top 10은 표본 수 30명 이상으로 제한했지만, 일부 전공은 여전히 표본이 작다. 발표에서는 "사용률이 높게 나타난 전공" 정도로 말하고, 전공 간 우열처럼 해석하지 않는 것이 좋다.

## 14. 학년 + 전공 조합 Top 12

가입자 수가 20명 이상인 조합만 비교했다.

| 학년 | 전공 | 가입자 | 실제 사용자 | 사용률 | 저장 행동 합계 |
|---:|---|---:|---:|---:|---:|
| 1 | 컴퓨터공학부 | 93 | 78 | 83.9% | 538 |
| 2 | 컴퓨터공학부 | 60 | 54 | 90.0% | 477 |
| 2 | 무역학부 | 47 | 44 | 93.6% | 415 |
| 3 | 컴퓨터공학부 | 35 | 32 | 91.4% | 335 |
| 2 | 스포츠과학부 | 35 | 34 | 97.1% | 326 |
| 3 | 경영학부 | 41 | 41 | 100.0% | 319 |
| 2 | 산업경영공학과 | 29 | 29 | 100.0% | 314 |
| 2 | 기계공학과 | 40 | 37 | 92.5% | 307 |
| 4 | 전자공학과 | 40 | 40 | 100.0% | 300 |
| 2 | 전기공학과 | 35 | 29 | 82.9% | 300 |
| 4 | 컴퓨터공학부 | 37 | 35 | 94.6% | 298 |
| 3 | 무역학부 | 34 | 34 | 100.0% | 279 |

해석:

- 1학년 컴퓨터공학부가 저장 행동 538건으로 가장 큰 학년+전공 조합이다.
- 2학년 컴퓨터공학부, 2학년 무역학부, 3학년 컴퓨터공학부도 상위권이다.
- 이는 학년과 전공에 따라 시간표 편성 수요가 다르게 나타난다는 점을 보여준다.

## 15. 가입과 저장 행동 추이

가입 추이:

| 가입일 | 가입자 | 실제 사용자 | 사용률 | 가입자 기준 저장 행동 |
|---|---:|---:|---:|---:|
| 2026-02-01 | 158 | 153 | 96.8% | 1,249 |
| 2026-02-02 | 1,348 | 1,292 | 95.8% | 11,383 |
| 2026-02-03 | 653 | 616 | 94.3% | 5,210 |
| 2026-02-04 | 417 | 382 | 91.6% | 3,022 |
| 2026-02-05 | 13 | 10 | 76.9% | 71 |
| 2026-02-06 | 10 | 10 | 100.0% | 68 |
| 2026-02-07 | 9 | 8 | 88.9% | 56 |
| 2026-02-08 이후 | 52 | 41 | 78.8% | 233 |

일자별 저장 행동:

| 일자 | 위시리스트 | 시간표 추가 | 합계 |
|---|---:|---:|---:|
| 2026-02-01 | 207 | 476 | 683 |
| 2026-02-02 | 2,553 | 5,995 | 8,548 |
| 2026-02-03 | 2,193 | 4,155 | 6,348 |
| 2026-02-04 | 1,320 | 3,100 | 4,420 |
| 2026-02-05 | 50 | 230 | 280 |
| 2026-02-06 | 12 | 90 | 102 |
| 2026-02-07 | 14 | 45 | 59 |
| 2026-02-08 이후 | 271 | 581 | 852 |

해석:

- 2026-02-01부터 2026-02-04까지 가입자 2,576명, 전체 가입자의 96.8%가 몰렸다.
- 같은 기간 저장 행동은 20,864건으로 전체 저장 행동의 98.0%다.
- 2026-02-02가 가장 큰 피크다. 가입자 1,348명, 저장 행동 8,548건이 발생했다.
- 2026-02-04에도 가입자 417명, 저장 행동 4,420건이 발생했다.
- 2026-02-05부터 가입과 저장 행동이 크게 감소한다.
- 사용자 확인 맥락인 2026-02-04 23:30 에타 시간표 업데이트와 맞물려, 시간표 정보가 갱신되는 시점에 학생들의 시간표 편성 수요가 집중된 것으로 볼 수 있다.

발표용 문장:

> 가입과 저장 행동은 2월 1일부터 4일까지 거의 대부분 발생했습니다. 이 4일 동안 전체 가입자의 96.8%, 저장 행동의 98.0%가 집중됐고, 2월 4일 밤 에타 시간표 업데이트 맥락과도 맞물립니다.

## 16. 인기 과목

인기 위시리스트 과목 Top 10:

| 과목 | 교수 | 학과 | 학년 | 위시리스트 담기 |
|---|---|---|---|---:|
| MBTI로찾아가는나의책 | 박상원 | 교양 | 전학년/미입력 | 50 |
| 대중매체속바이오테크놀로지 | 예정용 | 교양 | 전학년/미입력 | 43 |
| 영화속으로들어간클래식음악 | 김상림 | 교양 | 전학년/미입력 | 40 |
| 영화속바이러스의이해 | 예정용 | 교양 | 전학년/미입력 | 39 |
| 심리학의이해 | 서신화 | 교양 | 전학년/미입력 | 32 |
| 심리학의이해 | 박준성 | 교양 | 전학년/미입력 | 28 |
| 심리학의이해 | 서신화 | 교양 | 전학년/미입력 | 28 |
| 동물과인간사회 | 이종구 | 교양 | 전학년/미입력 | 27 |
| 배움특강 | 학생취업처 | 교양 | 전학년/미입력 | 26 |
| 예술과친구하기 | 이계원 | 교양 | 전학년/미입력 | 25 |

인기 시간표 추가 과목 Top 10:

| 과목 | 교수 | 학과 | 학년 | 시간표 추가 |
|---|---|---|---|---:|
| 대중매체속바이오테크놀로지 | 예정용 | 교양 | 전학년/미입력 | 94 |
| 영화속으로들어간클래식음악 | 김상림 | 교양 | 전학년/미입력 | 84 |
| MBTI로찾아가는나의책 | 박상원 | 교양 | 전학년/미입력 | 73 |
| 예술과친구하기 | 이계원 | 교양 | 전학년/미입력 | 63 |
| 심리학의이해 | 서신화 | 교양 | 전학년/미입력 | 49 |
| 데이터프로그래밍 | 이장호 | 컴퓨터공학부 | 2 | 48 |
| 심리학의이해 | 서신화 | 교양 | 전학년/미입력 | 47 |
| 미생물학입문 | 교육혁신본부 | 교양 | 전학년/미입력 | 46 |
| 영화로배우는독성학개론 | 예정용 | 교양 | 전학년/미입력 | 45 |
| 기업가정신 | 창업지원단 | 교양 | 전학년/미입력 | 43 |

해석:

- 인기 상위권은 대부분 교양 과목이다.
- 시간표 조합 과정에서 전공 필수 과목뿐 아니라 교양 선택 수요가 크게 나타났다고 볼 수 있다.
- 같은 과목명/교수라도 분반이 다르면 별도 과목 레코드로 집계될 수 있다.

## 17. 성능 테스트 배경

이 서비스는 이전 학기에 400명 이상이 사용했던 서비스였고, 재오픈 시 더 많은 사용자가 몰릴 수 있었다. 따라서 부하 테스트를 통해 병목을 먼저 확인했다.

테스트 도구:

- k6, Grafana Labs

테스트 조건:

- 테스트 기간: 약 5분 30초
- 최대 동시 사용자: 200명
- 대상 API: 과목 조회, 과목 검색, 과목 필터, 과목 수 조회, 시간표 조합 생성
- 트래픽 분배: 과목 조회/검색/필터 70%, 시간표 조합 생성 30%

초기 테스트 결과:

- 평균 응답시간: 14.55초
- p95 응답시간: 30초
- p99 응답시간: 30.22초
- 실패율: 25.79%
- 처리량: 6.9 req/s
- 총 요청 수: 2,399개

문제 원인:

1. HikariCP connection pool이 너무 작았다. 기본값 수준의 5개 연결로 200명 부하를 처리하면서 DB 연결 대기가 발생했다.
2. Supabase Transaction Pooler와 Hibernate Prepared Statement가 충돌했다.
3. 한글 검색어 URL 인코딩이 누락되어 검색 API가 실패했다.

## 18. 성능 최적화 과정과 결과

1차 개선:

- HikariCP `maximum-pool-size`를 30으로 증가
- `minimum-idle`을 10으로 증가
- connection timeout, idle timeout, max lifetime, leak detection threshold 설정 추가

2차 개선:

- Supabase Pooler의 Prepared Statement 충돌을 피하기 위해 JDBC URL에 `prepareThreshold=0` 적용

3차 개선:

- 한글 검색어를 URL 인코딩하도록 수정

최종 성능:

| 지표 | 개선 전 | 개선 후 |
|---|---:|---:|
| 평균 응답시간 | 14.55초 | 201ms |
| p95 응답시간 | 30초 | 386ms |
| p99 응답시간 | 30.22초 | 476ms |
| 실패율 | 25.79% | 0.00% |
| 처리량 | 6.9 req/s | 50.5 req/s |
| 총 요청 수 | 2,399개 | 16,788개 |
| 검색 API 성공률 | 0% | 100% |

발표용 문장:

> 초기 부하 테스트에서는 평균 응답시간 14.55초, 실패율 25.79%가 나왔습니다. 원인은 DB 커넥션 풀 고갈과 Supabase Pooler 호환성 문제였습니다. HikariCP 설정을 조정하고 `prepareThreshold=0`, 한글 URL 인코딩을 적용한 뒤 평균 응답시간은 201ms, 실패율은 0%, 처리량은 50.5 req/s까지 개선됐습니다.

주의:

이 성능 개선은 비즈니스 로직 자체가 72배 빨라졌다는 의미보다는, DB 연결 대기와 인프라 호환성 문제를 제거해서 타임아웃 병목을 없앤 성과로 설명하는 편이 정확하다.

## 19. 기술적 강점

첫째, 단순 CRUD가 아니라 데이터 입력부터 시간표 생성까지 이어지는 도메인 흐름이 있다. 서비스는 과목 데이터 수집/정제, 과목 검색/필터, 위시리스트, 조합 생성, 사용자 시간표 저장까지 연결된다.

둘째, 시간표 조합 로직이 실제 문제를 다룬다. 목표 학점, 필수 과목, 선택 과목, 시간 충돌, 공강 요일을 함께 고려한다.

셋째, 운영 DB 성능 문제를 실제 부하 테스트로 발견하고 개선했다. 평균 응답시간, p95, 실패율, 처리량을 측정했고, 병목 원인을 설정과 인프라 호환성까지 내려가서 해결했다.

넷째, 과목 조회/필터링에서 페이지네이션과 fetch join 문제를 의식한 2단계 조회 구조를 사용한다.

다섯째, Flyway 마이그레이션으로 중복 방지 인덱스와 과목 공식 키를 관리한다.

여섯째, 관리자 기능은 일반 사용자 기능보다 더 강한 보안 장치를 갖고 있다. 관리자 비밀번호는 BCrypt로 검증하고, 세션 고정 방지, CSRF 헤더, 로그인 실패 횟수 제한, 중복 import 방지 lock을 사용한다.

## 20. 한계와 개선 계획

현재 한계:

1. 검색, 필터, 조합 생성 클릭 같은 이벤트 로그가 없다.
2. 자동 조합 결과 자체를 히스토리로 저장하지 않는다.
3. 비로그인 방문자 수, 페이지뷰, 체류 시간, 재방문율은 알 수 없다.
4. 일반 사용자 인증은 자체 SHA-256 해시 비교 방식이라, 장기 운영 서비스로는 Spring Security/JWT/세션 보안 강화가 필요하다.
5. 사용성 퍼널을 정확히 보려면 별도 이벤트 테이블이 필요하다.
6. 현재 지표는 저장 행동 기준이므로, 검색만 하고 저장하지 않은 사용자 행동은 포함되지 않는다.

개선 계획:

1. 이벤트 로깅 추가: 검색, 필터, 조합 생성, 조합 저장, 페이지 방문 이벤트를 저장한다.
2. 퍼널 분석 추가: 방문 -> 검색 -> 위시리스트 -> 조합 생성 -> 시간표 저장 전환율을 본다.
3. 조합 결과 히스토리 저장: 어떤 조건으로 어떤 조합이 만들어졌고 실제 저장으로 이어졌는지 분석한다.
4. 사용자 인증 강화: 일반 사용자 인증도 Spring Security 기반으로 정리한다.
5. 캐싱 추가: 과목 수, 학과 목록, 학년 목록처럼 자주 바뀌지 않는 조회에 캐시를 적용한다.
6. 모니터링 확장: Prometheus/Grafana 대시보드로 운영 중 응답시간과 실패율을 본다.

## 21. 발표자료 구성 추천

슬라이드 1. 제목

- 인천대 시간표 마법사
- 시간표 편성 과정을 자동화한 학생용 시간표 조합 서비스

슬라이드 2. 문제 정의

- 시간표 편성은 과목 검색, 조건 비교, 시간 충돌 확인, 학점 계산이 모두 필요한 반복 작업
- 시간표 업데이트/수강신청 시점에는 짧은 시간 안에 많은 대안을 비교해야 함

슬라이드 3. 해결 방식

- 과목 검색/필터
- 위시리스트
- 목표 학점 기반 자동 조합
- 공강 요일/시간 충돌 검사
- 내 시간표 저장

슬라이드 4. 실제 사용량

- 테스트 계정 제외 가입자 2,660명
- 실제 사용자 2,512명
- 실사용률 94.4%
- 저장 행동 21,292건

슬라이드 5. 사용량 피크

- 2월 1일부터 4일까지 가입자 2,576명, 전체 96.8%
- 같은 기간 저장 행동 20,864건, 전체 98.0%
- 2월 4일 23:30 에타 시간표 업데이트 맥락과 맞물림

슬라이드 6. 학년/전공별 지표

- 2학년 저장 행동 7,211건으로 최다
- 컴퓨터공학부 저장 행동 1,648건으로 전공별 최다
- 비공학계열에서도 사용량이 크게 나타남

슬라이드 7. 인기 과목

- 인기 상위권은 교양 과목 중심
- 시간표 편성에서 교양 선택 수요가 큼

슬라이드 8. 기술 구조

- Spring Boot, JPA, PostgreSQL, Supabase, HikariCP, Flyway
- Gemini 기반 PDF 파싱, Excel 파싱
- 위시리스트 기반 재귀 조합 알고리즘

슬라이드 9. 성능 개선

- 평균 응답시간 14.55초 -> 201ms
- 실패율 25.79% -> 0%
- 처리량 6.9 req/s -> 50.5 req/s
- 원인: 커넥션 풀 고갈, Supabase Pooler 충돌, URL 인코딩 문제

슬라이드 10. 한계와 다음 단계

- 이벤트 로그 부재
- 검색/조합 생성 클릭 퍼널은 아직 측정 불가
- 다음 단계: 이벤트 로깅, 퍼널 분석, 조합 히스토리, 인증 강화, 모니터링

## 22. 발표 대본용 문장 모음

서비스 소개:

> 인천대 시간표 마법사는 학생들이 시간표를 짤 때 과목을 일일이 비교하고 시간이 겹치는지 확인해야 하는 문제를 줄이기 위해 만든 서비스입니다.

기획 의도:

> 단순히 과목을 보여주는 것이 아니라, 검색하고 담고 조합하고 저장하는 전체 흐름을 하나로 묶는 것이 목표였습니다.

자동 조합 설명:

> 사용자가 관심 과목을 위시리스트에 담으면, 서버는 필수 과목을 먼저 고정하고 선택 과목을 조합하면서 목표 학점과 시간 충돌, 공강 요일 조건을 검사합니다.

사용 지표:

> 테스트 계정을 제외하면 총 2,660명이 가입했고, 그중 2,512명인 94.4%가 실제로 과목을 담거나 시간표에 추가했습니다.

피크 해석:

> 2월 1일부터 4일까지 전체 가입자의 96.8%, 저장 행동의 98.0%가 몰렸습니다. 2월 4일 밤 에타 시간표 업데이트 시점과 맞물려 실제 시간표 편성 수요가 집중된 것으로 볼 수 있습니다.

성능 개선:

> 처음에는 200명 부하에서 평균 응답시간이 14.55초, 실패율이 25.79%까지 나왔습니다. 커넥션 풀과 Supabase Pooler 설정, URL 인코딩 문제를 수정한 뒤 평균 응답시간 201ms, 실패율 0%까지 개선했습니다.

한계:

> 다만 현재는 검색이나 조합 생성 클릭 같은 이벤트 로그를 저장하지 않기 때문에, 이번 지표는 저장 행동 기준의 실사용 전환율입니다.

마무리:

> 이 프로젝트는 단순히 기능을 만든 것에서 끝나지 않고, 실제 학생 사용량과 부하 테스트 지표를 통해 서비스가 언제, 어떻게, 얼마나 쓰였는지까지 확인한 프로젝트입니다.

## 23. 데이터 해석 주의사항

1. "실제 사용자"는 저장 행동 기준이다. 검색만 한 사용자는 포함되지 않는다.
2. 2026-02-04 23:30 에타 시간표 업데이트는 운영 맥락으로 확인된 정보지만, 현재 리포트는 일 단위 집계라 분 단위 인과관계를 직접 증명하지는 않는다.
3. 테스트 계정 25개와 해당 저장 행동 88건은 제외했다.
4. 전공별 사용률 100%는 가입자 30명 이상 전공만 비교했지만, 그래도 표본 수 차이를 고려해야 한다.
5. 인기 과목 집계는 과목 레코드 기준이다. 같은 과목명/교수라도 분반이 다르면 별도 레코드로 집계될 수 있다.
6. 성능 개선 수치는 k6 부하 테스트 조건에서의 결과다. 실제 운영 트래픽과 완전히 동일한 조건은 아니다.

## 24. NotebookLM에 요청할 때 사용할 프롬프트 예시

이 문서를 NotebookLM 또는 Gemini에 넣은 뒤 다음처럼 요청하면 된다.

프롬프트 1:

> 이 문서를 바탕으로 10장짜리 대학 발표용 슬라이드 구성을 만들어줘. 각 슬라이드마다 제목, 핵심 메시지, 넣을 지표, 발표자가 말할 대본을 함께 작성해줘.

프롬프트 2:

> 이 문서를 바탕으로 발표 대본을 5분 분량으로 작성해줘. 기술 설명은 너무 깊지 않게, 실제 사용자 지표와 성능 개선 성과가 잘 드러나게 해줘.

프롬프트 3:

> 이 문서에서 발표자료에 넣으면 좋은 숫자만 추려서 임팩트 순으로 정리해줘. 각 숫자마다 어떤 의미인지 한 문장으로 설명해줘.

프롬프트 4:

> 이 프로젝트의 기술적 차별점과 한계를 발표용으로 균형 있게 정리해줘. 과장된 표현은 빼고, 코드와 지표에 근거한 문장으로 작성해줘.

## 25. 원천 근거 파일 목록

서비스 소개와 기능:

- `/Users/coldmans/Documents/GitHub/inu_timetable/README.md`

성능 테스트:

- `/Users/coldmans/Documents/GitHub/inu_timetable/PERFORMANCE_TEST_REPORT.md`

운영 DB 지표:

- `/Users/coldmans/Documents/GitHub/inu_timetable/reports/usage-report-2026-04-29.md`

주요 백엔드 코드:

- `/Users/coldmans/Documents/GitHub/inu_timetable/src/main/java/inu/timetable/controller/AuthController.java`
- `/Users/coldmans/Documents/GitHub/inu_timetable/src/main/java/inu/timetable/controller/SubjectController.java`
- `/Users/coldmans/Documents/GitHub/inu_timetable/src/main/java/inu/timetable/controller/WishlistController.java`
- `/Users/coldmans/Documents/GitHub/inu_timetable/src/main/java/inu/timetable/controller/TimetableController.java`
- `/Users/coldmans/Documents/GitHub/inu_timetable/src/main/java/inu/timetable/controller/TimetableCombinationController.java`
- `/Users/coldmans/Documents/GitHub/inu_timetable/src/main/java/inu/timetable/service/AuthService.java`
- `/Users/coldmans/Documents/GitHub/inu_timetable/src/main/java/inu/timetable/service/WishlistService.java`
- `/Users/coldmans/Documents/GitHub/inu_timetable/src/main/java/inu/timetable/service/TimetableService.java`
- `/Users/coldmans/Documents/GitHub/inu_timetable/src/main/java/inu/timetable/service/TimetableCombinationService.java`
- `/Users/coldmans/Documents/GitHub/inu_timetable/src/main/java/inu/timetable/service/OfficialSubjectImportService.java`
- `/Users/coldmans/Documents/GitHub/inu_timetable/src/main/java/inu/timetable/entity/User.java`
- `/Users/coldmans/Documents/GitHub/inu_timetable/src/main/java/inu/timetable/entity/Subject.java`
- `/Users/coldmans/Documents/GitHub/inu_timetable/src/main/java/inu/timetable/entity/Schedule.java`
- `/Users/coldmans/Documents/GitHub/inu_timetable/src/main/java/inu/timetable/entity/WishlistItem.java`
- `/Users/coldmans/Documents/GitHub/inu_timetable/src/main/java/inu/timetable/entity/UserTimetable.java`

설정과 마이그레이션:

- `/Users/coldmans/Documents/GitHub/inu_timetable/src/main/resources/application.yml`
- `/Users/coldmans/Documents/GitHub/inu_timetable/src/main/resources/application-dev.yml`
- `/Users/coldmans/Documents/GitHub/inu_timetable/src/main/resources/application-prod.yml`
- `/Users/coldmans/Documents/GitHub/inu_timetable/src/main/resources/db/migration/V20260214_01__add_unique_indexes_timetable_wishlist.sql`
- `/Users/coldmans/Documents/GitHub/inu_timetable/src/main/resources/db/migration/V20260428_01__add_subject_official_keys.sql`
