export interface LeaveRequest {
  employeeId: number;
  leaveType: string;
  startDate: string;
  endDate: string;
  reason: string;
}

export type LeaveStatus = 'PENDING' | 'APPROVED' | 'REJECTED';

export interface LeaveResponse {
  id: number;
  employeeId: number;
  leaveType: string;
  startDate: string;
  endDate: string;
  reason: string;
  status: LeaveStatus;
  reviewedBy: number | null;
  reviewNotes: string | null;
  reviewedAt: string | null;
  createdAt: string;
  updatedAt: string;
  applicantUserId: number;
  applicantRole: 'EMPLOYEE' | 'HR' | 'ADMIN' | null;
  applicantName: string | null;
}

export interface LeaveBalance {
  employeeId: number;
  year: number;
  annualTotal: number;
  annualUsed: number;
  annualRemaining: number;
  sickTotal: number;
  sickUsed: number;
  sickRemaining: number;
  personalTotal: number;
  personalUsed: number;
  personalRemaining: number;
}

/** Body for PUT /api/leaves/{id}/review. The reviewer is taken from the token, never sent. */
export interface LeaveApproval {
  status: 'APPROVED' | 'REJECTED';
  reviewNotes?: string;
}
