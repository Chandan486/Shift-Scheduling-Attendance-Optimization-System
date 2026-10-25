package com.shiftscheduling.attendance.client;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

/**
 * Read-only calls to the other services through Eureka service names
 * (resolved by the load-balanced RestTemplate).
 */
@Component
public class UpstreamDataClient {

    private final RestTemplate rest;

    public UpstreamDataClient(RestTemplate rest) {
        this.rest = rest;
    }

    /** Active employees only (employee-service /api/employees/all). */
    public List<RemoteEmployee> activeEmployees() {
        return fetch("http://employee-service/api/employees/all", RemoteEmployee[].class, "employee-service");
    }

    /** Non-cancelled assignments of PUBLISHED schedules on the date. */
    public List<RemoteAssignment> publishedAssignments(LocalDate date) {
        return fetch("http://scheduling-service/api/schedules/assignments/by-date?date=" + date,
                RemoteAssignment[].class, "scheduling-service");
    }

    /** Leave requests with status APPROVED overlapping the date (the service filters by status). */
    public List<RemoteLeave> approvedLeaves(LocalDate date) {
        return fetch("http://leave-service/api/leaves/approved?startDate=" + date + "&endDate=" + date,
                RemoteLeave[].class, "leave-service");
    }

    private <T> List<T> fetch(String url, Class<T[]> type, String serviceName) {
        try {
            T[] body = rest.getForObject(url, type);
            return body == null ? List.of() : Arrays.asList(body);
        } catch (RestClientException ex) {
            throw new UpstreamServiceException(
                    "Could not load data from " + serviceName + ". Please make sure it is running and try again.", ex);
        }
    }
}
