import { Component, OnInit, ViewChild, AfterViewInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { MatTableModule, MatTableDataSource } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatPaginatorModule, MatPaginator } from '@angular/material/paginator';
import { ScheduleService } from '../../../../core/services/schedule.service';
import { AuthService } from '../../../../core/services/auth.service';
import { Schedule } from '../../../../core/models/schedule.model';

@Component({
  selector: 'app-schedule-list',
  standalone: true,
  imports: [CommonModule, RouterModule, MatTableModule, MatButtonModule, MatIconModule, MatSnackBarModule, MatProgressSpinnerModule, MatTooltipModule, MatPaginatorModule],
  templateUrl: './schedule-list.component.html',
  styleUrls: ['./schedule-list.component.scss']
})
export class ScheduleListComponent implements OnInit, AfterViewInit {
  private scheduleService = inject(ScheduleService);
  private authService = inject(AuthService);
  private snackBar = inject(MatSnackBar);

  dataSource = new MatTableDataSource<Schedule>([]);
  displayedColumns = ['name', 'startDate', 'endDate', 'status', 'assignmentCount', 'actions'];
  loading = true;
  isAdmin = false;

  @ViewChild(MatPaginator) paginator!: MatPaginator;

  ngOnInit(): void {
    this.isAdmin = this.authService.hasRole('ADMIN', 'HR');
    this.loadSchedules();
  }

  ngAfterViewInit(): void {
    this.dataSource.paginator = this.paginator;
  }

  loadSchedules(): void {
    this.loading = true;
    this.scheduleService.getSchedules().subscribe({
      next: data => {
        this.dataSource.data = data;
        this.loading = false;
      },
      error: () => { this.loading = false; this.snackBar.open('Failed to load schedules', 'Close', { duration: 3000 }); }
    });
  }

  getStatusColor(status: string): string {
    switch (status) {
      case 'DRAFT': return 'warn';
      case 'PUBLISHED': return 'primary';
      case 'ARCHIVED': return 'accent';
      default: return '';
    }
  }

  publish(schedule: Schedule): void {
    this.scheduleService.publishSchedule(schedule.id).subscribe({
      next: () => { this.snackBar.open('Schedule published!', 'Close', { duration: 3000 }); this.loadSchedules(); },
      error: err => this.snackBar.open(err.error?.error || 'Publish failed', 'Close', { duration: 3000 })
    });
  }

  delete(schedule: Schedule): void {
    if (confirm('Delete this schedule?')) {
      this.scheduleService.deleteSchedule(schedule.id).subscribe({
        next: () => { this.snackBar.open('Deleted', 'Close', { duration: 3000 }); this.loadSchedules(); },
        error: err => this.snackBar.open(err.error?.error || 'Delete failed', 'Close', { duration: 3000 })
      });
    }
  }
}
