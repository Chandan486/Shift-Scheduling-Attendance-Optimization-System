import { Routes } from '@angular/router';
import { authGuard, roleGuard } from './core/guards/auth.guard';

export const routes: Routes = [
  { path: 'login', loadComponent: () => import('./features/auth/components/login/login.component').then(m => m.LoginComponent) },
  {
    path: '',
    loadComponent: () => import('./layouts/main-layout/main-layout.component').then(m => m.MainLayoutComponent),
    canActivate: [authGuard],
    children: [
      { path: 'dashboard', loadComponent: () => import('./features/dashboard/components/dashboard.component').then(m => m.DashboardComponent) },
      { path: 'employees', loadComponent: () => import('./features/employees/components/employee-list/employee-list.component').then(m => m.EmployeeListComponent), canActivate: [roleGuard('ADMIN', 'HR')] },
      { path: 'employees/new', loadComponent: () => import('./features/employees/components/employee-form/employee-form.component').then(m => m.EmployeeFormComponent), canActivate: [roleGuard('ADMIN', 'HR')] },
      { path: 'employees/:id', loadComponent: () => import('./features/employees/components/employee-detail/employee-detail.component').then(m => m.EmployeeDetailComponent), canActivate: [roleGuard('ADMIN', 'HR')] },
      { path: 'employees/:id/edit', loadComponent: () => import('./features/employees/components/employee-form/employee-form.component').then(m => m.EmployeeFormComponent), canActivate: [roleGuard('ADMIN', 'HR')] },
      { path: 'shifts', loadComponent: () => import('./features/shifts/components/shift-list/shift-list.component').then(m => m.ShiftListComponent), canActivate: [roleGuard('ADMIN', 'HR')] },
      { path: 'shifts/new', loadComponent: () => import('./features/shifts/components/shift-form/shift-form.component').then(m => m.ShiftFormComponent), canActivate: [roleGuard('ADMIN', 'HR')] },
      { path: 'shifts/:id/edit', loadComponent: () => import('./features/shifts/components/shift-form/shift-form.component').then(m => m.ShiftFormComponent), canActivate: [roleGuard('ADMIN', 'HR')] },
      { path: 'my-shifts', loadComponent: () => import('./features/shifts/components/my-shifts/my-shifts.component').then(m => m.MyShiftsComponent) },
      { path: 'scheduling', loadComponent: () => import('./features/scheduling/components/schedule-list/schedule-list.component').then(m => m.ScheduleListComponent), canActivate: [roleGuard('ADMIN', 'HR')] },
      { path: 'scheduling/generate', loadComponent: () => import('./features/scheduling/components/schedule-generate/schedule-generate.component').then(m => m.ScheduleGenerateComponent), canActivate: [roleGuard('ADMIN', 'HR')] },
      { path: 'scheduling/:id', loadComponent: () => import('./features/scheduling/components/schedule-view/schedule-view.component').then(m => m.ScheduleViewComponent) },
      { path: 'attendance', loadComponent: () => import('./features/attendance/components/attendance-mark/attendance-mark.component').then(m => m.AttendanceMarkComponent) },
      { path: 'attendance/overview', loadComponent: () => import('./features/attendance/components/attendance-overview/attendance-overview.component').then(m => m.AttendanceOverviewComponent), canActivate: [roleGuard('ADMIN', 'HR')] },
      { path: 'attendance/history', loadComponent: () => import('./features/attendance/components/attendance-history/attendance-history.component').then(m => m.AttendanceHistoryComponent) },
      { path: 'leaves', loadComponent: () => import('./features/leaves/components/leave-list/leave-list.component').then(m => m.LeaveListComponent) },
      { path: 'leaves/apply', loadComponent: () => import('./features/leaves/components/leave-apply/leave-apply.component').then(m => m.LeaveApplyComponent) },
      { path: 'leaves/approval', loadComponent: () => import('./features/leaves/components/leave-approval/leave-approval.component').then(m => m.LeaveApprovalComponent), canActivate: [roleGuard('ADMIN', 'HR')] },
      { path: 'users', loadComponent: () => import('./features/users/components/user-management/user-management.component').then(m => m.UserManagementComponent), canActivate: [roleGuard('ADMIN')] },
      { path: 'notifications', loadComponent: () => import('./features/notifications/components/notification-list/notification-list.component').then(m => m.NotificationListComponent) },
      { path: 'reports', loadComponent: () => import('./features/reports/components/reports.component').then(m => m.ReportsComponent), canActivate: [roleGuard('ADMIN', 'HR')] },
      { path: '', redirectTo: 'dashboard', pathMatch: 'full' }
    ]
  },
  { path: '**', redirectTo: 'login' }
];
