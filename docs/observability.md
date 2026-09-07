# Observability

저장소가 보장하는 관측 표면은 Spring Boot Actuator/Micrometer endpoint, Cloud Run 배포 smoke, 그리고 로컬에서 재현 가능한 Prometheus/Grafana compose입니다. 이 저장소만으로 운영에 호스팅된 Prometheus나 Grafana가 배포된다고 보장하지 않습니다.

## Runtime Endpoints

| Endpoint | 용도 | 운영 프로파일 |
|---|---|---|
| `/actuator/health` | 기동과 서비스 health 확인 | 상세 component 비공개 |
| `/actuator/info` | 기본 앱 정보 | 환경변수/property 노출 비활성화 |
| `/actuator/prometheus` | Micrometer Prometheus scrape | 노출 |

Cloud Run workflow는 zero-traffic candidate에서 health와 주요 API를 호출하고, Prometheus 응답에 캐시 metric이 존재하는지 확인합니다. 트래픽 승격 뒤에는 기본 서비스 URL의 health와 과목 수가 정상인지 다시 확인합니다.

## 핵심 지표

### HTTP와 JVM/DB

- 요청 수·지연: `http_server_requests_seconds_*`
- p95/p99: `http_server_requests_seconds_bucket`의 histogram quantile
- 5xx 비율: `http_server_requests_seconds_count{status=~"5.."}`
- DB pool: `hikaricp_connections_*`
- JVM과 process: Spring Boot/Micrometer 기본 지표

### 사용자 지표

- 누적 계정: `inu_users_registered`
- 활성 계정: `inu_users_active`
- DAU: `inu_users_dau`
- MAU: `inu_users_mau`

`inu_users_dau`와 `inu_users_mau`는 사용자 ID를 Prometheus label에 넣지 않습니다. 인증 성공 또는 인증된 `/api/**` 요청을 `user_activity_daily`에 사용자별 하루 한 행으로 저장한 뒤 scrape 시점에 distinct user count를 집계합니다.

### 과목 캐시

- Redis L2 hit/miss/bypass: `subject_cache_requests_total{cache,result}`
- Caffeine L1 hit/miss: `subject_cache_l1_requests_total{cache,result}`
- Redis 장애 감지: `subject_cache_redis_failures_total`
- Redis 복구: `subject_cache_redis_recoveries_total`

운영 rollout은 Caffeine L1 + Redis L2입니다. Redis 오류가 나면 잠시 L2를 우회하고 PostgreSQL loader와 L1으로 응답하며, 복구 전에 stale L2 값을 비웁니다. 과목 변경의 인스턴스 간 일관성은 아직 `shared_cache_versions` DB version poll/publish가 담당하므로, Redis metric만 보고 무효화 상태를 판단하지 않습니다.

## 제품 분석 API와 운영 Metric의 구분

`POST /api/events`로 수집한 제품 행동은 `analytics_events`에 저장됩니다. 관리자는 다음 API로 기간별 집계를 조회합니다.

```http
GET /admin/api/analytics/summary?days=14
GET /admin/api/analytics/dashboard?range=today
```

이 값은 검색·시간표 행동을 분석하기 위한 application data입니다. HTTP latency, JVM, DB pool, cache health를 보는 Prometheus operational metric과 목적이 다릅니다.

## 로컬 Prometheus/Grafana 재현

1. 백엔드를 `localhost:8080`에서 실행합니다.
2. 로컬 관측 stack을 시작합니다.

```bash
cd observability
docker compose up -d
```

3. Prometheus target을 확인합니다.

```text
http://localhost:9090/targets
```

4. Grafana에 접속합니다.

```text
http://localhost:3000
admin / admin
```

기본 자격 증명은 로컬 개발용입니다. `INU Timetable / INU 시간표 운영 대시보드`가 compose 안의 Grafana에 provision되며, Prometheus는 `host.docker.internal:8080/actuator/prometheus`를 scrape합니다.

## 점검 예시

후보 또는 허가된 서비스 URL을 대상으로 확인합니다.

```bash
curl -fsS "$SERVICE_URL/actuator/health"
curl -fsS "$SERVICE_URL/actuator/prometheus" | rg 'subject_cache_(l1_)?requests_total'
```

PromQL 예시:

```promql
histogram_quantile(
  0.95,
  sum by (le) (rate(http_server_requests_seconds_bucket[5m]))
)
```

```promql
sum(rate(http_server_requests_seconds_count{status=~"5.."}[5m]))
/
sum(rate(http_server_requests_seconds_count[5m]))
```

```promql
sum by (result) (rate(subject_cache_requests_total[5m]))
```

## 해석 원칙

- 사용자 수는 캡처 시각과 metric 이름을 함께 기록하고 실시간 고정값처럼 인용하지 않습니다.
- p95/p99는 평균과 구분하고, endpoint·기간·트래픽 조건을 함께 남깁니다.
- Cloud Run revision/traffic 상태, 플랫폼 request log, 애플리케이션 metric을 서로 다른 증거로 취급합니다.
- 배포 성공은 workflow 완료만이 아니라 100% traffic revision, health, 핵심 API smoke까지 확인합니다.
- Redis 장애 시 첫 miss 지연과 이후 L1 hit를 분리해 봅니다.
- 사용자 ID를 metric label에 노출하지 않아 cardinality와 개인정보 노출을 제한합니다.
