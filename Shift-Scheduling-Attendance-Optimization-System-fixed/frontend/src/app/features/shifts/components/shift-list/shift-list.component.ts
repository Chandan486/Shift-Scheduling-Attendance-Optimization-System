import { Component, OnInit, ViewChild, AfterViewInit, TemplateRef, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { MatTableModule, MatTableDataSource } from '@angular/material/table';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatNativeDateModule } from '@angular/material/core';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatPaginatorModule, MatPaginator } from '@angular/material/paginator';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { ShiftService } from '../../../../core/services/shift.service';
import { EmployeeService } from '../../../../core/services/employee.service';
import { Shift, ShiftAssignment } from '../../../../core/models/shift.model';
import { Department } from '../../../../core/models/employee.model';

@Component({
  selector: 'app-shift-list',
  standalone: true,
  imports: [CommonModule, RouterModule, FormsModule, MatTableModule, MatFormFieldModule, MatInputModule, MatSelectModule, MatButtonModule, MatIconModule, MatDatepickerModule, MatNativeDateModule, MatTooltipModule, MatPaginatorModule, MatDialogModule],
  templateUrl: './shift-list.component.html',
  styleUrls: ['./shift-list.component.scss']
})
export class ShiftListComponent implements OnInit, AfterViewInit {
  private shiftService = inject(ShiftService);
  private employeeService = inject(EmployeeService);
  private router = inject(Router);
  private dialog = inject(MatDialog);

  displayedColumns = ['name', 'date', 'startTime', 'endTime', 'minStaff', 'maxStaff', 'nightShift', 'actions'];
  dataSource = new MatTableDataSource<Shift>([]);
  departments: Department[] = [];
  selectedDepartment?: number;
  startDate = '';
  endDate = '';

  @ViewChild(MatPaginator) paginator!: MatPaginator;
  @ViewChild('assigneesDialog') assigneesDialog!: TemplateRef<unknown>;

  selectedShift?: Shift;
  assignees: ShiftAssignment[] = [];
  assigneesLoading = false;
  assigneesError = '';

  ngOnInit(): void {
    this.employeeService.getDepartments().subscribe(d => this.departments = d);
    this.loadShifts();
  }

  ngAfterViewInit(): void {
    this.dataSource.paginator = this.paginator;
  }

  loadShifts(): void {
    this.shiftService.getShifts(this.selectedDepartment, this.startDate, this.endDate)
      .subscribe(shifts => {
        this.dataSource.data = shifts;
        if (this.paginator) {
          this.paginator.firstPage();
        }
      });
  }

  addShift(): void { this.router.navigate(['/shifts/new']); }

  editShift(id: number): void { this.router.navigate(['/shifts', id, 'edit']); }

  viewAssignees(shift: Shift): void {
    this.selectedShift = shift;
    this.assignees = [];
    this.assigneesError = '';
    this.assigneesLoading = true;
    this.dialog.open(this.assigneesDialog, { width: '520px' });
    this.shiftService.getAssignments(shift.id).subscribe({
      next: rows => { this.assignees = rows; this.assigneesLoading = false; },
      error: err => { this.assigneesError = err.error?.error || 'Could not load assigned employees'; this.assigneesLoading = false; }
    });
  }

  deleteShift(shift: Shift): void {
    if (confirm(`Delete shift "${shift.name}"?`)) {
      this.shiftService.deleteShift(shift.id).subscribe(() => this.loadShifts());
    }
  }
}
