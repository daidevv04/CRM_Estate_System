package com.estatecrm.customer_service.dto;

import com.estatecrm.customer_service.entity.Customer;
import com.estatecrm.customer_service.enums.CustomerStatus;
import com.estatecrm.customer_service.enums.DemandType;
import java.time.LocalDateTime;
import java.util.UUID;

public record CustomerResponse(
        UUID id,
        String fullName,
        String phone,
        String email,
        DemandType demandType,
        String source,
        UUID ownerId,
        CustomerStatus status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static CustomerResponse from(Customer customer) {
        return new CustomerResponse(
                customer.getId(),
                customer.getFullName(),
                customer.getPhone(),
                customer.getEmail(),
                customer.getDemandType(),
                customer.getSource(),
                customer.getOwnerId(),
                customer.getStatus(),
                customer.getCreatedAt(),
                customer.getUpdatedAt());
    }
}
