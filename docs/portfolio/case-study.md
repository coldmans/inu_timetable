# Case Study: INU Timetable

## Summary

INU 시간표는 인천대학교 학생이 과목을 검색하고, 위시리스트에 담고, 시간표 조합을 생성하고, 최종 시간표를 저장하는 서비스다. 단순 CRUD 토이 프로젝트가 아니라 운영 DB 기준 2,686명 규모의 사용자와 14,668건의 시간표 추가 행동이 남은 실사용 서비스다.

## Problem

초기 버전은 기능은 동작했지만 운영 서비스로 보기에는 네 가지 약점이 있었다.

| 영역 | 문제 |
|---|---|
| 성능 | 동시 요청에서 DB connection starvation이 발생하고 p95가 timeout 수준까지 증가 |
| 보안 | 클라이언트가 보내는 `userId`를 신뢰하면 타인 데이터 접근 위험 발생 |
| 배포 | `docker rm -f` 방식은 배포 중 다운타임과 migration drift 위험이 큼 |
| 관측성 | "몇 명이 썼는지"를 설명할 운영 지표와 p95/p99 근거가 부족 |

## Actions

### 1. 성능 병목 제거

- k6로 과목 검색, 필터, 시간표 조합 API를 부하 테스트했다.
- HikariCP pool, PostgreSQL `prepareThreshold=0`, 검색 인덱스, 쿼리 경로를 정리했다.
- 시간표 조합은 과목별 수업 시간을 `BitSet` mask로 변환해 충돌 검사를 `intersects`로 처리했다.

### 2. 인증/인가 보강

- Spring Security 세션 인증을 도입했다.
- private API에서 path/query의 `userId`를 그대로 믿지 않고 `@AuthenticationPrincipal` 기반으로 본인 여부를 검증했다.
- legacy SHA-256 비밀번호는 로그인 성공 시 BCrypt로 lazy migration 되도록 처리했다.
- 일반 사용자 로그인 실패 rate limit과 관리자 API audit log를 추가했다.

### 3. 배포와 migration 안정화

- GitHub Actions -> Docker Hub -> GCP 배포 파이프라인을 유지하면서 nginx blue-green 전환을 붙였다.
- 운영 profile은 Hibernate `ddl-auto=validate`로 전환하고 Flyway migration을 source of truth로 삼았다.
- 새 컨테이너 health check 실패나 nginx reload 실패 시 기존 포트로 rollback 하도록 배포 스크립트를 구성했다.

### 4. 운영 지표 구축

- Micrometer/Prometheus/Grafana를 붙이고 HTTP p95/p99, 5xx error rate, DAU/MAU, 누적 가입자 지표를 수집했다.
- 사용자 ID를 metric label에 넣지 않고, `user_activity_daily`에서 distinct user count를 집계했다.

## Impact

| 개선 | Before | After | Evidence |
|---|---:|---:|---|
| DB/search 부하 p95 | 30s | 386ms | `PERFORMANCE_TEST_REPORT.md` |
| DB/search failure rate | 25.79% | 0% | `PERFORMANCE_TEST_REPORT.md` |
| 30개 위시리스트 조합 p95 | 348.72ms | 9.39ms | `reports/combination-performance/README.md` |
| 저장 행동 | - | 21,292건 | `reports/usage-report-2026-04-29.md` |
| 시간표 추가 | - | 14,668건 | `reports/course-data-snapshot-2026-06-12/README.md` |

## What I Would Explain In An Interview

- 이 프로젝트의 가치는 기능 수보다 "운영 중인 학생 서비스에서 병목과 보안 취약점을 발견하고, 테스트와 지표로 고친 과정"에 있다.
- Redis나 message queue 같은 큰 인프라를 바로 붙이지 않고, read-heavy 과목 조회에는 local cache와 warm-up, CPU-bound 조합 생성에는 BitSet 알고리즘처럼 문제 성격에 맞는 작은 해법부터 적용했다.
- 사용자 수는 화면 방문 추정치가 아니라 운영 DB snapshot과 Prometheus gauge로 설명한다.
