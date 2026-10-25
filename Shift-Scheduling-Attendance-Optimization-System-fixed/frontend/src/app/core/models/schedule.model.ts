export interface Schedule {
  id: number;
  name: string;
  startDate: string;
  endDate: string;
  status: string;
  createdBy: string;
  notes: string;
  assignmentCount: number;
  createdAt: string;
  updatedAt: string;
}

export interface ScheduleAssignment {
  id: number;
  scheduleId: number;
  shiftId: number;
  shiftName: string;
  employeeId: number;
  employeeName: string;
  date: string;
  startTime: string;
  endTime: string;
  status: string;
  notes: string;
}

export interface ScheduleGenerationRequest {
  name: string;
  startDate: string;
  endDate: string;
  departmentId?: number;
  notes?: string;
}

export interface ScheduleGenerationResult {
  schedule: Schedule;
  totalAssignments: number;
  unfilledPositions: UnfilledPosition[];
  message: string;
}

export interface UnfilledPosition {
  shiftId: number;
  shiftName: string;
  date: string;
  reason: string;
}
