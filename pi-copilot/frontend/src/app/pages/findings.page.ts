import { Component, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatTooltipModule } from '@angular/material/tooltip';
import { ApiService, CodeRef, Finding, ScanJob } from '../services/api.service';

@Component({
  standalone: true,
  imports: [CommonModule, FormsModule, MatTableModule, MatButtonModule, MatIconModule,
            MatProgressBarModule, MatCheckboxModule, MatTooltipModule],
  template: `
    <h1>2. Review personal-data findings</h1>

    <div class="card" *ngIf="scan">
      <div class="row">
        <div class="grow">
          <div><b>{{ scan.repoUrl }}</b> <span class="muted">({{ scan.branch }})</span></div>
          <div class="muted" *ngIf="summary">{{ summary }}</div>
        </div>
        <span class="chip" [class.ok]="scan.status==='DONE'" [class.bad]="scan.status==='FAILED'"
              [class.info]="scan.status!=='DONE' && scan.status!=='FAILED'">{{ scan.status }}</span>
      </div>
      <div *ngIf="running" style="margin-top:12px">
        <div class="row muted">
          <span *ngFor="let s of steps; let last = last" [style.font-weight]="s===scan.status ? 500 : 400"
                [style.color]="stepIndex(s) <= stepIndex(scan.status) ? '#1e4b9b' : '#999'">
            {{ s }}<span *ngIf="!last"> →</span>
          </span>
        </div>
        <mat-progress-bar mode="indeterminate"></mat-progress-bar>
      </div>
      <div *ngIf="scan.status==='FAILED'" style="color:#b91c1c;margin-top:8px">{{ scan.message }}</div>
    </div>

    <div class="card" *ngIf="findings.length">
      <table mat-table [dataSource]="findings">
        <ng-container matColumnDef="column">
          <th mat-header-cell *matHeaderCellDef>Table.column</th>
          <td mat-cell *matCellDef="let f">
            <b>{{ f.tableName }}.{{ f.columnName }}</b>
            <div class="muted">{{ f.javaField }} : {{ f.javaType }}</div>
          </td>
        </ng-container>
        <ng-container matColumnDef="pi">
          <th mat-header-cell *matHeaderCellDef>PI type</th>
          <td mat-cell *matCellDef="let f">{{ f.piType }}</td>
        </ng-container>
        <ng-container matColumnDef="conf">
          <th mat-header-cell *matHeaderCellDef>Confidence</th>
          <td mat-cell *matCellDef="let f"><span class="chip" [ngClass]="confClass(f)">{{ f.confidence }}%</span></td>
        </ng-container>
        <ng-container matColumnDef="refs">
          <th mat-header-cell *matHeaderCellDef>Code refs</th>
          <td mat-cell *matCellDef="let f">
            <a href="javascript:void(0)" (click)="toggle(f); $event.stopPropagation()">{{ refs(f).length }} place(s)</a>
          </td>
        </ng-container>
        <ng-container matColumnDef="rec">
          <th mat-header-cell *matHeaderCellDef>Recommendation</th>
          <td mat-cell *matCellDef="let f">{{ f.recommendation }}</td>
        </ng-container>
        <ng-container matColumnDef="status">
          <th mat-header-cell *matHeaderCellDef>Decision</th>
          <td mat-cell *matCellDef="let f">
            <button mat-icon-button color="primary" matTooltip="Approve" (click)="review(f,'APPROVED'); $event.stopPropagation()"
                    [disabled]="f.recommendation==='NONE'"><mat-icon>check_circle</mat-icon></button>
            <button mat-icon-button color="warn" matTooltip="Reject" (click)="review(f,'REJECTED'); $event.stopPropagation()">
              <mat-icon>cancel</mat-icon></button>
            <span class="chip" [class.ok]="f.status==='APPROVED'" [class.bad]="f.status==='REJECTED'"
                  [class.info]="f.status==='NEW'">{{ f.status }}</span>
          </td>
        </ng-container>
        <tr mat-header-row *matHeaderRowDef="cols"></tr>
        <tr mat-row *matRowDef="let f; columns: cols" (click)="toggle(f)" style="cursor:pointer"></tr>
      </table>

      <div *ngIf="selected" style="margin-top:16px;padding:16px;background:#f5f7fb;border-radius:8px">
        <b>{{ selected.tableName }}.{{ selected.columnName }}</b> — <i>{{ selected.reason }}</i>
        <table style="margin-top:8px;font-size:13px;width:100%">
          <tr *ngFor="let r of refs(selected)">
            <td style="width:140px"><span class="chip info">{{ r.kind }}</span></td>
            <td>{{ r.className }}.{{ r.methodName }}<span *ngIf="r.line"> : line {{ r.line }}</span></td>
            <td class="muted">{{ r.filePath }}</td>
          </tr>
        </table>
      </div>

      <div class="row" style="margin-top:20px">
        <button mat-stroked-button (click)="approveAll()">Approve all recommended</button>
        <span class="grow"></span>
        <mat-checkbox [(ngModel)]="openPr">Push branch and open pull request</mat-checkbox>
        <button mat-flat-button color="primary" (click)="createPlan()" [disabled]="approvedCount()===0 || planning">
          <mat-icon>build</mat-icon> Generate changes ({{ approvedCount() }})
        </button>
      </div>
      <mat-progress-bar *ngIf="planning" mode="indeterminate" style="margin-top:8px"></mat-progress-bar>
      <div class="muted" *ngIf="error" style="color:#b91c1c;margin-top:8px">{{ error }}</div>
    </div>
  `
})
export class FindingsPage implements OnInit, OnDestroy {
  scanId = ''; scan?: ScanJob; findings: Finding[] = []; selected?: Finding;
  cols = ['column', 'pi', 'conf', 'refs', 'rec', 'status'];
  steps = ['CLONING', 'ANALYZING', 'CLASSIFYING'];
  timer?: any; openPr = true; planning = false; error = ''; summary = '';

