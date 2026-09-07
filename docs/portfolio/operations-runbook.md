# Operations Runbook

## Deployment Path

```text
Pull request
  -> clean test bootJar

main push or workflow_dispatch
  -> validate committed rollout profile
  -> clean test bootJar
  -> GitHub OIDC / GCP Workload Identity
  -> gcloud run deploy --source . --no-traffic
  -> tagged candidate health/API/auth/cache smoke
  -> 10% -> 50% -> 100% traffic promotion when bridge mode is off
  -> service health/count verification after each stage
  -> previous revision 100% rollback on verification failure
```

Current evidence:

- `.github/workflows/pull-request-validation.yml`
- `.github/workflows/docker-image.yml`
- `.github/deploy/production-rollout.env`

## Checked-in Rollout Profile

이 값은 저장소에 선언된 배포 의도입니다. 실제 running revision 상태는 배포 시 `gcloud`로 다시 확인합니다.

| Setting | Value |
|---|---|
| Region / service | `asia-northeast3` / `inu-timetable-backend` |
| Runtime | 1 vCPU, 1 GiB, port 8080, timeout 300s, CPU boost |
| Instances / concurrency | min 0, max 3 / 40 |
| JDBC pool per instance | max 10, min idle 2 |
| Session | shared Spring Session JDBC enabled |
| Session migration bridge | off |
| Subject cache | Caffeine L1 + Redis L2 |
| Cache consistency | DB version poll/publish enabled |
| Redis path | Direct VPC private ranges, Redis health enabled |
| ORM/schema | production `ddl-auto=validate`, Flyway enabled |

DB, Gemini, admin bootstrap, Redis credentials are injected from Secret Manager. Never print or copy their values into an incident note.

## Production Guardrails

| Guardrail | Implementation and limit |
|---|---|
| PR build gate | H2에서 `./gradlew clean test bootJar --no-daemon`; production PostgreSQL migration rehearsal은 아님 |
| Deploy build gate | 배포 전 `./gradlew clean test bootJar` |
| Short-lived cloud auth | GitHub OIDC Workload Identity, stored GCP key 없음 |
| Candidate isolation | tagged candidate를 `--no-traffic`으로 생성 |
| Candidate smoke | health, positive subject count, departments/filter, cache metrics, admin login/CSRF/unauthenticated boundary |
| Progressive traffic | bridge off일 때 10% -> 50% -> 100% |
| Automatic rollback scope | traffic 전환 뒤 service health/count 검증 실패 시 배포 전 revision으로 100% 복구 |
| Migration source | `src/main/resources/db/migration`의 forward migration |
| ORM safety | production Hibernate `ddl-auto=validate` |
| Shared sessions | PostgreSQL Spring Session JDBC |
| Admin/import safety | session + CSRF, audit log, plan row lock, PostgreSQL advisory lock, stale check, post-write verify |

후보 smoke 전에 실패하면 기존 production traffic은 움직이지 않습니다. workflow의 rollback 함수는 traffic 전환 뒤 `verify_service` 실패에 적용되며, 모든 가능한 `gcloud` 실패를 감싸는 범용 trap은 아닙니다. 실패한 run은 traffic table을 직접 확인해야 합니다.

## Pre-deploy Checklist

1. PR validation이 성공했는지 확인합니다.
2. `.github/deploy/production-rollout.env` diff에서 scale/session/cache phase가 의도한 값인지 확인합니다.
3. 새 Flyway migration이 forward-only이고 이전 revision과 함께 실행돼도 안전한지 확인합니다.
4. schema 변경이면 backup과 복구 절차를 확인합니다.
5. 빈 DB 또는 새 복구 환경이라면 자동 적용되지 않는 `db/baseline` 전제 조건을 먼저 해결합니다.
6. import와 같은 운영 데이터 변경이 동시에 진행 중이지 않은지 확인합니다.

## Deployment Verification

### 1. GitHub Actions 상태

```bash
gh run list --workflow "Deploy to GCP Cloud Run" --limit 5
```

해당 run에서 candidate URL, smoke, 단계별 traffic update, 최종 candidate revision assertion이 성공했는지 확인합니다.

### 2. 현재 traffic

```bash
GCP_PROJECT_ID=project-53f7b99e-c306-49a7-a7b
GCP_REGION=asia-northeast3
CLOUD_RUN_SERVICE=inu-timetable-backend

gcloud run services describe "$CLOUD_RUN_SERVICE" \
  --project "$GCP_PROJECT_ID" \
  --region "$GCP_REGION" \
  --format='table(status.traffic.revisionName,status.traffic.percent,status.traffic.tag,status.traffic.url)'
```

최종 상태에서 의도한 candidate가 100%인지 확인합니다. 문서에 특정 revision 이름을 고정하지 않습니다.

### 3. 공개 service smoke

