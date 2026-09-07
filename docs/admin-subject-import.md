# 관리자 과목 파일 검토와 선택 반영

관리자 화면 `/admin/import-review`와 `/admin/api/subject-import-plans` API는 통합정보시스템 파일을 현재 DB와 비교한 뒤 선택한 학수번호만 반영합니다. 미리보기 생성, 영향 재계산, 반영은 서로 분리되어 있습니다.

## 지원 형식

### 강의계획서 JSON

- 파일 확장자 또는 content type이 JSON이어야 합니다.
- 최상위 `rows` 배열을 읽습니다.
- 학기는 `yy`와 `tmGbn`으로 선택합니다. `10`, `20`, `30`, `40`은 각각 1학기, 2학기, 여름, 겨울로 해석합니다.
- 주요 필드는 `haksuNo`, `scNm`, `hp`, `profNm`, `hgMjNm`, `hySeqGbn`, `cptnGbn`, `timeNm`입니다.
- 선택한 학기에 해당하는 행이 없거나 학수번호가 비었거나 중복되면 `400 Bad Request`입니다.

### Excel `.xlsx`

첫 번째 sheet의 상단 11개 행 안에서 `학수번호`, `교과목명` 헤더를 찾습니다. 다음 컬럼을 사용합니다.

| 구분 | 헤더 |
|---|---|
| 필수 | `학수번호`, `교과목명`, `학점`, `담당교수`, `학과(부)`, `학년`, `이수구분`, `시간표(교시)` 또는 `시간표` |
| 선택 | `수업유형`/`수업구분`/`수업방법`, `강의계획서입력여부` |
| 학기 검증 | `년도` + `학기`, 또는 헤더 위의 `20xx학년도 n학기` 제목 |

`시간표(교시)`가 있으면 `OFFICIAL_TIMETABLE`, `시간표` 형식이면 `SYLLABUS`로 분류합니다. 파일에서 학기를 확인할 수 있을 때 선택 학기와 다르거나 여러 학기가 섞여 있으면 업로드를 거부합니다. 한 요일의 연속 교시는 수업 구간으로 합치되, 강의실이 바뀌면 `schedule_room_segments`에 구간별 강의실을 보존합니다.

> 이 안전 검토 API의 JSON/Excel 파싱은 결정적 필드 매핑을 사용합니다. Gemini는 별도의 PDF/레거시 Excel 파싱 경로에 사용되며 이 검토 계획 생성에는 사용되지 않습니다.

## 운영 절차

1. 적용하려는 DB와 현재 학기를 확인합니다.
2. 파일 안의 학기 분포를 먼저 확인합니다. 혼합 학기 Excel을 하나의 학기로 지정하지 않습니다.
3. 파일을 업로드해 미리보기를 생성합니다. 이 단계는 과목 데이터를 변경하지 않습니다.
4. `semester`, `sourceFormat`, `totalRows`, `changedCount`, `warnings`를 확인합니다.
5. 변경 유형과 각 과목의 `before`, `after`, `fields`, 시간·강의실 구간을 검토합니다.
6. 반영할 학수번호만 선택하고 영향 계산을 다시 요청합니다.
7. 시간표 사용자, 위시리스트 사용자, 예상 충돌, 폐강 제거 대상을 분리해 확인합니다.
8. 사용자의 명시적 승인을 받은 뒤 같은 `planId`와 선택 목록으로 반영합니다.
9. `applied=true`, `verified=true`, 모든 `verification[].matched=true`를 확인합니다.
10. 사용자 알림, 과목 업데이트 로그, 공개 과목 조회를 확인하고 작업 기록을 남깁니다.

변경 유형은 추가, 폐강, 시간 변경, 강의실 변경, 재개설, 기타 정보 변경으로 구분됩니다. 강의계획서 첨부 여부만 달라진 경우는 서비스 과목 데이터의 변경으로 보지 않습니다.

## 안전 장치

