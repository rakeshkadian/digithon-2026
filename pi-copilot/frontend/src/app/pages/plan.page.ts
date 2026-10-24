import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { MatTabsModule } from '@angular/material/tabs';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatListModule } from '@angular/material/list';
import { ApiService, GeneratedFile, Plan } from '../services/api.service';

@Component({
  standalone: true,
  imports: [CommonModule, RouterLink, MatTabsModule, MatButtonModule, MatIconModule, MatListModule],
  template: `
    <h1>3. Generated changes</h1>

    <div class="card" *ngIf="plan">
      <div class="row">
        <div class="grow">
          <div>Branch <b>{{ plan.branch }}</b></div>
          <div class="muted">{{ plan.message }}</div>
        </div>
        <span class="chip" [class.ok]="plan.status==='PR_OPENED' || plan.status==='GENERATED'"
              [class.bad]="plan.status==='FAILED'">{{ plan.status }}</span>
        <a mat-flat-button color="primary" *ngIf="plan.prUrl" [href]="plan.prUrl" target="_blank">
          <mat-icon>open_in_new</mat-icon> Open pull request
        </a>
        <a mat-stroked-button color="primary" [routerLink]="['/plans', plan.id, 'rollout']">
          Next: encrypt data <mat-icon>arrow_forward</mat-icon>
        </a>
      </div>
      <p class="muted" style="margin-top:12px">
        Everything below is <b>additive</b>: new columns and new methods only. Existing code keeps working.
        The pull request is reviewed and merged by your team; the Co-Pilot never pushes to main.
      </p>
    </div>

    <div class="card" *ngIf="plan">
      <mat-tab-group>
        <mat-tab label="Database (SQL)">
          <pre class="mono" style="margin-top:16px">{{ plan.sqlText }}</pre>
        </mat-tab>
        <mat-tab [label]="'Code changes (' + files.length + ' files)'">
          <div class="row" style="align-items:flex-start;margin-top:16px">
            <mat-nav-list style="width:380px;max-height:520px;overflow:auto">
              <a mat-list-item *ngFor="let f of files" (click)="current = f" [activated]="current===f">
                <span class="chip" [class.ok]="f.action==='ADDED'" [class.info]="f.action==='MODIFIED'"
                      style="margin-right:8px">{{ f.action }}</span>
                <span style="font-size:12px">{{ shortPath(f.path) }}</span>
              </a>
            </mat-nav-list>
            <div class="grow" *ngIf="current">
              <div class="muted" style="margin-bottom:6px">{{ current.path }}</div>
              <pre class="mono"><ng-container *ngFor="let line of lines(current)"><span [ngClass]="lineClass(line)">{{ line }}</span>
</ng-container></pre>
            </div>
          </div>
        </mat-tab>
      </mat-tab-group>
    </div>
  `
})
export class PlanPage implements OnInit {
  plan?: Plan; files: GeneratedFile[] = []; current?: GeneratedFile;
  constructor(private api: ApiService, private route: ActivatedRoute) {}

  ngOnInit() {
    this.api.getPlan(this.route.snapshot.params['planId']).subscribe(p => {
      this.plan = p;
      try { this.files = JSON.parse(p.filesJson || '[]'); } catch { this.files = []; }
      this.current = this.files.find(f => f.action === 'MODIFIED') ?? this.files[0];
    });
  }
  shortPath(p: string) { return p.replace(/^src\/main\/(java|resources)\//, ''); }
  lines(f: GeneratedFile) { return (f.action === 'MODIFIED' ? f.diff : f.content).split('\n'); }
  lineClass(l: string) {
    if (l.startsWith('+++') || l.startsWith('---') || l.startsWith('@@')) return 'diff-hdr';
    if (l.startsWith('+')) return 'diff-add';
    if (l.startsWith('-')) return 'diff-del';
    return '';
  }
}
