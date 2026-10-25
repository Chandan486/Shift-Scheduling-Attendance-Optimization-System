import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Shift, ShiftAssignment, ShiftRequest } from '../models/shift.model';

@Injectable({ providedIn: 'root' })
export class ShiftService {
  private apiUrl = `${environment.apiUrl}/api/shifts`;

  constructor(private http: HttpClient) {}

  getShifts(departmentId?: number, startDate?: string, endDate?: string): Observable<Shift[]> {
    let params = new HttpParams();
    if (departmentId) params = params.set('departmentId', departmentId);
    if (startDate) params = params.set('startDate', startDate);
    if (endDate) params = params.set('endDate', endDate);
    return this.http.get<Shift[]>(this.apiUrl, { params });
  }

  getShift(id: number): Observable<Shift> {
    return this.http.get<Shift>(`${this.apiUrl}/${id}`);
  }

  createShift(shift: ShiftRequest): Observable<Shift> {
    return this.http.post<Shift>(this.apiUrl, shift);
  }

  updateShift(id: number, shift: ShiftRequest): Observable<Shift> {
    return this.http.put<Shift>(`${this.apiUrl}/${id}`, shift);
  }

  deleteShift(id: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }

  /** Employees assigned to one shift (HR/ADMIN). */
  getAssignments(shiftId: number): Observable<ShiftAssignment[]> {
    return this.http.get<ShiftAssignment[]>(`${this.apiUrl}/${shiftId}/assignments`);
  }

  /** The logged-in employee's own assigned shifts. */
  getMyAssignments(startDate: string, endDate: string): Observable<ShiftAssignment[]> {
    const params = new HttpParams().set('startDate', startDate).set('endDate', endDate);
    return this.http.get<ShiftAssignment[]>(`${this.apiUrl}/assignments/my`, { params });
  }
}
