import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, Routes } from '@angular/router';
import { NavBarModule, UiManagerModule, WorkspaceCommonModule } from 'sqtm-core';
import { TranslateModule } from '@ngx-translate/core';
import { NzButtonModule } from 'ng-zorro-antd/button';
import { DtnDashboardPageComponent } from './containers/dtn-dashboard-page/dtn-dashboard-page.component';

export const routes: Routes = [
  {
    path: '',
    component: DtnDashboardPageComponent,
  },
];

@NgModule({
  imports: [
    CommonModule,
    RouterModule.forChild(routes),
    UiManagerModule,
    NavBarModule,
    WorkspaceCommonModule,
    TranslateModule.forChild(),
    NzButtonModule,
  ],
  declarations: [DtnDashboardPageComponent],
})
export class DtnDashboardModule {}
