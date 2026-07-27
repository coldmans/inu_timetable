# Redis 공유 캐시 도입 검증

측정일: 2026-07-27~28 (KST)

## 결론

운영 후보는 Redis 단독 캐시가 아니라 `Caffeine L1 + Redis L2` 두 단계
캐시로 결정했다. 뜨거운 조회는 인스턴스 메모리에서 처리하고, 새 Cloud Run
인스턴스나 L1 miss만 Memorystore Redis를 조회한다.

같은 1,000 VU/최대 3대 시나리오에서 최종 후보는 123,256건을 처리했다.
현재 Caffeine 기준선과 비교하면 처리량은 2.3% 낮고 평균은 4.1% 높지만,
p95는 2.1%, p99는 1.4% 낮았다. 서버 5xx, Redis 오류, 애플리케이션
WARNING/ERROR는 모두 0이었다. 성능을 크게 높인 변경은 아니지만 현재
처리 성능을 거의 유지하면서 다음 운영 안전장치를 추가했다.

- 여러 인스턴스와 신규 인스턴스가 같은 L2 캐시를 재사용한다.
- Redis 장애 시 5초 동안 Redis를 우회하고 PostgreSQL + Caffeine L1으로
  계속 응답한다.
- Redis 복구 전 공유 캐시를 비워 장애 중 오래된 값이 되살아나지 않게 한다.
- Redis 직렬화 타입을 애플리케이션 DTO와 제한된 JDK 패키지로 제한한다.
- 비밀번호는 Secret Manager에 두고 Cloud Run은 Direct VPC의 사설 IP로
  Redis에 접근한다.
- 캐시 전체 무효화는 `KEYS` 대신 `SCAN` batch를 사용한다.

다만 이번 단계는 DB 버전 폴링을 제거하지 않는다. Caffeine L1의
인스턴스 간 일관성을 유지하려면 현재 `shared_cache_versions` 브릿지가
계속 필요하다. 이후 Redis Pub/Sub 기반 L1 무효화를 추가하고 혼합
리비전이 모두 빠진 뒤에만 DB 폴링을 끌 수 있다.

## 최종 구조

```text
요청
  |
  v
Cloud Run 인스턴스별 Caffeine L1 (10분 TTL, 최대 1,000키)
  | L1 miss
  v
Memorystore Redis L2 (공유, 10분 TTL)
  | L2 miss 또는 Redis 장애
  v
PostgreSQL
```

쓰기 때문에 과목 캐시 버전이 바뀌면 기존 DB 버전 브릿지가 각 인스턴스의
L1과 Redis L2를 함께 비운다. 따라서 구 Caffeine 리비전과 새 two-level
리비전이 배포 중 함께 떠 있어도 동일한 무효화 계약을 사용한다.

## 인프라

| 항목 | 값 |
| --- | --- |
| GCP 프로젝트 | `project-53f7b99e-c306-49a7-a7b` |
| 리전 | `asia-northeast3` |
| Memorystore 인스턴스 | `inu-timetable-redis` |
| Redis | 7.2, Basic 1 GiB, `allkeys-lru` |
| 인증 | Redis AUTH 사용, 비밀번호는 Secret Manager |
| 네트워크 | Cloud Run Direct VPC, `default/default`, private ranges only |
| Redis endpoint | 사설 IP, 6379 |
| 벤치 Cloud Run | 1 vCPU, 1 GiB, concurrency 40, 최대 3대 |
| DB 풀 | 인스턴스당 maximum 10, minimum idle 2 |
| 운영 서비스 영향 | 벤치 중 운영 서비스 트래픽/리비전 변경 없음 |

Basic tier를 고른 이유는 과목 캐시가 PostgreSQL에서 완전히 재생성 가능하고,
Redis 전체 손실 때도 fail-open으로 서비스할 수 있기 때문이다. 자동
failover가 필요한 세션/원장 데이터는 이 Redis에 넣지 않는다.

TLS는 이번 단계에서 사용하지 않는다. Redis는 외부 IP가 없고 Direct
VPC 사설 경로와 AUTH를 사용한다. 향후 보안 요구가 올라가면 in-transit
encryption을 별도 롤아웃한다.

## 구현 범위

### 두 단계 캐시

