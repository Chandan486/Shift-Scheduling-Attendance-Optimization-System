import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatTableModule } from '@angular/material/table';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatChipsModule } from '@angular/material/chips';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { LeaveService } from '../../../../core/services/leave.service';
import { EmployeeService } from '../../../../core/services/employee.service';
import { AuthService } from '../../../../core/services/auth.service';
import { LeaveResponse } from '../../../../core/models/leave.model';

/**
 * Mirrors the backend rule (LeaveService.requiredApproverRole) so buttons only appear where the
 * server will accept the action. The server remains the authority; this only avoids dead buttons.
 */
function requiredApproverFor(applicantRole: string | null): string {
  if (applicantRole === 'EMPLOYEE') return 'HR';
  return 'ADMIN';
}

@Component({
  selector: 'app-leave-approval',
  standalone: true,
  imports: [CommonModule, FormsModule, MatTableModule, MatCardModule, MatButtonModule, MatIconModule,
    MatChipsModule, MatFormFieldModule, MatInputModule, MatTooltipModule, MatSnackBarModule],
  templateUrl: './leave-approval.component.html',
  styleUrls: ['./leave-approval.component.scss']
})
export class LeaveApprovalComponent implements OnInit {
  private leaveService = inject(LeaveService);
  private employeeService = inject(EmployeeService);
  private authService = inject(AuthService);
  private snackBar = inject(MatSnackBar);

  pendingLeaves: LeaveResponse[] = [];
  departmentByUserId = new Map<number, string>();
  reviewNotes: { [id: number]: string } = {};
  loading = false;
  loadError = '';
  busyId: number | null = null;

  displayedColumns = ['applicant', 'department', 'leaveType', 'dates', 'reason', 'submitted', 'actions'];

  get currentUserId(): number | null {
    return this.authService.getCurrentUser()?.userId ?? null;
  }

  ngOnInit(): void {
    this.employeeService.getAllEmployees().subscribe({
      next: list => list.forEach(e => this.departmentByUserId.set(e.userId, e.departmentName)),
      error: () => { /* department is informational only */ }
    });
    this.loadPending();
  }

  loadPending(): void {
    this.loading = true;
    this.loadError = '';
    this.leaveService.getPendingLeaves().subscribe({
      next: data => { this.pendingLeaves = data; this.loading = false; },
      error: err => { this.loading = false; this.loadError = err.error?.error || 'Could not load pending requests'; }
    });
  }

  /** Whether the logged-in user may decide this request (role match and not their own). */
  canReview(l: LeaveResponse): boolean {
    const me = this.authService.getCurrentUser();
    if (!me || l.applicantUserId === me.userId) return false;
    return me.role === requiredApproverFor(l.applicantRole);
  }

  awaitingLabel(l: LeaveResponse): string {
    return l.applicantUserId === this.currentUserId
      ? 'Your own request'
      : `Awaiting ${requiredApproverFor(l.applicantRole)} decision`;
  }

  roleLabel(role: string | null): string {
    switch (role) {
      case 'EMPLOYEE': return 'Employee';
      case 'HR': return 'HR';
      case 'ADMIN': return 'Admin';
      default: return 'Unknown role';
    }
  }

  approve(l: LeaveResponse): void {
    this.decide(l, 'APPROVED');
  }

  reject(l: LeaveResponse): void {
    if (!this.reviewNotes[l.id]?.trim()) {
      this.snackBar.open('Enter a reason before rejecting', 'Close', { duration: 4000 });
      return;
    }
    this.decide(l, 'REJECTED');
  }

  private decide(l: LeaveResponse, status: 'APPROVED' | 'REJECTED'): void {
    this.busyId = l.id;
    const notes = (this.reviewNotes[l.id] || '').trim();
    this.leaveService.reviewLeave(l.id, { status, reviewNotes: notes }).subscribe({
      next: () => {
        this.busyId = null;
        delete this.reviewNotes[l.id];
        this.pendingLeaves = this.pendingLeaves.filter(x => x.id !== l.id);
        this.snackBar.open(status === 'APPROVED' ? 'Leave approved' : 'Leave rejected', 'Close', { duration: 3000 });
      },
      error: err => {
        this.busyId = null;
        if (err.status === 409) this.loadPending(); // someone else already decided it: refresh the list
        this.snackBar.open(err.error?.error || 'Action failed', 'Close', { duration: 5000 });
      }
    });
  }
}
