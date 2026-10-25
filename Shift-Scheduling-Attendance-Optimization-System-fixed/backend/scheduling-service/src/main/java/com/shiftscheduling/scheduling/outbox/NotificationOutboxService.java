package com.shiftscheduling.scheduling.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Transactional outbox for notifications.
 *
 *  - enqueue(): called INSIDE the decision transaction, so the notification is recorded iff the decision is.
 *  - after commit: one immediate delivery attempt.
 *  - scheduled retry: anything still PENDING is retried with back-off until it is SENT or FAILED.
 *
 * Delivery is at-least-once; the receiver's dedupKey makes repeats harmless.
 */
@Service
public class NotificationOutboxService {

    private static final Logger log = LoggerFactory.getLogger(NotificationOutboxService.class);
    static final String NOTIFICATION_BASE_URL = "http://notification-service";
    static final int MAX_ATTEMPTS = 10;

    private final NotificationOutboxRepository repository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher events;
    private final int maxAttempts;

    public NotificationOutboxService(NotificationOutboxRepository repository,
                                     RestTemplate restTemplate,
                                     ObjectMapper objectMapper,
                                     ApplicationEventPublisher events) {
        this.repository = repository;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.events = events;
        this.maxAttempts = MAX_ATTEMPTS;
    }

    @Transactional
    public Long enqueue(String targetPath, Object payload) {
        NotificationOutbox row = new NotificationOutbox();
        row.setTargetPath(targetPath);
        try {
            row.setPayload(objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialise notification payload", e);
        }
        Long id = repository.save(row).getId();
        events.publishEvent(new OutboxEnqueued(id));
        return id;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCommitted(OutboxEnqueued event) {
        deliver(event.outboxId());
    }

    @Scheduled(fixedDelayString = "${outbox.retry-delay-ms:30000}", initialDelayString = "${outbox.retry-delay-ms:30000}")
    public void retryPending() {
        List<NotificationOutbox> due = repository.findTop50ByStatusAndNextAttemptAtLessThanEqualOrderByIdAsc(
                NotificationOutbox.Status.PENDING, LocalDateTime.now());
        due.forEach(row -> deliver(row.getId()));
    }

    /** Attempts one delivery. Never throws: failures are recorded for the retry job. */
    public void deliver(Long outboxId) {
        NotificationOutbox row = repository.findById(outboxId).orElse(null);
        if (row == null || row.getStatus() != NotificationOutbox.Status.PENDING) {
            return;
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            restTemplate.postForEntity(NOTIFICATION_BASE_URL + row.getTargetPath(),
                    new HttpEntity<>(row.getPayload(), headers), String.class);
            row.setStatus(NotificationOutbox.Status.SENT);
            row.setSentAt(LocalDateTime.now());
            row.setLastError(null);
        } catch (RestClientException ex) {
            int attempts = row.getAttempts() + 1;
            row.setAttempts(attempts);
            row.setLastError(truncate(ex.getMessage()));
            if (attempts >= maxAttempts) {
                row.setStatus(NotificationOutbox.Status.FAILED);
                log.error("Notification outbox {} gave up after {} attempts: {}", outboxId, attempts, ex.getMessage());
            } else {
                // Exponential back-off: 30s, 1m, 2m, 4m ... capped at 30 minutes.
                long delaySeconds = Math.min(1800L, 30L * (1L << Math.min(attempts - 1, 10)));
                row.setNextAttemptAt(LocalDateTime.now().plusSeconds(delaySeconds));
                log.warn("Notification outbox {} delivery failed (attempt {}), retry in {}s", outboxId, attempts, delaySeconds);
            }
        }
        repository.save(row);
    }

    private static String truncate(String s) {
        if (s == null) return null;
        return s.length() > 480 ? s.substring(0, 480) : s;
    }
}
