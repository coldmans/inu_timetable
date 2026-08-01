# Performance Evidence

서로 다른 날짜와 workload의 결과를 하나의 현재 production baseline으로 합치지 않습니다. 각 숫자는 아래 보고서가 기록한 범위에서만 인용합니다.

## 1. 2026-01-02 DB/Search Mixed Load Report

`PERFORMANCE_TEST_REPORT.md`가 기록한 테스트는 5분 30초 동안 최대 200 VU까지 올리고, 과목 조회·검색·필터·count와 시간표 조합을 70:30으로 섞은 시나리오입니다. 정확한 테스트 host와 raw result 파일은 보고서에 남아 있지 않으므로 현재 production telemetry가 아니라 dated report로 표현합니다.

| Metric | 1차 | 최종 | 보고서상 변화 |
|---|---:|---:|---:|
| Average | 14.55s | 201ms | 약 72x |
| p95 | 30s | 386ms | 약 77x |
| p99 | 30.22s | 476ms | 약 63x |
| Failure rate | 25.79% | 0.00% | 보고서상 제거 |
| Throughput | 6.9 req/s | 50.5 req/s | 약 7.3x |

보고서가 해당 결과에 직접 연결한 변경:

- HikariCP pool 확대와 timeout/lifetime 설정
- Supabase transaction pooler 경로의 `prepareThreshold=0`
- k6 한글 검색어 URL encoding 수정

검색 index는 같은 보고서의 후속 계획으로 적혀 있습니다. 따라서 별도 측정 없이 386ms 결과의 직접 원인으로 소급해 설명하지 않으며, 당시의 root 부하 script를 현재 재현 계약으로 제시하지 않습니다.

Evidence: `PERFORMANCE_TEST_REPORT.md`

## 2. 2026-06-14 Timetable Combination Benchmark

로컬 Spring Boot/H2에서 위시리스트 과목 수별로 기존 재귀 전체 쌍 충돌 검사와 BitSet 시간 mask를 비교했습니다. 케이스별 2 VU, 30초, think time 100ms, 최대 조합 20개 조건입니다.

| Wishlist size | Baseline avg | Baseline p95 | BitSet avg | BitSet p95 | p95 improvement |
|---:|---:|---:|---:|---:|---:|
| 6 | 21.69ms | 66.60ms | 4.29ms | 6.58ms | 10.1x |
| 12 | 28.78ms | 69.38ms | 5.15ms | 7.88ms | 8.8x |
| 18 | 33.44ms | 70.44ms | 5.46ms | 8.86ms | 7.9x |
| 24 | 61.25ms | 80.81ms | 5.52ms | 9.18ms | 8.8x |
| 30 | 310.40ms | 348.72ms | 5.76ms | 9.39ms | 37.1x |

과목별 수업 시간을 먼저 `BitSet`으로 만들고 재귀 중에는 `currentTimeMask.intersects(subjectMask)`로 확인합니다. 필수 위시리스트 과목을 먼저 배치하고, 선택 과목은 목표 학점 ±3 범위 또는 `ignoreTargetCredits=true`일 때 가능한 최대 학점을 찾습니다.

Evidence:

- `reports/combination-performance/README.md`
- `reports/combination-performance/baseline-recursive-k6-results.json`
- `reports/combination-performance/bitmask-k6-results.json`
- `src/main/java/inu/timetable/service/TimetableCombinationService.java`

Re-run:

```bash
./gradlew test --tests inu.timetable.service.TimetableCombinationServiceTest
BASE_URL=http://localhost:8080 \
CASES=6,12,18,24,30 \
VUS_PER_CASE=2 \
DURATION=30s \
THINK_TIME_MS=100 \
MAX_COMBINATIONS=20 \
k6 run scripts/k6/timetable-combination-cases.js
```

## 3. 2026-07-27~28 Shared Cache and Scale-out Benchmark

이 실험은 private Cloud Run benchmark service에서 운영과 같은 DB를 읽기 전용으로 사용한 1,000 VU/max 3 instance 시나리오입니다. production service traffic/revision은 바꾸지 않았고, 실제 production traffic의 완전한 복제도 아닙니다.

### Prerequisites and baseline

- Cloud Run 1 vCPU, 1 GiB, concurrency 40
- service와 revision의 max instance가 모두 3
- instance별 JDBC pool maximum 10, minimum idle 2
- shared JDBC session enabled, migration bridge off
- Caffeine maximum 1,000 entries, TTL 10분
- 기존 또는 검증된 baseline이 적용된 PostgreSQL과 `shared_cache_versions`
- read-only subject APIs만 호출

