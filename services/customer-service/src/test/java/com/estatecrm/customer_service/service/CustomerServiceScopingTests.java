package com.estatecrm.customer_service.service;

import com.estatecrm.customer_service.entity.Customer;
import com.estatecrm.customer_service.repository.AppointmentRepository;
import com.estatecrm.customer_service.repository.CustomerCareRepository;
import com.estatecrm.customer_service.repository.CustomerRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerServiceScopingTests {

    private static final UUID MANAGER_ID = UUID.randomUUID();
    private static final UUID SALES_ID = UUID.randomUUID();
    private static final UUID OTHER_SALES_ID = UUID.randomUUID();

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private CustomerCareRepository customerCareRepository;

    @Mock
    private AppointmentRepository appointmentRepository;

    @Mock
    private CrmClient crmClient;

    private CustomerService customerService;

    @BeforeEach
    void setUp() {
        customerService = new CustomerService(
                customerRepository,
                customerCareRepository,
                appointmentRepository,
                crmClient);
    }

    @Test
    @DisplayName("list when manager has no assigned projects returns empty page")
    void list_whenManagerHasNoAssignedProjects_returnsEmpty() {
        when(crmClient.getManagedUserIds(MANAGER_ID)).thenReturn(List.of());

        Page<?> page = customerService.list(
                MANAGER_ID, "MANAGER", null, null, null, null, null, PageRequest.of(0, 10));

        assertThat(page.getContent()).isEmpty();
    }

    @Test
    @DisplayName("list when manager manages a project filters by allowed sales IDs")
    void list_whenManagerHasAssignedProjects_filtersByAllowedSales() {
        when(crmClient.getManagedUserIds(MANAGER_ID)).thenReturn(List.of(MANAGER_ID, SALES_ID));

        Customer c1 = new Customer();
        c1.setId(UUID.randomUUID());
        c1.setFullName("Nguyen Van A");
        c1.setOwnerId(SALES_ID);

        when(customerRepository.findAll(any(Specification.class), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(c1)));

        Page<?> page = customerService.list(
                MANAGER_ID, "MANAGER", null, null, null, null, null, PageRequest.of(0, 10));

        assertThat(page.getContent()).hasSize(1);
    }

    @Test
    @DisplayName("requireAccess when manager accesses customer of unmanaged sales throws Forbidden")
    void requireAccess_whenManagerAccessesUnmanagedCustomer_throwsForbidden() {
        UUID customerId = UUID.randomUUID();
        Customer customer = new Customer();
        customer.setId(customerId);
        customer.setOwnerId(OTHER_SALES_ID);

        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(crmClient.getManagedUserIds(MANAGER_ID)).thenReturn(List.of(MANAGER_ID, SALES_ID));

        assertThatThrownBy(() -> customerService.requireAccess(customerId, MANAGER_ID, "MANAGER"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Customer is not within your managed project scope");
    }

    @Test
    @DisplayName("requireAccess when manager accesses customer of managed sales succeeds")
    void requireAccess_whenManagerAccessesManagedCustomer_succeeds() {
        UUID customerId = UUID.randomUUID();
        Customer customer = new Customer();
        customer.setId(customerId);
        customer.setOwnerId(SALES_ID);

        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(crmClient.getManagedUserIds(MANAGER_ID)).thenReturn(List.of(MANAGER_ID, SALES_ID));

        Customer result = customerService.requireAccess(customerId, MANAGER_ID, "MANAGER");
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(customerId);
    }

    @Test
    @DisplayName("requireAccess when sales accesses another sales customer throws Forbidden")
    void requireAccess_whenSalesAccessesOtherCustomer_throwsForbidden() {
        UUID customerId = UUID.randomUUID();
        Customer customer = new Customer();
        customer.setId(customerId);
        customer.setOwnerId(OTHER_SALES_ID);

        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));

        assertThatThrownBy(() -> customerService.requireAccess(customerId, SALES_ID, "SALES"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Customer is not assigned to you");
    }
}
