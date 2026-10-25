import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatIconModule } from '@angular/material/icon';
import { LeaveService } from '../../../../core/services/leave.service';
import { AuthService } from '../../../../core/services/auth.service';
import { LeaveBalance } from '../../../../core/models/leave.model';

@Component({
  selector: 'app-leave-apply',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterModule, MatCardModule, MatFormFieldModule, MatInputModule, MatSelectModule, MatButtonModule, MatSnackBarModule, MatIconModule],
  templateUrl: './leave-apply.component.html',
  styleUrls: ['./leave-apply.component.scss']
})
export class LeaveApplyComponent implements OnInit {
  private fb = inject(FormBuilder);
  private leaveService = inject(LeaveService);
  private authService = inject(AuthService);
  private snackBar = inject(MatSnackBar);

  form!: FormGroup;
  balance: LeaveBalance | null = null;
  leaveTypes = ['ANNUAL', 'SICK', 'PERSONAL', 'UNPAID'];

  ngOnInit(): void {
    const user = this.authService.getCurrentUser();
    this.form = this.fb.group({
      employeeId: [user?.userId, Validators.required],
      leaveType: ['', Validators.required],
      startDate: ['', Validators.required],
      endDate: ['', Validators.required],
      reason: ['', [Validators.required, Validators.minLength(10), Validators.maxLength(500)]]
    });
    if (user) this.leaveService.getBalance(user.userId).subscribe(b => this.balance = b);
  }

  submit(): void {
    if (this.form.invalid) return;
    this.leaveService.applyLeave(this.form.value).subscribe({
      next: () => { this.snackBar.open('Leave request submitted!', 'Close', { duration: 3000 }); this.form.reset(); },
      error: err => this.snackBar.open(err.error?.error || 'Failed to submit', 'Close', { duration: 3000 })
    });
  }
}
