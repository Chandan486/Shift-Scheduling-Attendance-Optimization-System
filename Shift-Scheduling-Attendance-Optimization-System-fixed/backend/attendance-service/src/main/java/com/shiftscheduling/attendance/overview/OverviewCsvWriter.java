package com.shiftscheduling.attendance.overview;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Builds the CSV text for the overview export (RFC 4180 quoting + spreadsheet formula guard). */
public final class OverviewCsvWriter {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private OverviewCsvWriter() {}

    public static String write(LocalDate date, List<OverviewRow> rows) {
        StringBuilder sb = new StringBuilder("\uFEFF");   // BOM so Excel opens UTF-8 correctly
        sb.append("Date,Employee ID,Employee Name,Department,Status,Check In,Check Out,Record Status,Incomplete,Shift,Leave Type\r\n");
        for (OverviewRow r : rows) {
            sb.append(date).append(',')
              .append(r.employeeId()).append(',')
              .append(cell(r.employeeName())).append(',')
              .append(cell(r.department())).append(',')
              .append(r.status().name()).append(',')
              .append(r.checkInTime() != null ? r.checkInTime().format(TS) : "").append(',')
              .append(r.checkOutTime() != null ? r.checkOutTime().format(TS) : "").append(',')
              .append(cell(r.attendanceStatus())).append(',')
              .append(r.status() == OverviewStatus.PRESENT ? (r.incomplete() ? "Yes" : "No") : "").append(',')
              .append(cell(r.shiftName())).append(',')
              .append(cell(r.leaveType()))
              .append("\r\n");
        }
        return sb.toString();
    }

    static String cell(String value) {
        if (value == null) return "";
        String v = value;
        if (!v.isEmpty() && "=+-@\t\r".indexOf(v.charAt(0)) >= 0) {
            v = "'" + v;                                   // neutralise CSV/formula injection
        }
        if (v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r")) {
            return "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }
}
