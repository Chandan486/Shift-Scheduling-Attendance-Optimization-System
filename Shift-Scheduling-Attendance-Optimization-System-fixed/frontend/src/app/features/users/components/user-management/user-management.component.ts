import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { MatTableModule } from '@angular/material/table';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { AuthService } from '../../../../core/services/auth.service';

@Component({
  selector: 'app-user-management',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, MatTableModule, MatIconModule, MatFormFieldModule, MatInputModule, MatSelectModule, MatSnackBarModule, MatTooltipModule],
  templateUrl: './user-management.component.html',
  styleUrls: ['./user-management.component.scss']
})
export class UserManagementComponent implements OnInit {
  private authService = inject(AuthService);
  private fb = inject(FormBuilder);
  private snackBar = inject(MatSnackBar);

  users: any[] = [];
  filteredUsers: any[] = [];
  displayedColumns = ['username', 'fullName', 'email', 'role', 'active', 'createdAt', 'actions'];
  showForm = false;
  form!: FormGroup;
  searchTerm = '';
  currentUsername = '';

  ngOnInit(): void {
    this.form = this.fb.group({
      username: ['', [Validators.required, Validators.minLength(4), Validators.maxLength(30), Validators.pattern(/^[A-Za-z0-9_-]{4,30}$/)]],
      fullName: ['', [Validators.required, Validators.minLength(2), Validators.maxLength(100)]],
      email: ['', [Validators.required, Validators.email]],
      password: ['', [Validators.required, Validators.minLength(6)]],
      role: ['HR', Validators.required]
    });
    const user = this.authService.getCurrentUser();
    if (user) {
      this.currentUsername = user.username;
    }
    this.loadUsers();
  }

  loadUsers(): void {
    this.authService.getAllUsers().subscribe({
      next: (users) => {
        this.users = users;
        this.applyFilter();
      },
      error: (err) => this.snackBar.open('Error loading users', 'OK', { duration: 3000 })
    });
  }

  applyFilter(): void {
    if (!this.searchTerm.trim()) {
      this.filteredUsers = this.users;
      return;
    }
    const term = this.searchTerm.toLowerCase().trim();
    this.filteredUsers = this.users.filter(u =>
      u.username?.toLowerCase().includes(term) ||
      u.fullName?.toLowerCase().includes(term) ||
      u.email?.toLowerCase().includes(term) ||
      u.role?.toLowerCase().includes(term)
    );
  }

  onSearchChange(event: Event): void {
    this.searchTerm = (event.target as HTMLInputElement).value;
    this.applyFilter();
  }

  toggleForm(): void {
    this.showForm = !this.showForm;
    if (this.showForm) {
      this.form.reset({ role: 'HR' });
    }
  }

  onSubmit(): void {
    if (this.form.invalid) return;
    const data = this.form.value;
    this.authService.register(data).subscribe({
      next: () => {
        this.snackBar.open('User created successfully', 'OK', { duration: 3000 });
        this.showForm = false;
        this.loadUsers();
      },
      error: (err) => this.snackBar.open(err.error?.error || err.error?.message || 'Error creating user', 'OK', { duration: 5000 })
    });
  }

  deactivate(user: any): void {
    if (user.username === 'admin') {
      this.snackBar.open('Cannot deactivate master admin', 'OK', { duration: 3000 });
      return;
    }
    if (user.username === this.currentUsername) {
      this.snackBar.open('Cannot deactivate your own account', 'OK', { duration: 3000 });
      return;
    }
    if (!confirm(`Are you sure you want to deactivate ${user.fullName}? They will no longer be able to log in.`)) {
      return;
    }
    this.authService.deactivateUser(user.id).subscribe({
      next: () => {
        this.snackBar.open(`${user.fullName} has been deactivated`, 'OK', { duration: 3000 });
        this.loadUsers();
      },
      error: (err) => this.snackBar.open(err.error?.error || err.error?.message || 'Error deactivating user', 'OK', { duration: 5000 })
    });
  }

  activate(user: any): void {
    if (!confirm(`Are you sure you want to reactivate ${user.fullName}?`)) {
      return;
    }
    this.authService.activateUser(user.id).subscribe({
      next: () => {
        this.snackBar.open(`${user.fullName} has been activated`, 'OK', { duration: 3000 });
        this.loadUsers();
      },
      error: (err) => this.snackBar.open(err.error?.error || err.error?.message || 'Error activating user', 'OK', { duration: 5000 })
    });
  }

  get activeAdminCount(): number {
    return this.users.filter(u => u.role === 'ADMIN' && u.active).length;
  }

  isLastActiveAdmin(user: any): boolean {
    return user.role === 'ADMIN' && user.active && this.activeAdminCount <= 1;
  }

  isSelf(user: any): boolean {
    return user.username === this.currentUsername;
  }
}