- 기본/로컬 개발: 기존 Caffeine
- 운영: Caffeine L1 + Redis L2
- 비교 실험용: Redis L2-only
- 캐시 대상: count, departments, grades, 과목명 검색, 교수 검색, 필터
- TTL: L1/L2 모두 10분
- Redis writer: non-locking
- 전체 삭제: `SCAN 1,000`

Redis locking writer는 hit에도 `EXISTS`를 추가해 왕복을 늘렸다. 이 데이터는
DB에서 안전하게 다시 계산할 수 있으므로 rare miss의 중복 계산보다 모든
hit의 추가 왕복 비용이 더 크다고 판단해 non-locking writer를 사용했다.

### 장애 격리

첫 Redis 예외가 발생하면 해당 인스턴스는 degraded 상태가 된다. 이후
5초 동안 Redis를 호출하지 않고 loader를 실행한다. 재시도 시 Redis의
모든 과목 캐시 namespace를 한 번 비운 뒤 정상 상태로 복귀한다.

같은 최종 이미지의 비트래픽 Cloud Run 리비전에서 Redis port를 의도적으로
6379가 아닌 6390으로 설정해 장애를 주입했다. Redis health contributor는
이 실험에서만 꺼서 리비전을 기동하고 API fallback 자체를 확인했다.

- 첫 count L1/L2 miss: HTTP 200, 6.06초 (Redis 연결 timeout 후 DB fallback)
- departments/filter: 모두 HTTP 200
- 이후 count L1 hit 3회: 72~79ms
- `subject_cache_redis_failures_total`: 증가 확인
- 각 캐시의 `result="bypass"`: 증가 확인

즉 Redis 단절은 첫 miss의 지연을 늘리지만 API 기능 장애로 전파되지 않고,
한번 적재된 값은 Caffeine L1에서 계속 응답했다.

관측 지표:

- `subject_cache_l1_requests_total{cache,result}`
- `subject_cache_requests_total{cache,result}` (Redis L2)
- `subject_cache_redis_failures_total`
- `subject_cache_redis_recoveries_total`

### 배포 안전장치

GitHub Actions 후보 검증에 다음을 추가했다.

- provider/boolean/Redis endpoint 설정 검증
- Direct VPC network/subnet/egress 적용
- `REDIS_PASSWORD` Secret 주입
- health와 주요 과목 API 반복 호출
- L2 메트릭 존재 확인
- two-level이면 L1 메트릭 존재도 확인

운영 설정은 DB 버전 poll/publish를 모두 `true`로 둔다. 이는 과거 리비전과
혼재하는 배포 구간뿐 아니라 현재 Caffeine L1의 인스턴스 간 일관성에도
필요하다.

## 부하 시나리오

기준선과 같은 k6 `portfolio` 프로필을 사용했다.

1. 20초 동안 최대 VU의 15%까지 상승
2. 60초 동안 50%까지 상승
3. 60초 동안 50% 유지
4. 20초 동안 100%까지 상승
5. 40초 동안 100% 유지
6. 20초 동안 0으로 하강

요청은 읽기 전용 과목 API만 사용했다. 벤치 서비스는 IAM 인증이 필요한
비공개 Cloud Run 서비스이며 운영과 같은 DB를 사용하므로 쓰기는 하지
않았다.

## 결과

| 후보 | 실제 인스턴스 | 요청 수 | 실패 | 실패율 | 평균 | p95 | p99 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 기존 Caffeine 기준선 | 3 | 126,106 | 8 | 0.0063% | 474.48ms | 1,962.62ms | 6,560.44ms |
| Redis L2-only, locking | 3 | 116,059 | 15 | 0.0129% | 554.21ms | 2,064.01ms | 6,540.04ms |
| Redis L2-only, non-locking | 3 | 118,201 | 12 | 0.0102% | 533.88ms | 1,979.13ms | 6,875.00ms |
| **Caffeine L1 + Redis L2** | **3** | **123,256** | **14** | **0.0114%** | **493.82ms** | **1,922.09ms** | **6,465.63ms** |

최종 후보와 기존 기준선의 차이:

- 요청 수: 126,106 -> 123,256, **2.3% 감소**
- 평균: 474.48ms -> 493.82ms, **4.1% 증가**
- p95: 1,962.62ms -> 1,922.09ms, **2.1% 감소**
- p99: 6,560.44ms -> 6,465.63ms, **1.4% 감소**

