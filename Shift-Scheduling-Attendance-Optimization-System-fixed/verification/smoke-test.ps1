<#
  LIVE end-to-end smoke test for a running stack (Windows PowerShell 5.1 or PowerShell 7).

  STATUS: written but NOT EXECUTED in the environment that produced this release (no Docker there).
  Run it after `docker compose up -d`. Every failed check is printed; the script exits 1 on any failure.

  Prerequisites (created through the UI once, as HR or ADMIN):
    * employee records for employee1 and employee3 (login accounts already exist), both in the same department.
    * a second ADMIN account (optional; only needed for the admin-leave check, which is skipped otherwise).

  Usage:
    powershell -ExecutionPolicy Bypass -File verification\smoke-test.ps1 -DepartmentId 1 -Employee1Id 1 -Employee3Id 3
#>
param(
  [string]$Gateway = "http://localhost:8080",
  [Parameter(Mandatory=$true)][int]$DepartmentId,
  [Parameter(Mandatory=$true)][int]$Employee1Id,
  [Parameter(Mandatory=$true)][int]$Employee3Id
)

$script:Failures = 0
function Check([string]$Name, [bool]$Ok, [string]$Detail = "") {
  if ($Ok) { Write-Host "PASS  $Name" -ForegroundColor Green }
  else     { Write-Host "FAIL  $Name  $Detail" -ForegroundColor Red; $script:Failures++ }
}

# Returns @{ Status = <int>; Body = <object or $null> } without throwing on HTTP errors.
function Call([string]$Method, [string]$Path, $Token = $null, $Body = $null) {
  $headers = @{}
  if ($Token) { $headers["Authorization"] = "Bearer $Token" }
  $params = @{ Uri = "$Gateway$Path"; Method = $Method; Headers = $headers; UseBasicParsing = $true }
  if ($null -ne $Body) { $params["Body"] = ($Body | ConvertTo-Json -Depth 6); $params["ContentType"] = "application/json" }
  try {
    $resp = Invoke-WebRequest @params
    return @{ Status = [int]$resp.StatusCode; Body = ($resp.Content | ConvertFrom-Json -ErrorAction SilentlyContinue) }
  } catch {
    $code = 0
    if ($_.Exception.Response) { $code = [int]$_.Exception.Response.StatusCode }
    return @{ Status = $code; Body = $null }
  }
}

function Login([string]$User, [string]$Pass) {
  $r = Call "POST" "/api/auth/login" $null @{ username = $User; password = $Pass }
  if ($r.Status -ne 200) { throw "Login failed for $User (HTTP $($r.Status))" }
  return $r.Body
}

Write-Host "== Logging in" -ForegroundColor Cyan
$admin = Login "admin" "admin123"
$hr    = Login "hr001" "hrpass123"
$emp1  = Login "employee1" "emp123"
$emp2  = Login "employee2" "emp123"
$emp3  = Login "employee3" "emp123"
Check "login: all five demo accounts authenticate" ($admin.token -and $hr.token -and $emp1.token -and $emp2.token -and $emp3.token)

Write-Host "== Gateway authorization (employee and HR limits)" -ForegroundColor Cyan
Check "employee cannot read /api/schedules" ((Call "GET" "/api/schedules" $emp1.token).Status -eq 403)
Check "employee can read the active employee list" ((Call "GET" "/api/employees/all" $emp1.token).Status -eq 200)
Check "employee cannot list user accounts" ((Call "GET" "/api/auth/users" $emp1.token).Status -eq 403)
Check "employee cannot create accounts" ((Call "POST" "/api/auth/register" $emp1.token @{ username="x_blocked"; email="x@b.com"; password="secret1"; fullName="Blocked User"; role="EMPLOYEE" }).Status -eq 403)
$ts = [int][double]::Parse((Get-Date -UFormat %s))
Check "HR cannot create an ADMIN account (service rule)" ((Call "POST" "/api/auth/register" $hr.token @{ username="adm_$ts"; email="adm$ts@b.com"; password="secret1"; fullName="Should Fail"; role="ADMIN" }).Status -eq 403)
Check "HR can create an EMPLOYEE account" ((Call "POST" "/api/auth/register" $hr.token @{ username="emp_$ts"; email="emp$ts@b.com"; password="secret1"; fullName="Smoke Employee"; role="EMPLOYEE" }).Status -eq 200)
Check "admin can list user accounts" ((Call "GET" "/api/auth/users" $admin.token).Status -eq 200)
$badPath = Call "GET" "/api/%65mployees/all" $emp1.token
Check "encoded path bypass is refused (400)" ($badPath.Status -eq 400)

