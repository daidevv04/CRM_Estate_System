package com.estatecrm.customer_service.service;

import com.estatecrm.customer_service.dto.CreateCustomerRequest;
import com.estatecrm.customer_service.dto.CustomerResponse;
import com.estatecrm.customer_service.dto.UpdateCustomerRequest;
import com.estatecrm.customer_service.entity.Customer;
import com.estatecrm.customer_service.enums.CustomerStatus;
import com.estatecrm.customer_service.enums.DemandType;
import com.estatecrm.customer_service.exception.ConflictException;
import com.estatecrm.customer_service.repository.AppointmentRepository;
import com.estatecrm.customer_service.repository.CustomerCareRepository;
import com.estatecrm.customer_service.repository.CustomerRepository;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class CustomerService {

    private static final String ADMIN = "ADMIN";
    private static final String MANAGER = "MANAGER";

    private final CustomerRepository customerRepository;
    private final CustomerCareRepository customerCareRepository;
    private final AppointmentRepository appointmentRepository;
    private final CrmClient crmClient;

    public CustomerService(
            CustomerRepository customerRepository,
            CustomerCareRepository customerCareRepository,
            AppointmentRepository appointmentRepository,
            CrmClient crmClient) {
        this.customerRepository = customerRepository;
        this.customerCareRepository = customerCareRepository;
        this.appointmentRepository = appointmentRepository;
        this.crmClient = crmClient;
    }

    @Transactional
    public CustomerResponse create(CreateCustomerRequest request, UUID actorId, String role) {
        String email = normalizeEmail(request.email());
        if (request.phone() != null && customerRepository.existsByPhone(request.phone())) {
            throw new ConflictException("Phone already exists");
        }
        if (email != null && customerRepository.existsByEmail(email)) {
            throw new ConflictException("Email already exists");
        }
        Customer customer = new Customer();
        customer.setFullName(request.fullName());
        customer.setPhone(request.phone());
        customer.setEmail(email);
        if (request.demandType() != null) {
            customer.setDemandType(request.demandType());
        }
        customer.setSource(request.source());
        customer.setStatus(request.status() == null ? CustomerStatus.NEW : request.status());

        UUID ownerId = actorId;
        if (ADMIN.equals(role) && request.ownerId() != null) {
            ownerId = request.ownerId();
        } else if (MANAGER.equals(role) && request.ownerId() != null) {
            List<UUID> allowedOwnerIds = crmClient.getManagedUserIds(actorId);
            if (!allowedOwnerIds.contains(request.ownerId())) {
                throw new ResponseStatusException(FORBIDDEN, "Cannot assign customer to staff outside your managed project scope");
            }
            ownerId = request.ownerId();
        }
        customer.setOwnerId(ownerId);
        customer.setCreatedBy(actorId);
        return CustomerResponse.from(customerRepository.save(customer));
    }

    @Transactional(readOnly = true)
    public Page<CustomerResponse> list(
            UUID actorId,
            String role,
            UUID ownerId,
            CustomerStatus status,
            DemandType demandType,
            String source,
            String keyword,
            Pageable pageable) {
        if (ADMIN.equals(role)) {
            return customerRepository
                    .findAll(CustomerRepository.filter(ownerId, null, status, demandType, source, keyword), pageable)
                    .map(CustomerResponse::from);
        }
        if (MANAGER.equals(role)) {
            List<UUID> allowedOwnerIds = crmClient.getManagedUserIds(actorId);
            if (allowedOwnerIds.isEmpty()) {
                return Page.empty(pageable);
            }
            if (ownerId != null && !allowedOwnerIds.contains(ownerId)) {
                return Page.empty(pageable);
            }
            return customerRepository
                    .findAll(CustomerRepository.filter(ownerId, allowedOwnerIds, status, demandType, source, keyword), pageable)
                    .map(CustomerResponse::from);
        }
        // SALES role
        return customerRepository
                .findAll(CustomerRepository.filter(actorId, null, status, demandType, source, keyword), pageable)
                .map(CustomerResponse::from);
    }

    @Transactional(readOnly = true)
    public CustomerResponse get(UUID customerId, UUID actorId, String role) {
        return CustomerResponse.from(requireAccess(customerId, actorId, role));
    }

    @Transactional
    public CustomerResponse update(
            UUID customerId, UpdateCustomerRequest request, UUID actorId, String role) {
        Customer customer = requireAccess(customerId, actorId, role);
        String email = normalizeEmail(request.email());
        if (request.phone() != null && !request.phone().equals(customer.getPhone())
                && customerRepository.existsByPhone(request.phone())) {
            throw new ConflictException("Phone already exists");
        }
        if (email != null && !email.equals(customer.getEmail())
                && customerRepository.existsByEmail(email)) {
            throw new ConflictException("Email already exists");
        }
        if (request.fullName() != null) {
            customer.setFullName(request.fullName());
        }
        if (request.phone() != null) {
            customer.setPhone(request.phone());
        }
        if (request.email() != null) {
            customer.setEmail(email);
        }
        if (request.demandType() != null) {
            customer.setDemandType(request.demandType());
        }
        if (request.source() != null) {
            customer.setSource(request.source());
        }
        if (request.status() != null) {
            customer.setStatus(request.status());
        }
        if (request.ownerId() != null) {
            if (ADMIN.equals(role)) {
                customer.setOwnerId(request.ownerId());
            } else if (MANAGER.equals(role)) {
                List<UUID> allowedOwnerIds = crmClient.getManagedUserIds(actorId);
                if (!allowedOwnerIds.contains(request.ownerId())) {
                    throw new ResponseStatusException(FORBIDDEN, "Cannot reassign customer to staff outside your managed project scope");
                }
                customer.setOwnerId(request.ownerId());
            }
        }
        customer.setUpdatedBy(actorId);
        return CustomerResponse.from(customerRepository.save(customer));
    }

    @Transactional
    public void delete(UUID customerId, UUID actorId, String role) {
        Customer customer = requireAccess(customerId, actorId, role);
        if (customerCareRepository.countByCustomerId(customerId) > 0) {
            throw new ConflictException("Customer still has care logs; delete them first");
        }
        if (appointmentRepository.countByCustomerId(customerId) > 0) {
            throw new ConflictException("Customer still has appointments; delete them first");
        }
        customerRepository.delete(customer);
    }

    @Transactional(readOnly = true)
    public Customer requireAccess(UUID customerId, UUID actorId, String role) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Customer not found"));
        if (ADMIN.equals(role)) {
            return customer;
        }
        if (MANAGER.equals(role)) {
            List<UUID> allowedOwnerIds = crmClient.getManagedUserIds(actorId);
            if (!allowedOwnerIds.contains(customer.getOwnerId())) {
                throw new ResponseStatusException(FORBIDDEN, "Customer is not within your managed project scope");
            }
            return customer;
        }
        if (!customer.getOwnerId().equals(actorId)) {
            throw new ResponseStatusException(FORBIDDEN, "Customer is not assigned to you");
        }
        return customer;
    }

    public List<UUID> getManagedUserIds(UUID managerId) {
        return crmClient.getManagedUserIds(managerId);
    }

    public boolean isPrivileged(String role) {
        return ADMIN.equals(role) || MANAGER.equals(role);
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
