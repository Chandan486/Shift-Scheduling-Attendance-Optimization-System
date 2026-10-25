import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, ActivatedRoute } from '@angular/router';
import { MatTabsModule } from '@angular/material/tabs';
import { MatChipsModule } from '@angular/material/chips';
import { MatIconModule } from '@angular/material/icon';
import { EmployeeService } from '../../../../core/services/employee.service';
import { Employee, Availability } from '../../../../core/models/employee.model';

@Component({
  selector: 'app-employee-detail',
  standalone: true,
  imports: [CommonModule, RouterModule, MatTabsModule, MatChipsModule, MatIconModule],
  templateUrl: './employee-detail.component.html',
  styleUrls: ['./employee-detail.component.scss']
})
export class EmployeeDetailComponent implements OnInit {
  private employeeService = inject(EmployeeService);
  private route = inject(ActivatedRoute);

  employee?: Employee;
  availability: Availability[] = [];
  daysOfWeek = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY'];

  ngOnInit(): void {
    const id = +this.route.snapshot.paramMap.get('id')!;
    this.employeeService.getEmployee(id).subscribe(e => this.employee = e);
    this.employeeService.getAvailability(id).subscribe(a => this.availability = a);
  }

  getAvailabilityForDay(day: string): Availability | undefined {
    return this.availability.find(a => a.dayOfWeek === day);
  }
}