Write-Host "== Shift assignment and employee schedule" -ForegroundColor Cyan
$day = (Get-Date).AddDays(14).ToString("yyyy-MM-dd")
$shiftBody = @{
  name = "Smoke Desk $ts"; departmentId = $DepartmentId; startTime = "08:00"; endTime = "16:00"; date = $day
  overnight = $false; maxStaff = 3; minStaff = 1; nightShift = $false; requirements = @()
  employeeIds = @($Employee1Id, $Employee3Id)
}
$created = Call "POST" "/api/shifts" $hr.token $shiftBody
Check "HR creates a shift and assigns two employees" ($created.Status -eq 200) "HTTP $($created.Status)"
$shiftId = $created.Body.id
$assignees = Call "GET" "/api/shifts/$shiftId/assignments" $hr.token
Check "HR sees exactly two assignees" ($assignees.Status -eq 200 -and @($assignees.Body).Count -eq 2)
$mine1 = Call "GET" "/api/shifts/assignments/my?startDate=$day&endDate=$day" $emp1.token
Check "employee1 sees the shift in My Shifts" ($mine1.Status -eq 200 -and (@($mine1.Body | Where-Object { $_.shiftId -eq $shiftId }).Count -eq 1))
$mine2 = Call "GET" "/api/shifts/assignments/my?startDate=$day&endDate=$day" $emp2.token
Check "unassigned employee2 does not see it" ($mine2.Status -eq 200 -and (@($mine2.Body | Where-Object { $_.shiftId -eq $shiftId }).Count -eq 0))
Check "employee1 cannot open another shift's assignee list" ((Call "GET" "/api/shifts/$shiftId/assignments" $emp1.token).Status -eq 403)

Write-Host "== Shift notifications (outbox delivers within seconds; allow up to 35s)" -ForegroundColor Cyan
Start-Sleep -Seconds 35
$n1 = Call "GET" "/api/notifications/user/$($emp1.userId)" $emp1.token
$n1Hit = @($n1.Body.content | Where-Object { $_.title -eq "New Shift Assigned" -and $_.message -like "*Smoke Desk $ts*" })
Check "employee1 receives exactly one 'New Shift Assigned' notification" ($n1Hit.Count -eq 1) "found $($n1Hit.Count)"
$n2 = Call "GET" "/api/notifications/user/$($emp2.userId)" $emp2.token
Check "employee2 receives no notification for this shift" (@($n2.Body.content | Where-Object { $_.message -like "*Smoke Desk $ts*" }).Count -eq 0)
Check "employee2 cannot read employee1's feed" ((Call "GET" "/api/notifications/user/$($emp1.userId)" $emp2.token).Status -eq 403)

