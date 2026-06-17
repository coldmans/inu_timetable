# Security Evidence

## Security Improvements

| Risk | Previous shape | Current control | Evidence |
|---|---|---|---|
| IDOR | Client-provided `userId` could be trusted by private APIs | Session principal is compared to requested user ID | `UserAccessGuard`, `WishlistControllerSecurityTest`, `TimetableControllerSecurityTest` |
| Password cracking | Legacy unsalted SHA-256 hashes existed | `LegacySha256DelegatingPasswordEncoder` migrates successful logins to BCrypt | `LegacySha256DelegatingPasswordEncoderTest`, `UserSecurityIntegrationTest` |
| CSRF | Browser session mutations need CSRF defense | Spring CSRF token required for private/admin mutations | `AdminCsrfIntegrationTest`, `UserSecurityIntegrationTest` |
| Session fixation | Login should rotate session ID | `ChangeSessionIdAuthenticationStrategy` and admin `changeSessionId()` | `SecurityConfig`, `AdminAuthService` |
| Admin exposure | Admin APIs mixed with public surface | Admin routes moved to `/admin/api/**`, public legacy paths return 404/403 | `AdminEndpointSeparationTest` |
| Brute force login | Repeated password attempts could continue indefinitely | User login rate limit returns 429 after repeated failures; admin login already had lockout | `LoginRateLimitService`, `UserSecurityIntegrationTest`, `AdminAuthServiceTest` |
| Admin action traceability | Production admin mutations had no durable trail | `/admin/api/**` metadata is written to `admin_audit_logs` | `AdminAuditInterceptor`, `AdminAuditLogIntegrationTest` |
| Error shape | Controllers repeated ad hoc try/catch | `@RestControllerAdvice` returns consistent API error body | `ApiExceptionHandler`, `ApiErrorResponse` |

## Auth Boundary Tests

High-value tests to cite:

- `UserSecurityIntegrationTest.userSessionProtectsPrivateUserDataEndpoints`
- `UserSecurityIntegrationTest.privateMutationRequiresCsrfToken`
- `UserSecurityIntegrationTest.legacySha256PasswordMigratesToBcryptOnSuccessfulLogin`
- `UserSecurityIntegrationTest.repeatedUserLoginFailuresAreRateLimited`
- `AdminEndpointSeparationTest.adminSubjectManagementPathRequiresAuthentication`
- `AdminEndpointSeparationTest.adminSubjectCreateRequiresAuthenticationAfterCsrf`
- `AdminCsrfIntegrationTest.adminApiLoginRequiresSpringCsrfToken`
- `AdminAuditLogIntegrationTest.adminLoginSuccessWritesAuditLog`

## Design Notes

- User-facing auth uses Spring Security session principal, not a client-owned token stored in local storage.
- The app preserves existing users by supporting legacy SHA-256 verification only as a migration bridge. Successful login rewrites the password with BCrypt.
- Admin audit logs intentionally do not store request bodies, passwords, CSRF tokens, or raw uploaded file contents.
- Prometheus metrics intentionally avoid user IDs as labels.
- User login rate limit is an in-memory guard for the current single-instance deployment. If the service runs multiple active app instances, the attempt store should move to Redis or another shared store.

## Re-run Commands

```bash
./gradlew test --tests inu.timetable.controller.UserSecurityIntegrationTest
./gradlew test --tests inu.timetable.controller.AdminEndpointSeparationTest
./gradlew test --tests inu.timetable.controller.AdminCsrfIntegrationTest
./gradlew test --tests inu.timetable.controller.AdminAuditLogIntegrationTest
./gradlew test --tests inu.timetable.security.LegacySha256DelegatingPasswordEncoderTest
```
