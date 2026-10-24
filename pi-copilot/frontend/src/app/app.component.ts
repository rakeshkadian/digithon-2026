import { Component } from '@angular/core';
import { RouterLink, RouterOutlet } from '@angular/router';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, RouterLink, MatToolbarModule, MatButtonModule, MatIconModule],
  template: `
    <mat-toolbar>
      <span class="brand-mark"><mat-icon>security</mat-icon></span>
      <span class="brand-name">PI Security Co-Pilot</span>
      <span class="grow"></span>
      <a mat-button routerLink="/">New scan</a>
      <a mat-button routerLink="/audit">Audit log</a>
    </mat-toolbar>
    <div class="page"><router-outlet></router-outlet></div>
  `
})
export class AppComponent {}