# Shift Scheduling & Attendance — Change Log

Scope: shift assignment, leave approval/rejection, notification delivery, and the security boundaries
that those workflows depend on. Existing features (authentication, attendance, dashboards, reports,
employee management) are preserved; where a rule was tightened, section 5 says so.

---

## 1. Status at a glance (read this first)

| Check | Result |
|---|---|
| Offline scenario harness (`verification/offline-harness`): runs the **real** leave, scheduling, notification and gateway-rule code against stand-ins for Spring/JPA | **PASSED 105 / 105** |
| Harness Windows runner (`run-offline-harness.bat`) | **NOT RUN** on Windows; the equivalent bash runner was executed and passed |
| Maven unit tests (`backend/*/src/test`) | **NOT RUN** — Maven and Maven Central were unavailable in the build environment |
| Full Maven compile of all backend services | **NOT RUN** — Spring/JPA/JJWT jars unavailable. Import resolution across all services and syntax (`javac` parse) pass; semantic compile did not run |
| Angular production build (`ng build --configuration=production`) | **NOT RUN** — npm registry unavailable, no `node_modules` |
| TypeScript syntax, all 45 frontend `.ts` files | **PASSED** (0 syntax errors) |
| Strict TypeScript semantic check | Only missing-module diagnostics (Angular/RxJS typings absent); no project-logic diagnostics |
| Angular template structure, 22 `.html` files | **PASSED** (balanced blocks and tags, script check) |
| Docker image builds and `docker compose up` | **NOT RUN** — Docker unavailable |
| Live end-to-end workflow test with Employee, HR and Admin accounts | **NOT RUN.** Script written: `verification/smoke-test.ps1` (unexecuted) |
| Database migrations executed on PostgreSQL | **NOT RUN.** Reviewed against entity mappings; no existing migration was modified |

Nothing in this document claims a build or test passed unless it appears as PASSED above.

---

## 2. Root causes of the original issues

| Area | Symptom | Root cause |
|---|---|---|
| Shift assignment | HR/Admin could not put employees on a shift | `ShiftRequest` had no employee field; assignments existed only inside a `Schedule` |
| Leave approval | Approve/Reject never worked | Frontend sent `{approved}`, backend expects `{status}`; frontend never sent the required `reviewerId` |
| Leave approval | Approver identity was client-supplied | `reviewerId` came from the query string; no role rules existed |
| Notifications | Employees never notified | Neither shift creation nor leave decisions called the notification service |
| Notifications | Read state and icons wrong | Frontend read `n.read`/`n.type`; backend returns `isRead`/`notificationType` |
| Notifications | List did not refresh | Loaded once; nothing polled |
| **Security** | **Any signed-in employee could create an ADMIN account** | `POST /api/auth/register` accepted any `role`, and the gateway allowed any authenticated user to call it |
| **Security** | Any user could read or deactivate user accounts | `/api/auth/users` routed to all authenticated users |
| **Security** | Any user could read all employee records, all schedules (including drafts), all shift definitions | Gateway checked only that a token was valid |
| **Security** | Any user could export company-wide attendance | `/api/attendance/export/csv` had no role check |
| **Security** | Any user could check in, check out or read history for another employee | `employeeId` taken from the request body and not compared with the caller |
| **Security** | Internal services bypassable | Auth, employee, scheduling, attendance, leave and notification ports were published to the host, so requests could skip the gateway's rules |
| Attendance | HR could not create employee accounts | `POST /api/auth/register` was `@PreAuthorize("hasRole('ADMIN')")`, so the employee form's account-creation step failed for HR |
| Shift edit | Edit button led nowhere | Navigated to `/shifts/edit/:id`; route is `/shifts/:id/edit` |

---

## 3. Authorization model

### 3.1 Gateway (`api-gateway/.../filter/JwtAuthFilter.java`, rules in `AccessRules.java`)

