package com.estatecrm.customer_service.dto;

import com.estatecrm.customer_service.entity.CustomerCare;
import com.estatecrm.customer_service.enums.CareType;
import java.time.LocalDateTime;
import java.util.UUID;

public record CustomerCareResponse(
        UUID id,
        UUID customerId,
        CareType type,
        String content,
        UUID createdBy,
        LocalDateTime createdAt) {

    public static CustomerCareResponse from(CustomerCare care) {
        return new CustomerCareResponse(
                care.getId(),
                care.getCustomer().getId(),
                care.getType(),
                care.getContent(),
                care.getCreatedBy(),
                care.getCreatedAt());
    }
}