- `POST` 미리보기는 검토 계획만 저장하며 과목을 변경하지 않습니다.
- 선택 목록에 없는 학수번호는 반영하지 않으며, 계획에 없는 학수번호 요청은 거부합니다.
- `deactivateMissing=true`일 때 파일에서 빠진 해당 학기 과목은 물리 삭제하지 않고 `active=false`로 바꿀 후보가 됩니다.
- 미리보기 이후 선택 과목의 DB snapshot이 바뀌면 `409 Conflict`로 중단합니다. 스케줄과 강의실 구간도 stale 비교 대상입니다.
- 비교 정책 버전이 달라진 이전 계획은 `409 Conflict`로 거부하고 새 업로드를 요구합니다.
- 계획 row는 apply 동안 pessimistic write lock을 잡으며, 기본 운영 설정은 PostgreSQL advisory lock으로 전체 과목 반영 작업을 직렬화합니다.
- 이미 반영된 계획은 다시 사용할 수 없습니다.
- 시간 변경으로 기존 시간표가 충돌하거나 과목이 폐강되면 관련 시간표 항목을 제거하고 사용자 알림을 남깁니다.
- 반영 직후 DB를 다시 읽어 계획의 `after`와 비교합니다. 불일치가 하나라도 있으면 트랜잭션을 rollback합니다.
- 검토 계획에는 원본 파일 SHA-256, 선택 가능한 변경, 사용자 영향과 반영 결과가 저장됩니다.
- 검토 원문과 결과는 기본 90일 보존 후 매일 한국시간 03:35에 정리합니다. `SUBJECT_IMPORT_PLAN_RETENTION_DAYS`, `SUBJECT_IMPORT_PLAN_CLEANUP_CRON`으로 조정할 수 있습니다.

## 인증과 CSRF

모든 관리자 API는 현재 관리자 세션이 필요합니다. `GET` 조회는 CSRF 헤더가 필요하지 않습니다. `POST`, `PUT`, `DELETE`처럼 상태를 바꾸는 요청은 세션 cookie와 Spring CSRF token을 함께 보내야 합니다.

브라우저 SPA는 credential을 포함해 `GET /api/auth/csrf`를 호출하고, 응답 token을 이후 변경 요청의 `X-XSRF-TOKEN` 헤더로 전송합니다. 관리자 bootstrap 비밀번호로 로그인한 뒤 비밀번호 변경이 요구되는 상태에서는 import 접근이 허용되지 않습니다.

## API 계약

### 1. 미리보기 생성

```http
POST /admin/api/subject-import-plans
Content-Type: multipart/form-data

file=<JSON 또는 .xlsx 파일>
semester=2026-2
deactivateMissing=true
```

응답에서 `planId`, `sourceFormat`, `changes[].courseCode`, `categories`, `fields`, `before`, `after`, `impact`, `warnings`를 검토합니다.

### 2. 저장된 계획 조회

```http
GET /admin/api/subject-import-plans/{planId}
```

페이지를 다시 열거나 검토를 이어갈 때 사용합니다. 조회만으로 impact나 DB 상태를 새로 계산하지는 않습니다.

### 3. 선택 영향 재계산

```http
POST /admin/api/subject-import-plans/{planId}/impact
Content-Type: application/json

{
  "courseCodes": ["AAA0001", "BBB0002"]
}
```

`timetableUsers`, `wishlistUsers`, 중복 제거된 `totalUsers`, `conflictUsers`, `conflictEntries`, `cancellationTimetableEntries`를 확인합니다. 충돌 상세는 최대 100건이며 잘렸다면 `conflictsTruncated=true`입니다.

### 4. 선택 반영

```http
POST /admin/api/subject-import-plans/{planId}/apply
Content-Type: application/json

{
  "courseCodes": ["AAA0001", "BBB0002"]
}
```

성공 조건은 다음 세 가지를 모두 만족하는 것입니다.

- `applied=true`
- `verified=true`
- 모든 `verification[].matched=true`

`reconciliation`에는 충돌이나 폐강으로 제거된 시간표 항목과 알림 처리 결과가 포함됩니다.

## 오류 대응

| 응답 | 의미 | 조치 |
|---|---|---|
| `400` | 파일/학기/헤더/학수번호/선택 목록 오류 | 원본과 선택 학기를 수정해 새 계획 생성 |
| `403` | 관리자 미로그인, 초기 비밀번호 변경 필요, 또는 CSRF 실패 | 인증 상태와 CSRF cookie/header 확인 |
| `404` | 계획 ID가 없음 | 올바른 환경과 `planId` 확인 |
| `409` | stale 계획, 정책 버전 불일치, 이미 반영됨, 중복 DB 키, 또는 다른 반영 진행 중 | 기존 계획을 재사용하지 말고 현 DB에서 새 미리보기 생성 |
| `5xx` | 저장 후 검증 실패 또는 인프라 오류 | 재시도 전에 DB/로그/트랜잭션 rollback 여부 확인 |

## 에이전트와 운영자 원칙

- 사용자가 반영을 명시적으로 승인하기 전에는 미리보기와 영향 계산까지만 수행합니다.
- 과목, 시간표, 위시리스트를 직접 SQL로 고쳐 import 결과를 맞추지 않습니다.
- 운영 데이터를 수정하기 전에 현재 동작과 diff를 먼저 관찰합니다.
- `409 Conflict` 뒤에는 기존 계획을 재사용하지 않습니다.
- API 성공만 보고 완료로 판단하지 않고 `verified`와 각 `matched`를 확인합니다.
- 머지와 운영 배포는 데이터 반영과 별도 승인 사항입니다.
