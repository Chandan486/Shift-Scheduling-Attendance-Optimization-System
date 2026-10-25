import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatSelectModule } from '@angular/material/select';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { AttendanceService } from '../../../../core/services/attendance.service';
import { EmployeeService } from '../../../../core/services/employee.service';
import { AuthService } from '../../../../core/services/auth.service';
import { Employee } from '../../../../core/models/employee.model';
import { AttendanceRecord } from '../../../../core/models/attendance.model';

@Component({
  selector: 'app-attendance-mark',
  standalone: true,
  imports: [CommonModule, FormsModule, MatCardModule, MatButtonModule, MatIconModule, MatSelectModule, MatFormFieldModule, MatSnackBarModule],
  templateUrl: './attendance-mark.component.html',
  styleUrls: ['./attendance-mark.component.scss']
})
export class AttendanceMarkComponent implements OnInit {
  private attendanceService = inject(AttendanceService);
  private employeeService = inject(EmployeeService);
  private authService = inject(AuthService);
  private snackBar = inject(MatSnackBar);

  employees: Employee[] = [];
  selectedEmployeeId: number | null = null;
  todayRecords: AttendanceRecord[] = [];
  isAdminOrHr = false;
  today = new Date().toISOString().split('T')[0];

  ngOnInit(): void {
    this.isAdminOrHr = this.authService.hasRole('ADMIN', 'HR');
    if (this.isAdminOrHr) {
      this.employeeService.getAllEmployees().subscribe(e => this.employees = e);
    } else {
      const user = this.authService.getCurrentUser();
      if (user) this.selectedEmployeeId = user.userId;
    }
    this.loadToday();
  }

  loadToday(): void {
    this.attendanceService.getByDate(this.today).subscribe(r => this.todayRecords = r);
  }

  checkIn(): void {
    if (!this.selectedEmployeeId) return;
    this.attendanceService.checkIn({ employeeId: this.selectedEmployeeId }).subscribe({
      next: () => { this.snackBar.open('Checked in!', 'Close', { duration: 3000 }); this.loadToday(); },
      error: err => this.snackBar.open(err.error?.error || 'Check-in failed', 'Close', { duration: 3000 })
    });
  }

  checkOut(): void {
    if (!this.selectedEmployeeId) return;
    this.attendanceService.checkOut({ employeeId: this.selectedEmployeeId }).subscribe({
      next: () => { this.snackBar.open('Checked out!', 'Close', { duration: 3000 }); this.loadToday(); },
      error: err => this.snackBar.open(err.error?.error || 'Check-out failed', 'Close', { duration: 3000 })
    });
  }

  getMyRecord(): AttendanceRecord | undefined {
    return this.todayRecords.find(r => r.employeeId === this.selectedEmployeeId);
  }
}
