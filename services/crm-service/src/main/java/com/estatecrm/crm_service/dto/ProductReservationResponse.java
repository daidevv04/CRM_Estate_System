package com.estatecrm.crm_service.dto;

import com.estatecrm.crm_service.entity.ProductReservation;
import com.estatecrm.crm_service.enums.ReservationStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record ProductReservationResponse(
        UUID id,
        UUID productId,
        UUID customerId,
        UUID salesId,
        Integer priority,
        ReservationStatus status,
        BigDecimal depositAmount,
        LocalDate expiryDate,
        String notes,
        LocalDateTime createdAt) {

    public static ProductReservationResponse from(ProductReservation r) {
        return new ProductReservationResponse(
                r.getId(),
                r.getProductId(),
                r.getCustomerId(),
                r.getSalesId(),
                r.getPriority(),
                r.getStatus(),
                r.getDepositAmount(),
                r.getExpiryDate(),
                r.getNotes(),
                r.getCreatedAt());
    }
}
