package com.estatecrm.customer_service.service;

import com.estatecrm.customer_service.dto.CreateCustomerCareRequest;
import com.estatecrm.customer_service.dto.CustomerCareResponse;
import com.estatecrm.customer_service.entity.Customer;
import com.estatecrm.customer_service.entity.CustomerCare;
import com.estatecrm.customer_service.enums.CareType;
import com.estatecrm.customer_service.repository.CustomerCareRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class CustomerCareService {

    private static final String ADMIN = "ADMIN";
    private static final String MANAGER = "MANAGER";

    private final CustomerCareRepository customerCareRepository;
    private final CustomerService customerService;
    private final CrmClient crmClient;

    public CustomerCareService(
            CustomerCareRepository customerCareRepository,
            CustomerService customerService,
            CrmClient crmClient) {
        this.customerCareRepository = customerCareRepository;
        this.customerService = customerService;
        this.crmClient = crmClient;
    }

    @Transactional
    public CustomerCareResponse create(
            UUID customerId, CreateCustomerCareRequest request, UUID actorId, String role) {
        Customer customer = customerService.requireAccess(customerId, actorId, role);
        CustomerCare care = new CustomerCare();
        care.setCustomer(customer);
        care.setType(request.type());
        care.setContent(request.content());
        care.setCreatedBy(actorId);
        return CustomerCareResponse.from(customerCareRepository.save(care));
    }

    @Transactional(readOnly = true)
    public Page<CustomerCareResponse> list(
            UUID customerId, CareType type, UUID actorId, String role, Pageable pageable) {
        customerService.requireAccess(customerId, actorId, role);
        return customerCareRepository
                .findAll(CustomerCareRepository.filter(customerId, type), pageable)
                .map(CustomerCareResponse::from);
    }

    @Transactional(readOnly = true)
    public Page<CustomerCareResponse> listAll(
            UUID actorId, String role, UUID ownerId, CareType type, String keyword, Pageable pageable) {
        if (ADMIN.equals(role)) {
            return customerCareRepository
                    .findAll(CustomerCareRepository.filterAll(ownerId, null, type, keyword), pageable)
                    .map(CustomerCareResponse::from);
        }
        if (MANAGER.equals(role)) {
            List<UUID> allowedOwnerIds = crmClient.getManagedUserIds(actorId);
            if (allowedOwnerIds.isEmpty()) {
                return Page.empty(pageable);
            }
            if (ownerId != null && !allowedOwnerIds.contains(ownerId)) {
                return Page.empty(pageable);
            }
            return customerCareRepository
                    .findAll(CustomerCareRepository.filterAll(ownerId, allowedOwnerIds, type, keyword), pageable)
                    .map(CustomerCareResponse::from);
        }
        return customerCareRepository
                .findAll(CustomerCareRepository.filterAll(actorId, null, type, keyword), pageable)
                .map(CustomerCareResponse::from);
    }

    @Transactional
    public void delete(UUID customerId, UUID careId, UUID actorId, String role) {
        customerService.requireAccess(customerId, actorId, role);
        CustomerCare care = customerCareRepository.findById(careId)
                .filter(item -> item.getCustomer().getId().equals(customerId))
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Customer care not found"));
        customerCareRepository.delete(care);
    }
}
