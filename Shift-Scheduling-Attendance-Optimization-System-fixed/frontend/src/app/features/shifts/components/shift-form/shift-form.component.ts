import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, Router, ActivatedRoute } from '@angular/router';
import { ReactiveFormsModule, FormBuilder, FormGroup, FormArray, Validators } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { ShiftService } from '../../../../core/services/shift.service';
import { EmployeeService } from '../../../../core/services/employee.service';
import { Department, Employee, Skill } from '../../../../core/models/employee.model';

@Component({
  selector: 'app-shift-form',
  standalone: true,
  imports: [CommonModule, RouterModule, ReactiveFormsModule, MatFormFieldModule, MatInputModule, MatSelectModule, MatButtonModule, MatIconModule, MatSlideToggleModule, MatSnackBarModule, MatTooltipModule],
  templateUrl: './shift-form.component.html',
  styleUrls: ['./shift-form.component.scss']
})
export class ShiftFormComponent implements OnInit {
  private fb = inject(FormBuilder);
  private shiftService = inject(ShiftService);
  private employeeService = inject(EmployeeService);
  private router = inject(Router);
  private route = inject(ActivatedRoute);
  private snackBar = inject(MatSnackBar);

  form!: FormGroup;
  departments: Department[] = [];
  skills: Skill[] = [];

  /** Active employees from employee-service. */
  employees: Employee[] = [];
  employeesLoading = false;
  employeesError = '';


  isEdit = false;
  shiftId?: number;
  saving = false;
  /** Edit mode: true only once the current assignments are known. Otherwise saving must not touch them. */
  assignmentsLoaded = false;

  get requirements(): FormArray { return this.form.get('requirements') as FormArray; }

  get selectedEmployeeIds(): number[] { return this.form.get('employeeIds')?.value ?? []; }

  get selectedEmployees(): Employee[] {
    const ids = this.selectedEmployeeIds;
    return this.employees.filter(e => ids.includes(e.id));
  }

  /** Only employees of the shift's own department can be assigned (the server enforces the same rule). */
  get filteredEmployees(): Employee[] {
    const dept = this.form?.get('departmentId')?.value;
    if (dept == null) return [];
    return this.employees.filter(e => e.departmentId === Number(dept));
  }

  get wrongDepartmentSelections(): number {
    const dept = Number(this.form.get('departmentId')?.value);
    return this.selectedEmployees.filter(e => e.departmentId !== dept).length;
  }

  get capacityExceeded(): boolean {
    const max = Number(this.form.get('maxStaff')?.value ?? 0);
    return this.selectedEmployeeIds.length > max;
  }

  ngOnInit(): void {
    this.form = this.fb.group({
      name: ['', [Validators.required, Validators.minLength(2), Validators.maxLength(100)]],
      departmentId: [null, Validators.required],
      date: ['', Validators.required],
      startTime: ['', Validators.required],
      endTime: ['', Validators.required],
      overnight: [false],
      minStaff: [1, [Validators.required, Validators.min(1)]],
      maxStaff: [5, [Validators.required, Validators.min(1)]],
      nightShift: [false],
      employeeIds: [[] as number[]],
      requirements: this.fb.array([])
    });

    this.employeeService.getDepartments().subscribe(d => this.departments = d);
    this.employeeService.getSkills().subscribe(s => this.skills = s);
    this.loadEmployees();

    // Changing the department drops selected employees who belong to another department.
    this.form.get('departmentId')!.valueChanges.subscribe(() => {
      const kept = this.selectedEmployeeIds.filter(id => this.filteredEmployees.some(e => e.id === id));
      if (kept.length !== this.selectedEmployeeIds.length) {
        this.form.patchValue({ employeeIds: kept });
      }
    });

    const id = this.route.snapshot.paramMap.get('id');
    if (!id) {
      this.assignmentsLoaded = true; // creating: there is nothing to preserve
    }
    if (id) {
      this.isEdit = true;
      this.shiftId = +id;
      this.shiftService.getShift(this.shiftId).subscribe(shift => {
        this.form.patchValue(shift);
      });
      this.shiftService.getAssignments(this.shiftId).subscribe({
        next: rows => {
          this.form.patchValue({ employeeIds: rows.map(r => r.employeeId) });
          this.assignmentsLoaded = true;
        },
        error: () => this.snackBar.open('Could not load current assignments; assignments will not be changed on save', 'OK', { duration: 6000 })
      });
    }
  }

  loadEmployees(): void {
    this.employeesLoading = true;
    this.employeesError = '';
    this.employeeService.getAllEmployees().subscribe({
      next: list => {
        this.employees = list.filter(e => e.active);
        this.employeesLoading = false;
      },
      error: err => {
        this.employeesLoading = false;
        this.employeesError = err.error?.error || 'Could not load employees.';
      }
    });
  }

  removeEmployee(id: number): void {
    this.form.patchValue({ employeeIds: this.selectedEmployeeIds.filter(x => x !== id) });
  }

  addRequirement(): void {
    this.requirements.push(this.fb.group({
      skillId: [null, Validators.required],
      requiredCount: [1, [Validators.required, Validators.min(1)]]
    }));
  }

  removeRequirement(index: number): void {
    this.requirements.removeAt(index);
  }

  onSubmit(): void {
    if (this.form.invalid || this.capacityExceeded || this.saving) return;
    this.saving = true;
    const data = { ...this.form.value };
    // Omitting employeeIds tells the server to leave assignments alone (never send an unknown list as empty).
    if (this.isEdit && !this.assignmentsLoaded) delete data.employeeIds;
    if (this.wrongDepartmentSelections > 0) {
      this.saving = false;
      this.snackBar.open('Remove employees who are not in the selected department', 'OK', { duration: 5000 });
      return;
    }
    const op = this.isEdit
      ? this.shiftService.updateShift(this.shiftId!, data)
      : this.shiftService.createShift(data);

    op.subscribe({
      next: () => {
        this.saving = false;
        const assigned = data.employeeIds?.length ?? 0;
        this.snackBar.open(
          (this.isEdit ? 'Shift updated' : 'Shift created') + (assigned ? `, ${assigned} employee(s) assigned` : ''),
          'OK', { duration: 3500 });
        this.router.navigate(['/shifts']);
      },
      error: (err) => {
        this.saving = false;
        this.snackBar.open(err.error?.error || 'Error saving shift', 'OK', { duration: 6000 });
      }
    });
  }
}
