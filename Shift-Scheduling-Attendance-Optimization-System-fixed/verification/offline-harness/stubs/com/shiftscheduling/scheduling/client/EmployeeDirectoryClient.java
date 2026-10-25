package com.shiftscheduling.scheduling.client;
/** Harness stand-in: scenario-controlled employee directory (real class calls employee-service over HTTP). */
public class EmployeeDirectoryClient {
    public final java.util.Map<Long, RemoteEmployee> directory = new java.util.HashMap<>();
    public RemoteEmployee get(Long id) {
        RemoteEmployee e = directory.get(id);
        if (e == null) throw new com.shiftscheduling.scheduling.exception.ApiException(org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY, "Employee " + id + " was not found");
        return e;
    }
    public String departmentName(Long id) { return "Customer Support"; }
}
