import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, Router, ActivatedRoute } from '@angular/router';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { EmployeeService } from '../../../../core/services/employee.service';
import { AuthService } from '../../../../core/services/auth.service';
import { Department, Skill } from '../../../../core/models/employee.model';
import { switchMap } from 'rxjs/operators';
import { of } from 'rxjs';

@Component({
  selector: 'app-employee-form',
  standalone: true,
  imports: [CommonModule, RouterModule, ReactiveFormsModule, MatFormFieldModule, MatInputModule, MatSelectModule, MatIconModule, MatSnackBarModule],
  templateUrl: './employee-form.component.html',
  styleUrls: ['./employee-form.component.scss']
})
export class EmployeeFormComponent implements OnInit {
  private fb = inject(FormBuilder);
  private employeeService = inject(EmployeeService);
  private authService = inject(AuthService);
  private router = inject(Router);
  private route = inject(ActivatedRoute);
  private snackBar = inject(MatSnackBar);

  form!: FormGroup;
  departments: Department[] = [];
  skills: Skill[] = [];
  isEdit = false;
  employeeId?: number;

  ngOnInit(): void {
    this.form = this.fb.group({
      userId: [null],
      username: ['', [Validators.required, Validators.minLength(4), Validators.maxLength(30), Validators.pattern(/^[A-Za-z0-9_-]{4,30}$/)]],
      password: ['', [Validators.required, Validators.minLength(6)]],
      fullName: ['', [Validators.required, Validators.minLength(2), Validators.maxLength(100)]],
      email: ['', [Validators.required, Validators.email]],
      phone: ['', [Validators.pattern(/^[6-9][0-9]{9}$/)]],
      departmentId: [null, Validators.required],
      position: ['', Validators.required],
      maxHoursPerWeek: [40, [Validators.required, Validators.min(1), Validators.max(80)]],
      maxShiftsPerWeek: [5, [Validators.required, Validators.min(1), Validators.max(7)]],
      minRestHours: [8, [Validators.required, Validators.min(4), Validators.max(24)]],
      skillIds: [[]]
    });

    this.employeeService.getDepartments().subscribe(d => this.departments = d);
    this.employeeService.getSkills().subscribe(s => this.skills = s);

    const id = this.route.snapshot.paramMap.get('id');
    if (id) {
      this.isEdit = true;
      this.employeeId = +id;
      this.employeeService.getEmployee(this.employeeId).subscribe(emp => {
        this.form.patchValue({
          userId: emp.userId,
          fullName: emp.fullName,
          email: emp.email,
          phone: emp.phone,
          departmentId: emp.departmentId,
          position: emp.position,
          maxHoursPerWeek: emp.maxHoursPerWeek,
          maxShiftsPerWeek: emp.maxShiftsPerWeek,
          minRestHours: emp.minRestHours,
          skillIds: emp.skillIds || []
        });
      });
    }
  }

  onSubmit(): void {
    if (this.form.invalid) return;
    const data = this.form.value;

    if (this.isEdit) {
      this.employeeService.updateEmployee(this.employeeId!, data).subscribe({
        next: () => {
          this.snackBar.open('Employee updated', 'OK', { duration: 3000 });
          this.router.navigate(['/employees']);
        },
        error: (err) => this.snackBar.open(err.error?.error || 'Error updating employee', 'OK', { duration: 5000 })
      });
    } else {
      // First register login account, then create employee record
      this.authService.register({
        username: data.username,
        email: data.email,
        password: data.password,
        fullName: data.fullName,
        role: 'EMPLOYEE'
      }).pipe(
        switchMap((authUser: any) => {
          data.userId = authUser.userId || authUser.id;
          return this.employeeService.createEmployee(data);
        })
      ).subscribe({
        next: () => {
          this.snackBar.open('Employee created with login account', 'OK', { duration: 3000 });
          this.router.navigate(['/employees']);
        },
        error: (err) => this.snackBar.open(err.error?.error || err.error?.message || 'Error creating employee', 'OK', { duration: 5000 })
      });
    }
  }
}
