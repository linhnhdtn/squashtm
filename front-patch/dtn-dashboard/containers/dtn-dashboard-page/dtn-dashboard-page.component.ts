import { ChangeDetectionStrategy, Component, OnInit } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ReferentialDataService } from 'sqtm-core';

interface DtnSummary {
  plugin: string;
  time: string;
  projects: number;
  testCases: number;
  executions: number;
  requirements: number;
}

@Component({
  selector: 'sqtm-app-dtn-dashboard-page',
  templateUrl: './dtn-dashboard-page.component.html',
  styleUrls: ['./dtn-dashboard-page.component.less'],
  changeDetection: ChangeDetectionStrategy.OnPush,
  standalone: false,
})
export class DtnDashboardPageComponent implements OnInit {
  summary$: Observable<DtnSummary>;

  readonly tiles: { key: keyof DtnSummary; label: string }[] = [
    { key: 'projects', label: 'Projects' },
    { key: 'testCases', label: 'Test cases' },
    { key: 'executions', label: 'Executions' },
    { key: 'requirements', label: 'Requirements' },
  ];

  constructor(
    private http: HttpClient,
    private referentialDataService: ReferentialDataService,
  ) {}

  ngOnInit(): void {
    this.referentialDataService.refresh().subscribe();
    this.load();
  }

  load(): void {
    this.summary$ = this.http.get<DtnSummary>('plugin/dtn-myfeature/api/summary');
  }
}
