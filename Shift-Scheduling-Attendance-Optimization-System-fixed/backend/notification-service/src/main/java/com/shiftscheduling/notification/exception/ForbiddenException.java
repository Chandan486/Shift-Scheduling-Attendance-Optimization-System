package com.shiftscheduling.notification.exception;

/** The caller is authenticated but is not allowed to touch this notification. */
public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) { super(message); }
}
