# INU Timetable Portfolio Packet

이 폴더는 INU 시간표 프로젝트를 면접에서 설명하기 위한 증거 묶음이다. 핵심은 "학생들이 실제로 쓴 시간표 서비스"를 운영하면서 보안, 성능, 배포, 관측 가능성을 개선했다는 점이다.

## One Line

인천대 학생용 시간표 서비스에서 2,686명 규모의 운영 데이터, 21,000건 이상의 저장 행동, 인증/인가 보안 개선, k6 기반 성능 개선, BitSet 조합 알고리즘 최적화, Flyway/blue-green 배포 전환을 직접 구현했다.

## Evidence Map

| 주제 | 핵심 주장 | 근거 |
|---|---|---|
| 실사용 | 운영 snapshot 기준 `users` 2,686 rows, `user_timetables` 14,668 rows | `reports/course-data-snapshot-2026-06-12/README.md` |
| 사용 깊이 | 테스트 계정 제외 가입자 2,660명 중 2,512명이 저장 행동 수행 | `reports/usage-report-2026-04-29.md` |
| 검색/DB 성능 | k6 p95 30s 수준 병목을 386ms 수준으로 개선 | `PERFORMANCE_TEST_REPORT.md` |
| 조합 알고리즘 | 30개 위시리스트 p95 348.72ms -> 9.39ms | `reports/combination-performance/README.md` |
| 보안 | IDOR 차단, 세션 인증, CSRF, BCrypt lazy migration, login rate limit | `src/test/java/inu/timetable/controller/UserSecurityIntegrationTest.java` |
| 관리자 안전성 | 관리자 route 분리, CSRF, audit log, import lock | `src/test/java/inu/timetable/controller/AdminEndpointSeparationTest.java` |
| 운영 | Flyway `ddl-auto=validate`, nginx blue-green 배포, rollback 경로 | `scripts/deploy-blue-green.sh` |
| 관측성 | Prometheus/Grafana, p95/p99, DAU/MAU, 사용자 수 gauge | `docs/observability.md` |

## Documents

- [Case Study](case-study.md)
- [Performance Evidence](performance-evidence.md)
- [Security Evidence](security-evidence.md)
- [Operations Runbook](operations-runbook.md)
- [Grafana Capture Guide](grafana-capture.md)
- [Deployment Diagram](../architecture/deployment-diagram.mmd)

## Interview Framing

1. 처음에는 "기능이 되는 시간표 서비스"였고, 이후에는 "운영 가능한 서비스"로 바꾸었다.
2. 성능 개선은 감으로 한 튜닝이 아니라 k6 시나리오와 p95/p99로 확인했다.
3. 보안 개선은 프론트 숨김이 아니라 서버 테스트로 인증/인가 경계를 증명했다.
4. 배포 개선은 Docker 재시작에서 nginx blue-green과 Flyway validate로 전환했다.
5. 데이터 지표는 proxy가 아니라 운영 DB snapshot과 저카디널리티 Prometheus metric으로 설명한다.
