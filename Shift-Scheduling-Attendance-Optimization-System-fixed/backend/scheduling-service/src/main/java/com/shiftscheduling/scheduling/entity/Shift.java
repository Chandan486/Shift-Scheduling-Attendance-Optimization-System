package com.shiftscheduling.scheduling.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "shifts")
public class Shift {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false)
    private Long departmentId;

    @Column(nullable = false)
    private LocalTime startTime;

    @Column(nullable = false)
    private LocalTime endTime;

    @Column(nullable = false)
    private LocalDate date;

    @Column(nullable = false)
    private boolean overnight = false;

    @Column(nullable = false)
    private int maxStaff = 10;

    @Column(nullable = false)
    private int minStaff = 1;

    @Column(nullable = false)
    private boolean nightShift = false;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "shift", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ShiftRequirement> requirements = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    // Getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Long getDepartmentId() { return departmentId; }
    public void setDepartmentId(Long departmentId) { this.departmentId = departmentId; }

    public LocalTime getStartTime() { return startTime; }
    public void setStartTime(LocalTime startTime) { this.startTime = startTime; }

    public LocalTime getEndTime() { return endTime; }
    public void setEndTime(LocalTime endTime) { this.endTime = endTime; }

    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }

    public boolean isOvernight() { return overnight; }
    public void setOvernight(boolean overnight) { this.overnight = overnight; }

    public int getMaxStaff() { return maxStaff; }
    public void setMaxStaff(int maxStaff) { this.maxStaff = maxStaff; }

    public int getMinStaff() { return minStaff; }
    public void setMinStaff(int minStaff) { this.minStaff = minStaff; }

    public boolean isNightShift() { return nightShift; }
    public void setNightShift(boolean nightShift) { this.nightShift = nightShift; }

    public LocalDateTime getCreatedAt() { return createdAt; }

    public List<ShiftRequirement> getRequirements() { return requirements; }
    public void setRequirements(List<ShiftRequirement> requirements) { this.requirements = requirements; }
}
