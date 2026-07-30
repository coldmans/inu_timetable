---
name: inu-subject-import
description: INU 강의계획서 JSON의 변경점과 사용자 영향을 검토하고 관리자가 승인한 과목만 안전하게 반영한다.
---

# INU Subject Import

강의계획서 JSON 반영 작업은 [`docs/admin-subject-import.md`](../../../docs/admin-subject-import.md)의 관리자 화면과 API만 사용한다.

## Required flow

1. JSON 파일과 대상 학기를 확인한다.
2. 미리보기를 생성하되 DB 반영은 하지 않는다.
3. 변경 유형별 과목 수와 과목별 before/after를 정리한다.
4. 시간표 사용자, 위시리스트 사용자, 중복 제거 전체 사용자, 예상 충돌을 확인한다.
5. 사용자가 명시적으로 선택하거나 전체 반영을 승인할 때만 해당 학수번호를 apply 요청에 넣는다.
6. `verified=true`와 모든 `verification[].matched=true`를 확인한다.
7. 폐강 및 충돌로 제거된 시간표 항목을 reconciliation 결과로 보고한다.

## Never

- preview 없이 apply하지 않는다.
- 사용자가 승인하지 않은 학수번호를 선택하지 않는다.
- 과목 또는 사용자 시간표를 직접 SQL로 수정하지 않는다.
- stale plan의 `409 Conflict`를 우회하지 않는다.
- 성공 응답만 보고 DB 반영이 정확하다고 단정하지 않는다.
- 별도 요청 없이 머지하거나 운영 배포하지 않는다.
