package com.estatecrm.crm_service.dto;

import java.math.BigDecimal;
import java.util.List;

public final class SalesPolicyDtos {
    private SalesPolicyDtos() {}

    public record PolicyOption(
            String id,
            String name,
            String description,
            BigDecimal discountRate,
            String paymentScheduleNote
    ) {}

    public record DiscountCalculateRequest(
            BigDecimal listedPrice,
            String policyId,
            Integer bulkQuantity,
            Boolean isLoyalCustomer,
            BigDecimal customVoucherAmount
    ) {}

    public record DiscountCalculateResponse(
            BigDecimal listedPrice,
            BigDecimal baseDiscountRate,
            BigDecimal bulkDiscountRate,
            BigDecimal loyalDiscountRate,
            BigDecimal totalDiscountRate,
            BigDecimal percentageDiscountAmount,
            BigDecimal voucherDiscountAmount,
            BigDecimal totalDiscountAmount,
            BigDecimal netContractValue,
            String policyName,
            List<String> discountAppliedBreakdowns
    ) {}
}
