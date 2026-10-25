import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Schedule, ScheduleAssignment, ScheduleGenerationRequest, ScheduleGenerationResult } from '../models/schedule.model';

@Injectable({ providedIn: 'root' })
export class ScheduleService {
  private apiUrl = `${environment.apiUrl}/api/schedules`;

  constructor(private http: HttpClient) {}

  getSchedules(): Observable<Schedule[]> {
    return this.http.get<Schedule[]>(this.apiUrl);
  }

  getSchedule(id: number): Observable<Schedule> {
    return this.http.get<Schedule>(`${this.apiUrl}/${id}`);
  }

  generateSchedule(request: ScheduleGenerationRequest): Observable<ScheduleGenerationResult> {
    return this.http.post<ScheduleGenerationResult>(`${this.apiUrl}/generate`, request);
  }

  publishSchedule(id: number): Observable<Schedule> {
    return this.http.put<Schedule>(`${this.apiUrl}/${id}/publish`, {});
  }

  getAssignments(scheduleId: number): Observable<ScheduleAssignment[]> {
    return this.http.get<ScheduleAssignment[]>(`${this.apiUrl}/${scheduleId}/assignments`);
  }

  getMySchedule(startDate: string, endDate: string): Observable<ScheduleAssignment[]> {
    return this.http.get<ScheduleAssignment[]>(`${this.apiUrl}/my-schedule`, {
      params: { startDate, endDate }
    });
  }

  deleteSchedule(id: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }
}