Every request except login and health needs a valid Bearer token. The gateway then applies an ordered
rule table; the first matching rule decides. Paths are refused outright (400) if they contain `%`, `;`,
`..`, `//` or `\` — these are the characters that let a gateway and Spring MVC interpret the same path
differently.

| Path (prefix unless noted) | Method | Allowed roles |
|---|---|---|
| `/api/notifications` | POST | nobody from outside (services call notification-service directly) |
| `/api/notifications/user/{id}…` | any | the user whose id is in the path |
| `/api/auth/users` | any | ADMIN |
| `/api/auth/register` | any | ADMIN, HR (auth-service further restricts HR to EMPLOYEE accounts) |
| `/api/leaves/approved`, `/api/leaves/check` | any | ADMIN, HR |
| `/api/employees/all`, `/api/employees/user/{id}` | GET | any signed-in user |
| `/api/employees` (everything else) | any | ADMIN, HR |
| `/api/departments`, `/api/skills` | GET | any signed-in user |
| `/api/departments`, `/api/skills` | other | ADMIN, HR |
| `/api/schedules` | any | ADMIN, HR |
| `/api/shifts/assignments/my` | GET | any signed-in user |
| `/api/shifts` | any | ADMIN, HR |
| `/api/attendance/overview`, `/api/attendance/export` | any | ADMIN, HR |
| `/api/attendance/{id}`, `/api/attendance/{id}/correct` | any | ADMIN, HR |
| everything else (leave apply/pending/decide, check-in/out, history, dashboards) | any | any signed-in user; the owning service applies the finer rule |

Identity headers (`X-User-Id`, `X-User-Role`, `X-User-Name`, `X-Username`) are removed from the incoming
request and re-added from the verified token, so a client cannot supply them.

### 3.2 Services (second layer)

- **auth-service `POST /api/auth/register`:** caller ADMIN → any role; caller HR → EMPLOYEE only (403 otherwise). The caller's role comes from the verified JWT in the security context.
- **attendance-service** check-in, check-out and history: an EMPLOYEE may act only when `employeeId` equals their own user id (the frontend already sends that value); HR and ADMIN are unrestricted. Missing identity fails closed.
- **leave-service:** application requires `employeeId` = caller; viewing another employee's leave or balance requires HR/ADMIN; decisions follow section 4.
- **scheduling-service:** shift create/update/delete and the assignee list require HR or ADMIN.
- **notification-service:** read, update and feed endpoints require the caller to own the notification or feed.

### 3.3 Network

Only the frontend (port 80) and the gateway (127.0.0.1:8080) are published by default. Postgres (127.0.0.1:5432) and Eureka (127.0.0.1:8761) are loopback-only. Services are reachable only on the Docker network. For Swagger during development, use the optional override in section 9.3.

---

## 4. Leave approval rules

| Applicant | Who may approve or reject | Own request |
|---|---|---|
| Employee | HR | never |
| HR | ADMIN, a different user | never |
| ADMIN | another ADMIN | never |
| Legacy row with no recorded role | ADMIN only | never |

- Only `PENDING` requests can be decided. The decision takes a row lock, so concurrent decisions serialize; a repeat decision returns **409** and does not deduct balance again.
- Rejection requires a non-blank reason (**400** otherwise).
- At approval the balance is re-checked under a balance row lock; if it no longer covers the leave, approval is refused (**409**).
- Overlapping `PENDING` or `APPROVED` requests for the same employee are refused at application (**409**).
- Applications must be for the caller; start date may not be in the past; reason 10–500 characters.
- **Single-ADMIN systems:** an ADMIN's own leave stays `PENDING` until a second ADMIN exists. Nothing is approved silently.

---

## 5. Shift assignment rules

Enforced server-side in `ShiftAssignmentService` and `SchedulingService`. The UI mirrors them but is not relied on.

- An employee is assigned to a shift at most once (unique key plus explicit check; **409**).
- **Department rule (new in this pass):** an employee may be assigned only if their department is the shift's department (**422**). The shift form lists only that department's employees, and changing the department drops selections that no longer match.
- The employee must exist, be active, and have a login account (**422** otherwise).
- No overlapping shift for the same employee on the same date (**409**). Overnight shifts are treated conservatively as overlapping.
- Employees assigned ≤ `maxStaff` (**422**).
- Shift times: start ≠ end; end after start unless "Overnight" is ticked; `maxStaff ≥ minStaff`; on create, the date may not be in the past (**400**).
- **Edit with a new employee list:** every submitted employee is re-validated (active, department, overlap). Kept employees are not notified again; newly added employees are notified. A refused edit changes nothing.
- **Edit without a new employee list (new in this pass):** existing assignments are re-validated against the edited date, times and department. If one no longer fits, the edit is refused (**422/409**) so the user resubmits with a corrected list.

---

## 6. Notification rules and delivery

- **Shift assignment:** title `New Shift Assigned`, type `SHIFT_ASSIGNED`. Message: shift name, date, start and end time (with "(next day)" for overnight), department, and who assigned it. Recipient: the assigned employee's login user only.
- **Leave approved:** `Leave Request Approved` — "Your leave request from <start> to <end> has been approved by <approver>. Leave type: <type>. Decision date: <date>."
- **Leave rejected:** `Leave Request Rejected` — same, plus "Reason: <reason>". Recipient: the applicant, never the approver.
- **Transactional outbox:** each notification is written in the same transaction as the assignment or decision (`notification_outbox` table). Delivery happens after commit, with an immediate attempt and then a scheduled retry: 30 s base, doubling, capped at 30 min, up to 10 attempts, then `FAILED`. A notification outage never rolls back or corrupts a decision.
- **Deduplication:** notification-service stores a unique `dedup_key` (`SHIFT_ASSIGNED:<assignmentId>`, `LEAVE_DECISION:<leaveId>:<applicant>`). A retried delivery returns the stored row and creates nothing.
- Notifications are created only by services; the gateway refuses user-initiated creation.
- The Notifications window and unread badge refresh every 30 seconds.

---

## 7. Modified and new files

### auth-service
- `controller/AuthController.java` — register: `hasAnyRole('ADMIN','HR')` plus the HR→EMPLOYEE rule (fixes the HR account-creation bug and the privilege escalation).
- `security/JwtService.java`, `service/AuthService.java` — `fullName` claim (from earlier work).

### api-gateway
- `filter/JwtAuthFilter.java` — token verification, path refusal, header normalisation, feed ownership, calls `AccessRules`.
- `filter/AccessRules.java` — **new**, pure-Java rule table (testable without Spring).

### attendance-service
- `controller/AttendanceController.java` — own-record checks on check-in, check-out and history.

### scheduling-service
- `service/ShiftAssignmentService.java` — department rule; re-validation of kept employees on edit; `revalidateExisting` for edits without a new list.
- `service/SchedulingService.java` — calls `revalidateExisting` when no employee list is submitted.
- Earlier work (unchanged here): `ShiftAssignment`, repository, outbox, employee client, `ShiftController` role checks and endpoints, migration V3.

### leave-service, notification-service
- No changes in this pass. Earlier work: approval matrix, locks, outbox, dedup, ownership, migrations V2.

### frontend (Angular)
- `features/shifts/components/shift-form/*` — employee list restricted to the shift's department; "Select a department first" state; blocks save while selections belong to another department.
- Earlier work: My Shifts page, leave-approval page, notification list polling, shift-list assignee dialog, models and services.

### Infrastructure
- `docker-compose.yml` — internal service ports removed; Postgres, Eureka and the gateway bound to 127.0.0.1.
- `docker-compose.dev.yml` — **new**, optional override that publishes service ports to 127.0.0.1 for Swagger. Not for deployment.
- `README.md` — Swagger URLs and the security note updated.

### Verification (new)
- `verification/offline-harness/` — stand-ins, scenario harness (105 checks), Windows `.bat` and bash runners.
- `verification/smoke-test.ps1` — live end-to-end script (not executed).

---

## 8. Database changes

| Service | Migration | Change | Compatibility |
|---|---|---|---|
| leave | `V2__leave_approval_workflow.sql` | applicant id/role/name, `reviewed_at`, `version`; CHECK on `applicant_role`; backfill `applicant_user_id`; indexes; `notification_outbox` | additive; legacy rows → ADMIN-only review |
| scheduling | `V3__shift_assignments_and_outbox.sql` | `shift_assignments` (FK to `shifts` with `ON DELETE CASCADE`, unique `(shift_id, employee_id)`); `notification_outbox` | additive |
| notification | `V2__notification_dedup_and_shift_type.sql` | `notifications.dedup_key` with partial unique index | additive |

Verified in this pass: no existing migration (any `V1`, or any seed) was modified, so Flyway checksums are unchanged. The tables these migrations reference (`shifts`, `leave_requests`, `leave_balances`, `notifications`) are created by the existing V1 migrations. **Not executed against PostgreSQL.**

---

## 9. Endpoints and commands

### 9.1 New or changed endpoints

| Method | Path | Caller | Notes |
|---|---|---|---|
| POST | `/api/shifts` | HR/ADMIN | body accepts `employeeIds` |
| PUT | `/api/shifts/{id}` | HR/ADMIN | `employeeIds` syncs; omitted → existing assignments re-validated |
| GET | `/api/shifts/{id}/assignments` | HR/ADMIN | new |
| GET | `/api/shifts/assignments/my?startDate&endDate` | any signed-in user | new; own assignments only |
| POST | `/api/leaves` | any | `employeeId` must be the caller |
| GET | `/api/leaves/pending` | any (non-managers receive `[]`) | only requests the caller may decide |
| PUT | `/api/leaves/{id}/review` | per section 4 | body `{"status":"APPROVED"\|"REJECTED","reviewNotes":"…"}` |
| POST | `/api/auth/register` | ADMIN, HR (HR → EMPLOYEE only) | |
| GET | `/api/notifications/user/{id}`, `/unread`, `/count` | owner | |
| PUT | `/api/notifications/{id}/read`, `/user/{id}/read-all` | owner | |
| POST | `/api/notifications`, `/leave-decision`, `/schedule-published` | services only | refused at the gateway |

### 9.2 Windows CMD / PowerShell commands

Prerequisites: Docker Desktop; JDK 17 and Maven (only for unit tests); Node.js 20 (only for the frontend build).

**Build and start (from the project folder):**
```bat
docker compose down
docker compose build --no-cache auth-service api-gateway employee-service scheduling-service attendance-service leave-service notification-service frontend
docker compose up -d
docker compose ps
```
Open `http://localhost`. Demo accounts (seeded on startup): `admin / admin123`, `hr001 / hrpass123`, `employee1`, `employee2`, `employee3` (all `emp123`).

**Unit tests:**
```bat
cd backend\leave-service && mvn test && cd ..\scheduling-service && mvn test && cd ..\notification-service && mvn test && cd ..\auth-service && mvn test && cd ..\attendance-service && mvn test && cd ..\..
```

**Offline scenario harness (JDK only, no Maven):**
```bat
verification\offline-harness\run-offline-harness.bat
```
Expected final line: `RESULT: 105 passed, 0 failed`.

**Frontend build:**
```bat
cd frontend && npm install && npm run build && cd ..
```

**Live smoke test (after `docker compose up -d`; needs employee records for employee1 and employee3 in one department):**
```powershell
powershell -ExecutionPolicy Bypass -File verification\smoke-test.ps1 -DepartmentId 1 -Employee1Id 1 -Employee3Id 3
```
Note: the script has not been executed; treat its first run as a test of the script as well as the system.

**Stop:**
```bat
docker compose down
```

### 9.3 Developer override (Swagger)
```bat
docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d
```
Do not use in any shared or deployed environment.

---

## 10. Manual role test checklist

1. **Shift:** sign in as `hr001`. Create a shift in the employee's department; select `employee1` and `employee3`. Save. Use the group icon to see both names.
2. **Schedule:** sign in as `employee1` → My Schedule shows the shift. `employee2` does not.
3. **Shift notification:** `employee1` → Notifications shows "New Shift Assigned" within ~30 s. `employee2` has none.
4. **Employee leave, HR decides:** `employee1` applies (reason ≥ 10 chars). `hr001` → Leave Approval → Reject without reason (blocked), then with a reason. `employee1` sees "Leave Request Rejected" with the reason.
5. **HR leave, admin decides:** `hr001` applies. `admin` approves; `hr001` cannot approve their own.
6. **Admin leave:** needs a second ADMIN (create via User Management). The first admin cannot decide their own request.
7. **Rejections:** approving the same request twice shows "already been approved". Overlapping dates are refused.
8. **Authorization:** `employee1` cannot open Schedules, Employees, Users or Shifts screens, and the API refuses them.

---

## 11. Known limitations and open items

- **Not verified by a live run.** See section 1. The most important unverified items are the Spring compile, Maven tests, the Angular build, Docker startup, and the migrations on PostgreSQL.
- **Attendance today's list** (`/api/attendance/date/{date}`) is open to all signed-in users because the attendance screen shows everyone's check-in status to employees. This is an existing design choice that was kept deliberately.
- **Employee contact details:** `/api/employees/all` returns names and contact fields to all signed-in users because attendance screens use it. Consider a name-only projection in a later release.
- **Trusted headers:** services trust `X-User-*` headers because only the gateway can reach them. Anyone with access to the Docker network can still send headers directly. Mutual TLS or a shared internal token would close this; it was not implemented.
- **Default secrets:** the database password, JWT secret and demo passwords are development defaults in `docker-compose.yml` and `.env.example`. Change them before any real use. `DEMO_RESET_PASSWORDS=true` (the compose default) resets demo passwords on every start.
- **Department enforcement** applies to shift assignment only. Leave and attendance do not use departments.
- **Existing shifts with invalid times** will be refused on edit until corrected (the new time rules apply on save).
- **Schedule detail screen** (`/scheduling/:id`) has no UI guard for employees, but the API now refuses them, so the screen shows an error for employees rather than data.
- **Postman collection and `docs/architecture-diagram.md`** still mention the old service ports; use the gateway (`localhost:8080`) instead, or the dev override.
