package com.shiftscheduling.scheduling.outbox;
public class NotificationOutboxService {
    public final java.util.List<Object[]> enqueued = new java.util.ArrayList<>();
    public Long enqueue(String targetPath, Object payload) { enqueued.add(new Object[]{targetPath, payload}); return (long) enqueued.size(); }
}
