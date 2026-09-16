package com.estatecrm.customer_service.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.estatecrm.customer_service.entity.Customer;
import com.estatecrm.customer_service.exception.ConflictException;
import com.estatecrm.customer_service.repository.AppointmentRepository;
import com.estatecrm.customer_service.repository.CustomerCareRepository;
import com.estatecrm.customer_service.repository.CustomerRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CustomerServiceDeleteTests {

    private static final UUID ACTOR = UUID.randomUUID();
    private static final UUID CUSTOMER_ID = UUID.randomUUID();

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private CustomerCareRepository customerCareRepository;

    @Mock
    private AppointmentRepository appointmentRepository;

    @InjectMocks
    private CustomerService customerService;

    @Test
    void blocksDeleteWhenCustomerHasCareLogs() {
        when(customerRepository.findById(CUSTOMER_ID)).thenReturn(Optional.of(customer(ACTOR)));
        when(customerCareRepository.countByCustomerId(CUSTOMER_ID)).thenReturn(1L);

        assertThatThrownBy(() -> customerService.delete(CUSTOMER_ID, ACTOR, "ADMIN"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("care logs");
        verify((org.springframework.data.repository.CrudRepository<Customer, UUID>) customerRepository, never())
                .delete(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void blocksDeleteWhenCustomerHasAppointments() {
        when(customerRepository.findById(CUSTOMER_ID)).thenReturn(Optional.of(customer(ACTOR)));
        when(customerCareRepository.countByCustomerId(CUSTOMER_ID)).thenReturn(0L);
        when(appointmentRepository.countByCustomerId(CUSTOMER_ID)).thenReturn(1L);

        assertThatThrownBy(() -> customerService.delete(CUSTOMER_ID, ACTOR, "ADMIN"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("appointments");
        verify((org.springframework.data.repository.CrudRepository<Customer, UUID>) customerRepository, never())
                .delete(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void deletesWhenNoChildren() {
        Customer customer = customer(ACTOR);
        when(customerRepository.findById(CUSTOMER_ID)).thenReturn(Optional.of(customer));
        when(customerCareRepository.countByCustomerId(CUSTOMER_ID)).thenReturn(0L);
        when(appointmentRepository.countByCustomerId(CUSTOMER_ID)).thenReturn(0L);

        assertThatCode(() -> customerService.delete(CUSTOMER_ID, ACTOR, "ADMIN"))
                .doesNotThrowAnyException();
        verify((org.springframework.data.repository.CrudRepository<Customer, UUID>) customerRepository)
                .delete(customer);
    }

    private Customer customer(UUID ownerId) {
        Customer customer = new Customer();
        customer.setId(CUSTOMER_ID);
        customer.setOwnerId(ownerId);
        return customer;
    }
}
