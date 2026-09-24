package com.estatecrm.customer_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.estatecrm.customer_service.dto.CreateAppointmentRequest;
import com.estatecrm.customer_service.entity.Customer;
import com.estatecrm.customer_service.exception.ConflictException;
import com.estatecrm.customer_service.repository.AppointmentRepository;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class AppointmentServiceTests {

    private static final UUID ACTOR = UUID.randomUUID();
    private static final UUID CUSTOMER_ID = UUID.randomUUID();
    // Ngay co dinh se het han: AppointmentService.requireFuture chan startTime qua khu.
    // Lay moc tuong lai theo gio chay test de test khong tu chet theo thoi gian.
    private static final LocalDateTime START =
            LocalDateTime.now().plusDays(1).withHour(9).withMinute(0).withSecond(0).withNano(0);
    private static final LocalDateTime END = START.plusHours(1);

    @Mock
    private AppointmentRepository appointmentRepository;

    @Mock
    private CustomerService customerService;

    @InjectMocks
    private AppointmentService appointmentService;

    @Test
    void rejectsEndTimeNotAfterStartTime() {
        CreateAppointmentRequest request =
                new CreateAppointmentRequest(CUSTOMER_ID, "Viewing", END, START, null, null, null, null);

        assertThatThrownBy(() -> appointmentService.create(request, ACTOR, "SALES"))
                .isInstanceOf(ResponseStatusException.class);
        verify(appointmentRepository, never()).save(any());
    }

    @Test
    void rejectsStartTimeInThePast() {
        LocalDateTime past = LocalDateTime.now().minusDays(1);
        CreateAppointmentRequest request =
                new CreateAppointmentRequest(CUSTOMER_ID, "Viewing", past, past.plusHours(1), null, null, null, null);

        assertThatThrownBy(() -> appointmentService.create(request, ACTOR, "SALES"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("future");
        verify(appointmentRepository, never()).save(any());
    }

    @Test
    void rejectsDoubleBookingForSameSalesUser() {
        when(customerService.requireAccess(CUSTOMER_ID, ACTOR, "SALES")).thenReturn(new Customer());
        when(appointmentRepository.countOverlapping(
                        eq(ACTOR), eq(START), eq(END), eq(AppointmentRepository.NO_APPOINTMENT)))
                .thenReturn(1L);

        CreateAppointmentRequest request =
                new CreateAppointmentRequest(CUSTOMER_ID, "Viewing", START, END, null, null, null, null);

        assertThatThrownBy(() -> appointmentService.create(request, ACTOR, "SALES"))
                .isInstanceOf(ConflictException.class);
        verify(appointmentRepository, never()).save(any());
    }

    @Test
    void salesUserCannotClaimAnotherSalesCalendar() {
        when(customerService.requireAccess(CUSTOMER_ID, ACTOR, "SALES")).thenReturn(new Customer());
        when(customerService.isPrivileged(anyString())).thenReturn(false);
        when(appointmentRepository.countOverlapping(
                        eq(ACTOR), eq(START), eq(END), eq(AppointmentRepository.NO_APPOINTMENT)))
                .thenReturn(0L);
        when(appointmentRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        UUID otherSales = UUID.randomUUID();
        CreateAppointmentRequest request =
                new CreateAppointmentRequest(CUSTOMER_ID, "Viewing", START, END, null, null, otherSales, null);

        assertThat(appointmentService.create(request, ACTOR, "SALES").salesId()).isEqualTo(ACTOR);
    }
}
