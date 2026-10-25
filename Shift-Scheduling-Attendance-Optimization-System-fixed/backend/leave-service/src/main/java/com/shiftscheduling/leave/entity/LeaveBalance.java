package com.shiftscheduling.leave.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "leave_balances", uniqueConstraints = @UniqueConstraint(columnNames = {"employee_id", "year"}))
public class LeaveBalance {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long employeeId;

    @Column(nullable = false)
    private int annualLeave = 20;

    @Column(nullable = false)
    private int sickLeave = 10;

    @Column(nullable = false)
    private int personalLeave = 5;

    @Column(nullable = false)
    private int annualUsed = 0;

    @Column(nullable = false)
    private int sickUsed = 0;

    @Column(nullable = false)
    private int personalUsed = 0;

    @Column(nullable = false)
    private int year;

    // Getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getEmployeeId() { return employeeId; }
    public void setEmployeeId(Long employeeId) { this.employeeId = employeeId; }

    public int getAnnualLeave() { return annualLeave; }
    public void setAnnualLeave(int annualLeave) { this.annualLeave = annualLeave; }

    public int getSickLeave() { return sickLeave; }
    public void setSickLeave(int sickLeave) { this.sickLeave = sickLeave; }

    public int getPersonalLeave() { return personalLeave; }
    public void setPersonalLeave(int personalLeave) { this.personalLeave = personalLeave; }

    public int getAnnualUsed() { return annualUsed; }
    public void setAnnualUsed(int annualUsed) { this.annualUsed = annualUsed; }

    public int getSickUsed() { return sickUsed; }
    public void setSickUsed(int sickUsed) { this.sickUsed = sickUsed; }

    public int getPersonalUsed() { return personalUsed; }
    public void setPersonalUsed(int personalUsed) { this.personalUsed = personalUsed; }

    public int getYear() { return year; }
    public void setYear(int year) { this.year = year; }
}
