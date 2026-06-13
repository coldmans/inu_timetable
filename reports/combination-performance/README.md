# 시간표 조합 성능 테스트 기록

## 목적

위시리스트 과목 수가 늘어날 때 시간표 조합 생성 API의 응답 시간이 어떻게 변하는지 확인하고, 기존 재귀 + 전체 쌍 비교 방식과 BitSet 시간 마스크 방식의 차이를 측정한다.

## 환경

- 날짜: 2026-06-14
- 서버: Spring Boot local profile, H2 in-memory DB
- 엔드포인트: `POST /api/timetable-combination/generate`
- 시나리오 생성: `POST /api/dev/combination-scenario`
- k6 조건: 케이스별 2 VUs, 30초, think time 100ms, `maxCombinations=20`
- 케이스: 위시리스트 6, 12, 18, 24, 30개 과목

## 실행 명령

```bash
BASE_URL=http://localhost:8080 CASES=6,12,18,24,30 VUS_PER_CASE=2 DURATION=30s THINK_TIME_MS=100 MAX_COMBINATIONS=20 k6 run scripts/k6/timetable-combination-cases.js
```

## 결과

| 위시리스트 과목 수 | 기존 avg | 기존 p95 | BitSet avg | BitSet p95 | p95 개선 |
| ---: | ---: | ---: | ---: | ---: | ---: |
| 6 | 21.69ms | 66.60ms | 4.29ms | 6.58ms | 10.1x |
| 12 | 28.78ms | 69.38ms | 5.15ms | 7.88ms | 8.8x |
| 18 | 33.44ms | 70.44ms | 5.46ms | 8.86ms | 7.9x |
| 24 | 61.25ms | 80.81ms | 5.52ms | 9.18ms | 8.8x |
| 30 | 310.40ms | 348.72ms | 5.76ms | 9.39ms | 37.1x |

## 해석

기존 방식은 후보 조합을 만들 때마다 현재 조합 전체를 다시 순회하고 모든 과목 쌍의 시간표 충돌을 비교했다. 과목 수가 30개까지 늘어나면 이 반복 비용이 크게 튀었다.

BitSet 방식은 과목별 수업 시간을 미리 마스크로 변환하고, 재귀 중에는 현재 시간 마스크와 후보 과목 마스크의 교집합만 확인한다. 그래서 입력이 커져도 충돌 검사 비용이 거의 일정하게 유지됐다.

## 원본 결과

- `baseline-recursive-k6-results.json`
- `bitmask-k6-results.json`
