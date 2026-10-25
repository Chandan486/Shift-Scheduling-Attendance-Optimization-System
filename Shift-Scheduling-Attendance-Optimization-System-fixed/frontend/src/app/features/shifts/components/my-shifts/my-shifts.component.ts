import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { ShiftService } from '../../../../core/services/shift.service';
import { EmployeeService } from '../../../../core/services/employee.service';
import { ShiftAssignment } from '../../../../core/models/shift.model';
import { Department } from '../../../../core/models/employee.model';

/** The logged-in employee's assigned shifts for the next four weeks. */
@Component({
  selector: 'app-my-shifts',
  standalone: true,
  imports: [CommonModule, MatIconModule, MatButtonModule],
  templateUrl: './my-shifts.component.html'
})
export class MyShiftsComponent implements OnInit {
  private shiftService = inject(ShiftService);
  private employeeService = inject(EmployeeService);

  assignments: ShiftAssignment[] = [];
  departments = new Map<number, string>();
  loading = false;
  loadError = '';
  readonly rangeDays = 28;
  startDate = this.isoDate(new Date());
  endDate = this.isoDate(new Date(Date.now() + this.rangeDays * 86400000));

  ngOnInit(): void {
    this.employeeService.getDepartments().subscribe(list =>
      list.forEach((d: Department) => this.departments.set(d.id, d.name)));
    this.load();
  }

  load(): void {
    this.loading = true;
    this.loadError = '';
    this.shiftService.getMyAssignments(this.startDate, this.endDate).subscribe({
      next: rows => { this.assignments = rows; this.loading = false; },
      error: err => { this.loading = false; this.loadError = err.error?.error || 'Could not load your shifts'; }
    });
  }

  private isoDate(d: Date): string {
    return new Date(d.getTime() - d.getTimezoneOffset() * 60000).toISOString().slice(0, 10);
  }
}
