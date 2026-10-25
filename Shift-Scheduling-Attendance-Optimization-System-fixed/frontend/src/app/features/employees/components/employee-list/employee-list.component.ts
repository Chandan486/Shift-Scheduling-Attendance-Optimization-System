import { Component, OnInit, ViewChild, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, Router } from '@angular/router';
import { MatTableModule, MatTableDataSource } from '@angular/material/table';
import { MatPaginatorModule, MatPaginator } from '@angular/material/paginator';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { EmployeeService } from '../../../../core/services/employee.service';
import { Employee } from '../../../../core/models/employee.model';

@Component({
  selector: 'app-employee-list',
  standalone: true,
  imports: [CommonModule, RouterModule, MatTableModule, MatPaginatorModule, MatIconModule, MatTooltipModule],
  templateUrl: './employee-list.component.html',
  styleUrls: ['./employee-list.component.scss']
})
export class EmployeeListComponent implements OnInit {
  private employeeService = inject(EmployeeService);
  private router = inject(Router);

  displayedColumns = ['fullName', 'email', 'departmentName', 'position', 'active', 'actions'];
  dataSource = new MatTableDataSource<Employee>([]);
  totalElements = 0;
  pageSize = 10;
  searchTerm = '';

  @ViewChild(MatPaginator) paginator!: MatPaginator;

  ngOnInit(): void {
    this.loadEmployees();
  }

  loadEmployees(page = 0): void {
    this.employeeService.getEmployees(page, this.pageSize, this.searchTerm || undefined).subscribe({
      next: (response) => {
        this.dataSource.data = response.content || response;
        this.totalElements = response.totalElements || this.dataSource.data.length;
      },
      error: () => {
        this.employeeService.getAllEmployees().subscribe(data => {
          this.dataSource.data = data;
          this.totalElements = data.length;
        });
      }
    });
  }

  applyFilter(event: Event): void {
    this.searchTerm = (event.target as HTMLInputElement).value.trim();
    if (this.paginator) {
      this.paginator.pageIndex = 0;
    }
    this.loadEmployees(0);
  }

  onPageChange(event: any): void {
    this.pageSize = event.pageSize;
    this.loadEmployees(event.pageIndex);
  }

  addEmployee(): void {
    this.router.navigate(['/employees/new']);
  }

  editEmployee(id: number): void {
    this.router.navigate(['/employees/edit', id]);
  }

  deleteEmployee(employee: Employee): void {
    if (confirm(`Delete employee ${employee.fullName}?`)) {
      this.employeeService.deleteEmployee(employee.id).subscribe(() => this.loadEmployees());
    }
  }
}
