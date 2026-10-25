package com.shiftscheduling.attendance.client;

/** Raised when a dependent service (employee/scheduling/leave) cannot be reached or returns an error. */
public class UpstreamServiceException extends RuntimeException {
    public UpstreamServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
