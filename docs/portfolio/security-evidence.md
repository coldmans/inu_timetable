# Security Evidence

## Current Security Model

| Risk | Current control | Shared backing | Evidence |
|---|---|---|---|
| IDOR | private API의 요청 `userId`와 session principal 일치 검증 | HTTP session | `UserAccessGuard`, `WishlistControllerSecurityTest`, `TimetableControllerSecurityTest` |
| Legacy password | 성공 로그인 때 SHA-256 검증값을 BCrypt로 lazy migration | PostgreSQL | `LegacySha256DelegatingPasswordEncoderTest`, `UserSecurityIntegrationTest` |
| CSRF | browser mutation에 cookie/token proof 요구 | `XSRF-TOKEN` + `X-XSRF-TOKEN` | `SpaCsrfTokenRequestHandler`, `AdminCsrfIntegrationTest`, `UserSecurityIntegrationTest` |
| Session fixation | 로그인과 관리자 credential 변경 때 session ID rotation | Spring Security/JDBC session | `ChangeSessionIdAuthenticationStrategy`, `AdminAuthService` |
| Scale-out session | 최대 3개 Cloud Run instance가 같은 session 사용 | Supabase PostgreSQL `SPRING_SESSION*` | `SharedJdbcSessionConfig`, `SessionMigrationBridgeIntegrationTest` |
| Brute-force login | user/admin namespace와 username+IP별 실패 제한 | PostgreSQL `login_rate_limits` | `JdbcLoginAttemptStore`, `JdbcLoginAttemptStoreTest` |
| Public inquiry abuse | IP별 접수 횟수 제한 | 같은 JDBC store의 `INQUIRY` namespace | `UserInquiryService`, `UserInquiryServiceTest` |
| Admin credential bootstrap | 최초 bootstrap 뒤 DB BCrypt hash와 credential version을 정본으로 사용 | PostgreSQL `admin_accounts` | `AdminAccountIntegrationTest`, `AdminAuthServiceTest` |
| Stale admin session | 현재 DB credential version과 session version 불일치 시 거부 | PostgreSQL + JDBC session | `AdminAuthService` |
| Admin route exposure | `/admin/api/**` controller/service guard, legacy route separation | DB-backed admin session | `AdminAccessGuard`, `AdminEndpointSeparationTest` |
| Import race/stale data | PostgreSQL advisory lock, plan row lock, before snapshot/version 검사, 저장 후 검증 | PostgreSQL | `PostgresAdvisoryLockProviderTest`, `SubjectImportReviewServiceTest` |
| Admin traceability | request metadata를 body 없이 감사 로그로 기록 | PostgreSQL `admin_audit_logs` | `AdminAuditInterceptor`, `AdminAuditLogIntegrationTest` |
| Error leakage | 일관된 API error body와 production health detail 제한 | application layer | `ApiExceptionHandler`, `application-prod.yml` |

## Authentication and Session Boundary

- 사용자 인증은 local-storage bearer token이 아니라 Spring Security session principal을 사용합니다.
- production rollout은 `SHARED_SESSION_ENABLED=true`, `SESSION_BRIDGE_MODE=off`입니다. 현재 계약은 임시 bridge 없이 JDBC session만 사용하는 phase입니다.
- serialized user principal에는 password hash를 넣지 않습니다.
- 관리자 계정은 singleton DB row입니다. DB row가 없을 때만 environment bootstrap credential을 사용하며, bootstrap login 직후에는 credential 변경 전까지 일반 관리자 기능을 막습니다.
- 관리자 credential 변경은 BCrypt hash를 저장하고 version을 올립니다. 모든 관리자 guard는 현재 DB version과 session version을 비교하므로 이전 session은 권한을 잃습니다.
- Redis는 과목 cache용이며 사용자/admin session을 저장하지 않습니다.

## CSRF Boundary

`CookieCsrfTokenRepository`와 SPA request handler는 다음 두 형태를 지원합니다.

- `GET /api/auth/csrf`가 반환한 masked token
- `XSRF-TOKEN` cookie의 raw token을 `X-XSRF-TOKEN` header로 복사한 값

`POST`, `PUT`, `PATCH`, `DELETE` 같은 browser mutation은 명시된 public 예외가 아니면 CSRF proof가 필요합니다. 안전한 `GET`에는 CSRF header를 요구하지 않습니다. 관리자 URL은 Spring authorization rule 자체보다 `AdminAccessGuard`/`AdminAuthService`가 session을 다시 검사하는 구조이므로 controller guard test가 중요합니다.

