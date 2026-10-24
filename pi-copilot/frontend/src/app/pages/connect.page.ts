import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { ApiService } from '../services/api.service';

@Component({
  standalone: true,
  imports: [CommonModule, FormsModule, MatFormFieldModule, MatInputModule, MatButtonModule, MatIconModule],
  template: `
    <main class="connect-shell">
      <section class="hero">
        <div class="hero-copy">
          <div class="eyebrow"><span class="pulse-dot"></span> PRIVACY SECURITY WORKSPACE</div>
          <h1>Find sensitive data.<br><span>Protect it with confidence.</span></h1>
          <p class="hero-lede">Map personal information across your codebase and get a clear, reviewable path to safer data handling.</p>
          <div class="hero-points">
            <div><span class="point-icon"><mat-icon>code</mat-icon></span><span><strong>Code-aware scan</strong><small>Understands entities and how fields are used</small></span></div>
            <div><span class="point-icon"><mat-icon>visibility_off</mat-icon></span><span><strong>No database access</strong><small>Analyzes source code only, never row values</small></span></div>
          </div>
        </div>
        <div class="hero-art" aria-hidden="true">
          <div class="orbit orbit-one"></div><div class="orbit orbit-two"></div>
          <div class="shield"><mat-icon>verified_user</mat-icon></div>
          <div class="float-card float-card-top"><span class="mini-lock"><mat-icon>lock</mat-icon></span><span><b>PI discovery</b><small>Source-level analysis</small></span><i></i></div>
          <div class="float-card float-card-bottom"><span class="mini-check"><mat-icon>check</mat-icon></span><span><b>Privacy posture</b><small>Clear findings, ready to review</small></span></div>
          <div class="art-caption">SECURE BY DESIGN <span>·</span> BUILT FOR DEVELOPERS</div>
        </div>
      </section>

      <section class="setup-grid">
        <div class="setup-intro">
          <div class="section-kicker">GET STARTED <span>01 / 03</span></div>
          <h2>Connect your repository</h2>
          <p>Choose the source code you want to assess. We’ll map its data model and flag fields that may need protection.</p>
          <div class="steps">
            <div class="step active"><span>1</span><div><b>Connect</b><small>Choose a repository</small></div></div>
            <div class="step-line"></div>
            <div class="step"><span>2</span><div><b>Scan</b><small>Analyze source code</small></div></div>
            <div class="step-line"></div>
            <div class="step"><span>3</span><div><b>Review</b><small>Explore findings</small></div></div>
          </div>
        </div>

        <div class="connect-card">
          <div class="card-heading">
            <div class="repo-icon"><mat-icon>account_tree</mat-icon></div>
            <div><h3>Repository details</h3><p>Enter a Git URL to start your privacy scan.</p></div>
          </div>
          <form (ngSubmit)="start()" #scanForm="ngForm">
            <label class="field-label" for="repo-url">Git repository URL <span>REQUIRED</span></label>
            <mat-form-field appearance="outline" class="full-field">
              <mat-icon matPrefix>link</mat-icon>
              <input id="repo-url" matInput name="repoUrl" [(ngModel)]="repoUrl" required
                pattern="^(https?://|git@).+" placeholder="https://github.com/org/employee-app.git">
            </mat-form-field>

            <div class="field-row">
              <div class="branch-field">
                <label class="field-label" for="branch">Branch</label>
                <mat-form-field appearance="outline" class="full-field">
                  <mat-icon matPrefix>call_split</mat-icon>
                  <input id="branch" matInput name="branch" [(ngModel)]="branch" placeholder="main">
                </mat-form-field>
              </div>
              <div class="access-field">
                <label class="field-label" for="token">Access token <span>OPTIONAL</span></label>
                <mat-form-field appearance="outline" class="full-field">
                  <mat-icon matPrefix>key</mat-icon>
                  <input id="token" matInput name="token" [(ngModel)]="token" [type]="showToken ? 'text' : 'password'"
                    placeholder="Only needed for private repos">
                  <button mat-icon-button matSuffix type="button" aria-label="Toggle token visibility" (click)="showToken = !showToken">
                    <mat-icon>{{ showToken ? 'visibility_off' : 'visibility' }}</mat-icon>
                  </button>
                </mat-form-field>
              </div>
            </div>
            <p class="field-hint"><mat-icon>info</mat-icon> Use a fine-grained token with access to this repository only.</p>

            <div class="error-banner" *ngIf="error"><mat-icon>error_outline</mat-icon><span>{{ error }}</span></div>
            <button class="scan-button" mat-flat-button color="primary" type="submit"
              [disabled]="!scanForm.valid || busy">
              <mat-icon>{{ busy ? 'hourglass_top' : 'search' }}</mat-icon>
              {{ busy ? 'Starting your scan…' : 'Start privacy scan' }}
              <mat-icon class="button-arrow" *ngIf="!busy">arrow_forward</mat-icon>
            </button>
            <div class="privacy-note"><mat-icon>shield</mat-icon><span><b>Your code stays yours.</b> We inspect source files only. No database credentials or personal records are needed.</span></div>
          </form>
        </div>
      </section>
      <footer class="page-foot"><span>PI SECURITY CO-PILOT</span><span>Privacy insights, grounded in your code.</span></footer>
    </main>
  `
})
export class ConnectPage {
  repoUrl = '';
  branch = 'main';
  token = '';
  busy = false;
  error = '';
  showToken = false;

  constructor(private api: ApiService, private router: Router) {}

  start() {
    if (this.busy || !this.repoUrl.trim()) return;
    this.busy = true;
    this.error = '';
    sessionStorage.setItem('gitToken', this.token);
    this.api.startScan(this.repoUrl.trim(), this.branch.trim() || 'main', this.token).subscribe({
      next: r => this.router.navigate(['/scans', r.scanId]),
      error: e => {
        this.busy = false;
        this.error = e.error?.message ?? 'We couldn’t start the scan. Check the repository URL and try again.';
      }
    });
  }
}