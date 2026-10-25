export interface Employee {
  id: number;
  userId: number;
  fullName: string;
  email: string;
  phone: string;
  departmentId: number;
  departmentName: string;
  position: string;
  maxHoursPerWeek: number;
  maxShiftsPerWeek: number;
  minRestHours: number;
  skillIds: number[];
  skillNames: string[];
  active: boolean;
}

export interface EmployeeRequest {
  userId: number;
  fullName: string;
  email: string;
  phone: string;
  departmentId: number;
  position: string;
  maxHoursPerWeek: number;
  maxShiftsPerWeek: number;
  minRestHours: number;
  skillIds: number[];
}

export interface Department {
  id: number;
  name: string;
  description: string;
}

export interface Skill {
  id: number;
  name: string;
  description: string;
}

export interface Availability {
  id: number;
  employeeId: number;
  dayOfWeek: string;
  startTime: string;
  endTime: string;
  available: boolean;
  preference: number;
}
