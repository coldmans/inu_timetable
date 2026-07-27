# Redis 도입 전 현재 상태 기준선

측정일: 2026-07-27 (KST)

## 결론

현재 구조는 `Cloud Run 최대 3대 + 인스턴스별 Caffeine + PostgreSQL 버전
1초 폴링`이다. 200 VU에서는 단일 인스턴스만으로 48,975건을 실패 없이
처리했고 p95는 47.83ms였다. 반면 1,000 VU에서 실제 3대로 확장했을 때는
126,106건, 실패율 0.0063%, p95 1.96초, p99 6.56초였다.

Redis 도입의 근거도 실제 부하 중 확인됐다.

- 1,000 VU 측정 4분 동안 `subject-filters` 버전이 30번 증가했다.
- 세 인스턴스가 이 변화를 각자 폴링해 로컬 캐시를 총 79번 비웠다.
- 새 인스턴스 두 대는 각각 약 25초 동안 기동한 뒤 각자의 캐시를 다시
  예열했다.
- 서버 5xx와 Hikari connection timeout은 0이었다. 병목은 DB 연결 고갈보다
  자동 확장 지연, 인스턴스별 캐시 중복, 전체 필터 캐시 무효화, CPU/응답
  직렬화 대기가 합쳐진 결과로 보는 것이 타당하다.

따라서 Redis는 단순 기술 추가가 아니라 다음 문제를 해결하는 후보가 된다.

1. 인스턴스마다 같은 값을 중복 저장하고 예열하는 문제
2. 캐시 변경 확인을 위해 인스턴스마다 매초 DB를 조회하는 문제
3. 인기 순서가 한 번 바뀔 때 모든 인스턴스의 필터 캐시가 함께 사라지는 문제
4. 무효화 직후 여러 인스턴스가 같은 키를 동시에 다시 계산하는 문제

다만 Redis만 붙여도 Cloud Run 콜드 스타트와 JSON 직렬화/CPU 대기가
사라지는 것은 아니다. 도입 후에는 같은 시나리오를 다시 실행해 효과를
분리해서 증명해야 한다.

## 측정 대상

| 항목 | 값 |
| --- | --- |
| Git 커밋 | `b2800ba2797b15040066eecc8f71589fa3b0f65e` |
| 애플리케이션 | Spring Boot 3.5.4, Java 17 |
| Cloud Run 리전 | `asia-northeast3` |
| 벤치 서비스 | `inu-timetable-backend-cache-bench` |
| 벤치 리비전 | `inu-timetable-backend-cache-bench-redisbase` |
| 접근 | Google IAM 인증이 필요한 비공개 서비스 |
| 컨테이너 | 1 vCPU, 1 GiB |
| 요청 동시성 | 인스턴스당 40 |
| DB 풀 | 인스턴스당 maximum 10, minimum idle 2 |
| 세션 | shared JDBC session 활성화, migration bridge `off` |
| 현재 학기 | `2026-2` |
| 활성 과목 수 | 5,280 |
| 캐시 | 인스턴스별 Caffeine, maximum size 1,000, TTL 10분 |
| 분산 무효화 | `shared_cache_versions`를 인스턴스마다 1초 간격으로 폴링 |
| 부하 도구 | k6 1.4.2 |

최신 main은 측정 전에 다음 검증을 통과했다.

- `./gradlew clean assemble -x test`
- `./gradlew test`
- `./gradlew clean bootJar`

## 중요한 데이터베이스 범위

벤치 서비스는 `BENCH_DB_*` Secret을 사용하지만, Secret 값을 노출하지 않고
지문을 비교한 결과 운영 Secret과 다음 항목이 같았다.

- URL에서 연결 옵션을 제외한 DB endpoint와 database
- DB username
- DB password

즉 Cloud Run 서비스는 별도지만 DB는 운영 DB와 같다. 이번 k6 시나리오는
아래 읽기 API만 사용하며 사용자/과목 데이터를 쓰지 않았다.

- `/api/subjects/count`
- `/api/subjects/departments`
- `/api/subjects/grades`
- `/api/subjects/search`
- `/api/subjects/search/professor`
- `/api/subjects/filter`

두 번의 1,000 VU 측정 동안 운영 서비스의 health와 과목 count를 10초
간격으로 총 48회 확인했고 모두 HTTP 200이었다. 같은 시간대 운영 서비스의
Cloud Run 5xx와 ERROR 로그도 0건이었다. 그래도 운영 DB와 완전히 격리된
실험은 아니므로, Redis 전후 최종 비교는 DB clone에서 반복하는 편이 더
엄밀하다.

