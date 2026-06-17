# Grafana Capture Guide

포트폴리오에 넣을 Grafana 캡처는 "예쁜 대시보드"보다 실제 운영 질문에 답해야 한다.

## Dashboard Source

- Grafana dashboard JSON: `observability/grafana/dashboards/inu-timetable-overview.json`
- Prometheus scrape config: `observability/prometheus.yml`
- Metrics guide: `docs/observability.md`

## Local Capture

1. 백엔드를 실행한다.

```bash
./gradlew bootRun
```

2. Prometheus/Grafana를 실행한다.

```bash
cd observability
docker compose up -d
```

3. Grafana 접속:

```text
http://localhost:3000
admin / admin
```

4. `INU Timetable / INU 시간표 운영 대시보드`를 연다.
5. 캡처에 포함할 패널:

| Panel | Interview use |
|---|---|
| 누적 가입자 | "2,500명 어떻게 측정했나요?"에 대한 근거 |
| DAU/MAU | 수강신청 기간 사용 집중도 |
| HTTP 레이턴시 p95/p99 | 평균이 아닌 tail latency 설명 |
| HTTP 5xx 에러율 | 장애 여부와 배포 안정성 설명 |

## Production Capture Checklist

운영 캡처를 만들 때는 다음을 같이 남긴다.

- 캡처 날짜와 시간대
- 배포 commit hash
- Prometheus time range
- 제외/포함한 트래픽 범위
- 대시보드 JSON 경로

## Caveats

- Prometheus metric 이름은 dot notation `inu.users.registered`가 scrape 시 `inu_users_registered`로 변환된다.
- DAU/MAU는 Prometheus label aggregation이 아니라 DB의 `user_activity_daily` distinct count다.
- 사용자 ID, 학번, 세션 ID는 dashboard label로 넣지 않는다.
