import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { LeaveRequest, LeaveResponse, LeaveBalance, LeaveApproval } from '../models/leave.model';

@Injectable({ providedIn: 'root' })
export class LeaveService {
  private apiUrl = `${environment.apiUrl}/api/leaves`;

  constructor(private http: HttpClient) {}

  applyLeave(request: LeaveRequest): Observable<LeaveResponse> {
    return this.http.post<LeaveResponse>(this.apiUrl, request);
  }

  getLeaves(employeeId: number, page = 0, size = 20): Observable<any> {
    return this.http.get<any>(`${this.apiUrl}/employee/${employeeId}`, {
      params: new HttpParams().set('page', page).set('size', size)
    });
  }

  getPendingLeaves(): Observable<LeaveResponse[]> {
    return this.http.get<LeaveResponse[]>(`${this.apiUrl}/pending`);
  }

  reviewLeave(id: number, approval: LeaveApproval): Observable<LeaveResponse> {
    return this.http.put<LeaveResponse>(`${this.apiUrl}/${id}/review`, approval);
  }

  getBalance(employeeId: number): Observable<LeaveBalance> {
    return this.http.get<LeaveBalance>(`${this.apiUrl}/balance/${employeeId}`);
  }
}
