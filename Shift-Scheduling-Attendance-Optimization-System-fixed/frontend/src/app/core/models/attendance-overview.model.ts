export type OverviewStatus = 'PRESENT' | 'ABSENT' | 'ON_LEAVE' | 'NOT_SCHEDULED';

export interface OverviewRow {
  employeeId: number;
  employeeName: string;
  departmentId: number | null;
  department: string | null;
  status: OverviewStatus;
  checkInTime: string | null;
  checkOutTime: string | null;
  attendanceStatus: string | null;
  incomplete: boolean;
  shiftName: string | null;
  leaveType: string | null;
}

export interface OverviewSummary {
  present: number;
  absent: number;
  onLeave: number;
  notScheduled: number;
  total: number;
}

export interface OverviewResponse {
  date: string;
  today: boolean;
  summary: OverviewSummary;
  content: OverviewRow[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}
