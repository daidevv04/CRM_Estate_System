package com.estatecrm.customer_service.service;

import com.estatecrm.customer_service.dto.CustomerCareResponse;
import com.estatecrm.customer_service.entity.Customer;
import com.estatecrm.customer_service.entity.CustomerCare;
import com.estatecrm.customer_service.enums.CareType;
import com.estatecrm.customer_service.repository.CustomerCareRepository;
import java.time.LocalDateTime;
import java.util.List;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerCareServiceTests {

    private static final UUID ADMIN_ID = UUID.randomUUID();
    private static final UUID MANAGER_ID = UUID.randomUUID();
    private static final UUID SALES_ID = UUID.randomUUID();

    @Mock
    private CustomerCareRepository customerCareRepository;

    @Mock
    private CustomerService customerService;

    @Mock
    private CrmClient crmClient;

    private CustomerCareService customerCareService;

    @BeforeEach
    void setUp() {
        customerCareService = new CustomerCareService(
                customerCareRepository,
                customerService,
                crmClient);
    }

    private CustomerCare createCare(UUID ownerId, String customerName) {
        Customer customer = new Customer();
        customer.setId(UUID.randomUUID());
        customer.setFullName(customerName);
        customer.setPhone("0901234567");
        customer.setOwnerId(ownerId);

        CustomerCare care = new CustomerCare();
        care.setId(UUID.randomUUID());
        care.setCustomer(customer);
        care.setType(CareType.CALL);
        care.setContent("Tư vấn dự án Regal Legend");
        care.setCreatedBy(ownerId);
        care.setCreatedAt(LocalDateTime.now());
        return care;
    }

    @Test
    @DisplayName("listAll for ADMIN queries with ownerId filter")
    void listAll_admin_queriesSuccessfully() {
        PageRequest pageable = PageRequest.of(0, 10);
        CustomerCare care = createCare(SALES_ID, "Nguyen Van A");
        Page<CustomerCare> mockPage = new PageImpl<>(List.of(care), pageable, 1);

        when(customerCareRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(mockPage);

        Page<CustomerCareResponse> result = customerCareService.listAll(
                ADMIN_ID, "ADMIN", SALES_ID, CareType.CALL, "Regal", pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).customerName()).isEqualTo("Nguyen Van A");
        assertThat(result.getContent().get(0).type()).isEqualTo(CareType.CALL);
    }

    @Test
    @DisplayName("listAll for MANAGER with empty assigned projects returns empty page")
    void listAll_managerNoAssignedProjects_returnsEmpty() {
        PageRequest pageable = PageRequest.of(0, 10);
        when(crmClient.getManagedUserIds(MANAGER_ID)).thenReturn(List.of());

        Page<CustomerCareResponse> result = customerCareService.listAll(
                MANAGER_ID, "MANAGER", null, null, null, pageable);

        assertThat(result.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("listAll for MANAGER with assigned projects queries successfully")
    void listAll_managerWithAssignedProjects_returnsPage() {
        PageRequest pageable = PageRequest.of(0, 10);
        CustomerCare care = createCare(SALES_ID, "Tran Thi B");
        Page<CustomerCare> mockPage = new PageImpl<>(List.of(care), pageable, 1);

        when(crmClient.getManagedUserIds(MANAGER_ID)).thenReturn(List.of(SALES_ID));
        when(customerCareRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(mockPage);

        Page<CustomerCareResponse> result = customerCareService.listAll(
                MANAGER_ID, "MANAGER", SALES_ID, null, null, pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).customerName()).isEqualTo("Tran Thi B");
    }

    @Test
    @DisplayName("listAll for SALES scopes to actorId")
    void listAll_sales_scopesToActorId() {
        PageRequest pageable = PageRequest.of(0, 10);
        CustomerCare care = createCare(SALES_ID, "Le Van C");
        Page<CustomerCare> mockPage = new PageImpl<>(List.of(care), pageable, 1);

        when(customerCareRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(mockPage);

        Page<CustomerCareResponse> result = customerCareService.listAll(
                SALES_ID, "SALES", null, CareType.NOTE, "Ghi chú", pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).customerName()).isEqualTo("Le Van C");
    }
}
