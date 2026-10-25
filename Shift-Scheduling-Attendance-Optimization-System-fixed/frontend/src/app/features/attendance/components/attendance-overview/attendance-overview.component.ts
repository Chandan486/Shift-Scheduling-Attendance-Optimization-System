import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatTableModule } from '@angular/material/table';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatIconModule } from '@angular/material/icon';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { Subject, Subscription, debounceTime, switchMap, tap, catchError, of } from 'rxjs';
import { AttendanceService } from '../../../../core/services/attendance.service';
import { EmployeeService } from '../../../../core/services/employee.service';
import { Department } from '../../../../core/models/employee.model';
import { OverviewResponse, OverviewRow, OverviewStatus, OverviewSummary } from '../../../../core/models/attendance-overview.model';

interface TabDef { status: OverviewStatus; label: string; icon: string; color: string; badge: string; }

@Component({
  selector: 'app-attendance-overview',
  standalone: true,
  imports: [CommonModule, FormsModule, MatTableModule, MatFormFieldModule, MatInputModule, MatSelectModule,
            MatIconModule, MatPaginatorModule, MatProgressBarModule, MatSnackBarModule],
  templateUrl: './attendance-overview.component.html',
  styleUrls: ['./attendance-overview.component.scss']
})
export class AttendanceOverviewComponent implements OnInit, OnDestroy {
  private attendanceService = inject(AttendanceService);
  private employeeService = inject(EmployeeService);
  private snackBar = inject(MatSnackBar);

  readonly tabs: TabDef[] = [
    { status: 'PRESENT', label: 'Present', icon: 'check_circle', color: 'green', badge: 'present' },
    { status: 'ABSENT', label: 'Absent', icon: 'cancel', color: 'red', badge: 'absent' },
    { status: 'ON_LEAVE', label: 'On Leave', icon: 'beach_access', color: 'amber', badge: 'pending' },
    { status: 'NOT_SCHEDULED', label: 'Not Scheduled', icon: 'event_busy', color: 'purple', badge: 'muted' }
  ];

  today = this.toLocalIso(new Date());
  selectedDate = this.today;
  activeStatus: OverviewStatus = 'PRESENT';
  displayedColumns: string[] = this.columnsFor('PRESENT');
  departmentId: number | null = null;
  search = '';
  departments: Department[] = [];

  summary: OverviewSummary = { present: 0, absent: 0, onLeave: 0, notScheduled: 0, total: 0 };
  rows: OverviewRow[] = [];
  isToday = false;
  totalElements = 0;
  pageIndex = 0;
  pageSize = 20;

  loading = false;
  exporting = false;
  errorMessage = '';
  dateError = '';

  private load$ = new Subject<void>();
  private search$ = new Subject<void>();
  private subs = new Subscription();

  ngOnInit(): void {
    this.employeeService.getDepartments().subscribe({
      next: d => this.departments = d,
      error: () => { /* department filter is optional; the report still works without it */ }
    });

    this.subs.add(
      this.load$.pipe(
        tap(() => { this.loading = true; this.errorMessage = ''; }),
        switchMap(() => this.attendanceService.getOverview(
          this.selectedDate, this.activeStatus, this.departmentId, this.search, this.pageIndex, this.pageSize).pipe(
            catchError(err => { this.errorMessage = this.describeError(err); return of(null); })))
      ).subscribe((res: OverviewResponse | null) => {
        this.loading = false;
        if (res) {
          this.summary = res.summary;
          this.rows = res.content;
          this.totalElements = res.totalElements;
          this.isToday = res.today;
        } else {
          this.rows = [];
          this.totalElements = 0;
        }
      })
    );
    this.subs.add(this.search$.pipe(debounceTime(300)).subscribe(() => this.reload(true)));

    this.reload(true);
  }

  ngOnDestroy(): void {
    this.subs.unsubscribe();
  }

