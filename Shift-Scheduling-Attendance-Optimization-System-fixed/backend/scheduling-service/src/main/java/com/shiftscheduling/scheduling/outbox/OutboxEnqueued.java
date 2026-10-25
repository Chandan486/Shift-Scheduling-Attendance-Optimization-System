package com.shiftscheduling.scheduling.outbox;

/** Published inside the decision transaction; delivered only after that transaction commits. */
public record OutboxEnqueued(Long outboxId) {}
