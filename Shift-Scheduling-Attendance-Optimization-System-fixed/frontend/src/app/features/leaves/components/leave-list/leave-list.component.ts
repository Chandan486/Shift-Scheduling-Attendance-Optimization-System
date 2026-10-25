import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatTableModule } from '@angular/material/table';
import { MatCardModule } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { RouterModule } from '@angular/router';
import { LeaveService } from '../../../../core/services/leave.service';
import { AuthService } from '../../../../core/services/auth.service';
import { LeaveResponse } from '../../../../core/models/leave.model';

@Component({
  selector: 'app-leave-list',
  standalone: true,
  imports: [CommonModule, MatTableModule, MatCardModule, MatChipsModule, MatPaginatorModule, MatButtonModule, MatIconModule, RouterModule],
  templateUrl: './leave-list.component.html',
  styleUrls: ['./leave-list.component.scss']
})
export class LeaveListComponent implements OnInit {
  private leaveService = inject(LeaveService);
  private authService = inject(AuthService);

  leaves: LeaveResponse[] = [];
  displayedColumns = ['leaveType', 'startDate', 'endDate', 'status', 'reason'];
  totalElements = 0;
  pageSize = 20;

  ngOnInit(): void { this.loadLeaves(); }

  loadLeaves(page = 0): void {
    const user = this.authService.getCurrentUser();
    if (!user) return;
    this.leaveService.getLeaves(user.userId, page, this.pageSize).subscribe(data => {
      this.leaves = data.content || data;
      this.totalElements = data.totalElements || this.leaves.length;
    });
  }

  onPage(event: PageEvent): void {
    this.pageSize = event.pageSize;
    this.loadLeaves(event.pageIndex);
  }

  getStatusColor(status: string): string {
    switch (status) { case 'APPROVED': return 'primary'; case 'REJECTED': return 'warn'; default: return 'accent'; }
  }
}
