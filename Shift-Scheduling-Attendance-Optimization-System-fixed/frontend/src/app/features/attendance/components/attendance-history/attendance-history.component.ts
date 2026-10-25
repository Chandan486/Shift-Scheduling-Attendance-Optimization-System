import { Component, OnInit, ViewChild, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatTableModule } from '@angular/material/table';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatPaginatorModule, MatPaginator, PageEvent } from '@angular/material/paginator';
import { MatChipsModule } from '@angular/material/chips';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { AttendanceService } from '../../../../core/services/attendance.service';
import { EmployeeService } from '../../../../core/services/employee.service';
import { AuthService } from '../../../../core/services/auth.service';
import { AttendanceRecord } from '../../../../core/models/attendance.model';
import { Employee } from '../../../../core/models/employee.model';

@Component({
  selector: 'app-attendance-history',
  standalone: true,
  imports: [CommonModule, FormsModule, MatTableModule, MatCardModule, MatFormFieldModule, MatInputModule, MatButtonModule, MatIconModule, MatPaginatorModule, MatChipsModule, MatSelectModule, MatSnackBarModule],
  templateUrl: './attendance-history.component.html',
  styleUrls: ['./attendance-history.component.scss']
})
export class AttendanceHistoryComponent implements OnInit {
  private attendanceService = inject(AttendanceService);
  private employeeService = inject(EmployeeService);
  private authService = inject(AuthService);
  private snackBar = inject(MatSnackBar);

  records: AttendanceRecord[] = [];
  employees: Employee[] = [];
  displayedColumns = ['date', 'checkInTime', 'checkOutTime', 'workedHours', 'status'];
  selectedEmployeeId: number | null = null;
  startDate = '';
  endDate = '';
  totalElements = 0;
  pageSize = 20;
  isAdminOrHr = false;

  @ViewChild(MatPaginator) paginator!: MatPaginator;

  ngOnInit(): void {
    this.isAdminOrHr = this.authService.hasRole('ADMIN', 'HR');
    if (this.isAdminOrHr) {
      this.employeeService.getAllEmployees().subscribe(e => this.employees = e);
    } else {
      const user = this.authService.getCurrentUser();
      if (user) { this.selectedEmployeeId = user.userId; this.search(); }
    }
  }

  search(page = 0): void {
    if (!this.selectedEmployeeId) return;
    if (page === 0 && this.paginator) {
      this.paginator.pageIndex = 0;
    }
    this.attendanceService.getHistory(this.selectedEmployeeId, this.startDate || undefined, this.endDate || undefined, page, this.pageSize).subscribe({
      next: data => {
        this.records = data.content || data;
        this.totalElements = data.totalElements || this.records.length;
      },
      error: () => this.snackBar.open('Failed to load history', 'Close', { duration: 3000 })
    });
  }

  onPage(event: PageEvent): void {
    this.pageSize = event.pageSize;
    this.search(event.pageIndex);
  }

  exportCsv(): void {
    if (!this.selectedEmployeeId || !this.startDate || !this.endDate) {
      this.snackBar.open('Select employee and date range to export', 'Close', { duration: 3000 });
      return;
    }
    this.attendanceService.exportCsv(this.selectedEmployeeId, this.startDate, this.endDate).subscribe(blob => {
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url; a.download = 'attendance-export.csv'; a.click();
      window.URL.revokeObjectURL(url);
    });
  }

  getStatusColor(status: string): string {
    switch (status) {
      case 'CHECKED_OUT': return 'primary';
      case 'CHECKED_IN': return 'accent';
      case 'LATE': return 'warn';
      case 'ABSENT': return 'warn';
      default: return '';
    }
  }
}
