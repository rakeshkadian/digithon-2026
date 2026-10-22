import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { FormsModule, NgForm } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { Employee, EmployeeRequest } from './models/employee.model';
import { EmployeeService } from './services/employee.service';

type EmployeeForm = { firstName: string; lastName: string; email: string; phone: string; department: string; joiningDate: string };

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="shell">
      <header class="topbar">
        <div class="brand-mark">EM</div>
        <div class="brand-copy"><strong>PeopleDesk</strong><span>Employee Management</span></div>
        <div class="topbar-right"><span class="api-dot"></span> API connected via localhost:8080</div>
      </header>

      <main>
        <section class="page-heading">
          <div><p class="eyebrow">PEOPLE OPERATIONS</p><h1>Employees</h1><p class="subtitle">Manage your employee directory in one place.</p></div>
          <button class="primary" (click)="startCreate()">＋ Add employee</button>
        </section>

        <div class="alert error" *ngIf="errorMessage" role="alert"><span>{{ errorMessage }}</span><button class="icon-button" (click)="errorMessage = ''" aria-label="Dismiss">×</button></div>
        <div class="alert success" *ngIf="successMessage" role="status">{{ successMessage }}</div>

        <section class="stats-grid">
          <article class="stat-card"><span class="stat-label">Total employees</span><strong>{{ employees.length }}</strong><span class="stat-note">In the directory</span></article>
          <article class="stat-card"><span class="stat-label">Departments</span><strong>{{ departmentCount }}</strong><span class="stat-note">Represented teams</span></article>
          <article class="stat-card"><span class="stat-label">Recently added</span><strong>{{ recentCount }}</strong><span class="stat-note">Joined in the last 30 days</span></article>
        </section>

        <section class="panel">
          <div class="panel-heading"><div><h2>Employee directory</h2><p>Search, update or remove employee records.</p></div><div class="search-box"><span>⌕</span><input aria-label="Search employees" placeholder="Search employees..." [(ngModel)]="searchTerm"></div></div>
          <div class="table-wrap" *ngIf="!loading; else loadingTemplate">
            <table>
              <thead><tr><th>EMPLOYEE</th><th>DEPARTMENT</th><th>PHONE</th><th>JOINING DATE</th><th class="actions-col">ACTIONS</th></tr></thead>
              <tbody>
                <tr *ngFor="let employee of filteredEmployees">
                  <td><div class="person"><div class="avatar">{{ initials(employee) }}</div><div><strong>{{ employee.firstName }} {{ employee.lastName }}</strong><span>{{ employee.email }}</span></div></div></td>
                  <td><span class="department-pill">{{ employee.department }}</span></td>
                  <td>{{ employee.phone || '—' }}</td>
                  <td>{{ employee.joiningDate | date:'mediumDate' }}</td>
                  <td><div class="row-actions"><button class="text-action" (click)="startEdit(employee)">Edit</button><button class="text-action danger-text" (click)="remove(employee)">Delete</button></div></td>
                </tr>
                <tr *ngIf="filteredEmployees.length === 0"><td colspan="5" class="empty-state"><div class="empty-icon">⌕</div><strong>No employees found</strong><span>Try another search or add a new employee.</span></td></tr>
              </tbody>
            </table>
          </div>
          <ng-template #loadingTemplate><div class="loading-state">Loading employee records…</div></ng-template>
          <div class="table-footer"><span>Showing {{ filteredEmployees.length }} of {{ employees.length }} employees</span><button class="refresh-button" (click)="loadEmployees()">↻ Refresh</button></div>
        </section>

        <section class="panel form-panel" *ngIf="formVisible" id="employee-form">
          <div class="panel-heading"><div><h2>{{ editingId ? 'Edit employee' : 'Add employee' }}</h2><p>{{ editingId ? 'Update the employee details below.' : 'Enter the details to create an employee record.' }}</p></div><button class="icon-button" (click)="cancelForm()" aria-label="Close form">×</button></div>
          <form #employeeForm="ngForm" (ngSubmit)="saveEmployee(employeeForm)" class="employee-form">
            <label>First name<input name="firstName" [(ngModel)]="form.firstName" required maxlength="80" #firstName="ngModel"><small class="field-error" *ngIf="firstName.invalid && firstName.touched">First name is required.</small></label>
            <label>Last name<input name="lastName" [(ngModel)]="form.lastName" required maxlength="80" #lastName="ngModel"><small class="field-error" *ngIf="lastName.invalid && lastName.touched">Last name is required.</small></label>
            <label>Email address<input type="email" name="email" [(ngModel)]="form.email" required email maxlength="180" #email="ngModel"><small class="field-error" *ngIf="email.invalid && email.touched">Enter a valid email address.</small></label>
            <label>Phone number<input name="phone" [(ngModel)]="form.phone" maxlength="25" placeholder="Optional"></label>
            <label>Department<select name="department" [(ngModel)]="form.department" required><option value="" disabled>Select department</option><option *ngFor="let department of departments" [value]="department">{{ department }}</option></select></label>
            <label>Joining date<input type="date" name="joiningDate" [(ngModel)]="form.joiningDate" required [max]="today"></label>
            <div class="form-actions"><button type="button" class="secondary" (click)="cancelForm()">Cancel</button><button type="submit" class="primary" [disabled]="employeeForm.invalid || saving">{{ saving ? 'Saving…' : (editingId ? 'Save changes' : 'Create employee') }}</button></div>
          </form>
        </section>
        <footer><span>PeopleDesk · Employee Management MVP</span><a href="http://localhost:8080/swagger-ui.html" target="_blank" rel="noreferrer">Explore REST API ↗</a></footer>
      </main>
    </div>
  `
})
export class AppComponent implements OnInit {
  private readonly employeeService = inject(EmployeeService);
  employees: Employee[] = [];
  searchTerm = '';
  loading = false;
  saving = false;
  formVisible = false;
  editingId: number | null = null;
  errorMessage = '';
  successMessage = '';
  readonly today = new Date().toISOString().slice(0, 10);
  readonly departments = ['Engineering', 'Human Resources', 'Finance', 'Sales', 'Marketing', 'Operations', 'Support'];
  form: EmployeeForm = this.emptyForm();

  ngOnInit(): void { this.loadEmployees(); }
  get filteredEmployees(): Employee[] {
    const term = this.searchTerm.trim().toLowerCase();
    if (!term) return this.employees;
    return this.employees.filter(e => `${e.firstName} ${e.lastName} ${e.email} ${e.department} ${e.phone ?? ''}`.toLowerCase().includes(term));
  }
  get departmentCount(): number { return new Set(this.employees.map(e => e.department)).size; }
  get recentCount(): number {
    const cutoff = new Date(); cutoff.setDate(cutoff.getDate() - 30);
    return this.employees.filter(e => new Date(e.joiningDate) >= cutoff).length;
  }
  loadEmployees(): void {
    this.loading = true; this.errorMessage = '';
    this.employeeService.findAll().subscribe({
      next: employees => { this.employees = employees; this.loading = false; },
      error: err => { this.loading = false; this.showError(err); }
    });
  }
  startCreate(): void { this.editingId = null; this.form = this.emptyForm(); this.formVisible = true; this.clearMessages(); }
  startEdit(employee: Employee): void {
    this.editingId = employee.id;
    this.form = { firstName: employee.firstName, lastName: employee.lastName, email: employee.email, phone: employee.phone ?? '', department: employee.department, joiningDate: employee.joiningDate };
    this.formVisible = true; this.clearMessages();
  }
  cancelForm(): void { this.formVisible = false; this.editingId = null; }
  saveEmployee(formDirective: NgForm): void {
    if (formDirective.invalid || this.saving) return;
    this.saving = true; this.clearMessages();
    const request: EmployeeRequest = { ...this.form, phone: this.form.phone.trim() || null };
    const request$ = this.editingId === null ? this.employeeService.create(request) : this.employeeService.update(this.editingId, request);
    request$.subscribe({
      next: () => { this.saving = false; this.formVisible = false; this.successMessage = this.editingId === null ? 'Employee created successfully.' : 'Employee updated successfully.'; this.editingId = null; this.loadEmployees(); },
      error: err => { this.saving = false; this.showError(err); }
    });
  }
  remove(employee: Employee): void {
    if (!confirm(`Delete ${employee.firstName} ${employee.lastName}? This action cannot be undone.`)) return;
    this.clearMessages();
    this.employeeService.delete(employee.id).subscribe({
      next: () => { this.successMessage = 'Employee deleted successfully.'; this.loadEmployees(); },
      error: err => this.showError(err)
    });
  }
  initials(employee: Employee): string { return `${employee.firstName.charAt(0)}${employee.lastName.charAt(0)}`.toUpperCase(); }
  private emptyForm(): EmployeeForm { return { firstName: '', lastName: '', email: '', phone: '', department: '', joiningDate: this.today }; }
  private clearMessages(): void { this.errorMessage = ''; this.successMessage = ''; }
  private showError(error: HttpErrorResponse): void {
    const detail = error.error?.detail || error.error?.message;
    this.errorMessage = typeof detail === 'string' ? detail : error.status === 0 ? 'Cannot connect to the API. Check that Spring Boot and PostgreSQL are running.' : 'The request failed. Please review the data and try again.';
  }
}
