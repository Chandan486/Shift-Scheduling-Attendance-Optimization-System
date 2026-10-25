package com.shiftscheduling.attendance.service;

import com.shiftscheduling.attendance.dto.*;
import com.shiftscheduling.attendance.entity.AttendanceCorrection;
import com.shiftscheduling.attendance.entity.AttendanceRecord;
import com.shiftscheduling.attendance.entity.AttendanceStatus;
import com.shiftscheduling.attendance.repository.AttendanceCorrectionRepository;
import com.shiftscheduling.attendance.repository.AttendanceRecordRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;

@Service
public class AttendanceService {

    private final AttendanceRecordRepository recordRepo;
    private final AttendanceCorrectionRepository correctionRepo;

    public AttendanceService(AttendanceRecordRepository recordRepo,
                             AttendanceCorrectionRepository correctionRepo) {
        this.recordRepo = recordRepo;
        this.correctionRepo = correctionRepo;
    }

    @Transactional
    public AttendanceResponse checkIn(CheckInRequest req) {
        LocalDate today = LocalDate.now();

        boolean alreadyCheckedIn = recordRepo.existsByEmployeeIdAndDateAndStatusIn(
                req.employeeId(), today,
                Set.of(AttendanceStatus.CHECKED_IN, AttendanceStatus.CHECKED_OUT,
                        AttendanceStatus.LATE, AttendanceStatus.EARLY_DEPARTURE));
        if (alreadyCheckedIn) {
            throw new RuntimeException("Employee already has an attendance record for today");
        }

        AttendanceRecord record = new AttendanceRecord();
        record.setEmployeeId(req.employeeId());
        record.setShiftId(req.shiftId());
        record.setDate(today);
        record.setCheckInTime(LocalDateTime.now());
        record.setStatus(AttendanceStatus.CHECKED_IN);
        record.setNotes(req.notes());

        return toResponse(recordRepo.save(record));
    }

    @Transactional
    public AttendanceResponse checkOut(CheckOutRequest req) {
        LocalDate today = LocalDate.now();

        AttendanceRecord record = recordRepo.findByEmployeeIdAndDateAndStatus(
                req.employeeId(), today, AttendanceStatus.CHECKED_IN)
                .orElseThrow(() -> new RuntimeException("No active check-in found for employee today"));

        LocalDateTime now = LocalDateTime.now();
        record.setCheckOutTime(now);

        double hours = Duration.between(record.getCheckInTime(), now).toMinutes() / 60.0;
        record.setWorkedHours(Math.round(hours * 100.0) / 100.0);

        if (hours < 4.0) {
            record.setStatus(AttendanceStatus.EARLY_DEPARTURE);
        } else {
            record.setStatus(AttendanceStatus.CHECKED_OUT);
        }

        if (req.notes() != null && !req.notes().isBlank()) {
            String existing = record.getNotes();
            record.setNotes(existing != null ? existing + " | " + req.notes() : req.notes());
        }

        return toResponse(recordRepo.save(record));
    }

    public Page<AttendanceResponse> getAttendanceHistory(Long employeeId, LocalDate startDate,
                                                          LocalDate endDate, Pageable pageable) {
        return recordRepo.findByEmployeeIdAndDateBetween(employeeId, startDate, endDate, pageable)
                .map(this::toResponse);
    }

    public List<AttendanceResponse> getAttendanceByDate(LocalDate date) {
        return recordRepo.findByDate(date).stream().map(this::toResponse).toList();
    }

    @Transactional
    public CorrectionResponse correctAttendance(Long attendanceId, CorrectionRequest req) {
        AttendanceRecord record = recordRepo.findById(attendanceId)
                .orElseThrow(() -> new RuntimeException("Attendance record not found"));

        AttendanceCorrection correction = new AttendanceCorrection();
        correction.setAttendanceRecordId(attendanceId);
        correction.setCorrectedBy(req.correctedBy());
        correction.setPreviousCheckIn(record.getCheckInTime());
        correction.setPreviousCheckOut(record.getCheckOutTime());
        correction.setNewCheckIn(req.newCheckIn());
        correction.setNewCheckOut(req.newCheckOut());
        correction.setReason(req.reason());

        if (req.newCheckIn() != null) {
            record.setCheckInTime(req.newCheckIn());
        }
        if (req.newCheckOut() != null) {
            record.setCheckOutTime(req.newCheckOut());
        }

        if (record.getCheckInTime() != null && record.getCheckOutTime() != null) {
            double hours = Duration.between(record.getCheckInTime(), record.getCheckOutTime()).toMinutes() / 60.0;
            record.setWorkedHours(Math.round(hours * 100.0) / 100.0);
            if (hours < 4.0) {
                record.setStatus(AttendanceStatus.EARLY_DEPARTURE);
            } else {
                record.setStatus(AttendanceStatus.CHECKED_OUT);
            }
        }

        recordRepo.save(record);
        AttendanceCorrection saved = correctionRepo.save(correction);
        return toCorrectionResponse(saved);
    }

    public AttendanceReportResponse exportCsv(LocalDate startDate, LocalDate endDate) {
        List<AttendanceRecord> records = recordRepo.findByDateBetween(startDate, endDate);

        StringBuilder csv = new StringBuilder();
        csv.append("ID,Employee ID,Shift ID,Date,Check In,Check Out,Status,Worked Hours,Notes\n");

        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        for (AttendanceRecord r : records) {
            csv.append(r.getId()).append(',');
            csv.append(r.getEmployeeId()).append(',');
            csv.append(r.getShiftId() != null ? r.getShiftId() : "").append(',');
            csv.append(r.getDate()).append(',');
            csv.append(r.getCheckInTime() != null ? r.getCheckInTime().format(dtf) : "").append(',');
            csv.append(r.getCheckOutTime() != null ? r.getCheckOutTime().format(dtf) : "").append(',');
            csv.append(r.getStatus()).append(',');
            csv.append(r.getWorkedHours() != null ? r.getWorkedHours() : "").append(',');
            csv.append(escapeCsv(r.getNotes()));
            csv.append('\n');
        }

        String filename = "attendance_" + startDate + "_to_" + endDate + ".csv";
        return new AttendanceReportResponse(csv.toString(), filename, records.size());
    }

    public AttendanceResponse getAttendanceById(Long id) {
        return toResponse(recordRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Attendance record not found")));
    }

    private String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private AttendanceResponse toResponse(AttendanceRecord r) {
        return new AttendanceResponse(
                r.getId(), r.getEmployeeId(), r.getShiftId(), r.getDate(),
                r.getCheckInTime(), r.getCheckOutTime(),
                r.getStatus().name(), r.getWorkedHours(), r.getNotes(),
                r.getCreatedAt(), r.getUpdatedAt()
        );
    }

    private CorrectionResponse toCorrectionResponse(AttendanceCorrection c) {
        return new CorrectionResponse(
                c.getId(), c.getAttendanceRecordId(), c.getCorrectedBy(),
                c.getPreviousCheckIn(), c.getPreviousCheckOut(),
                c.getNewCheckIn(), c.getNewCheckOut(),
                c.getReason(), c.getCreatedAt()
        );
    }
}
