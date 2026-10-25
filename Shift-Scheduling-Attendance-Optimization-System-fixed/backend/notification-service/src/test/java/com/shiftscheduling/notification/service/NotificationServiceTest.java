package com.shiftscheduling.notification.service;

import com.shiftscheduling.notification.dto.LeaveDecisionRequest;
import com.shiftscheduling.notification.dto.NotificationRequest;
import com.shiftscheduling.notification.dto.NotificationResponse;
import com.shiftscheduling.notification.entity.Notification;
import com.shiftscheduling.notification.entity.NotificationType;
import com.shiftscheduling.notification.exception.ForbiddenException;
import com.shiftscheduling.notification.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock NotificationRepository repo;
    NotificationService service;

    @BeforeEach
    void setUp() {
        service = new NotificationService(repo);
        org.mockito.Mockito.lenient().when(repo.save(any(Notification.class))).thenAnswer(i -> {
            Notification n = i.getArgument(0);
            return n;
        });
    }

    @Test
    void a_retried_delivery_with_the_same_dedupKey_returns_the_stored_row_and_writes_nothing() {
        Notification stored = new Notification();
        stored.setUserId(101L);
        stored.setTitle("New Shift Assigned");
        stored.setMessage("already here");
        stored.setNotificationType(NotificationType.SHIFT_ASSIGNED);
        when(repo.findByDedupKey("SHIFT_ASSIGNED:55")).thenReturn(Optional.of(stored));

        NotificationResponse out = service.createNotification(new NotificationRequest(
                101L, "New Shift Assigned", "again", NotificationType.SHIFT_ASSIGNED, 7L, "SHIFT_ASSIGNED:55"));

        assertEquals("already here", out.message());
        verify(repo, never()).save(any());
    }

    @Test
    void leave_approval_message_names_dates_approver_and_type_and_goes_to_the_applicant() {
        LeaveDecisionRequest req = new LeaveDecisionRequest(5L, 900L, true, null, "ANNUAL",
                LocalDate.of(2027, 3, 1), LocalDate.of(2027, 3, 3), "Priya HR",
                LocalDateTime.of(2026, 10, 25, 9, 0), "LEAVE_DECISION:900:5");
        when(repo.findByDedupKey(anyString())).thenReturn(Optional.empty());

        NotificationResponse out = service.notifyLeaveDecision(req);

        assertEquals(5L, out.userId());
        assertEquals("Leave Request Approved", out.title());
        assertTrue(out.message().contains("from 01 Mar 2027 to 03 Mar 2027"));
        assertTrue(out.message().contains("approved by Priya HR"));
        assertTrue(out.message().contains("Leave type: ANNUAL"));
        assertTrue(out.message().contains("Decision date: 25 Oct 2026"));
        assertEquals(NotificationType.LEAVE_APPROVED, out.notificationType());
    }

    @Test
    void leave_rejection_message_includes_the_reason() {
        LeaveDecisionRequest req = new LeaveDecisionRequest(5L, 901L, false, "Team is short that week", "SICK",
                LocalDate.of(2027, 4, 1), LocalDate.of(2027, 4, 2), "Admin One",
                LocalDateTime.of(2026, 10, 25, 9, 0), "LEAVE_DECISION:901:5");
        when(repo.findByDedupKey(anyString())).thenReturn(Optional.empty());

        NotificationResponse out = service.notifyLeaveDecision(req);

        assertEquals("Leave Request Rejected", out.title());
        assertTrue(out.message().contains("rejected by Admin One"));
        assertTrue(out.message().contains("Reason: Team is short that week"));
        assertEquals(NotificationType.LEAVE_REJECTED, out.notificationType());
    }

    @Test
    void only_the_recipient_can_mark_a_notification_read() {
        Notification mine = new Notification();
        mine.setUserId(5L);
        when(repo.findById(1L)).thenReturn(Optional.of(mine));

        assertThrows(ForbiddenException.class, () -> service.markAsRead(1L, 6L));
        assertFalse(mine.isRead());

        NotificationResponse ok = service.markAsRead(1L, 5L);
        assertTrue(ok.isRead());
    }
}
