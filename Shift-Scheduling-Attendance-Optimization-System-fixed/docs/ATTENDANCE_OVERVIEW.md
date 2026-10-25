# Attendance Overview (Admin / HR)

Date-wise report: pick a date and see who was **Present**, **Absent**, **On Leave** or **Not Scheduled**.
UI route: `/attendance/overview` (sidebar: *Attendance Overview*, plus a Dashboard quick action). Admin and HR only.

## How it works
`attendance-service` builds the report by reading (never writing) data from the other services through Eureka:

| Data | Source |
|---|---|
| Active employees + department | employee-service `GET /api/employees/all` (existing) |
| Attendance records | attendance-service own DB |
| Approved leave covering the date | leave-service `GET /api/leaves/approved?startDate=D&endDate=D` (existing) |
| Scheduled shifts | scheduling-service `GET /api/schedules/assignments/by-date?date=D` (**new**, read-only) |

No tables, columns or migrations were added or changed.

## Classification rules (each employee lands in exactly one category)
1. **Present** - at least one attendance record on that date with status CHECKED_IN, CHECKED_OUT, LATE or EARLY_DEPARTURE. Several records for the same employee/day are merged (earliest check-in, latest check-out), so counts are never inflated. A record without check-out is still Present and flagged "No check-out". A record with status `ABSENT` does not count as Present.
2. **On Leave** - no attendance record, and an APPROVED leave covers the date. PENDING and REJECTED leave are ignored.
3. **Absent** - no attendance record, no approved leave, and the employee has a non-cancelled assignment on a PUBLISHED schedule for that date.
4. **Not Scheduled** - everything else (no shift, only DRAFT/ARCHIVED schedule, or cancelled assignment).

Notes: if someone has approved leave but still checked in, they are shown as Present (real attendance wins). Only active employees are listed. An overnight shift belongs to the date it starts on (same as the existing check-in `date` field); its check-out may show the next day. Future dates are rejected (HTTP 400). For *today*, employees whose shift has not started yet appear as Absent until they check in (the UI shows a banner).

## API (through the gateway, port 8080)
All require `Authorization: Bearer <token>` of an ADMIN or HR user (others get 403).

- `GET /api/attendance/overview/summary?date=2026-10-19[&departmentId=1][&search=asha]`
  -> `{"present":4,"absent":6,"onLeave":1,"notScheduled":14,"total":25}`
- `GET /api/attendance/overview/employees?date=2026-10-19&status=ABSENT[&departmentId][&search][&page=0&size=20]`
  -> `{"date":"2026-10-19","today":false,"summary":{...},"content":[{"employeeId":5,"employeeName":"...","department":"...","status":"ABSENT","checkInTime":null,"checkOutTime":null,"attendanceStatus":null,"incomplete":false,"shiftName":"Morning","leaveType":null}],"page":0,"size":20,"totalElements":6,"totalPages":1}`
  (`status` optional: PRESENT, ABSENT, ON_LEAVE, NOT_SCHEDULED; size 1-100)
- `GET /api/attendance/overview/export?date=...&status=...` -> CSV file (all rows matching the filters, not just one page)

Errors: 400 bad date/paging/status, 403 wrong role, 503 when employee/scheduling/leave service is unreachable.

## Security
- The gateway (`JwtAuthFilter`) verifies the JWT, returns 403 on `/api/attendance/overview/**` unless the token role is ADMIN or HR, and now strips any client-sent `X-User-Id` / `X-User-Role` / `X-Username` headers before adding the verified ones.
- The controller re-checks the role header as a second layer. Note: downstream service ports are published in docker-compose (existing behaviour); in production only the gateway port should be exposed.

## Tests
`backend/attendance-service/src/test/java/.../overview/` - classifier rules, service (summary = lists, search, department, pagination, validation), CSV writer, controller authorization. Run: `cd backend && mvn -pl attendance-service test` (or `mvn test` in `backend/attendance-service`).
