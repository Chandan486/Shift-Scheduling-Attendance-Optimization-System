package com.shiftscheduling.scheduling.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface NotificationOutboxRepository extends JpaRepository<NotificationOutbox, Long> {

    List<NotificationOutbox> findTop50ByStatusAndNextAttemptAtLessThanEqualOrderByIdAsc(
            NotificationOutbox.Status status, LocalDateTime now);
}
