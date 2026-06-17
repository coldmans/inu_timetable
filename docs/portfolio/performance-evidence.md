# Performance Evidence

## 1. DB/Search Performance

초기 병목은 알고리즘보다 DB connection starvation이었다. k6로 동시 사용자 부하를 걸었을 때 요청이 connection pool에서 대기하다 timeout으로 이어졌다.

| Metric | Before | After | Change |
|---|---:|---:|---:|
| Average response time | 14.5s | 200ms | 72.5x faster |
| p95 | 30s | 386ms | 77x faster |
| p99 | 30.22s | 476ms | 63x faster |
| Failure rate | 25.79% | 0% | eliminated |
| Throughput | 6.9 req/s | 50.5 req/s | 7.3x higher |

Evidence:

- `PERFORMANCE_TEST_REPORT.md`
- `README.md#performance-tuning`
- `load-test.js`

What changed:

- HikariCP pool size and timeout settings
- Supabase/PostgreSQL prepared statement path with `prepareThreshold=0`
- Korean search query encoding
- indexes for subject search/filter paths

## 2. Timetable Combination Algorithm

기존 조합 생성은 후보를 추가할 때마다 현재 조합의 과목 쌍을 다시 돌며 시간 충돌을 검사했다. 개선 후에는 과목별 시간을 `BitSet` mask로 미리 만들고, 재귀 중에는 `currentTimeMask.intersects(subjectMask)`로 충돌만 확인한다.

| Wishlist size | Baseline avg | Baseline p95 | BitSet avg | BitSet p95 | p95 improvement |
|---:|---:|---:|---:|---:|---:|
| 6 | 21.69ms | 66.60ms | 4.29ms | 6.58ms | 10.1x |
| 12 | 28.78ms | 69.38ms | 5.15ms | 7.88ms | 8.8x |
| 18 | 33.44ms | 70.44ms | 5.46ms | 8.86ms | 7.9x |
| 24 | 61.25ms | 80.81ms | 5.52ms | 9.18ms | 8.8x |
| 30 | 310.40ms | 348.72ms | 5.76ms | 9.39ms | 37.1x |

Evidence:

- `reports/combination-performance/README.md`
- `reports/combination-performance/baseline-recursive-k6-results.json`
- `reports/combination-performance/bitmask-k6-results.json`
- `src/main/java/inu/timetable/service/TimetableCombinationService.java`
- `src/test/java/inu/timetable/service/TimetableCombinationServiceTest.java`

## 3. User-Perceived Performance

프론트는 검색 결과를 서버 페이지네이션으로 받고, 과목 추가/담기/시간표 적용 흐름에서 낙관적 상태 갱신과 toast feedback을 사용한다. 사용자가 느끼는 지연은 API 개선뿐 아니라 "버튼을 눌렀을 때 바로 반응한다"는 피드백에도 영향을 받는다.

Evidence:

- Frontend commits: `feat: 과목 추가 즉시 반영 UX 개선`, `feat: 과목 담은 수 반응형 표시`
- Frontend files: `inu_timetable_front/src/App.jsx`, `inu_timetable_front/src/services/api.js`

## Re-run Commands

```bash
./gradlew test --tests inu.timetable.service.TimetableCombinationServiceTest
BASE_URL=http://localhost:8080 CASES=6,12,18,24,30 VUS_PER_CASE=2 DURATION=30s THINK_TIME_MS=100 MAX_COMBINATIONS=20 k6 run scripts/k6/timetable-combination-cases.js
```

## Caveats

- k6 results are controlled test results, not a perfect copy of production traffic.
- The combination benchmark used local profile and generated scenarios to isolate algorithm cost.
- Production metrics should be explained with Prometheus p95/p99 once enough live traffic exists.
