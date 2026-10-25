import { Component, DestroyRef, inject, OnInit } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { switchMap, timer } from 'rxjs';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatListModule } from '@angular/material/list';
import { MatIconModule } from '@angular/material/icon';
import { MatBadgeModule } from '@angular/material/badge';
import { MatButtonModule } from '@angular/material/button';
import { MatMenuModule } from '@angular/material/menu';
import { MatDividerModule } from '@angular/material/divider';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { AuthService } from '../../core/services/auth.service';
import { NotificationService } from '../../core/services/notification.service';
import { ThemeService } from '../../core/services/theme.service';

interface NavItem {
  label: string;
  icon: string;
  route: string;
  roles?: string[];
  dividerAfter?: boolean;
}

@Component({
  selector: 'app-main-layout',
  standalone: true,
  imports: [
    CommonModule,
    RouterModule,
    MatToolbarModule,
    MatSidenavModule,
    MatListModule,
    MatIconModule,
    MatBadgeModule,
    MatButtonModule,
    MatMenuModule,
    MatDividerModule,
    MatTooltipModule,
    MatSlideToggleModule
  ],
  templateUrl: './main-layout.component.html',
  styleUrls: ['./main-layout.component.scss']
})
export class MainLayoutComponent implements OnInit {
  authService = inject(AuthService);
  notificationService = inject(NotificationService);
  themeService = inject(ThemeService);
  private destroyRef = inject(DestroyRef);

  sidenavOpened = true;
  sidenavCollapsed = false;
  unreadCount = 0;

  navItems: NavItem[] = [
    { label: 'Dashboard', icon: 'dashboard', route: '/dashboard' },
    { label: 'Employees', icon: 'people', route: '/employees', roles: ['ADMIN', 'HR'], dividerAfter: true },
    { label: 'Shifts', icon: 'access_time', route: '/shifts', roles: ['ADMIN', 'HR'] },
    { label: 'Scheduling', icon: 'calendar_month', route: '/scheduling', roles: ['ADMIN', 'HR'] },
    { label: 'My Schedule', icon: 'event_note', route: '/my-shifts', dividerAfter: true },
    { label: 'Mark Attendance', icon: 'fingerprint', route: '/attendance' },
    { label: 'Attendance Overview', icon: 'groups', route: '/attendance/overview', roles: ['ADMIN', 'HR'] },
    { label: 'Attendance History', icon: 'fact_check', route: '/attendance/history', roles: ['ADMIN', 'HR'], dividerAfter: true },
    { label: 'My Leaves', icon: 'beach_access', route: '/leaves' },
    { label: 'Leave Approval', icon: 'approval', route: '/leaves/approval', roles: ['ADMIN', 'HR'], dividerAfter: true },
    { label: 'Notifications', icon: 'notifications', route: '/notifications' },
    { label: 'Reports', icon: 'assessment', route: '/reports', roles: ['ADMIN', 'HR'] },
    { label: 'User Management', icon: 'admin_panel_settings', route: '/users', roles: ['ADMIN'] }
  ];

  ngOnInit(): void {
    const user = this.authService.getCurrentUser();
    if (user) {
      // Keep the unread badge fresh so new shift assignments and leave decisions show up without a reload.
      timer(0, 30000).pipe(
        switchMap(() => this.notificationService.getUnreadCount(user.userId)),
        takeUntilDestroyed(this.destroyRef)
      ).subscribe();
      this.notificationService.unreadCount$.pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe(count => this.unreadCount = count);
    }
  }

  isVisible(item: NavItem): boolean {
    if (!item.roles) return true;
    return this.authService.hasRole(...item.roles as any[]);
  }

  get currentUser() {
    return this.authService.getCurrentUser();
  }

  get userInitials(): string {
    const name = this.currentUser?.fullName || '';
    return name.split(' ').map(n => n[0]).join('').toUpperCase().slice(0, 2);
  }

  logout(): void {
    this.authService.logout();
  }

  toggleSidenav(): void {
    this.sidenavCollapsed = !this.sidenavCollapsed;
  }

  toggleTheme(): void {
    this.themeService.toggle();
  }

  get isDark(): boolean {
    return this.themeService.isDark();
  }
}
