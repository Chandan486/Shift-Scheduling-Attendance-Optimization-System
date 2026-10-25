import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatTabsModule } from '@angular/material/tabs';
import { MatTableModule } from '@angular/material/table';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatIconModule } from '@angular/material/icon';
import { AttendanceService } from '../../../core/services/attendance.service';
import { ScheduleService } from '../../../core/services/schedule.service';

@Component({
  selector: 'app-reports',
  standalone: true,
  imports: [CommonModule, FormsModule, MatTabsModule, MatTableModule, MatFormFieldModule, MatInputModule, MatIconModule],
  templateUrl: './reports.component.html',
  styleUrls: ['./reports.component.scss']
})
export class ReportsComponent {
  private attendanceService = inject(AttendanceService);
  private scheduleService = inject(ScheduleService);

  attendanceDate = '';
  attendanceRecords: any[] = [];
  attendanceColumns = ['employeeId', 'date', 'checkInTime', 'checkOutTime', 'workedHours', 'status'];

  schedules: any[] = [];
  scheduleColumns = ['name', 'startDate', 'endDate', 'status', 'assignmentCount'];

  loadAttendance(): void {
    if (!this.attendanceDate) return;
    this.attendanceService.getByDate(this.attendanceDate).subscribe(d => this.attendanceRecords = d);
  }

  loadSchedules(): void {
    this.scheduleService.getSchedules().subscribe(d => this.schedules = d);
  }
}
