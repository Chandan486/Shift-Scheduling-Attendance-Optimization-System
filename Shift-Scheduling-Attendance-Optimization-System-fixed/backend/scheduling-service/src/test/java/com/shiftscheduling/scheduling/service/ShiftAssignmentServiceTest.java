package com.shiftscheduling.scheduling.service;

import com.shiftscheduling.scheduling.client.EmployeeDirectoryClient;
import com.shiftscheduling.scheduling.client.RemoteEmployee;
import com.shiftscheduling.scheduling.dto.NotificationPayload;
import com.shiftscheduling.scheduling.entity.Shift;
import com.shiftscheduling.scheduling.entity.ShiftAssignment;
import com.shiftscheduling.scheduling.exception.ApiException;
import com.shiftscheduling.scheduling.outbox.NotificationOutboxService;
import com.shiftscheduling.scheduling.repository.ShiftAssignmentRepository;
import com.shiftscheduling.scheduling.security.RequestUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShiftAssignmentServiceTest {

    @Mock ShiftAssignmentRepository assignments;
    @Mock EmployeeDirectoryClient employees;
    @Mock NotificationOutboxService outbox;

    ShiftAssignmentService service;
    Shift shift;
    final RequestUser hr = new RequestUser(20L, "HR", "Priya HR");

    @BeforeEach
    void setUp() {
        service = new ShiftAssignmentService(assignments, employees, outbox);
        shift = new Shift();
        shift.setId(7L);
        shift.setName("Morning Desk");
        shift.setDepartmentId(3L);
        shift.setDate(LocalDate.of(2027, 2, 10));
        shift.setStartTime(LocalTime.of(8, 0));
        shift.setEndTime(LocalTime.of(16, 0));
        shift.setMaxStaff(3);
        shift.setMinStaff(1);
        lenient().when(assignments.saveAndFlush(any(ShiftAssignment.class))).thenAnswer(i -> i.getArgument(0));
        lenient().when(employees.departmentName(3L)).thenReturn("Customer Support");
    }

    private static RemoteEmployee emp(long id, long userId, String name, boolean active) {
        return new RemoteEmployee(id, userId, name, 3L, "Customer Support", active);
    }

    private ShiftAssignment existing(long employeeId, Shift other) {
        ShiftAssignment a = new ShiftAssignment();
        a.setShift(other);
        a.setEmployeeId(employeeId);
        a.setUserId(employeeId + 100);
        a.setEmployeeName("Existing " + employeeId);
        return a;
    }

    @Test
    void assigns_each_selected_employee_and_notifies_only_them_with_the_right_recipient() {
        when(employees.get(1L)).thenReturn(emp(1L, 101L, "Asha", true));
        when(employees.get(2L)).thenReturn(emp(2L, 102L, "Bilal", true));

        List<ShiftAssignment> created = service.assignNew(shift, List.of(1L, 2L), hr);

        assertEquals(2, created.size());
        ArgumentCaptor<Object> payloads = ArgumentCaptor.forClass(Object.class);
        verify(outbox, times(2)).enqueue(eq("/api/notifications"), payloads.capture());
        List<Object> all = payloads.getAllValues();
        assertEquals(101L, ((NotificationPayload) all.get(0)).userId());
        assertEquals(102L, ((NotificationPayload) all.get(1)).userId());
        NotificationPayload first = (NotificationPayload) all.get(0);
        assertEquals("New Shift Assigned", first.title());
        assertEquals("SHIFT_ASSIGNED", first.type());
        assertTrue(first.message().contains("Morning Desk"));
        assertTrue(first.message().contains("08:00 to 16:00"));
        assertTrue(first.message().contains("Customer Support"));
    }

    @Test
    void duplicate_ids_in_one_request_create_one_assignment_and_one_notification() {
        when(employees.get(1L)).thenReturn(emp(1L, 101L, "Asha", true));

        List<ShiftAssignment> created = service.assignNew(shift, List.of(1L, 1L, 1L), hr);

        assertEquals(1, created.size());
        verify(outbox, times(1)).enqueue(anyString(), any());
    }

    @Test
    void inactive_employee_is_refused_before_anything_is_saved() {
        when(employees.get(1L)).thenReturn(emp(1L, 101L, "Asha", true));
        when(employees.get(2L)).thenReturn(emp(2L, 102L, "Gone Gerry", false));

        ApiException ex = assertThrows(ApiException.class, () -> service.assignNew(shift, List.of(1L, 2L), hr));
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, ex.getStatus());
        verify(assignments, never()).saveAndFlush(any());
        verifyNoInteractions(outbox);
    }

    @Test
    void more_employees_than_max_staff_is_refused() {
        shift.setMaxStaff(2);
        ApiException ex = assertThrows(ApiException.class, () -> service.assignNew(shift, List.of(1L, 2L, 3L), hr));
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, ex.getStatus());
        verifyNoInteractions(employees);
    }

    @Test
    void double_booking_on_an_overlapping_shift_is_a_conflict() {
        Shift other = new Shift();
        other.setId(99L);
        other.setName("Late Desk");
        other.setDate(shift.getDate());
        other.setStartTime(LocalTime.of(15, 0));
        other.setEndTime(LocalTime.of(23, 0));
        when(employees.get(1L)).thenReturn(emp(1L, 101L, "Asha", true));
        when(assignments.findByEmployeeIdAndShift_Date(1L, shift.getDate())).thenReturn(List.of(existing(1L, other)));

        ApiException ex = assertThrows(ApiException.class, () -> service.assignNew(shift, List.of(1L), hr));
        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertTrue(ex.getMessage().contains("Late Desk"));
        verifyNoInteractions(outbox);
    }

    @Test
    void back_to_back_shifts_that_do_not_overlap_are_allowed() {
        Shift earlier = new Shift();
        earlier.setId(98L);
        earlier.setName("Early Desk");
        earlier.setDate(shift.getDate());
        earlier.setStartTime(LocalTime.of(0, 0));
        earlier.setEndTime(LocalTime.of(8, 0));
        when(employees.get(1L)).thenReturn(emp(1L, 101L, "Asha", true));
        when(assignments.findByEmployeeIdAndShift_Date(1L, shift.getDate())).thenReturn(List.of(existing(1L, earlier)));

        assertEquals(1, service.assignNew(shift, List.of(1L), hr).size());
    }

    @Test
    void sync_removes_unselected_people_and_notifies_only_newly_added_ones() {
        ShiftAssignment keepAsha = existing(1L, shift);
        ShiftAssignment dropBilal = existing(2L, shift);
        when(assignments.findByShift_IdOrderByEmployeeNameAsc(7L)).thenReturn(List.of(keepAsha, dropBilal));
        when(employees.get(3L)).thenReturn(emp(3L, 103L, "Chen", true));

        service.sync(shift, List.of(1L, 3L), hr);

        verify(assignments).deleteAll(List.of(dropBilal));
        ArgumentCaptor<Object> payloads = ArgumentCaptor.forClass(Object.class);
        verify(outbox, times(1)).enqueue(anyString(), payloads.capture());
        assertEquals(103L, ((NotificationPayload) payloads.getValue()).userId());
        verify(employees, never()).get(1L); // already assigned: no re-resolve, no second notification
    }

    @Test
    void employee_schedule_query_is_keyed_by_the_logged_in_user() {
        service.forUser(101L, shift.getDate(), shift.getDate().plusDays(7));
        verify(assignments).findByUserIdAndShift_DateBetweenOrderByShift_DateAscShift_StartTimeAsc(
                101L, shift.getDate(), shift.getDate().plusDays(7));
    }

    private static String anyString() { return any(String.class); }
}