  constructor(private api: ApiService, private route: ActivatedRoute, private router: Router) {}

  ngOnInit() {
    this.scanId = this.route.snapshot.params['scanId'];
    this.poll();
    this.timer = setInterval(() => this.poll(), 2000);
  }
  ngOnDestroy() { clearInterval(this.timer); }

  get running() { return !!this.scan && !['DONE', 'FAILED'].includes(this.scan.status); }
  stepIndex(s: string) { return this.steps.indexOf(s); }

  poll() {
    this.api.getScan(this.scanId).subscribe(s => {
      this.scan = s;
      if (s.snapshotJson && !this.summary) {
        try {
          const snap = JSON.parse(s.snapshotJson);
          this.summary = `${snap.entities?.length ?? 0} entities, ${snap.repositories?.length ?? 0} repositories, `
            + `${snap.services?.length ?? 0} services, ${snap.controllers?.length ?? 0} controllers`
            + (snap.datasourceUrl ? ` · datasource ${snap.datasourceUrl}` : '');
        } catch { /* ignore */ }
      }
      if (!this.running) {
        clearInterval(this.timer);
        if (s.status === 'DONE') this.api.getFindings(this.scanId).subscribe(f => this.findings = f);
      }
    });
  }

  refs(f: Finding): CodeRef[] { try { return JSON.parse(f.codeRefsJson || '[]'); } catch { return []; } }
  toggle(f: Finding) { this.selected = this.selected === f ? undefined : f; }
  confClass(f: Finding) { return f.confidence >= 80 ? 'high' : f.confidence >= 50 ? 'mid' : 'low'; }
  approvedCount() { return this.findings.filter(f => f.status === 'APPROVED').length; }

  review(f: Finding, status: string) {
    this.api.review(f.id, status).subscribe(u => Object.assign(f, u));
  }
  approveAll() {
    this.findings.filter(f => f.status === 'NEW' && f.recommendation.startsWith('ENCRYPT'))
      .forEach(f => this.review(f, 'APPROVED'));
  }
  createPlan() {
    this.planning = true; this.error = '';
    const token = sessionStorage.getItem('gitToken') ?? '';
    this.api.createPlan(this.scanId, token, this.openPr).subscribe({
      next: p => this.router.navigate(['/plans', p.id]),
      error: e => { this.planning = false; this.error = e.error?.message ?? 'Plan generation failed'; }
    });
  }
}
