package com.estatecrm.crm_service.dto;

import com.estatecrm.crm_service.entity.Product;
import com.estatecrm.crm_service.enums.ProductStatus;
import com.estatecrm.crm_service.enums.ProductType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record ProductResponse(
        UUID id,
        UUID projectId,
        String code,
        ProductType type,
        BigDecimal area,
        String block,
        BigDecimal price,
        Integer bedroom,
        String direction,
        String imageUrl,
        ProductStatus status,
        UUID lockedBy,
        UUID lockedCustomerId,
        LocalDate lockExpiresAt,
        BigDecimal lockDepositAmount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static ProductResponse from(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getProjectId(),
                product.getCode(),
                product.getType(),
                product.getArea(),
                product.getBlock(),
                product.getPrice(),
                product.getBedroom(),
                product.getDirection(),
                product.getImageUrl(),
                product.getStatus(),
                product.getLockedBy(),
                product.getLockedCustomerId(),
                product.getLockExpiresAt(),
                product.getLockDepositAmount(),
                product.getCreatedAt(),
                product.getUpdatedAt());
    }
}
