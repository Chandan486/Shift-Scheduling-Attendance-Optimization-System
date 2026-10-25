package com.shiftscheduling.attendance.overview;

import com.shiftscheduling.attendance.client.RemoteAssignment;
import com.shiftscheduling.attendance.client.RemoteEmployee;
import com.shiftscheduling.attendance.client.RemoteLeave;
import com.shiftscheduling.attendance.entity.AttendanceRecord;
import com.shiftscheduling.attendance.entity.AttendanceStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AttendanceClassifierTest {

    private static final LocalDate DATE = LocalDate.of(2026, 10, 19);

    private static RemoteEmployee emp(long id, String name, boolean active) {
        return new RemoteEmployee(id, name, 1L, "Operations", active);
    }

    private static AttendanceRecord rec(long empId, LocalDate date, AttendanceStatus st,
                                        LocalDateTime in, LocalDateTime out) {
        AttendanceRecord r = new AttendanceRecord();
        r.setEmployeeId(empId);
        r.setDate(date);
        r.setStatus(st);
        r.setCheckInTime(in);
        r.setCheckOutTime(out);
        return r;
    }

    private static RemoteAssignment assign(long empId, String status) {
        return new RemoteAssignment(empId, DATE, 10L, "Morning", LocalTime.of(9, 0), LocalTime.of(17, 0), status);
    }

    private static RemoteLeave leave(long empId, String status, LocalDate from, LocalDate to) {
        return new RemoteLeave(empId, "SICK", from, to, status);
    }

    private static OverviewRow rowOf(List<OverviewRow> rows, long id) {
        return rows.stream().filter(r -> r.employeeId() == id).findFirst().orElseThrow();
    }

    @Test
    void validAttendanceIsPresent() {
        var rows = AttendanceClassifier.classify(DATE, List.of(emp(1, "Asha", true)),
                List.of(rec(1, DATE, AttendanceStatus.CHECKED_OUT, DATE.atTime(9, 5), DATE.atTime(17, 0))),
                List.of(assign(1, "ASSIGNED")), List.of());
        assertEquals(OverviewStatus.PRESENT, rowOf(rows, 1).status());
        assertFalse(rowOf(rows, 1).incomplete());
    }

    @Test
    void scheduledWithoutRecordOrLeaveIsAbsent() {
        var rows = AttendanceClassifier.classify(DATE, List.of(emp(1, "Asha", true)),
                List.of(), List.of(assign(1, "CONFIRMED")), List.of());
        assertEquals(OverviewStatus.ABSENT, rowOf(rows, 1).status());
    }

    @Test
    void approvedLeaveIsOnLeave() {
        var rows = AttendanceClassifier.classify(DATE, List.of(emp(1, "Asha", true)),
                List.of(), List.of(assign(1, "ASSIGNED")), List.of(leave(1, "APPROVED", DATE.minusDays(1), DATE.plusDays(1))));
        assertEquals(OverviewStatus.ON_LEAVE, rowOf(rows, 1).status());
        assertEquals("SICK", rowOf(rows, 1).leaveType());
    }

    @Test
    void noShiftIsNotScheduled() {
        var rows = AttendanceClassifier.classify(DATE, List.of(emp(1, "Asha", true)),
                List.of(), List.of(), List.of());
        assertEquals(OverviewStatus.NOT_SCHEDULED, rowOf(rows, 1).status());
    }

    @Test
    void pendingAndRejectedLeaveDoNotCountAsLeave() {
        var rows = AttendanceClassifier.classify(DATE,
                List.of(emp(1, "Asha", true), emp(2, "Ravi", true)),
                List.of(), List.of(assign(1, "ASSIGNED"), assign(2, "ASSIGNED")),
                List.of(leave(1, "PENDING", DATE, DATE), leave(2, "REJECTED", DATE, DATE)));
        assertEquals(OverviewStatus.ABSENT, rowOf(rows, 1).status());
        assertEquals(OverviewStatus.ABSENT, rowOf(rows, 2).status());
    }

    @Test
    void leaveOutsideDateRangeIsIgnored() {
        var rows = AttendanceClassifier.classify(DATE, List.of(emp(1, "Asha", true)),
                List.of(), List.of(assign(1, "ASSIGNED")),
                List.of(leave(1, "APPROVED", DATE.plusDays(1), DATE.plusDays(3))));
        assertEquals(OverviewStatus.ABSENT, rowOf(rows, 1).status());
    }

    @Test
    void cancelledAssignmentMeansNotScheduled() {
        var rows = AttendanceClassifier.classify(DATE, List.of(emp(1, "Asha", true)),
                List.of(), List.of(assign(1, "CANCELLED")), List.of());
        assertEquals(OverviewStatus.NOT_SCHEDULED, rowOf(rows, 1).status());
    }

    @Test
    void incompleteRecordIsPresentAndFlagged() {
        var rows = AttendanceClassifier.classify(DATE, List.of(emp(1, "Asha", true)),
                List.of(rec(1, DATE, AttendanceStatus.CHECKED_IN, DATE.atTime(9, 0), null)),
                List.of(assign(1, "ASSIGNED")), List.of());
        OverviewRow r = rowOf(rows, 1);
        assertEquals(OverviewStatus.PRESENT, r.status());
        assertTrue(r.incomplete());
        assertNull(r.checkOutTime());
    }

    @Test
    void absentStatusRecordDoesNotCountAsPresent() {
        var rows = AttendanceClassifier.classify(DATE, List.of(emp(1, "Asha", true)),
                List.of(rec(1, DATE, AttendanceStatus.ABSENT, DATE.atTime(9, 0), null)),
                List.of(assign(1, "ASSIGNED")), List.of());
        assertEquals(OverviewStatus.ABSENT, rowOf(rows, 1).status());
    }

    @Test
    void duplicateRecordsAreMergedAndNotCountedTwice() {
        var rows = AttendanceClassifier.classify(DATE, List.of(emp(1, "Asha", true)),
                List.of(rec(1, DATE, AttendanceStatus.CHECKED_OUT, DATE.atTime(9, 0), DATE.atTime(13, 0)),
                        rec(1, DATE, AttendanceStatus.CHECKED_OUT, DATE.atTime(14, 0), DATE.atTime(18, 0))),
                List.of(assign(1, "ASSIGNED")), List.of());
        assertEquals(1, rows.size());
        assertEquals(DATE.atTime(9, 0), rows.get(0).checkInTime());
        assertEquals(DATE.atTime(18, 0), rows.get(0).checkOutTime());
        assertEquals(1, AttendanceClassifier.summarize(rows).present());
    }

    @Test
    void overnightShiftCheckoutOnNextDayStaysOnWorkDate() {
        var rows = AttendanceClassifier.classify(DATE, List.of(emp(1, "Asha", true)),
                List.of(rec(1, DATE, AttendanceStatus.CHECKED_OUT, DATE.atTime(22, 0), DATE.plusDays(1).atTime(6, 0))),
                List.of(), List.of());
        assertEquals(OverviewStatus.PRESENT, rowOf(rows, 1).status());
        assertEquals(DATE.plusDays(1).atTime(6, 0), rowOf(rows, 1).checkOutTime());
    }

    @Test
    void recordsFromOtherDatesAreIgnored() {
        var rows = AttendanceClassifier.classify(DATE, List.of(emp(1, "Asha", true)),
                List.of(rec(1, DATE.minusDays(1), AttendanceStatus.CHECKED_OUT, DATE.minusDays(1).atTime(9, 0), null)),
                List.of(assign(1, "ASSIGNED")), List.of());
        assertEquals(OverviewStatus.ABSENT, rowOf(rows, 1).status());
    }

    @Test
    void inactiveEmployeesAreExcludedAndDuplicatesCollapsed() {
        var rows = AttendanceClassifier.classify(DATE,
                List.of(emp(1, "Asha", true), emp(1, "Asha", true), emp(2, "Old", false)),
                List.of(), List.of(), List.of());
        assertEquals(1, rows.size());
    }

    @Test
    void attendanceWinsOverLeave_andEveryEmployeeIsInExactlyOneCategory() {
        var employees = List.of(emp(1, "A", true), emp(2, "B", true), emp(3, "C", true), emp(4, "D", true));
        var rows = AttendanceClassifier.classify(DATE, employees,
                List.of(rec(1, DATE, AttendanceStatus.LATE, DATE.atTime(10, 0), DATE.atTime(17, 0))),
                List.of(assign(1, "ASSIGNED"), assign(3, "ASSIGNED")),
                List.of(leave(1, "APPROVED", DATE, DATE), leave(2, "APPROVED", DATE, DATE)));
        OverviewSummary s = AttendanceClassifier.summarize(rows);
        assertEquals(OverviewStatus.PRESENT, rowOf(rows, 1).status());
        assertEquals(1, s.present());
        assertEquals(1, s.onLeave());
        assertEquals(1, s.absent());
        assertEquals(1, s.notScheduled());
        assertEquals(employees.size(), s.present() + s.absent() + s.onLeave() + s.notScheduled());
        assertEquals(employees.size(), s.total());
    }
}
