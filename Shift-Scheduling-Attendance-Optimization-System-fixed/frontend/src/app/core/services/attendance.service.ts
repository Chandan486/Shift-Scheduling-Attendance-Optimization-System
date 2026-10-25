import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AttendanceRecord, CheckInRequest, CheckOutRequest } from '../models/attendance.model';
import { OverviewResponse, OverviewStatus } from '../models/attendance-overview.model';

@Injectable({ providedIn: 'root' })
export class AttendanceService {
  private apiUrl = `${environment.apiUrl}/api/attendance`;

  constructor(private http: HttpClient) {}

  checkIn(request: CheckInRequest): Observable<AttendanceRecord> {
    return this.http.post<AttendanceRecord>(`${this.apiUrl}/check-in`, request);
  }

  checkOut(request: CheckOutRequest): Observable<AttendanceRecord> {
    return this.http.post<AttendanceRecord>(`${this.apiUrl}/check-out`, request);
  }

  getHistory(employeeId: number, startDate?: string, endDate?: string, page = 0, size = 20): Observable<any> {
    let params = new HttpParams().set('employeeId', employeeId).set('page', page).set('size', size);
    if (startDate) params = params.set('startDate', startDate);
    if (endDate) params = params.set('endDate', endDate);
    return this.http.get<any>(`${this.apiUrl}/history`, { params });
  }

  getByDate(date: string): Observable<AttendanceRecord[]> {
    return this.http.get<AttendanceRecord[]>(`${this.apiUrl}/date/${date}`);
  }

  correctAttendance(id: number, correction: any): Observable<any> {
    return this.http.post<any>(`${this.apiUrl}/${id}/correct`, correction);
  }

  exportCsv(employeeId: number, startDate: string, endDate: string): Observable<Blob> {
    const params = new HttpParams()
      .set('employeeId', employeeId)
      .set('startDate', startDate)
      .set('endDate', endDate);
    return this.http.get(`${this.apiUrl}/export/csv`, { params, responseType: 'blob' });
  }

  // ---- Admin/HR date-wise attendance overview ----
  private overviewParams(date: string, status?: OverviewStatus | null, departmentId?: number | null, search?: string): HttpParams {
    let params = new HttpParams().set('date', date);
    if (status) params = params.set('status', status);
    if (departmentId) params = params.set('departmentId', departmentId);
    if (search && search.trim()) params = params.set('search', search.trim());
    return params;
  }

  getOverview(date: string, status: OverviewStatus | null, departmentId: number | null,
              search: string, page: number, size: number): Observable<OverviewResponse> {
    const params = this.overviewParams(date, status, departmentId, search)
      .set('page', page).set('size', size);
    return this.http.get<OverviewResponse>(`${this.apiUrl}/overview/employees`, { params });
  }

  exportOverviewCsv(date: string, status: OverviewStatus | null, departmentId: number | null,
                    search: string): Observable<Blob> {
    const params = this.overviewParams(date, status, departmentId, search);
    return this.http.get(`${this.apiUrl}/overview/export`, { params, responseType: 'blob' });
  }
}
