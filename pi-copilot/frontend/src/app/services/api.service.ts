import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export const API = 'http://localhost:8081/api';

export interface ScanJob {
  id: string; repoUrl: string; branch: string; commitId?: string;
  status: 'QUEUED' | 'CLONING' | 'ANALYZING' | 'CLASSIFYING' | 'DONE' | 'FAILED';
  message?: string; snapshotJson?: string;
}
export interface CodeRef { filePath: string; className: string; methodName: string; line: number; kind: string; }
export interface Finding {
  id: number; scanId: string; tableName: string; columnName: string; javaField: string; javaType: string;
  piType: string; confidence: number; reason: string; recommendation: string;
  status: 'NEW' | 'APPROVED' | 'REJECTED'; reviewComment?: string; codeRefsJson: string;
}
export interface Plan {
  id: string; scanId: string; status: string; branch: string; prUrl?: string; message?: string;
  sqlText: string; filesJson: string; rowsTotal: number; rowsDone: number; rowsVerified: number;
}
export interface GeneratedFile { path: string; action: string; content: string; diff: string; }
export interface DbRequest { jdbcUrl: string; username: string; password: string; batchSize: number; }

@Injectable({ providedIn: 'root' })
export class ApiService {
  constructor(private http: HttpClient) {}

  startScan(repoUrl: string, branch: string, token: string) {
    return this.http.post<{ scanId: string }>(`${API}/scans`, { repoUrl, branch, token });
  }
  getScan(id: string): Observable<ScanJob> { return this.http.get<ScanJob>(`${API}/scans/${id}`); }
  getFindings(id: string) { return this.http.get<Finding[]>(`${API}/scans/${id}/findings`); }
  review(id: number, status: string, comment = '') {
    return this.http.patch<Finding>(`${API}/findings/${id}`, { status, comment });
  }
  createPlan(scanId: string, token: string, openPullRequest: boolean) {
    return this.http.post<Plan>(`${API}/plans`, { scanId, token, openPullRequest });
  }
  getPlan(id: string) { return this.http.get<Plan>(`${API}/plans/${id}`); }
  testDb(db: DbRequest) {
    return this.http.post<{ ok: boolean; message: string; tables: string[] }>(`${API}/db/test`, db);
  }
  backfill(planId: string, db: DbRequest) { return this.http.post<Plan>(`${API}/plans/${planId}/backfill`, db); }
  getFlag(targetBaseUrl: string) {
    return this.http.get<{ encryption: boolean }>(`${API}/flags`, { params: { targetBaseUrl } });
  }
  setFlag(targetBaseUrl: string, encryption: boolean) {
    return this.http.post<{ encryption: boolean }>(`${API}/flags`, { targetBaseUrl, encryption });
  }
  audit() { return this.http.get<any[]>(`${API}/audit`); }
}