Redis L2-only non-locking 후보와 비교하면 최종 후보는 요청 수가 4.3%
늘었고 평균은 7.5%, p95는 2.9%, p99는 6.0% 낮았다. 모든 요청을
네트워크 Redis에 보내는 설계가 현재 서비스에는 적합하지 않다는 것을
실측으로 확인한 결과다.

k6의 14건 실패는 1,000 VU 정점과 하강 구간의 HTTP/2 connection EOF,
client connection 수립 실패, 60초 request timeout이었다. 같은 측정
구간 Cloud Run request log의 서버 5xx는 0건이고 애플리케이션
WARNING/ERROR도 0건이었다. k6 exit code 99는 기능 실패가 아니라 기존
성능 임계값 `p95 < 500ms`, `p99 < 1,000ms` 초과 때문이다.

## Redis 사용량

최종 후보는 부하 중 실제 Cloud Run 인스턴스 3개로 확장했다. 측정 후
한 인스턴스에서 본 누적 표본은 다음과 같다.

| 계층 | 접근 수 |
| --- | ---: |
| Caffeine L1 | 26,483 |
| Redis L2 | 129 |
| Redis fallback 오류 | 0 |

즉 이 표본에서는 L1 miss만 Redis로 내려가 Redis 접근이 L1 접근의 약
0.49%였다. Memorystore의 분당 명령 지표에서도 부하 중 `GET`은
32 -> 110 -> 176회로, 전체 HTTP 요청에 비해 매우 작았다.

관측된 Redis 자원 최대치는 다음과 같다.

- 연결 수: 14
- 메모리 사용률: 0.54% 미만
- main thread CPU: 관측된 1분 구간의 user+system 합 최대 약
  0.20 CPU-second
- Redis cache fallback: 0

Redis가 병목이라는 증거는 없었다. 주요 tail latency는 기준선과 마찬가지로
Cloud Run 확장/기동과 검색·필터 응답 경로의 영향을 더 크게 받는다.

## 판정

기준 보고서의 공격적인 목표인 요청 수 +20%, p95 -30%에는 도달하지
못했다. 따라서 “Redis를 붙여 성능이 크게 향상됐다”고 주장하면 안 된다.

이번 변경의 합격 근거는 다음처럼 제한한다.

1. 현 처리량과 지연이 기준선과 대체로 동급이다.
2. 3개 인스턴스에서 공유 L2가 실제 사용된다.
3. Redis 장애가 요청 실패로 전파되지 않도록 fail-open 경로와 테스트가 있다.
4. 후보에서 Redis 오류, 서버 5xx, 애플리케이션 오류가 0이다.
5. 기존 DB 버전 브릿지를 유지해 혼합 리비전과 L1 일관성을 보존한다.

다음 성능 개선은 Redis 자체보다 25초 수준의 Spring Boot 콜드 스타트,
과목 검색/필터 쿼리, 캐시 무효화 범위 축소를 대상으로 하는 편이
실측 근거에 맞다.

## 후속 단계

1. Redis Pub/Sub 채널로 과목 cache namespace/version 이벤트 발행
2. 각 인스턴스가 이벤트를 받아 L1만 즉시 무효화
3. 구 리비전이 모두 drain된 것을 확인
4. DB version poll을 먼저 끄고 publish를 마지막에 끄는 단계 배포
5. 이벤트 유실 복구를 위해 낮은 빈도의 DB reconciliation 또는 versioned
   Redis key를 유지

Pub/Sub 없이 poll을 끄면 인스턴스별 L1이 TTL 동안 서로 다른 값을
제공할 수 있으므로 이번 PR에서는 하지 않는다.

## 재현

```bash
ID_TOKEN="$(gcloud auth print-identity-token)" \
BASE_URL="https://<private-benchmark-cloud-run-url>" \
BENCHMARK_NAME="two-level-caffeine-redis-max3-1000vus" \
RESULT_FILE="03-two-level-caffeine-redis-max3-1000vus.json" \
PROFILE="portfolio" \
PEAK_VUS="1000" \
WARMUP="true" \
k6 run scripts/k6/subject-read-benchmark.js
```

원본 결과:

- `00-smoke-10vus.json`
- `01-redis-shared-cache-max3-1000vus.json`
- `02-redis-shared-cache-nonlocking-max3-1000vus.json`
- `03-two-level-caffeine-redis-max3-1000vus.json`
