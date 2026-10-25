export interface AttendanceRecord {
  id: number;
  employeeId: number;
  employeeName: string;
  date: string;
  checkInTime: string;
  checkOutTime: string;
  scheduledStart: string;
  scheduledEnd: string;
  workedHours: number;
  status: string;
  notes: string;
}

export interface CheckInRequest {
  employeeId: number;
  notes?: string;
}

export interface CheckOutRequest {
  employeeId: number;
  notes?: string;
}

export interface AttendanceReport {
  employeeId: number;
  employeeName: string;
  totalDays: number;
  presentDays: number;
  absentDays: number;
  lateDays: number;
  earlyDepartures: number;
  totalHoursWorked: number;
  averageHoursPerDay: number;
}
