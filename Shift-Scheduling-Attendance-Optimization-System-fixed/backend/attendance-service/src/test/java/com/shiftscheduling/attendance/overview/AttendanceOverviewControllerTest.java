package com.shiftscheduling.attendance.overview;

import com.shiftscheduling.attendance.config.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class AttendanceOverviewControllerTest {

    @Mock AttendanceOverviewService service;
    MockMvc mvc;
    final String date = LocalDate.now().minusDays(1).toString();

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new AttendanceOverviewController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void employeeRoleIsForbidden() throws Exception {
        mvc.perform(get("/api/attendance/overview/employees").param("date", date).header("X-User-Role", "EMPLOYEE"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/attendance/overview/summary").param("date", date).header("X-User-Role", "EMPLOYEE"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/attendance/overview/export").param("date", date).header("X-User-Role", "EMPLOYEE"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void missingRoleIsForbidden() throws Exception {
        mvc.perform(get("/api/attendance/overview/employees").param("date", date))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test
    void adminAndHrCanReadSummary() throws Exception {
        when(service.summary(any(), any(), any())).thenReturn(new OverviewSummary(3, 2, 1, 4, 10));
        for (String role : List.of("ADMIN", "HR")) {
            mvc.perform(get("/api/attendance/overview/summary").param("date", date).header("X-User-Role", role))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.present").value(3))
                    .andExpect(jsonPath("$.total").value(10));
        }
    }

    @Test
    void exportReturnsCsvAttachment() throws Exception {
        when(service.export(any(), any(), any(), any())).thenReturn(List.of());
        mvc.perform(get("/api/attendance/overview/export").param("date", date).param("status", "ABSENT")
                        .header("X-User-Role", "HR"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("_absent.csv")));
    }

    @Test
    void badDateFormatIsBadRequest() throws Exception {
        mvc.perform(get("/api/attendance/overview/summary").param("date", "not-a-date").header("X-User-Role", "ADMIN"))
                .andExpect(status().isBadRequest());
    }
}
