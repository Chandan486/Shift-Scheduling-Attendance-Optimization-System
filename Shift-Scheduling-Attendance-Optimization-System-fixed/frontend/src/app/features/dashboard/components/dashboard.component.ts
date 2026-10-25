import { Component, inject, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { AuthService } from '../../../core/services/auth.service';
import { EmployeeService } from '../../../core/services/employee.service';
import { LeaveService } from '../../../core/services/leave.service';
import { ScheduleService } from '../../../core/services/schedule.service';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [
    CommonModule,
    RouterModule,
    MatIconModule
  ],
  templateUrl: './dashboard.component.html',
  styleUrls: ['./dashboard.component.scss']
})
export class DashboardComponent implements OnInit {
  private authService = inject(AuthService);
  private employeeService = inject(EmployeeService);
  private leaveService = inject(LeaveService);
  private scheduleService = inject(ScheduleService);

  currentUser = this.authService.getCurrentUser();
  isAdminOrHr = this.authService.hasRole('ADMIN', 'HR');
  today = new Date();

  // Admin/HR stats
  totalEmployees = 0;
  pendingLeaves = 0;
  activeSchedules = 0;

  // Employee stats
  leaveBalance: any = null;

  ngOnInit(): void {
    if (this.isAdminOrHr) {
      this.loadAdminStats();
    } else {
      this.loadEmployeeStats();
    }
  }

  private loadAdminStats(): void {
    this.employeeService.getAllEmployees().subscribe({
      next: (employees) => this.totalEmployees = employees.length,
      error: () => this.totalEmployees = 0
    });
    this.leaveService.getPendingLeaves().subscribe({
      next: (leaves) => this.pendingLeaves = leaves.length,
      error: () => this.pendingLeaves = 0
    });
    this.scheduleService.getSchedules().subscribe({
      next: (schedules) => this.activeSchedules = schedules.filter(s => s.status === 'PUBLISHED').length,
      error: () => this.activeSchedules = 0
    });
  }

  private loadEmployeeStats(): void {
    if (this.currentUser) {
      this.leaveService.getBalance(this.currentUser.userId).subscribe({
        next: (balance) => this.leaveBalance = balance,
        error: () => {}
      });
    }
  }
}
