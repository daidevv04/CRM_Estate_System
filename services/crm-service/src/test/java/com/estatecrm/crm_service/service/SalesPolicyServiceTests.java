package com.estatecrm.crm_service.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.estatecrm.crm_service.dto.SalesPolicyDtos.DiscountCalculateRequest;
import com.estatecrm.crm_service.dto.SalesPolicyDtos.DiscountCalculateResponse;
import com.estatecrm.crm_service.dto.SalesPolicyDtos.PolicyOption;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SalesPolicyServiceTests {

    private SalesPolicyService salesPolicyService;

    @BeforeEach
    void setUp() {
        salesPolicyService = new SalesPolicyService();
    }

    @Test
    @DisplayName("Lấy danh sách các gói chính sách bán hàng khả dụng")
    void getAvailablePolicies_shouldReturnDefaultList() {
        List<PolicyOption> policies = salesPolicyService.getAvailablePolicies();
        assertNotNull(policies);
        assertEquals(4, policies.size());
        assertTrue(policies.stream().anyMatch(p -> p.id().equals("FAST_95")));
    }

    @Test
    @DisplayName("Tính chiết khấu gói thanh toán sớm 95% + Mua sỉ 2 lô + Khách cũ + Voucher 50 triệu")
    void calculateDiscount_fullCombination() {
        // Căn hộ 3.5 tỷ = 3.500.000.000
        BigDecimal listedPrice = new BigDecimal("3500000000");

        DiscountCalculateRequest req = new DiscountCalculateRequest(
                listedPrice,
                "FAST_95", // Base discount: 6.0%
                2,          // Bulk: 2 units -> +1.0%
                true,       // Loyal customer -> +1.0%
                new BigDecimal("50000000") // Voucher: 50.000.000đ
        );

        DiscountCalculateResponse res = salesPolicyService.calculateDiscount(req);

        // Total rate = 6.0 + 1.0 + 1.0 = 8.0%
        assertEquals(new BigDecimal("8.0"), res.totalDiscountRate());

        // Percentage discount = 3.500.000.000 * 8% = 280.000.000
        assertEquals(new BigDecimal("280000000"), res.percentageDiscountAmount());

        // Total discount = 280.000.000 + 50.000.000 = 330.000.000
        assertEquals(new BigDecimal("330000000"), res.totalDiscountAmount());

        // Net contract value = 3.500.000.000 - 330.000.000 = 3.170.000.000
        assertEquals(new BigDecimal("3170000000"), res.netContractValue());

        assertEquals(4, res.discountAppliedBreakdowns().size());
    }

    @Test
    @DisplayName("Tính chiết khấu gói tiến độ chuẩn không phụ trội")
    void calculateDiscount_standardNoDiscount() {
        BigDecimal listedPrice = new BigDecimal("2000000000");

        DiscountCalculateRequest req = new DiscountCalculateRequest(
                listedPrice,
                "STANDARD",
                1,
                false,
                BigDecimal.ZERO
        );

        DiscountCalculateResponse res = salesPolicyService.calculateDiscount(req);

        assertEquals(new BigDecimal("0.0"), res.totalDiscountRate());
        assertEquals(BigDecimal.ZERO, res.totalDiscountAmount());
        assertEquals(listedPrice, res.netContractValue());
    }
}
