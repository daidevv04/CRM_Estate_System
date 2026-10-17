package com.estatecrm.customer_service.dto;

import com.estatecrm.customer_service.entity.CustomerCare;
import com.estatecrm.customer_service.enums.CareType;
import java.time.LocalDateTime;
import java.util.UUID;

public record CustomerCareResponse(
        UUID id,
        UUID customerId,
        String customerName,
        String customerPhone,
        CareType type,
        String content,
        UUID createdBy,
        LocalDateTime createdAt) {

    public static CustomerCareResponse from(CustomerCare care) {
        return new CustomerCareResponse(
                care.getId(),
                care.getCustomer() != null ? care.getCustomer().getId() : null,
                care.getCustomer() != null ? care.getCustomer().getFullName() : null,
                care.getCustomer() != null ? care.getCustomer().getPhone() : null,
                care.getType(),
                care.getContent(),
                care.getCreatedBy(),
                care.getCreatedAt());
    }
}
