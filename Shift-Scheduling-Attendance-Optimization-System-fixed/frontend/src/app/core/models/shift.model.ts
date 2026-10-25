export interface Shift {
  id: number;
  name: string;
  departmentId: number;
  startTime: string;
  endTime: string;
  date: string;
  overnight: boolean;
  maxStaff: number;
  minStaff: number;
  nightShift: boolean;
}

export interface ShiftRequest {
  name: string;
  departmentId: number;
  startTime: string;
  endTime: string;
  date: string;
  overnight: boolean;
  maxStaff: number;
  minStaff: number;
  nightShift: boolean;
  requirements: ShiftRequirementRequest[];
  /** employee-service ids (not user ids). Omit on update to leave assignments untouched. */
  employeeIds?: number[];
}

export interface ShiftRequirement {
  id: number;
  shiftId: number;
  skillId: number;
  requiredCount: number;
}

export interface ShiftRequirementRequest {
  skillId: number;
  requiredCount: number;
}

export interface ShiftAssignment {
  id: number;
  shiftId: number;
  shiftName: string;
  date: string;
  startTime: string;
  endTime: string;
  departmentId: number;
  employeeId: number;
  employeeName: string;
  userId: number;
  status: string;
  assignedAt: string;
}