## 시나리오

`portfolio` 프로필은 총 3분 40초다.

1. 20초 동안 최대 VU의 15%까지 상승
2. 60초 동안 50%까지 상승
3. 60초 동안 50% 유지
4. 20초 동안 최대 VU까지 상승
5. 40초 동안 최대 VU 유지
6. 20초 동안 0으로 하강

각 VU는 요청 후 0.2~0.7초를 기다린다. 요청 비율은 count 20%,
departments 15%, grades 15%, subject search 20%, professor search 15%,
filter 15%다.

## 결과

| 조건 | 최대 VU | 실제 인스턴스 | 요청 수 | 실패 | 실패율 | 평균 | p95 | p99 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 스모크, service cap 1 | 10 | 1 | 349 | 0 | 0% | 29.49ms | 82.07ms | 122.53ms |
| 현재 Caffeine, service cap 1 | 200 | 1 | 48,975 | 0 | 0% | 23.10ms | 47.83ms | 78.97ms |
| 현재 Caffeine, 잘못 남은 service cap 1 | 1,000 | 1 | 89,889 | 0 | 0% | 847.87ms | 2,302.42ms | 2,605.88ms |
| 현재 Caffeine, service/revision cap 3 | 1,000 | 3 | 126,106 | 8 | 0.0063% | 474.48ms | 1,962.62ms | 6,560.44ms |

8건의 실패는 k6 쪽 HTTP/2 connection 수립 실패와 request timeout이었다.
같은 시간대 벤치 리비전의 서버 5xx는 0건이었다. k6가 exit code 99로
종료한 이유는 기능 오류율이 아니라 스크립트의 성능 임계값
`p95 < 500ms`, `p99 < 1,000ms`를 넘었기 때문이다.

### 수평 확장 효과

현재 코드의 단일 인스턴스 1,000 VU와 3대 결과를 비교하면 다음과 같다.

- 요청 수: 89,889 -> 126,106, **40.3% 증가**
- 평균: 847.87ms -> 474.48ms, **44.0% 감소**
- p95: 2,302.42ms -> 1,962.62ms, **14.8% 감소**
- p99: 2,605.88ms -> 6,560.44ms, **151.8% 증가**

처리량과 평균은 좋아졌지만 p99는 자동 확장과 client timeout의 영향을
받아 크게 나빠졌다. 최대 인스턴스 수만 늘리는 것으로 스파이크의 tail
latency가 해결되지는 않는다.

### 발견된 Cloud Run 설정 함정

처음 배포 후 리비전의 `max-instances`는 3이었지만 서비스 전체
`max`가 과거 실험 값 1로 남아 있었다. 서비스 전체 cap이 우선 적용돼
첫 1,000 VU 실험에서는 인스턴스가 한 대만 기동했다.

다음 두 값을 모두 3으로 맞춘 뒤 재측정했다.

- service-level maximum: 3
- revision-level maximum: 3

이 차이는 배포 설정에서 함께 검증해야 한다. 리비전 설정만 보고 “최대
3대”라고 판단하면 실제 런타임과 어긋날 수 있다.

## 자동 확장과 캐시 예열 증거

3대 실험은 2026-07-27 23:10:02 KST 전후에 시작했다.

| 이벤트 | 측정 시작 후 |
| --- | ---: |
| 두 번째 인스턴스 시작 요청 | 약 7초 |
| 두 번째 인스턴스 애플리케이션 ready | 약 34초 |
| 두 번째 인스턴스 캐시 warm-up 완료 | 약 35초 |
| 세 번째 인스턴스 시작 요청 | 약 39초 |
| 세 번째 인스턴스 애플리케이션 ready | 약 66초 |
| 세 번째 인스턴스 캐시 warm-up 완료 | 약 68초 |

두 새 인스턴스의 Spring Boot 기동 시간은 각각 25.09초와 25.29초였다.
각 인스턴스는 7개 warm-up task를 별도로 수행했고 모두 성공했다.
따라서 초기 1분 이상은 3대가 완전히 준비된 정상 상태가 아니었다.

## 무효화 churn

### 200 VU

- 실제 인스턴스: 1
- 서로 다른 `subject-filters` 버전 변경: 20회
- 로컬 캐시 무효화 적용 로그: 20회

### 1,000 VU, 3대

