package com.estatecrm.crm_service.dto;

import com.estatecrm.crm_service.entity.ContactDetail;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record ContactDetailResponse(
        UUID id,
        UUID dealId,
        UUID productId,
        BigDecimal unitPrice,
        Integer quantity,
        String note,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static ContactDetailResponse from(ContactDetail detail) {
        return new ContactDetailResponse(
                detail.getId(),
                detail.getDealId(),
                detail.getProductId(),
                detail.getUnitPrice(),
                detail.getQuantity(),
                detail.getNote(),
                detail.getCreatedAt(),
                detail.getUpdatedAt());
    }
}
