# Observability

INU 시간표의 운영 지표는 Spring Boot Actuator, Micrometer, Prometheus, Grafana를 기준으로 본다.

## 수집 지표

- HTTP 트래픽: `http_server_requests_seconds_*`
- HTTP p95/p99: `http_server_requests_seconds_bucket` 기반 histogram quantile
- HTTP 5xx 에러율: `http_server_requests_seconds_count{status=~"5.."}`
- 누적 가입자: `inu_users_registered`
- 활성 계정: `inu_users_active`
- DAU: `inu_users_dau`
- MAU: `inu_users_mau`
- DB 커넥션 풀: `hikaricp_connections_*`

`inu_users_dau`와 `inu_users_mau`는 Prometheus label에 사용자 ID를 넣지 않는다. 인증 성공 또는 인증된 `/api/**` 요청을 `user_activity_daily`에 사용자별 하루 한 행으로 저장한 뒤, Prometheus scrape 시점에 DB에서 distinct user count를 집계한다.

## 로컬 실행

1. 백엔드 앱을 `8080` 포트로 실행한다.
2. 모니터링 스택을 실행한다.

```bash
cd observability
docker compose up -d
```

3. Prometheus에서 target 상태를 확인한다.

```text
http://localhost:9090/targets
```

4. Grafana에 접속한다.

```text
http://localhost:3000
admin / admin
```

대시보드는 `INU Timetable / INU 시간표 운영 대시보드`에 자동 provision 된다.

## 면접에서 설명할 포인트

- "2,500명"은 감이 아니라 `users` 테이블 기반 `inu_users_registered`로 설명한다.
- DAU/MAU는 로그 샘플링이 아니라 `user_activity_daily`의 고유 사용자 집계로 설명한다.
- HTTP p95/p99는 평균 응답시간이 아니라 histogram 기반 분위수로 설명한다.
- 사용자 ID를 Prometheus label로 노출하지 않아 cardinality 폭발과 개인정보 노출을 피했다.
