import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatListModule } from '@angular/material/list';
import { MatIconModule } from '@angular/material/icon';
import { ScheduleService } from '../../../../core/services/schedule.service';
import { EmployeeService } from '../../../../core/services/employee.service';
import { Department } from '../../../../core/models/employee.model';
import { ScheduleGenerationResult } from '../../../../core/models/schedule.model';

@Component({
  selector: 'app-schedule-generate',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, MatCardModule, MatFormFieldModule, MatInputModule, MatSelectModule, MatButtonModule, MatProgressSpinnerModule, MatSnackBarModule, MatListModule, MatIconModule],
  templateUrl: './schedule-generate.component.html',
  styleUrls: ['./schedule-generate.component.scss']
})
export class ScheduleGenerateComponent implements OnInit {
  private fb = inject(FormBuilder);
  private scheduleService = inject(ScheduleService);
  private employeeService = inject(EmployeeService);
  private snackBar = inject(MatSnackBar);
  private router = inject(Router);

  form!: FormGroup;
  departments: Department[] = [];
  generating = false;
  result: ScheduleGenerationResult | null = null;

  ngOnInit(): void {
    this.form = this.fb.group({
      name: ['', Validators.required],
      startDate: ['', Validators.required],
      endDate: ['', Validators.required],
      departmentId: [null],
      notes: ['']
    });
    this.employeeService.getDepartments().subscribe(d => this.departments = d);
  }

  generate(): void {
    if (this.form.invalid) return;
    this.generating = true;
    this.result = null;
    this.scheduleService.generateSchedule(this.form.value).subscribe({
      next: res => { this.result = res; this.generating = false; this.snackBar.open('Schedule generated!', 'Close', { duration: 3000 }); },
      error: err => { this.generating = false; this.snackBar.open(err.error?.error || 'Generation failed', 'Close', { duration: 5000 }); }
    });
  }

  viewSchedule(): void {
    if (this.result?.schedule) {
      this.router.navigate(['/scheduling/view', this.result.schedule.id]);
    }
  }
}
