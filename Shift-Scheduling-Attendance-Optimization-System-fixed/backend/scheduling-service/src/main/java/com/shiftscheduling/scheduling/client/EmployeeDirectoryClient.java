package com.shiftscheduling.scheduling.client;

import com.shiftscheduling.scheduling.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * Resolves employees through employee-service (by Eureka name). The recipient user id is
 * always taken from here, never from the request body.
 */
@Component
public class EmployeeDirectoryClient {

    private final RestTemplate rest;

    public EmployeeDirectoryClient(RestTemplate rest) {
        this.rest = rest;
    }

    /** Department name for notification text; null if unavailable (never blocks an assignment). */
    public String departmentName(Long departmentId) {
        if (departmentId == null) return null;
        try {
            RemoteDepartment[] all = rest.getForObject("http://employee-service/api/departments", RemoteDepartment[].class);
            if (all == null) return null;
            for (RemoteDepartment d : all) {
                if (departmentId.equals(d.id())) return d.name();
            }
            return null;
        } catch (RestClientException ex) {
            return null;
        }
    }

    public RemoteEmployee get(Long employeeId) {
        try {
            RemoteEmployee employee = rest.getForObject(
                    "http://employee-service/api/employees/{id}", RemoteEmployee.class, employeeId);
            if (employee == null || employee.userId() == null) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "Employee " + employeeId + " has no linked login account and cannot be assigned");
            }
            return employee;
        } catch (HttpClientErrorException ex) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "Employee " + employeeId + " was not found");
        } catch (RestClientException ex) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Employee service is unavailable. Please try again shortly.");
        }
    }
}