명시적 public/CSRF 예외에는 사용자 login/register, CSRF token 조회, 과목 조회, product event, 공개 문의, dev endpoint가 포함됩니다. 예외 endpoint를 추가할 때는 인증 필요 여부와 abuse limit을 별도로 검토해야 합니다.

## DB-backed Rate Limits

이전의 in-memory login limit 설명은 더 이상 맞지 않습니다. production 구현은 `JdbcLoginAttemptStore`입니다.

- user와 admin login은 서로 다른 namespace를 사용합니다.
- identity는 정규화한 username과 framework가 해석한 remote address 조합입니다.
- inquiry는 별도 namespace에서 IP만 사용합니다.
- namespace와 identity를 SHA-256으로 hash한 key만 DB에 저장합니다.
- 실패 횟수 증가, 차단 종료 시각, 성공 시 삭제가 instance 간 공유됩니다.
- 오래된 row는 scheduled cleanup으로 정리됩니다.

DB 공유는 Cloud Run scale-out에서 일관된 제한을 제공하지만 DB 장애가 login과 공개 문의 접수에 영향을 줄 수 있습니다. 또한 IP 기반 제한은 학교 NAT처럼 여러 사용자가 같은 공인 IP를 공유하는 환경에서 함께 차단될 수 있으므로 운영 metric과 문의를 함께 봅니다.

## Import Safety Boundary

검토 계획 apply에는 다음 보호가 겹쳐 있습니다.

1. 기본 provider인 PostgreSQL `pg_try_advisory_lock`으로 같은 admin operation의 instance 간 동시 실행을 막습니다.
2. `subject_import_plans` row를 pessimistic write lock으로 읽어 같은 계획의 중복 apply를 막습니다.
3. plan status와 comparison policy version을 확인합니다.
4. 선택한 학수번호의 현재 DB snapshot을 preview 당시 `before`와 비교합니다.
5. 저장 후 entity context를 비우고 다시 조회해 `after`와 일치하는지 검증합니다.
6. 불일치 시 exception으로 transaction을 rollback합니다.

이 전역 advisory lock은 review apply와 일부 공식 replacement import 경로의 보호입니다. 모든 수동 과목 CRUD나 legacy incremental PDF/Excel mutation이 같은 lock으로 직렬화된다고 주장하면 안 됩니다. stale 검사는 선택한 학수번호에 한정되고, 강의계획서 첨부 여부만의 차이는 의도적으로 비교에서 제외됩니다.

## Audit and Data Exposure

- admin audit log는 route, method, status, admin identity 같은 metadata를 저장합니다.
- request body, password, CSRF token, raw upload contents는 감사 로그에 저장하지 않습니다.
- `subject_import_plans`에는 RLS를 켜 Supabase Data API 노출을 제한합니다.
- Prometheus user metric에는 user ID label을 넣지 않습니다.
- production Swagger/OpenAPI는 비활성화하고 Actuator health detail도 숨깁니다.

## High-value Tests

```bash
./gradlew test --tests inu.timetable.controller.UserSecurityIntegrationTest
./gradlew test --tests inu.timetable.controller.AdminEndpointSeparationTest
./gradlew test --tests inu.timetable.controller.AdminCsrfIntegrationTest
./gradlew test --tests inu.timetable.controller.AdminAuditLogIntegrationTest
./gradlew test --tests inu.timetable.service.JdbcLoginAttemptStoreTest
./gradlew test --tests inu.timetable.service.AdminAuthServiceTest
./gradlew test --tests inu.timetable.service.PostgresAdvisoryLockProviderTest
./gradlew test --tests inu.timetable.service.SubjectImportReviewServiceTest
```

## Residual Risks and Scope

- GitHub workflow와 checked-in rollout 값은 배포 계약의 증거이며 live revision 환경을 독립적으로 증명하지는 않습니다.
- PR validation은 H2/Flyway-off이므로 PostgreSQL migration rehearsal을 대체하지 않습니다.
- 과목 Redis는 private VPC와 AUTH를 사용하지만 현재 cache report의 구성은 in-transit TLS를 사용하지 않습니다. 재생성 가능한 비민감 과목 cache로 범위를 제한합니다.
- admin authorization은 custom session guard에 의존하므로 새 `/admin/api/**` controller에 guard 누락이 없는지 separation test를 계속 확장해야 합니다.
