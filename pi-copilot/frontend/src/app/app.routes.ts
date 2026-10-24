import { Routes } from '@angular/router';
import { ConnectPage } from './pages/connect.page';
import { FindingsPage } from './pages/findings.page';
import { PlanPage } from './pages/plan.page';
import { RolloutPage } from './pages/rollout.page';
import { AuditPage } from './pages/audit.page';

export const routes: Routes = [
  { path: '', component: ConnectPage },
  { path: 'scans/:scanId', component: FindingsPage },
  { path: 'plans/:planId', component: PlanPage },
  { path: 'plans/:planId/rollout', component: RolloutPage },
  { path: 'audit', component: AuditPage },
  { path: '**', redirectTo: '' }
];
