package com.shiftscheduling.attendance.overview;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OverviewCsvWriterTest {

    @Test
    void writesHeaderAndRowsWithQuotingAndFormulaGuard() {
        LocalDate d = LocalDate.of(2026, 10, 19);
        var present = new OverviewRow(1L, "Doe, John", 1L, "Ops", OverviewStatus.PRESENT,
                d.atTime(9, 0), null, "CHECKED_IN", true, "Morning", null);
        var absent = new OverviewRow(2L, "=cmd()", 1L, "Ops", OverviewStatus.ABSENT,
                null, null, null, false, "Morning", null);
        String csv = OverviewCsvWriter.write(d, List.of(present, absent));
        String[] lines = csv.split("\r\n");
        assertTrue(lines[0].contains("Employee ID,Employee Name"));
        assertTrue(lines[1].contains("\"Doe, John\""));
        assertTrue(lines[1].contains("2026-10-19 09:00:00"));
        assertTrue(lines[1].contains(",Yes,"));
        assertTrue(lines[2].contains("'=cmd()"));
        assertEquals(3, lines.length);
    }
}
