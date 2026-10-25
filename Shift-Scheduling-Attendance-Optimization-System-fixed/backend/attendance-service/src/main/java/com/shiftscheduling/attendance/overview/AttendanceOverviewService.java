package com.shiftscheduling.attendance.overview;

import com.shiftscheduling.attendance.client.UpstreamDataClient;
import com.shiftscheduling.attendance.repository.AttendanceRecordRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

@Service
public class AttendanceOverviewService {

    static final int MAX_PAGE_SIZE = 100;

    private final AttendanceRecordRepository recordRepo;
    private final UpstreamDataClient upstream;

    public AttendanceOverviewService(AttendanceRecordRepository recordRepo, UpstreamDataClient upstream) {
        this.recordRepo = recordRepo;
        this.upstream = upstream;
    }

    /** Summary counts for the date, honouring the department and search filters (not the status filter). */
    public OverviewSummary summary(LocalDate date, Long departmentId, String search) {
        return AttendanceClassifier.summarize(filteredRows(date, departmentId, search));
    }

    /** Summary + one page of rows, in a single round of upstream calls so the two always agree. */
    public OverviewResponse overview(LocalDate date, OverviewStatus status, Long departmentId,
                                     String search, int page, int size) {
        if (page < 0) throw new IllegalArgumentException("page must be 0 or greater");
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("size must be between 1 and " + MAX_PAGE_SIZE);
        }
        List<OverviewRow> rows = filteredRows(date, departmentId, search);
        OverviewSummary summary = AttendanceClassifier.summarize(rows);
        List<OverviewRow> matching = status == null ? rows
                : rows.stream().filter(r -> r.status() == status).toList();

        long total = matching.size();
        int from = (int) Math.min((long) page * size, total);
        int to = (int) Math.min(from + (long) size, total);
        int totalPages = (int) ((total + size - 1) / size);
        return new OverviewResponse(date, date.equals(LocalDate.now()), summary,
                matching.subList(from, to), page, size, total, totalPages);
    }

    /** All rows for the status/department/search filter (used by CSV export). */
    public List<OverviewRow> export(LocalDate date, OverviewStatus status, Long departmentId, String search) {
        List<OverviewRow> rows = filteredRows(date, departmentId, search);
        return status == null ? rows : rows.stream().filter(r -> r.status() == status).toList();
    }

    public static OverviewStatus parseStatus(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return OverviewStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("status must be one of PRESENT, ABSENT, ON_LEAVE, NOT_SCHEDULED");
        }
    }

    private List<OverviewRow> filteredRows(LocalDate date, Long departmentId, String search) {
        validateDate(date);
        List<OverviewRow> rows = AttendanceClassifier.classify(
                date,
                upstream.activeEmployees(),
                recordRepo.findByDate(date),
                upstream.publishedAssignments(date),
                upstream.approvedLeaves(date));

        String q = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        return rows.stream()
                .filter(r -> departmentId == null || departmentId.equals(r.departmentId()))
                .filter(r -> q.isEmpty()
                        || (r.employeeName() != null && r.employeeName().toLowerCase(Locale.ROOT).contains(q))
                        || String.valueOf(r.employeeId()).equals(q))
                .toList();
    }

    private void validateDate(LocalDate date) {
        if (date == null) throw new IllegalArgumentException("date is required (format yyyy-MM-dd)");
        if (date.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("Attendance cannot be evaluated for a future date");
        }
    }
}
