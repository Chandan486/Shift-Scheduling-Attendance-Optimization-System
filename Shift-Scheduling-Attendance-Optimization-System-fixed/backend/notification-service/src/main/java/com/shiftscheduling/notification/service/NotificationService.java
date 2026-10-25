package com.shiftscheduling.notification.service;

import com.shiftscheduling.notification.dto.*;
import com.shiftscheduling.notification.entity.Notification;
import com.shiftscheduling.notification.entity.NotificationType;
import com.shiftscheduling.notification.exception.ForbiddenException;
import com.shiftscheduling.notification.repository.NotificationRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class NotificationService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMM yyyy");

    private final NotificationRepository repository;

    public NotificationService(NotificationRepository repository) {
        this.repository = repository;
    }

    /**
     * Stores a notification. When a dedupKey is supplied and a row with that key already exists,
     * the existing row is returned instead of creating a duplicate (safe retries).
     */
    @Transactional
    public NotificationResponse createNotification(NotificationRequest request) {
        Notification notification = new Notification();
        notification.setUserId(request.userId());
        notification.setTitle(request.title());
        notification.setMessage(request.message());
        notification.setNotificationType(request.type());
        notification.setReferenceId(request.referenceId());
        notification.setDedupKey(blankToNull(request.dedupKey()));
        return toResponse(saveOrExisting(notification));
    }

    public Page<NotificationResponse> getNotifications(Long userId, Pageable pageable) {
        return repository.findByUserIdOrderByCreatedAtDesc(userId, pageable).map(this::toResponse);
    }

    public List<NotificationResponse> getUnreadNotifications(Long userId) {
        return repository.findByUserIdAndIsReadFalseOrderByCreatedAtDesc(userId)
                .stream().map(this::toResponse).toList();
    }

    public NotificationCountResponse getUnreadCount(Long userId) {
        return new NotificationCountResponse(repository.countByUserIdAndIsReadFalse(userId));
    }

    /** Only the recipient may mark their notification as read. */
    @Transactional
    public NotificationResponse markAsRead(Long notificationId, Long callerId) {
        Notification notification = repository.findById(notificationId)
                .orElseThrow(() -> new RuntimeException("Notification not found with id: " + notificationId));
        if (!notification.getUserId().equals(callerId)) {
            throw new ForbiddenException("You can only update your own notifications");
        }
        notification.setRead(true);
        return toResponse(repository.save(notification));
    }

    @Transactional
    public void markAllAsRead(Long userId) {
        repository.markAllAsReadByUserId(userId);
    }

    @Transactional
    public List<NotificationResponse> notifySchedulePublished(SchedulePublishedRequest request) {
        List<NotificationResponse> responses = new ArrayList<>();
        String scheduleName = request.scheduleName() != null ? request.scheduleName() : "Schedule";

        for (Long userId : request.userIds()) {
            Notification notification = new Notification();
            notification.setUserId(userId);
            notification.setTitle("Schedule Published");
            notification.setMessage("A new schedule has been published: " + scheduleName + ". Please check your upcoming shifts.");
            notification.setNotificationType(NotificationType.SCHEDULE_PUBLISHED);
            notification.setReferenceId(request.scheduleId());
            responses.add(toResponse(repository.save(notification)));
        }
        return responses;
    }

    /**
     * Leave decision for the APPLICANT. Wording follows the agreed templates:
     * "Your leave request from <start> to <end> has been approved by <approver>."
     */
    @Transactional
    public NotificationResponse notifyLeaveDecision(LeaveDecisionRequest request) {
        Notification notification = new Notification();
        notification.setUserId(request.userId());
        notification.setReferenceId(request.leaveId());
        notification.setDedupKey(blankToNull(request.dedupKey()));

        String period = "from " + formatDate(request.startDate()) + " to " + formatDate(request.endDate());
        String approver = request.approverName() == null || request.approverName().isBlank()
                ? "an authorised approver" : request.approverName();
        String details = detailSuffix(request);

        if (Boolean.TRUE.equals(request.approved())) {
            notification.setTitle("Leave Request Approved");
            notification.setMessage("Your leave request " + period + " has been approved by " + approver + "." + details);
            notification.setNotificationType(NotificationType.LEAVE_APPROVED);
        } else {
            String reason = request.reason() == null || request.reason().isBlank()
                    ? "No reason was given." : "Reason: " + request.reason().trim();
            notification.setTitle("Leave Request Rejected");
            notification.setMessage("Your leave request " + period + " has been rejected by " + approver + ". "
                    + reason + details);
            notification.setNotificationType(NotificationType.LEAVE_REJECTED);
        }
        return toResponse(saveOrExisting(notification));
    }

    // ---------- helpers ----------

    private Notification saveOrExisting(Notification notification) {
        String key = notification.getDedupKey();
        if (key != null) {
            Optional<Notification> existing = repository.findByDedupKey(key);
            if (existing.isPresent()) {
                return existing.get();
            }
        }
        try {
            return repository.save(notification);
        } catch (DataIntegrityViolationException race) {
            // Another request with the same dedupKey committed first; return that row.
            if (key == null) throw race;
            return repository.findByDedupKey(key).orElseThrow(() -> race);
        }
    }

    private String detailSuffix(LeaveDecisionRequest r) {
        StringBuilder sb = new StringBuilder();
        if (r.leaveType() != null) sb.append(" Leave type: ").append(r.leaveType()).append(".");
        if (r.decidedAt() != null) sb.append(" Decision date: ")
                .append(r.decidedAt().toLocalDate().format(DATE)).append(".");
        return sb.toString();
    }

    private static String formatDate(LocalDate d) {
        return d == null ? "(unknown date)" : d.format(DATE);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    private NotificationResponse toResponse(Notification n) {
        return new NotificationResponse(
                n.getId(), n.getUserId(), n.getTitle(), n.getMessage(),
                n.getNotificationType(), n.getReferenceId(), n.isRead(), n.getCreatedAt());
    }
}