초기 실험에서 service-level max가 1로 남아 scale-out 결과를 무효화한 사례가 있으므로 revision 설정만 보지 않습니다.

### Result

| Candidate | Instances | Requests | Client failures | Avg | p95 | p99 |
|---|---:|---:|---:|---:|---:|---:|
| Caffeine baseline | 3 | 126,106 | 8 | 474.48ms | 1,962.62ms | 6,560.44ms |
| Caffeine L1 + Redis L2 | 3 | 123,256 | 14 | 493.82ms | 1,922.09ms | 6,465.63ms |

최종 후보와 Caffeine 기준선의 차이:

- requests: 2.3% 감소
- average latency: 4.1% 증가
- p95: 2.1% 감소
- p99: 1.4% 감소

따라서 “Redis로 성능이 크게 향상됐다”는 결론은 근거와 맞지 않습니다. Redis는 다음 운영 특성을 추가하면서 처리 성능을 대체로 기준선에 가깝게 유지했습니다.

- 새 Cloud Run instance가 다른 instance가 채운 L2를 재사용
- Redis 오류 뒤 5초 동안 L2를 우회하고 PostgreSQL + Caffeine L1으로 응답
- 복구 전에 stale Redis namespace를 비우고 재사용
- `SCAN` batch 기반 cache clear와 제한된 serializer type
- Direct VPC private endpoint와 Secret Manager의 Redis credential

장애 주입에서 잘못된 Redis port의 첫 miss는 6.06초가 걸렸지만 HTTP 200이었고, 이후 L1 hit는 72~79ms였습니다. client-side k6 실패 14건은 있었지만 같은 측정 구간 Cloud Run server 5xx와 application WARNING/ERROR는 0으로 보고됐습니다. 이를 “모든 요청 성공”으로 줄여 말하지 않습니다.

Evidence:

- `reports/performance/2026-07-27-redis-shared-cache/README.md`
- `reports/performance/2026-07-27-redis-shared-cache/03-two-level-caffeine-redis-max3-1000vus.json`
- `reports/performance/2026-07-27-redis-baseline/README.md`
- `src/main/java/inu/timetable/config/TwoLevelCacheManager.java`
- `src/main/java/inu/timetable/config/ResilientRedisCacheManager.java`

Re-run against an explicitly provisioned private benchmark service, not a write-capable production workflow:

```bash
ID_TOKEN="$(gcloud auth print-identity-token)" \
BASE_URL="https://<private-benchmark-cloud-run-url>" \
BENCHMARK_NAME="two-level-caffeine-redis-max3-1000vus" \
RESULT_FILE="two-level-caffeine-redis-max3-1000vus.json" \
PROFILE="portfolio" \
PEAK_VUS="1000" \
WARMUP="true" \
k6 run scripts/k6/subject-read-benchmark.js
```

## 4. Current Checked-in Production Rollout

현재 저장소의 rollout profile은 성능 측정값이 아니라 배포 설정입니다.

| Item | Checked-in value |
|---|---|
| max instances / concurrency | 3 / 40 |
| JDBC pool per instance | max 10 / min idle 2 |
| sessions | shared JDBC, bridge off |
| subject cache | Caffeine L1 + Redis L2 |
| L1/L2 TTL | 기본 10분 |
| cross-instance invalidation | DB `shared_cache_versions` poll/publish enabled |
| Redis failure mode | fail-open to DB loader + L1 |

과목 변경 after-commit event가 DB version을 올리고 각 instance가 version 증가를 polling하면, two-level cache의 clear가 L1과 L2를 함께 비웁니다. Redis Pub/Sub 전환이 완료되기 전에는 DB poll이 제거됐다고 설명하지 않습니다.

## Caveats

- January mixed-load, June algorithm, July cache benchmarks are 서로 다른 환경과 목적입니다.
- local/H2 알고리즘 수치는 network와 production DB 비용을 포함하지 않습니다.
- July benchmark는 production과 같은 DB를 읽었지만 별도 private service의 read-only workload입니다.
- checked-in rollout profile은 live Cloud Run 상태를 독립적으로 증명하지 않습니다.
- production 성능을 말할 때는 캡처 기간의 Cloud Run/platform log와 Actuator p95/p99를 새로 확인해야 합니다.
