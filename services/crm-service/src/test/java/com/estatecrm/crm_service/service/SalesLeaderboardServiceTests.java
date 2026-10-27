package com.estatecrm.crm_service.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

import com.estatecrm.crm_service.dto.LeaderboardDtos.LeaderboardBadge;
import com.estatecrm.crm_service.dto.LeaderboardDtos.SalesLeaderboardSummary;
import com.estatecrm.crm_service.entity.Deal;
import com.estatecrm.crm_service.entity.DealCommission;
import com.estatecrm.crm_service.enums.DealStatus;
import com.estatecrm.crm_service.repository.DealCommissionRepository;
import com.estatecrm.crm_service.repository.DealRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SalesLeaderboardServiceTests {

    @Mock
    private DealRepository dealRepository;

    @Mock
    private DealCommissionRepository commissionRepository;

    private SalesLeaderboardService leaderboardService;

    @BeforeEach
    void setUp() {
        leaderboardService = new SalesLeaderboardService(dealRepository, commissionRepository);
    }

    @Test
    void leaderboardIncludesCommissionWithLegacyApprovedStatus() {
        UUID salesId = UUID.randomUUID();
        Deal deal = new Deal();
        deal.setSalesId(salesId);
        deal.setStatus(DealStatus.ACTIVE);
        deal.setContractValue(new BigDecimal("3500000000"));
        DealCommission commission = new DealCommission();
        commission.setSalesId(salesId);
        commission.setStatus(new org.hibernate.type.descriptor.java.EnumJavaType<>(
                com.estatecrm.crm_service.enums.CommissionStatus.class).fromName("APPROVED"));
        commission.setCommissionAmount(new BigDecimal("87500000"));
        when(dealRepository.findAll()).thenReturn(List.of(deal));
        when(commissionRepository.findAll()).thenReturn(List.of(commission));

        var result = leaderboardService.getLeaderboard("ALL", 2026, null, Map.of(salesId, "Sales legacy"));
        assertEquals(1, result.items().size());
        assertEquals(new BigDecimal("87500000"), result.items().get(0).totalCommission());
        assertEquals("APPROVED", commission.getStatus().name());
    }

    @Test
    @DisplayName("Tổng hợp bảng xếp hạng và tính rank Gold/Silver chuẩn doanh số")
    void getLeaderboard_rankingsAndPacing() {
        UUID sales1 = UUID.randomUUID();
        UUID sales2 = UUID.randomUUID();

        // Deal 1: Sales 1 - 7.000.000.000đ
        Deal d1 = new Deal();
        d1.setId(UUID.randomUUID());
        d1.setSalesId(sales1);
        d1.setContractValue(new BigDecimal("7000000000"));
        d1.setStatus(DealStatus.ACTIVE);
        d1.setSignedDate(LocalDate.of(2026, 10, 5));

        // Deal 2: Sales 1 - 3.500.000.000đ
        Deal d2 = new Deal();
        d2.setId(UUID.randomUUID());
        d2.setSalesId(sales1);
        d2.setContractValue(new BigDecimal("3500000000"));
        d2.setStatus(DealStatus.ACTIVE);
        d2.setSignedDate(LocalDate.of(2026, 10, 12));

        // Deal 3: Sales 2 - 5.000.000.000đ
        Deal d3 = new Deal();
        d3.setId(UUID.randomUUID());
        d3.setSalesId(sales2);
        d3.setContractValue(new BigDecimal("5000000000"));
        d3.setStatus(DealStatus.ACTIVE);
        d3.setSignedDate(LocalDate.of(2026, 10, 15));

        when(dealRepository.findAll()).thenReturn(List.of(d1, d2, d3));

        DealCommission comm1 = new DealCommission();
        comm1.setSalesId(sales1);
        comm1.setCommissionAmount(new BigDecimal("210000000"));

        DealCommission comm2 = new DealCommission();
        comm2.setSalesId(sales2);
        comm2.setCommissionAmount(new BigDecimal("100000000"));

        when(commissionRepository.findAll()).thenReturn(List.of(comm1, comm2));

        Map<UUID, String> names = Map.of(
                sales1, "Nguyễn Văn Top 1",
                sales2, "Trần Văn Top 2"
        );

        SalesLeaderboardSummary summary = leaderboardService.getLeaderboard("MONTH", 2026, 10, names);

        assertNotNull(summary);
        assertEquals(2, summary.items().size());

        // Sales 1 total = 7B + 3.5B = 10.5B -> Rank 1 (GOLD)
        assertEquals("Nguyễn Văn Top 1", summary.items().get(0).salesName());
        assertEquals(1, summary.items().get(0).rank());
        assertEquals(LeaderboardBadge.GOLD, summary.items().get(0).badge());
        assertEquals(2, summary.items().get(0).dealsCount());
        assertEquals(new BigDecimal("10500000000"), summary.items().get(0).totalRevenue());
        assertEquals(new BigDecimal("210000000"), summary.items().get(0).totalCommission());

        // Sales 2 total = 5B -> Rank 2 (SILVER)
        assertEquals("Trần Văn Top 2", summary.items().get(1).salesName());
        assertEquals(2, summary.items().get(1).rank());
        assertEquals(LeaderboardBadge.SILVER, summary.items().get(1).badge());
        assertEquals(1, summary.items().get(1).dealsCount());

        // Total team revenue = 15.5B, target = 30B -> pacing = 51.7%
        assertEquals(new BigDecimal("15500000000"), summary.teamAchievedRevenue());
        assertEquals(new BigDecimal("51.7"), summary.teamPacingRate());
    }
}
