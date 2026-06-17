# Operations Runbook

## Deployment Path

```text
GitHub main push
  -> GitHub Actions
  -> Gradle test/build
  -> Docker image push
  -> GCP SSH deploy
  -> inactive blue/green container start
  -> /actuator/health check
  -> nginx upstream switch
  -> previous container cleanup
```

Evidence:

- `.github/workflows/docker-image.yml`
- `scripts/deploy-blue-green.sh`

## Production Guardrails

| Guardrail | Implementation |
|---|---|
| Build gate | `./gradlew clean build` in GitHub Actions |
| Migration source of truth | Flyway migration files in `src/main/resources/db/migration` |
| Hibernate prod safety | `SPRING_JPA_HIBERNATE_DDL_AUTO=validate` |
| Startup safety | New color must pass `/actuator/health` before nginx switch |
| Rollback path | nginx can be switched back to previous active port if public health check fails |
| Admin safety | `/admin/api/**` requires admin session guard and records audit log |
| Observability | Actuator, Micrometer, Prometheus, Grafana |

## Manual Deployment Verification

After a production deploy:

```bash
gh run list --workflow "Deploy to GCP (via Docker Hub)" --limit 5
curl -fsSL https://inuu-timetable.vercel.app/api/subjects/count
```

Expected public course count around the current 2026-1 dataset:

```text
2894
```

If the backend health endpoint is private or proxied through auth, verify health in GitHub Actions deploy logs or on the GCP host:

```bash
curl -fsS http://127.0.0.1:8080/actuator/health
sudo docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
```

## Rollback

The script records the active nginx port before switching. If the new public health check fails, it calls:

```bash
rollback_to_port "$ACTIVE_PORT" "$TARGET_NAME"
```

This writes nginx back to the previous port, reloads nginx, and removes the failed target container.

Manual rollback if needed:

```bash
sudo tee /etc/nginx/conf.d/inu-backend.conf >/dev/null <<'EOF'
server {
    listen 8080;
    server_name _;

    client_max_body_size 50m;

    location / {
        proxy_pass http://127.0.0.1:8081; # or 8082, whichever container is healthy
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_connect_timeout 5s;
        proxy_read_timeout 120s;
    }
}
EOF

sudo nginx -t
sudo systemctl reload nginx
```

## Flyway Checklist

Before a schema-changing deploy:

1. Read the new migration and confirm it is forward-only.
2. Do not rewrite an already-applied migration checksum.
3. Back up production DB before first production migration in a new area.
4. Confirm prod profile uses `ddl-auto=validate`.
5. After deploy, inspect `flyway_schema_history`.

Useful query:

```sql
SELECT installed_rank, version, description, success
FROM flyway_schema_history
ORDER BY installed_rank;
```

## Incident Notes

Create an incident note when:

- deploy fails after image push
- Flyway validation fails
- public course count suddenly drops
- p95/p99 or 5xx rate spikes during registration period
- admin import is applied to the wrong semester

Minimum fields:

```text
Date:
Impact:
Detection:
Root cause:
Rollback or mitigation:
Follow-up test:
```