Write-Host "== Leave: employee request, HR decision" -ForegroundColor Cyan
$leaveStart = (Get-Date).AddDays(60).ToString("yyyy-MM-dd"); $leaveEnd = (Get-Date).AddDays(61).ToString("yyyy-MM-dd")
$leave = Call "POST" "/api/leaves" $emp1.token @{ employeeId = $emp1.userId; leaveType = "PERSONAL"; startDate = $leaveStart; endDate = $leaveEnd; reason = "Smoke test leave request $ts" }
Check "employee1 submits leave as PENDING" ($leave.Status -eq 200 -and $leave.Body.status -eq "PENDING") "HTTP $($leave.Status)"
$leaveId = $leave.Body.id
Check "HR sees it in pending queue" (@((Call "GET" "/api/leaves/pending" $hr.token).Body | Where-Object { $_.id -eq $leaveId }).Count -eq 1)
Check "admin does not see employee requests in their queue" (@((Call "GET" "/api/leaves/pending" $admin.token).Body | Where-Object { $_.id -eq $leaveId }).Count -eq 0)
Check "employee cannot decide leave" ((Call "PUT" "/api/leaves/$leaveId/review" $emp1.token @{ status = "APPROVED" }).Status -eq 403)
Check "rejection without a reason is refused" ((Call "PUT" "/api/leaves/$leaveId/review" $hr.token @{ status = "REJECTED"; reviewNotes = "" }).Status -eq 400)
$rej = Call "PUT" "/api/leaves/$leaveId/review" $hr.token @{ status = "REJECTED"; reviewNotes = "Smoke test rejection reason" }
Check "HR rejects with reason" ($rej.Status -eq 200 -and $rej.Body.status -eq "REJECTED")
Check "second decision is refused (409)" ((Call "PUT" "/api/leaves/$leaveId/review" $hr.token @{ status = "APPROVED" }).Status -eq 409)

Write-Host "== Leave: HR request, decided by admin" -ForegroundColor Cyan
$hrLeave = Call "POST" "/api/leaves" $hr.token @{ employeeId = $hr.userId; leaveType = "PERSONAL"; startDate = $leaveStart; endDate = $leaveStart; reason = "Smoke HR leave $ts" }
Check "HR submits leave as PENDING" ($hrLeave.Status -eq 200 -and $hrLeave.Body.status -eq "PENDING")
Check "HR cannot decide their own leave" ((Call "PUT" "/api/leaves/$($hrLeave.Body.id)/review" $hr.token @{ status = "APPROVED" }).Status -eq 403)
$hrOk = Call "PUT" "/api/leaves/$($hrLeave.Body.id)/review" $admin.token @{ status = "APPROVED" }
Check "admin approves HR leave" ($hrOk.Status -eq 200 -and $hrOk.Body.status -eq "APPROVED")

Start-Sleep -Seconds 35
$n1b = Call "GET" "/api/notifications/user/$($emp1.userId)" $emp1.token
$rejNote = @($n1b.Body.content | Where-Object { $_.title -eq "Leave Request Rejected" -and $_.message -like "*Smoke test rejection reason*" })
Check "employee1 receives the rejection with the reason (once)" ($rejNote.Count -eq 1) "found $($rejNote.Count)"
$hrNotes = Call "GET" "/api/notifications/user/$($hr.userId)" $hr.token
Check "HR receives the approval of their leave" (@($hrNotes.Body.content | Where-Object { $_.title -eq "Leave Request Approved" }).Count -ge 1)

Write-Host "== Attendance self-service" -ForegroundColor Cyan
Check "employee2 cannot check in for employee1" ((Call "POST" "/api/attendance/check-in" $emp2.token @{ employeeId = $emp1.userId }).Status -eq 403)
Check "employee1 can read their own history" ((Call "GET" "/api/attendance/history?employeeId=$($emp1.userId)&startDate=$day&endDate=$day" $emp1.token).Status -eq 200)
Check "employee cannot export company attendance" ((Call "GET" "/api/attendance/export/csv?startDate=$day&endDate=$day" $emp1.token).Status -eq 403)

Write-Host ""
if ($script:Failures -eq 0) { Write-Host "ALL CHECKS PASSED" -ForegroundColor Green; exit 0 }
else { Write-Host "$script:Failures CHECK(S) FAILED" -ForegroundColor Red; exit 1 }
