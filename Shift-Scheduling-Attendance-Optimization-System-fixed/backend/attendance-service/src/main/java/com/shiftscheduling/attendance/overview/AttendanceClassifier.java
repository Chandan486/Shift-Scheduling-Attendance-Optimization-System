package com.shiftscheduling.attendance.overview;

import com.shiftscheduling.attendance.client.RemoteAssignment;
import com.shiftscheduling.attendance.client.RemoteEmployee;
import com.shiftscheduling.attendance.client.RemoteLeave;
import com.shiftscheduling.attendance.entity.AttendanceRecord;
import com.shiftscheduling.attendance.entity.AttendanceStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Pure classification logic (no Spring, no I/O) so it can be unit-tested directly.
 *
 * Precedence, so every employee lands in exactly one category:
 *   1. PRESENT       - a valid attendance record exists for the date
 *   2. ON_LEAVE      - an APPROVED leave covers the date
 *   3. ABSENT        - scheduled (published, non-cancelled assignment) but no record and no approved leave
 *   4. NOT_SCHEDULED - everything else
 */
public final class AttendanceClassifier {

    /** Record statuses that count as "the employee showed up". ABSENT records do not. */
    static final Set<AttendanceStatus> PRESENT_STATUSES = EnumSet.of(
            AttendanceStatus.CHECKED_IN, AttendanceStatus.CHECKED_OUT,
            AttendanceStatus.LATE, AttendanceStatus.EARLY_DEPARTURE);

    private AttendanceClassifier() {}

    public static List<OverviewRow> classify(LocalDate date,
                                             List<RemoteEmployee> employees,
                                             List<AttendanceRecord> records,
                                             List<RemoteAssignment> assignments,
                                             List<RemoteLeave> leaves) {

        Map<Long, List<AttendanceRecord>> recordsByEmployee = new HashMap<>();
        for (AttendanceRecord r : records) {
            if (r.getEmployeeId() == null || r.getStatus() == null || r.getCheckInTime() == null) continue;
            if (!date.equals(r.getDate()) || !PRESENT_STATUSES.contains(r.getStatus())) continue;
            recordsByEmployee.computeIfAbsent(r.getEmployeeId(), k -> new ArrayList<>()).add(r);
        }

        Map<Long, RemoteLeave> leaveByEmployee = new HashMap<>();
        for (RemoteLeave l : leaves) {
            if (l.employeeId() == null || l.startDate() == null || l.endDate() == null) continue;
            if (!"APPROVED".equalsIgnoreCase(l.status())) continue;          // pending / rejected never count
            if (date.isBefore(l.startDate()) || date.isAfter(l.endDate())) continue;
            leaveByEmployee.putIfAbsent(l.employeeId(), l);
        }

        Map<Long, RemoteAssignment> scheduledByEmployee = new HashMap<>();
        for (RemoteAssignment a : assignments) {
            if (a.employeeId() == null || !date.equals(a.date())) continue;
            if ("CANCELLED".equalsIgnoreCase(a.status())) continue;
            scheduledByEmployee.merge(a.employeeId(), a, (first, second) -> earlier(first, second));
        }

        // De-duplicate employees by id, keep only active ones.
        Map<Long, RemoteEmployee> uniqueEmployees = new LinkedHashMap<>();
        for (RemoteEmployee e : employees) {
            if (e.id() != null && e.active()) uniqueEmployees.putIfAbsent(e.id(), e);
        }

        List<OverviewRow> rows = new ArrayList<>();
        for (RemoteEmployee e : uniqueEmployees.values()) {
            List<AttendanceRecord> recs = recordsByEmployee.get(e.id());
            RemoteLeave leave = leaveByEmployee.get(e.id());
            RemoteAssignment scheduled = scheduledByEmployee.get(e.id());
            String shiftName = scheduled != null ? scheduled.shiftName() : null;

            if (recs != null && !recs.isEmpty()) {
                rows.add(presentRow(e, recs, shiftName));
            } else if (leave != null) {
                rows.add(new OverviewRow(e.id(), e.fullName(), e.departmentId(), e.departmentName(),
                        OverviewStatus.ON_LEAVE, null, null, null, false, shiftName, leave.leaveType()));
            } else if (scheduled != null) {
                rows.add(new OverviewRow(e.id(), e.fullName(), e.departmentId(), e.departmentName(),
                        OverviewStatus.ABSENT, null, null, null, false, shiftName, null));
            } else {
                rows.add(new OverviewRow(e.id(), e.fullName(), e.departmentId(), e.departmentName(),
                        OverviewStatus.NOT_SCHEDULED, null, null, null, false, null, null));
            }
        }

        rows.sort(Comparator
                .comparing((OverviewRow r) -> r.employeeName() == null ? "" : r.employeeName().toLowerCase(Locale.ROOT))
                .thenComparing(OverviewRow::employeeId));
        return rows;
    }

    public static OverviewSummary summarize(List<OverviewRow> rows) {
        int present = 0, absent = 0, onLeave = 0, notScheduled = 0;
        for (OverviewRow r : rows) {
            switch (r.status()) {
                case PRESENT -> present++;
                case ABSENT -> absent++;
                case ON_LEAVE -> onLeave++;
                case NOT_SCHEDULED -> notScheduled++;
            }
        }
        return new OverviewSummary(present, absent, onLeave, notScheduled, rows.size());
    }

    /** Several records for one employee/day are merged into one row so counts are never inflated. */
    private static OverviewRow presentRow(RemoteEmployee e, List<AttendanceRecord> recs, String shiftName) {
        LocalDateTime firstIn = null;
        LocalDateTime lastOut = null;
        AttendanceRecord earliest = null;
        AttendanceRecord withLastOut = null;
        for (AttendanceRecord r : recs) {
            if (firstIn == null || r.getCheckInTime().isBefore(firstIn)) {
                firstIn = r.getCheckInTime();
                earliest = r;
            }
            if (r.getCheckOutTime() != null && (lastOut == null || r.getCheckOutTime().isAfter(lastOut))) {
                lastOut = r.getCheckOutTime();
                withLastOut = r;
            }
        }
        AttendanceRecord source = withLastOut != null ? withLastOut : earliest;
        boolean incomplete = lastOut == null;
        return new OverviewRow(e.id(), e.fullName(), e.departmentId(), e.departmentName(),
                OverviewStatus.PRESENT, firstIn, lastOut, source.getStatus().name(), incomplete, shiftName, null);
    }

    private static RemoteAssignment earlier(RemoteAssignment a, RemoteAssignment b) {
        if (a.shiftStartTime() == null) return b;
        if (b.shiftStartTime() == null) return a;
        return b.shiftStartTime().isBefore(a.shiftStartTime()) ? b : a;
    }
}
