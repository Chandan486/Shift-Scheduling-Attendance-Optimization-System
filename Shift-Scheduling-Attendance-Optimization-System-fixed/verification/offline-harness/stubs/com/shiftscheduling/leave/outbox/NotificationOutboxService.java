package com.shiftscheduling.leave.outbox;
/** Harness stand-in: records what the real outbox would enqueue (the real class needs Spring/Jackson). */
public class NotificationOutboxService {
    public final java.util.List<Object[]> enqueued = new java.util.ArrayList<>();
    public Long enqueue(String targetPath, Object payload) { enqueued.add(new Object[]{targetPath, payload}); return (long) enqueued.size(); }
}
