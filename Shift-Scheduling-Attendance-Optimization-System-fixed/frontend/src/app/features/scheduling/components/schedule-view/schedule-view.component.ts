import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { ScheduleService } from '../../../../core/services/schedule.service';
import { Schedule, ScheduleAssignment } from '../../../../core/models/schedule.model';

@Component({
  selector: 'app-schedule-view',
  standalone: true,
  imports: [CommonModule, MatIconModule, MatProgressSpinnerModule],
  templateUrl: './schedule-view.component.html',
  styleUrls: ['./schedule-view.component.scss']
})
export class ScheduleViewComponent implements OnInit {
  private route = inject(ActivatedRoute);
  private scheduleService = inject(ScheduleService);

  schedule: Schedule | null = null;
  assignments: ScheduleAssignment[] = [];
  groupedByDate: Map<string, ScheduleAssignment[]> = new Map();
  loading = true;

  ngOnInit(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    this.scheduleService.getSchedule(id).subscribe(s => this.schedule = s);
    this.scheduleService.getAssignments(id).subscribe({
      next: data => {
        this.assignments = data;
        this.groupByDate();
        this.loading = false;
      },
      error: () => this.loading = false
    });
  }

  private groupByDate(): void {
    this.groupedByDate = new Map();
    for (const a of this.assignments) {
      const list = this.groupedByDate.get(a.date) || [];
      list.push(a);
      this.groupedByDate.set(a.date, list);
    }
  }

  getDates(): string[] {
    return Array.from(this.groupedByDate.keys()).sort();
  }

  getShiftColor(name: string): string {
    const n = name.toLowerCase();
    if (n.includes('morning')) return '#e3f2fd';
    if (n.includes('afternoon')) return '#fff3e0';
    if (n.includes('night')) return '#ede7f6';
    return '#f5f5f5';
  }

  getShiftClass(name: string): string {
    const n = name.toLowerCase();
    if (n.includes('morning')) return 'morning';
    if (n.includes('afternoon')) return 'afternoon';
    if (n.includes('night')) return 'night';
    return '';
  }
}