- 실제 인스턴스: 3
- 서로 다른 `subject-filters` 버전 변경: 30회 (`4139` -> `4168`)
- 로컬 캐시 무효화 적용 로그: 79회
- 벤치 종료 후 한 인스턴스에서 관측한 필터 캐시 hit/miss:
  30,238 / 356, 적중률 98.84%
- Hikari connection timeout: 0

30번의 논리적 변경이 최대 90번의 로컬 clear가 될 수 있고, 이번에는
인스턴스가 순차 기동해 총 79번이 관측됐다. 현재 구현은 시간표 추가/삭제로
인기 카운트가 바뀔 때 `subjectFilters` 전체를 비운다. 사용량이 늘수록
필터 캐시가 가장 자주 식는 구조다.

또한 최대 3대가 모두 살아 있으면 `shared_cache_versions` 확인 쿼리가
초당 약 3회, 분당 약 180회 발생한다. 지금 수치에서 DB 풀 고갈은
없었지만, 캐시 일관성만을 위해 계속 발생하는 고정 DB 부하다.

## 2026-07-25 기준선과 비교

이전 보고서의 서울 DB/Caffeine 결과와 비교하면 다음과 같다.

| 최대 VU / 인스턴스 | 2026-07-25 요청 수 | 현재 요청 수 | 요청 수 변화 | 이전 p95 | 현재 p95 |
| --- | ---: | ---: | ---: | ---: | ---: |
| 1,000 / 1 | 94,434 | 89,889 | -4.8% | 1,991.06ms | 2,302.42ms |
| 1,000 / 3 | 172,904 | 126,106 | -27.1% | 808.45ms | 1,962.62ms |

현재는 활성 과목이 2,894개에서 5,280개로 늘었고, 공유 세션과 DB 버전
폴링 기반 분산 무효화가 추가됐으며, 운영 사용자의 실제 시간표 변경으로
필터 캐시가 계속 무효화됐다. 따라서 이 표를 특정 한 변경의 인과관계로
해석하면 안 된다. “현재 운영 코드와 현재 데이터에서 다시 잰 출발점”으로
사용해야 한다.

## Redis 실험 설계

첫 실험은 다음 범위가 적절하다.

1. count/departments/grades/search/filter 결과를 Redis shared cache로 이동
2. `shared_cache_versions` 1초 DB 폴링 제거
3. 인기 순서에 영향받는 키와 영향받지 않는 필터 키를 분리
4. 버전이 포함된 key namespace로 전체 삭제 비용 축소
5. 동일 키 miss에 `SET NX PX` 기반 짧은 lock을 두어 cache stampede 방지
6. 로컬 L1을 유지한다면 Redis Pub/Sub로 L1만 즉시 무효화하고, L1 없이
   시작한다면 일관성 경로를 더 단순하게 유지

Redis 전후 합격선은 다음처럼 잡는다.

| 지표 | 현재 기준선 | 최소 목표 | 도전 목표 |
| --- | ---: | ---: | ---: |
| 200 VU p95 | 47.83ms | 55ms 이하, 실패 0 | 현재 이하 |
| 1,000 VU 요청 수 | 126,106 | 151,000 이상 (+20%) | 172,904 이상 |
| 1,000 VU p95 | 1,962.62ms | 1,374ms 이하 (-30%) | 1,000ms 이하 |
| 1,000 VU p99 | 6,560.44ms | 4,592ms 이하 (-30%) | 2,500ms 이하 |
| 무효화 확인 DB 쿼리 | 최대 약 3회/초 | 0 | 0 |
| 동일 키 재계산 | 인스턴스별 발생 가능 | 변경당 최대 1회 | 변경당 최대 1회 |

## 재현

```bash
BENCH_URL="https://<private-cloud-run-url>"
ID_TOKEN="$(gcloud auth print-identity-token)"

BASE_URL="$BENCH_URL" \
BENCHMARK_NAME="current-caffeine-max3-pool10-1000vus" \
RESULT_FILE="03-current-caffeine-max3-1000vus.json" \
PROFILE="portfolio" \
PEAK_VUS="1000" \
ID_TOKEN="$ID_TOKEN" \
k6 run scripts/k6/subject-read-benchmark.js
```

원본 결과:

- `00-smoke-service-cap1-10vus.json`
- `01-current-caffeine-service-cap1-200vus.json`
- `02-current-caffeine-service-cap1-1000vus.json`
- `03-current-caffeine-max3-1000vus.json`
