import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable, tap } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Notification, NotificationCount } from '../models/notification.model';

@Injectable({ providedIn: 'root' })
export class NotificationService {
  private apiUrl = `${environment.apiUrl}/api/notifications`;
  private unreadCountSubject = new BehaviorSubject<number>(0);
  public unreadCount$ = this.unreadCountSubject.asObservable();

  constructor(private http: HttpClient) {}

  getNotifications(userId: number, page = 0, size = 20): Observable<any> {
    return this.http.get<any>(`${this.apiUrl}/user/${userId}`, {
      params: { page: page.toString(), size: size.toString() }
    });
  }

  getUnreadCount(userId: number): Observable<NotificationCount> {
    return this.http.get<NotificationCount>(`${this.apiUrl}/user/${userId}/count`).pipe(
      tap(count => this.unreadCountSubject.next(count.unreadCount))
    );
  }

  markAsRead(id: number): Observable<void> {
    return this.http.put<void>(`${this.apiUrl}/${id}/read`, {});
  }

  markAllAsRead(userId: number): Observable<void> {
    return this.http.put<void>(`${this.apiUrl}/user/${userId}/read-all`, {}).pipe(
      tap(() => this.unreadCountSubject.next(0))
    );
  }

  refreshCount(userId: number): void {
    this.getUnreadCount(userId).subscribe();
  }
}