  onDateChange(): void {
    if (!this.selectedDate) { this.dateError = 'Please select a date.'; return; }
    if (this.selectedDate > this.today) { this.dateError = 'Future dates cannot be reported yet.'; return; }
    this.dateError = '';
    this.reload(true);
  }

  onSearchChange(): void { this.search$.next(); }

  onDepartmentChange(): void { this.reload(true); }

  selectTab(status: OverviewStatus): void {
    if (this.activeStatus === status) return;
    this.activeStatus = status;
    this.displayedColumns = this.columnsFor(status);
    this.reload(true);
  }

  onPage(e: PageEvent): void {
    this.pageIndex = e.pageIndex;
    this.pageSize = e.pageSize;
    this.reload(false);
  }

  reload(resetPage: boolean): void {
    if (this.dateError || !this.selectedDate) return;
    if (resetPage) this.pageIndex = 0;
    this.load$.next();
  }

  clearFilters(): void {
    this.search = '';
    this.departmentId = null;
    this.reload(true);
  }

  countFor(status: OverviewStatus): number {
    switch (status) {
      case 'PRESENT': return this.summary.present;
      case 'ABSENT': return this.summary.absent;
      case 'ON_LEAVE': return this.summary.onLeave;
      default: return this.summary.notScheduled;
    }
  }

  get activeTab(): TabDef { return this.tabs.find(t => t.status === this.activeStatus)!; }

  private columnsFor(status: OverviewStatus): string[] {
    switch (status) {
      case 'PRESENT': return ['employeeId', 'employeeName', 'department', 'status', 'checkInTime', 'checkOutTime'];
      case 'ABSENT': return ['employeeId', 'employeeName', 'department', 'status', 'shiftName'];
      case 'ON_LEAVE': return ['employeeId', 'employeeName', 'department', 'status', 'leaveType'];
      default: return ['employeeId', 'employeeName', 'department', 'status'];
    }
  }

  get hasFilters(): boolean { return !!this.search.trim() || this.departmentId !== null; }

  exportCsv(): void {
    if (this.dateError || !this.selectedDate || this.exporting) return;
    this.exporting = true;
    this.attendanceService.exportOverviewCsv(this.selectedDate, this.activeStatus, this.departmentId, this.search).subscribe({
      next: blob => {
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `attendance_${this.selectedDate}_${this.activeStatus.toLowerCase()}.csv`;
        a.click();
        window.URL.revokeObjectURL(url);
        this.exporting = false;
      },
      error: err => {
        this.exporting = false;
        this.snackBar.open(err?.status === 403 ? 'You do not have permission to export this report.' : 'Failed to export CSV', 'Close', { duration: 4000 });
      }
    });
  }

  /** Check-out may fall on the next day (overnight shift); show the date only in that case. */
  formatTime(iso: string | null, relativeToDate: string): string {
    if (!iso) return '—';
    const d = new Date(iso);
    if (isNaN(d.getTime())) return '—';
    const time = d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
    return iso.substring(0, 10) === relativeToDate ? time : `${time} (${iso.substring(0, 10)})`;
  }

  statusLabel(s: string | null): string {
    return s ? s.replace(/_/g, ' ') : '';
  }

  private describeError(err: any): string {
    if (err?.status === 0) return 'Cannot reach the server. Please check that the backend services are running.';
    if (err?.status === 403) return 'You do not have permission to view attendance reports.';
    if (err?.status === 503) return err?.error?.error || 'A dependent service (employee, scheduling or leave) is unavailable. Please try again shortly.';
    if (err?.status === 400 && err?.error?.error) return err.error.error;
    return 'Failed to load the attendance overview. Please try again.';
  }

  private toLocalIso(d: Date): string {
    const m = String(d.getMonth() + 1).padStart(2, '0');
    const day = String(d.getDate()).padStart(2, '0');
    return `${d.getFullYear()}-${m}-${day}`;
  }
}
