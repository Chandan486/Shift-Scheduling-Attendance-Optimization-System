package com.shiftscheduling.attendance.overview;

import com.shiftscheduling.attendance.client.RemoteAssignment;
import com.shiftscheduling.attendance.client.RemoteEmployee;
import com.shiftscheduling.attendance.client.RemoteLeave;
import com.shiftscheduling.attendance.client.UpstreamDataClient;
import com.shiftscheduling.attendance.entity.AttendanceRecord;
import com.shiftscheduling.attendance.entity.AttendanceStatus;
import com.shiftscheduling.attendance.repository.AttendanceRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AttendanceOverviewServiceTest {

    @Mock AttendanceRecordRepository recordRepo;
    @Mock UpstreamDataClient upstream;

    AttendanceOverviewService service;
    final LocalDate date = LocalDate.now().minusDays(1);

    @BeforeEach
    void setUp() {
        service = new AttendanceOverviewService(recordRepo, upstream);
        List<RemoteEmployee> employees = new ArrayList<>();
        for (long i = 1; i <= 25; i++) {
            employees.add(new RemoteEmployee(i, "Employee " + String.format("%02d", i), i % 2 == 0 ? 2L : 1L,
                    i % 2 == 0 ? "Support" : "Ops", true));
        }
        when(upstream.activeEmployees()).thenReturn(employees);
        when(upstream.approvedLeaves(any())).thenReturn(List.of(new RemoteLeave(3L, "ANNUAL", date, date, "APPROVED")));
        // employees 1..10 scheduled; 1..4 attended
        List<RemoteAssignment> assigns = new ArrayList<>();
        for (long i = 1; i <= 10; i++) {
            assigns.add(new RemoteAssignment(i, date, 7L, "Morning", LocalTime.of(9, 0), LocalTime.of(17, 0), "ASSIGNED"));
        }
        when(upstream.publishedAssignments(any())).thenReturn(assigns);
        List<AttendanceRecord> recs = new ArrayList<>();
        for (long i = 1; i <= 4; i++) {
            AttendanceRecord r = new AttendanceRecord();
            r.setEmployeeId(i);
            r.setDate(date);
            r.setStatus(AttendanceStatus.CHECKED_OUT);
            r.setCheckInTime(date.atTime(9, 0));
            r.setCheckOutTime(date.atTime(17, 0));
            recs.add(r);
        }
        when(recordRepo.findByDate(date)).thenReturn(recs);
    }

    @Test
    void summaryMatchesListsAndCoversEveryEmployee() {
        OverviewResponse all = service.overview(date, null, null, null, 0, 100);
        OverviewSummary s = all.summary();
        // present: 1,2,4 (3 is on leave but did not attend) -> 4 attended incl. 3? 3 attended => PRESENT wins
        assertEquals(4, s.present());
        assertEquals(6, s.absent());          // 5..10 scheduled, no record, no leave
        assertEquals(0, s.onLeave());          // 3 attended, so PRESENT takes precedence
        assertEquals(15, s.notScheduled());
        assertEquals(25, s.total());
        assertEquals(s.present(), service.overview(date, OverviewStatus.PRESENT, null, null, 0, 100).totalElements());
        assertEquals(s.absent(), service.overview(date, OverviewStatus.ABSENT, null, null, 0, 100).totalElements());
    }

    @Test
    void paginationSplitsRows() {
        OverviewResponse p0 = service.overview(date, OverviewStatus.NOT_SCHEDULED, null, null, 0, 10);
        OverviewResponse p1 = service.overview(date, OverviewStatus.NOT_SCHEDULED, null, null, 1, 10);
        assertEquals(15, p0.totalElements());
        assertEquals(2, p0.totalPages());
        assertEquals(10, p0.content().size());
        assertEquals(5, p1.content().size());
        assertTrue(service.overview(date, OverviewStatus.NOT_SCHEDULED, null, null, 5, 10).content().isEmpty());
    }

    @Test
    void searchByNameAndIdAndDepartmentFilter() {
        assertEquals(1, service.overview(date, null, null, "employee 07", 0, 20).totalElements());
        assertEquals(1, service.overview(date, null, null, "12", 0, 20).totalElements());
        OverviewResponse support = service.overview(date, null, 2L, null, 0, 100);
        assertEquals(12, support.totalElements());
        assertEquals(12, support.summary().total());   // summary follows the same filter
    }

    @Test
    void rejectsFutureDateBadPagingAndBadStatus() {
        assertThrows(IllegalArgumentException.class,
                () -> service.overview(LocalDate.now().plusDays(1), null, null, null, 0, 20));
        assertThrows(IllegalArgumentException.class, () -> service.overview(date, null, null, null, -1, 20));
        assertThrows(IllegalArgumentException.class, () -> service.overview(date, null, null, null, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> service.overview(date, null, null, null, 0, 101));
        assertThrows(IllegalArgumentException.class, () -> AttendanceOverviewService.parseStatus("LATE"));
        assertNull(AttendanceOverviewService.parseStatus(" "));
        assertEquals(OverviewStatus.ON_LEAVE, AttendanceOverviewService.parseStatus("on_leave"));
    }

    @Test
    void emptyEmployeeListReturnsEmptyResultNotAnError() {
        when(upstream.activeEmployees()).thenReturn(List.of());
        OverviewResponse r = service.overview(date, null, null, null, 0, 20);
        assertEquals(0, r.totalElements());
        assertEquals(0, r.summary().total());
        assertTrue(r.content().isEmpty());
    }
}
