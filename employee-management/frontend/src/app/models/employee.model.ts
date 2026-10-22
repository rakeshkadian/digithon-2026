export interface Employee {
  id: number;
  firstName: string;
  lastName: string;
  email: string;
  phone: string | null;
  department: string;
  joiningDate: string;
}

export interface EmployeeRequest {
  firstName: string;
  lastName: string;
  email: string;
  phone: string | null;
  department: string;
  joiningDate: string;
}
