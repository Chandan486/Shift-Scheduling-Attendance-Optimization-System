-- Idempotency key: the same logical notification (e.g. "SHIFT_ASSIGNED:<shift>:<user>") is stored once,
-- even if the producing service retries its outbox delivery.
ALTER TABLE notifications ADD COLUMN dedup_key VARCHAR(200);
CREATE UNIQUE INDEX uq_notifications_dedup_key ON notifications(dedup_key) WHERE dedup_key IS NOT NULL;
