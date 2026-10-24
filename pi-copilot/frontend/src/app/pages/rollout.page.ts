import { Component, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { ApiService, DbRequest, Plan } from '../services/api.service';

@Component({
  standalone: true,
  imports: [CommonModule, FormsModule, MatFormFieldModule, MatInputModule, MatButtonModule, MatIconModule,
            MatProgressBarModule, MatSlideToggleModule],
  template: `
    <h1>4. Encrypt data and switch</h1>

    <div class="card">
      <h3 style="margin-top:0">Target database</h3>
      <p class="muted">Used only for this job. Credentials are not stored. The generated SQL is applied first
        (safe to re-run), then plain values are copied into the encrypted columns in batches.</p>
      <div class="row">
        <mat-form-field appearance="outline" class="grow">
          <mat-label>JDBC URL</mat-label>
          <input matInput [(ngModel)]="db.jdbcUrl" placeholder="jdbc:postgresql://localhost:5432/empdb">
        </mat-form-field>
        <mat-form-field appearance="outline" style="width:180px">
          <mat-label>User</mat-label><input matInput [(ngModel)]="db.username">
        </mat-form-field>
        <mat-form-field appearance="outline" style="width:180px">
          <mat-label>Password</mat-label><input matInput type="password" [(ngModel)]="db.password">
        </mat-form-field>
        <mat-form-field appearance="outline" style="width:120px">
          <mat-label>Batch</mat-label><input matInput type="number" [(ngModel)]="db.batchSize">
        </mat-form-field>
      </div>
      <div class="row">
        <button mat-stroked-button (click)="test()">Test connection</button>
        <span class="muted" *ngIf="testMsg" [style.color]="testOk ? '#1e6b2f' : '#b91c1c'">{{ testMsg }}</span>
        <span class="grow"></span>
        <button mat-flat-button color="primary" (click)="start()" [disabled]="!testOk || running">
          <mat-icon>lock</mat-icon> Encrypt data
        </button>
      </div>
    </div>

    <div class="card" *ngIf="plan">
      <h3 style="margin-top:0">Progress</h3>
      <div class="row">
        <span class="chip" [class.ok]="plan.status==='VERIFIED'" [class.bad]="plan.status==='FAILED'"
              [class.info]="plan.status!=='VERIFIED' && plan.status!=='FAILED'">{{ plan.status }}</span>
        <span>{{ plan.rowsDone }} / {{ plan.rowsTotal }} rows encrypted</span>
        <span *ngIf="plan.rowsVerified">· {{ plan.rowsVerified }} sample rows decrypted OK</span>
      </div>
      <mat-progress-bar style="margin-top:8px" [mode]="running && !plan.rowsTotal ? 'indeterminate' : 'determinate'"
                        [value]="plan.rowsTotal ? 100 * plan.rowsDone / plan.rowsTotal : 0"></mat-progress-bar>
      <div class="muted" style="margin-top:8px" [style.color]="plan.status==='FAILED' ? '#b91c1c' : ''">{{ plan.message }}</div>
    </div>

    <div class="card">
      <h3 style="margin-top:0">Feature flag in the application</h3>
      <p class="muted">Deploy the merged pull request with <code>PI_MASTER_KEY</code> set. Then switch reads to the
        encrypted path. Switch back for instant rollback — the plain columns are still there and kept in sync.</p>
      <div class="row">
        <mat-form-field appearance="outline" class="grow">
          <mat-label>Application base URL</mat-label>
          <input matInput [(ngModel)]="appUrl" placeholder="http://localhost:8080">
        </mat-form-field>
        <button mat-stroked-button (click)="readFlag()">Read flag</button>
        <mat-slide-toggle color="primary" [checked]="flag" (change)="setFlag($event.checked)"
                          [disabled]="flag===undefined">
          pi.encryption {{ flag===undefined ? '(unknown)' : (flag ? 'ON' : 'OFF') }}
        </mat-slide-toggle>
      </div>
      <div class="muted" *ngIf="flagMsg" style="color:#b91c1c">{{ flagMsg }}</div>
    </div>
  `
})
export class RolloutPage implements OnInit, OnDestroy {
  planId = ''; plan?: Plan; timer?: any;
  db: DbRequest = { jdbcUrl: 'jdbc:postgresql://localhost:5432/empdb', username: 'postgres', password: '', batchSize: 500 };
  testOk = false; testMsg = '';
  appUrl = 'http://localhost:8080'; flag?: boolean; flagMsg = '';

  constructor(private api: ApiService, private route: ActivatedRoute) {}

  ngOnInit() {
    this.planId = this.route.snapshot.params['planId'];
    this.refresh();
  }
  ngOnDestroy() { clearInterval(this.timer); }

  get running() { return !!this.plan && ['BACKFILL_RUNNING', 'BACKFILL_DONE'].includes(this.plan.status); }

  refresh() {
    this.api.getPlan(this.planId).subscribe(p => {
      this.plan = p;
      if (this.running && !this.timer) this.timer = setInterval(() => this.refresh(), 1000);
      if (!this.running && this.timer) { clearInterval(this.timer); this.timer = undefined; }
    });
  }
  test() {
    this.testMsg = 'Testing…';
    this.api.testDb(this.db).subscribe({
      next: r => { this.testOk = r.ok; this.testMsg = r.message; },
      error: e => { this.testOk = false; this.testMsg = e.error?.message ?? 'Failed'; }
    });
  }
  start() {
    this.api.backfill(this.planId, this.db).subscribe(p => { this.plan = p; this.refresh(); });
  }
  readFlag() {
    this.flagMsg = '';
    this.api.getFlag(this.appUrl).subscribe({
      next: r => this.flag = r.encryption,
      error: e => { this.flag = undefined; this.flagMsg = 'Cannot reach ' + this.appUrl + '/pi/flag — is the PR deployed?'; }
    });
  }
  setFlag(on: boolean) {
    this.api.setFlag(this.appUrl, on).subscribe({
      next: r => this.flag = r.encryption,
      error: e => this.flagMsg = e.error?.message ?? 'Toggle failed'
    });
  }
}
