import { Component, DestroyRef, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { BehaviorSubject, switchMap, timer } from 'rxjs';
import { NotificationService } from '../../../../core/services/notification.service';
import { AuthService } from '../../../../core/services/auth.service';
import { Notification } from '../../../../core/models/notification.model';

/** How often the open notification window re-fetches so new assignments/decisions appear without a reload. */
const REFRESH_MS = 30000;

@Component({
  selector: 'app-notification-list',
  standalone: true,
  imports: [CommonModule, MatIconModule, MatPaginatorModule],
  templateUrl: './notification-list.component.html',
  styleUrls: ['./notification-list.component.scss']
})
export class NotificationListComponent implements OnInit {
  private notificationService = inject(NotificationService);
  private authService = inject(AuthService);
  private destroyRef = inject(DestroyRef);

  notifications: Notification[] = [];
  userId = 0;
  totalElements = 0;
  pageSize = 20;
  loadError = '';
  loading = false;

  private page$ = new BehaviorSubject<number>(0);

  ngOnInit(): void {
    const user = this.authService.getCurrentUser();
    if (!user) return;
    this.userId = user.userId;

    // Re-fetch on page change and every REFRESH_MS; the previous request is cancelled by switchMap.
    this.page$.pipe(
      switchMap(page => timer(0, REFRESH_MS).pipe(
        switchMap(() => {
          this.loading = true;
          return this.notificationService.getNotifications(this.userId, page, this.pageSize);
        })
      )),
      takeUntilDestroyed(this.destroyRef)
    ).subscribe({
      next: data => {
        this.notifications = data.content || data;
        this.totalElements = data.totalElements ?? this.notifications.length;
        this.loading = false;
        this.loadError = '';
      },
      error: err => {
        this.loading = false;
        this.loadError = err.error?.error || 'Could not load notifications. Retrying…';
      }
    });
  }

  onPage(event: PageEvent): void {
    this.pageSize = event.pageSize;
    this.page$.next(event.pageIndex);
  }

  markRead(n: Notification): void {
    if (n.isRead) return;
    this.notificationService.markAsRead(n.id).subscribe(() => {
      n.isRead = true;
      this.notificationService.refreshCount(this.userId);
    });
  }

  markAllRead(): void {
    this.notificationService.markAllAsRead(this.userId).subscribe(() => {
      this.notifications.forEach(n => n.isRead = true);
    });
  }

  getIcon(type: string): string {
    switch (type) {
      case 'SCHEDULE_PUBLISHED': return 'event_available';
      case 'SHIFT_ASSIGNED': return 'event';
      case 'LEAVE_APPROVED': return 'check_circle';
      case 'LEAVE_REJECTED': return 'cancel';
      case 'ATTENDANCE_REMINDER': return 'alarm';
      case 'SHIFT_CHANGE': return 'swap_horiz';
      default: return 'notifications';
    }
  }
}
