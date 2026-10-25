package com.shiftscheduling.attendance.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "attendance_corrections")
public class AttendanceCorrection {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long attendanceRecordId;

    @Column(nullable = false)
    private Long correctedBy;

    private LocalDateTime previousCheckIn;

    private LocalDateTime previousCheckOut;

    private LocalDateTime newCheckIn;

    private LocalDateTime newCheckOut;

    @Column(nullable = false, length = 500)
    private String reason;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    // Getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getAttendanceRecordId() { return attendanceRecordId; }
    public void setAttendanceRecordId(Long attendanceRecordId) { this.attendanceRecordId = attendanceRecordId; }

    public Long getCorrectedBy() { return correctedBy; }
    public void setCorrectedBy(Long correctedBy) { this.correctedBy = correctedBy; }

    public LocalDateTime getPreviousCheckIn() { return previousCheckIn; }
    public void setPreviousCheckIn(LocalDateTime previousCheckIn) { this.previousCheckIn = previousCheckIn; }

    public LocalDateTime getPreviousCheckOut() { return previousCheckOut; }
    public void setPreviousCheckOut(LocalDateTime previousCheckOut) { this.previousCheckOut = previousCheckOut; }

    public LocalDateTime getNewCheckIn() { return newCheckIn; }
    public void setNewCheckIn(LocalDateTime newCheckIn) { this.newCheckIn = newCheckIn; }

    public LocalDateTime getNewCheckOut() { return newCheckOut; }
    public void setNewCheckOut(LocalDateTime newCheckOut) { this.newCheckOut = newCheckOut; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public LocalDateTime getCreatedAt() { return createdAt; }
}