```bash
SERVICE_URL="$(gcloud run services describe "$CLOUD_RUN_SERVICE" \
  --project "$GCP_PROJECT_ID" \
  --region "$GCP_REGION" \
  --format='value(status.url)')"

curl -fsS "$SERVICE_URL/actuator/health"
SUBJECT_COUNT="$(curl -fsS "$SERVICE_URL/api/subjects/count")"
[[ "$SUBJECT_COUNT" =~ ^[0-9]+$ ]] && test "$SUBJECT_COUNT" -gt 0
curl -fsS "$SERVICE_URL/api/subjects/departments?semester=2026-2" >/dev/null
curl -fsS "$SERVICE_URL/api/subjects/filter?semester=2026-2&page=0&size=20" >/dev/null
curl -fsS "$SERVICE_URL/actuator/prometheus" | rg 'subject_cache_requests_total'
curl -fsS "$SERVICE_URL/actuator/prometheus" | rg 'subject_cache_l1_requests_total'
```

과목 수는 고정된 현재값과 비교하지 않습니다. 학기 import 등으로 정상 변경될 수 있으므로, 배포 전후 값과 업무상 기대 diff를 비교합니다.

### 4. 인증 경계

- `/admin/login`이 `200`인지 확인합니다.
- 새 cookie jar로 `/api/auth/csrf`가 token을 반환하는지 확인합니다.
- 인증하지 않은 `/admin/api/auth/me`가 `403`인지 확인합니다.
- 실제 mutable 관리자 비밀번호를 CI smoke에 입력하지 않습니다.

## Manual Rollback

먼저 현재 traffic과 직전 정상 revision을 정확히 식별합니다.

```bash
gcloud run services describe "$CLOUD_RUN_SERVICE" \
  --project "$GCP_PROJECT_ID" \
  --region "$GCP_REGION" \
  --format='table(status.traffic.revisionName,status.traffic.percent,status.traffic.tag)'
```

승인된 직전 정상 revision으로 traffic을 되돌립니다.

```bash
PREVIOUS_REVISION="<verified-previous-revision>"

gcloud run services update-traffic "$CLOUD_RUN_SERVICE" \
  --project "$GCP_PROJECT_ID" \
  --region "$GCP_REGION" \
  --to-revisions "$PREVIOUS_REVISION=100"
```

rollback 뒤 traffic table, `/actuator/health`, subject API, 인증 경계를 다시 확인합니다. migration은 공유 DB에 이미 적용됐을 수 있으므로 revision rollback이 schema rollback을 뜻하지 않습니다. destructive down migration을 즉시 실행하지 말고 forward fix 또는 사전에 준비한 DB 복구 절차를 사용합니다.

## Flyway and Baseline Checklist

기존 운영 DB는 과거 Hibernate가 만든 핵심 schema 위에 Flyway 증분 migration을 쌓은 구조입니다. `baseline-on-migrate=true`만으로 빈 DB를 현재 schema로 재현할 수 없습니다.

1. 기존 migration의 내용을 수정하거나 checksum을 바꾸지 않습니다.
2. `src/main/resources/db/baseline/V1__baseline_core_schema.sql`은 자동 migration 위치가 아니며 best-effort 초안이라는 경고를 확인합니다.
3. 새 DB는 운영 `pg_dump --schema-only`와 대조한 baseline 및 리허설 없이는 production profile로 띄우지 않습니다.
4. 후보 revision도 shared DB에 연결하므로 migration을 먼저 실행할 수 있음을 고려합니다.
5. 배포 뒤 history를 확인합니다.

```sql
SELECT installed_rank, version, description, success
FROM flyway_schema_history
ORDER BY installed_rank;
```

## Cache and Session Checks

- session은 Redis가 아니라 Supabase PostgreSQL의 `SPRING_SESSION*` table에 저장됩니다.
- Redis는 재생성 가능한 과목 L2 cache입니다. Redis 장애가 session 장애를 의미하지 않습니다.
- Redis failure/recovery/bypass metric과 API status를 함께 봅니다.
- 과목 변경 뒤 `shared_cache_versions`가 증가하고 각 instance L1/L2가 무효화되는지 확인합니다.
- DB version poll/publish는 Redis Pub/Sub 대체가 검증되기 전까지 끄지 않습니다.

## Admin Import Incident

잘못된 학기, 예상 밖 대량 폐강, `409 Conflict`, 사후 검증 실패는
[`../admin-subject-import.md`](../admin-subject-import.md)의 절차를 따릅니다. API 결과를 맞추기 위해 production 과목/시간표를 직접 SQL로 수정하지 않습니다.

## Incident Note

다음 상황에는 dated incident note를 남깁니다.

- candidate smoke 또는 traffic promotion 실패
- 의도하지 않은 revision traffic 분배
- Flyway migration/validation 실패
- 배포 전후 과목 수 또는 기능 결과의 설명되지 않는 변화
- p95/p99, 5xx, Redis failure, DB pool wait 급증
- 잘못된 학기 import 또는 사용자 영향 불일치

```text
Date/time and timezone:
Affected service/revision:
Traffic split:
Impact:
Detection evidence:
Root cause:
Rollback or mitigation:
Database/cache/session checks:
Follow-up test:
```
