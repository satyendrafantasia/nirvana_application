export interface User {
  id: string;
  firstName: string;
  lastName: string;
  email: string;
  roles?: string[];
}

export interface AuthResponse {
  token: string;
  user: User;
}

export interface LoginPayload {
  email: string;
  roles?: string[];
  password: string;
}

export interface RegisterPayload {
  firstName: string;
  lastName: string;
  email: string;
  roles?: string[];
  password: string;
}
