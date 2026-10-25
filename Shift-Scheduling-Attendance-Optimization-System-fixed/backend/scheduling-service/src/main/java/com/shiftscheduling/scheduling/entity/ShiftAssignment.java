package com.shiftscheduling.scheduling.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "shift_assignments",
        uniqueConstraints = @UniqueConstraint(name = "uq_shift_assignment_employee", columnNames = {"shift_id", "employee_id"}))
public class ShiftAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shift_id", nullable = false)
    private Shift shift;

    /** employee-service employee id. */
    @Column(nullable = false)
    private Long employeeId;

    /** Auth user id of the employee; the notification recipient and the owner of "my shifts". */
    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, length = 100)
    private String employeeName;

    @Column(nullable = false, length = 20)
    private String status = "ASSIGNED";

    private Long assignedBy;

    @Column(nullable = false, updatable = false)
    private LocalDateTime assignedAt;

    @PrePersist
    protected void onCreate() {
        assignedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Shift getShift() { return shift; }
    public void setShift(Shift shift) { this.shift = shift; }
    public Long getEmployeeId() { return employeeId; }
    public void setEmployeeId(Long employeeId) { this.employeeId = employeeId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getEmployeeName() { return employeeName; }
    public void setEmployeeName(String employeeName) { this.employeeName = employeeName; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getAssignedBy() { return assignedBy; }
    public void setAssignedBy(Long assignedBy) { this.assignedBy = assignedBy; }
    public LocalDateTime getAssignedAt() { return assignedAt; }
}
