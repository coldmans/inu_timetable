# 관리자 강의계획서 JSON 반영

관리자 화면 `/admin/import-review`에서 통합정보시스템 강의계획서 JSON을 현재 DB와 비교하고, 선택한 과목만 반영할 수 있다.

## 운영 순서

1. 통합정보시스템에서 내려받은 `INU_강의계획서_연도_학기.json`을 업로드한다.
2. 반영 학기를 확인하고 `검증하고 변경점 보기`를 누른다.
3. 변경 유형과 과목별 before/after를 확인한다.
4. 반영할 과목을 체크한다. 전체 선택과 개별 선택을 모두 지원한다.
5. 선택 기준 시간표 사용자, 위시리스트 사용자, 예상 시간표 충돌을 확인한다.
6. `선택한 과목 DB 반영`을 누르고 확인 창에서 최종 반영한다.
7. `DB 재검증 완료`와 모든 과목의 `matched=true`를 확인한다.

변경 유형은 강의 추가, 폐강, 시간 변경, 강의실 변경, 강의계획서 첨부/제거, 재개설, 기타 정보 변경으로 나뉜다.

## 안전 장치

- 미리보기와 반영은 분리되어 있다. 미리보기만으로 DB 과목은 바뀌지 않는다.
- 반영 대상은 학수번호 체크 목록으로 고정하며, 체크하지 않은 과목은 수정하지 않는다.
- 미리보기 이후 DB 값이 달라졌다면 반영 요청은 `409 Conflict`로 중단된다.
- 폐강 과목은 물리 삭제하지 않고 `active=false`로 변경한다.
- 변경된 시간 때문에 기존 시간표가 충돌하면 관련 시간표 항목을 제거하고 사용자 알림을 남긴다.
- 반영 직후 저장된 과목 값을 계획의 after 값과 다시 비교한다. 하나라도 다르면 전체 트랜잭션을 롤백한다.
- 검토 계획에는 원본 파일 SHA-256, 선택 대상, 사용자 영향, 제거된 시간표 항목, 검증 결과가 남는다.
- 검토 원문과 사용자 영향 결과는 기본 90일 보존 후 매일 한국시간 03:35에 삭제한다.
  `SUBJECT_IMPORT_PLAN_RETENTION_DAYS`와 `SUBJECT_IMPORT_PLAN_CLEANUP_CRON`으로 조정할 수 있다.
- 동일 계획은 DB 행 잠금으로 한 번만 반영할 수 있고 관리자 반영 작업은 분산 잠금으로도 직렬화한다.

## 관리자 API

모든 요청은 로그인된 관리자 세션과 CSRF 헤더가 필요하다.

### 1. 미리보기 생성

```http
POST /admin/api/subject-import-plans
Content-Type: multipart/form-data

file=<JSON 파일>
semester=2026-2
deactivateMissing=true
```

응답의 `planId`, `changes[].courseCode`, `categories`, `fields`, `impact`를 검토한다.

### 2. 선택 영향 다시 계산

```http
POST /admin/api/subject-import-plans/{planId}/impact
Content-Type: application/json

{
  "courseCodes": ["AAA0001", "BBB0002"]
}
```

### 3. 선택 반영

```http
POST /admin/api/subject-import-plans/{planId}/apply
Content-Type: application/json

{
  "courseCodes": ["AAA0001", "BBB0002"]
}
```

성공 조건은 `applied=true`, `verified=true`, `verification[].matched=true`다.

## 에이전트 작업 원칙

- 사용자가 명시적으로 반영을 승인하기 전에는 미리보기와 영향 계산까지만 수행한다.
- 직접 SQL로 과목, 시간표, 위시리스트를 수정하지 않는다.
- `409 Conflict`가 발생하면 기존 계획을 재사용하지 않고 새 미리보기를 만든다.
- 사용자 영향은 시간표와 위시리스트를 분리해서 보고하고, 중복 제거된 전체 사용자 수도 함께 보고한다.
- 반영 뒤에는 API 성공 여부만 보지 말고 `verified`와 각 `matched` 값을 확인한다.
- 머지와 운영 배포는 별도 승인 사항이다.
