package com.shiftscheduling.scheduling.exception;

import org.springframework.http.HttpStatus;

/** Business or authorisation failure that maps to a specific HTTP status. */
public class ApiException extends RuntimeException {
    private final HttpStatus status;

    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() { return status; }
}
