export interface LoginRequest {
  username: string;
  password: string;
}

export interface LoginResponse {
  token: string;
  username: string;
  fullName: string;
  role: string;
  userId: number;
}

export interface TokenValidationResponse {
  valid: boolean;
  username: string;
  role: string;
  userId: number;
}

export type Role = 'ADMIN' | 'HR' | 'EMPLOYEE';
