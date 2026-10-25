import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Employee, EmployeeRequest, Department, Skill, Availability } from '../models/employee.model';

@Injectable({ providedIn: 'root' })
export class EmployeeService {
  private apiUrl = `${environment.apiUrl}/api/employees`;
  private deptUrl = `${environment.apiUrl}/api/departments`;
  private skillUrl = `${environment.apiUrl}/api/skills`;

  constructor(private http: HttpClient) {}

  getEmployees(page = 0, size = 20, search?: string): Observable<any> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (search) params = params.set('search', search);
    return this.http.get<any>(this.apiUrl, { params });
  }

  getAllEmployees(): Observable<Employee[]> {
    return this.http.get<Employee[]>(`${this.apiUrl}/all`);
  }

  getEmployee(id: number): Observable<Employee> {
    return this.http.get<Employee>(`${this.apiUrl}/${id}`);
  }

  createEmployee(employee: EmployeeRequest): Observable<Employee> {
    return this.http.post<Employee>(this.apiUrl, employee);
  }

  updateEmployee(id: number, employee: EmployeeRequest): Observable<Employee> {
    return this.http.put<Employee>(`${this.apiUrl}/${id}`, employee);
  }

  deleteEmployee(id: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }

  getByDepartment(departmentId: number): Observable<Employee[]> {
    return this.http.get<Employee[]>(`${this.apiUrl}/department/${departmentId}`);
  }

  getAvailability(employeeId: number): Observable<Availability[]> {
    return this.http.get<Availability[]>(`${this.apiUrl}/${employeeId}/availability`);
  }

  setAvailability(employeeId: number, availability: any[]): Observable<Availability[]> {
    return this.http.put<Availability[]>(`${this.apiUrl}/${employeeId}/availability`, availability);
  }

  getDepartments(): Observable<Department[]> {
    return this.http.get<Department[]>(this.deptUrl);
  }

  createDepartment(dept: any): Observable<Department> {
    return this.http.post<Department>(this.deptUrl, dept);
  }

  getSkills(): Observable<Skill[]> {
    return this.http.get<Skill[]>(this.skillUrl);
  }

  createSkill(skill: any): Observable<Skill> {
    return this.http.post<Skill>(this.skillUrl, skill);
  }
}
