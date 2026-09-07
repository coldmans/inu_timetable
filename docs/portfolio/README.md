# INU Timetable Portfolio Packet

이 폴더는 실사용 시간표 서비스를 운영하면서 기능, 데이터 안전성, 보안, 성능, 확장, 배포를 개선한 근거를 한곳에서 찾기 위한 문서 묶음입니다.

## One Line

인천대 학생용 시간표 서비스에서 DB 초기화 전 이력을 포함한 누적 가입 ID 4천 돌파와 2026-08-01 운영 DB read-only 집계의 3,554개 활성·비테스트 계정, 3,371명 저장 행동 사용자를 바탕으로 세션 인증, 안전한 과목 import, BitSet 조합 최적화, 공유 세션·캐시, Cloud Run 단계 배포를 직접 구현했습니다.

## Evidence Map

수치는 현재 실시간 상태가 아니라 명시된 시점의 스냅샷 또는 통제된 테스트 결과입니다.

| 주제 | 날짜가 붙은 주장 | 근거 |
|---|---|---|
| 누적 가입 이정표 | 운영자 확인상 DB 초기화 전 행을 포함해 사용자 ID sequence가 2026-08-01에 4,045 도달 | `reports/usage-report-2026-08-01.md` |
| 현재 계정 규모 | 2026-08-01 운영 DB의 `users` 3,596행, 활성·비테스트 계정 3,554개 | `reports/usage-report-2026-08-01.md` |
| 현재 사용 깊이 | 같은 집계에서 저장 행동 사용자 3,371명, 시간표·위시리스트 저장 행 29,502개 | `reports/usage-report-2026-08-01.md` |
| 실사용 규모 | 2026-06-12 운영 DB snapshot의 `users` 2,686행, `user_timetables` 14,668행 | `reports/course-data-snapshot-2026-06-12/README.md` |
| 사용 깊이 | 2026-04-29 리포트에서 테스트 계정 제외 가입자 2,660명 중 2,512명이 저장 행동 수행 | `reports/usage-report-2026-04-29.md` |
| DB/search 성능 | 2026-01-02 200 VU 혼합 부하 보고서에서 p95 30초 -> 386ms | `PERFORMANCE_TEST_REPORT.md` |
| 조합 알고리즘 | 2026-06-14 로컬 30개 위시리스트 케이스 p95 348.72ms -> 9.39ms | `reports/combination-performance/README.md` |
| 공유 캐시 | 2026-07-27~28 Cloud Run 읽기 벤치에서 기준선과 유사한 성능으로 Caffeine L1 + Redis L2와 fail-open 검증 | `reports/performance/2026-07-27-redis-shared-cache/README.md` |
| 보안 | IDOR 차단, 세션/CSRF, BCrypt migration, DB 기반 login limit, 관리자 credential version | `docs/portfolio/security-evidence.md` |
| 관리자 안전성 | import preview/impact/selective apply, stale/row/advisory lock, 저장 후 재검증 | `docs/admin-subject-import.md` |
| 운영 | GitHub OIDC, Cloud Run zero-traffic candidate, smoke, 단계 승격과 revision rollback | `.github/workflows/docker-image.yml` |
| 관측성 | Actuator/Micrometer metric과 로컬 Prometheus/Grafana 재현 | `docs/observability.md` |

## Documents

- [Case Study](case-study.md)
- [Performance Evidence](performance-evidence.md)
- [Security Evidence](security-evidence.md)
- [Operations Runbook](operations-runbook.md)
- [Grafana Capture Guide](grafana-capture.md)
- [Deployment Diagram](../architecture/deployment-diagram.mmd)
- [Admin Subject Import](../admin-subject-import.md)

## Current System Story

1. 동적 학기와 과목 검색, 위시리스트, 필수/선택 학점 조합, 개인 시간표를 제공했습니다.
2. 과목 시간·폐강 변경을 업데이트 로그와 사용자 알림으로 연결하고 구간별 강의실을 보존했습니다.
3. JSON/Excel import를 변경점과 사용자 영향을 먼저 보는 선택 반영 workflow로 바꿨습니다.
4. 앱 내 문의와 관리형 FAQ, 관리자 분석 화면을 추가해 운영 동선을 제품 안으로 가져왔습니다.
5. 다중 Cloud Run 인스턴스에서도 세션과 로그인 제한은 PostgreSQL에 공유하고, 과목 조회는 Caffeine L1 + Redis L2로 구성했습니다.
6. 배포는 OIDC 기반 source deploy, zero-traffic 후보 검증, 10%/50%/100% 승격, 실패 시 이전 revision 복구로 운영합니다.

## Interview Framing

- 성능 숫자는 테스트 날짜·부하·대상 API와 함께 설명합니다.
- Redis는 큰 속도 향상이 아니라 새 인스턴스의 공유 L2와 장애 격리를 얻은 결정으로 설명합니다.
- 보안은 프론트 route 숨김이 아니라 서버의 인증·소유권·CSRF·DB 공유 제한과 테스트로 증명합니다.
- import는 파싱 성공보다 preview, impact, 선택 반영, stale 방지, 사후 검증의 데이터 안전성을 중심으로 설명합니다.
- Prometheus/Grafana는 저장소에서 로컬 재현 가능한 관측 구성입니다. 별도 운영 호스팅이 확인되지 않은 상태에서 production dashboard라고 부르지 않습니다.
- 사용량은 현재값이 아니라 dated report와 운영 DB snapshot으로 인용합니다.
- 사용자 ID sequence 4,045는 DB 초기화 전 이력을 포함한 누적 4천 돌파로 설명하고, 현재 계정 수 3,596행과는 같은 지표로 취급하지 않습니다.
