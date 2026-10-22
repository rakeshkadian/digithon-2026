import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Employee, EmployeeRequest } from '../models/employee.model';

@Injectable({ providedIn: 'root' })
export class EmployeeService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = 'http://localhost:8080/api/employees';

  findAll(): Observable<Employee[]> { return this.http.get<Employee[]>(this.apiUrl); }
  create(request: EmployeeRequest): Observable<Employee> { return this.http.post<Employee>(this.apiUrl, request); }
  update(id: number, request: EmployeeRequest): Observable<Employee> { return this.http.put<Employee>(`${this.apiUrl}/${id}`, request); }
  delete(id: number): Observable<void> { return this.http.delete<void>(`${this.apiUrl}/${id}`); }
}
