package com.estatecrm.crm_service.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.estatecrm.crm_service.dto.LeaderboardDtos.SalesLeaderboardSummary;
import com.estatecrm.crm_service.dto.SalesPolicyDtos.DiscountCalculateRequest;
import com.estatecrm.crm_service.dto.SalesPolicyDtos.DiscountCalculateResponse;
import com.estatecrm.crm_service.dto.SalesPolicyDtos.PolicyOption;
import com.estatecrm.crm_service.service.SalesLeaderboardService;
import com.estatecrm.crm_service.service.SalesPolicyService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SalesPolicyAndLeaderboardControllerTests {

    @Mock
    private SalesPolicyService salesPolicyService;

    @Mock
    private SalesLeaderboardService leaderboardService;

    private SalesPolicyController policyController;
    private SalesLeaderboardController leaderboardController;

    @BeforeEach
    void setUp() {
        policyController = new SalesPolicyController(salesPolicyService);
        leaderboardController = new SalesLeaderboardController(leaderboardService);
    }

    @Test
    void getAvailablePolicies_delegatesToService() {
        when(salesPolicyService.getAvailablePolicies()).thenReturn(List.of(
                new PolicyOption("STANDARD", "Tiến độ chuẩn", "Mô tả", BigDecimal.ZERO, "Tiến độ")
        ));

        List<PolicyOption> res = policyController.getAvailablePolicies();
        assertThat(res).hasSize(1);
        assertThat(res.get(0).id()).isEqualTo("STANDARD");
        verify(salesPolicyService).getAvailablePolicies();
    }

    @Test
    void calculateDiscount_delegatesToService() {
        DiscountCalculateRequest req = new DiscountCalculateRequest(
                new BigDecimal("3000000000"),
                "FAST_95",
                1,
                false,
                BigDecimal.ZERO
        );
        DiscountCalculateResponse mockRes = new DiscountCalculateResponse(
                new BigDecimal("3000000000"),
                new BigDecimal("6.0"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                new BigDecimal("6.0"),
                new BigDecimal("180000000"),
                BigDecimal.ZERO,
                new BigDecimal("180000000"),
                new BigDecimal("2820000000"),
                "Thanh toán nhanh",
                List.of()
        );

        when(salesPolicyService.calculateDiscount(any())).thenReturn(mockRes);

        DiscountCalculateResponse res = policyController.calculateDiscount(req);
        assertThat(res).isNotNull();
        assertThat(res.netContractValue()).isEqualTo(new BigDecimal("2820000000"));
        verify(salesPolicyService).calculateDiscount(any());
    }

    @Test
    void getLeaderboard_delegatesToService() {
        SalesLeaderboardSummary mockSummary = new SalesLeaderboardSummary(
                "MONTH",
                2026,
                10,
                new BigDecimal("30000000000"),
                new BigDecimal("15000000000"),
                new BigDecimal("50.0"),
                5,
                List.of()
        );

        when(leaderboardService.getLeaderboard(eq("MONTH"), eq(2026), eq(10), any())).thenReturn(mockSummary);

        SalesLeaderboardSummary res = leaderboardController.getLeaderboard("MONTH", 2026, 10);
        assertThat(res).isNotNull();
        assertThat(res.teamPacingRate()).isEqualTo(new BigDecimal("50.0"));
        verify(leaderboardService).getLeaderboard(eq("MONTH"), eq(2026), eq(10), any());
    }
}
