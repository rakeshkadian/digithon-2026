import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatTableModule } from '@angular/material/table';
import { ApiService } from '../services/api.service';

@Component({
  standalone: true,
  imports: [CommonModule, MatTableModule],
  template: `
    <h1>Audit log</h1>
    <div class="card">
      <p class="muted">Every scan, decision, generated change, backfill and flag switch. Exportable evidence for DPDP Sec 8(5).</p>
      <table mat-table [dataSource]="rows">
        <ng-container matColumnDef="at"><th mat-header-cell *matHeaderCellDef>When</th>
          <td mat-cell *matCellDef="let r">{{ r.at | date:'medium' }}</td></ng-container>
        <ng-container matColumnDef="actor"><th mat-header-cell *matHeaderCellDef>Who</th>
          <td mat-cell *matCellDef="let r">{{ r.actor }}</td></ng-container>
        <ng-container matColumnDef="action"><th mat-header-cell *matHeaderCellDef>Action</th>
          <td mat-cell *matCellDef="let r"><span class="chip info">{{ r.action }}</span></td></ng-container>
        <ng-container matColumnDef="ref"><th mat-header-cell *matHeaderCellDef>Object</th>
          <td mat-cell *matCellDef="let r">{{ r.entityRef }}</td></ng-container>
        <ng-container matColumnDef="detail"><th mat-header-cell *matHeaderCellDef>Detail</th>
          <td mat-cell *matCellDef="let r" class="muted">{{ r.detail }}</td></ng-container>
        <tr mat-header-row *matHeaderRowDef="cols"></tr>
        <tr mat-row *matRowDef="let r; columns: cols"></tr>
      </table>
    </div>
  `
})
export class AuditPage implements OnInit {
  rows: any[] = []; cols = ['at', 'actor', 'action', 'ref', 'detail'];
  constructor(private api: ApiService) {}
  ngOnInit() { this.api.audit().subscribe(r => this.rows = r); }
}
