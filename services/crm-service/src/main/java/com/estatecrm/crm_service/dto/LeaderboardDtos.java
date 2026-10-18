package com.estatecrm.crm_service.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public final class LeaderboardDtos {
    private LeaderboardDtos() {}

    public enum LeaderboardBadge {
        GOLD,
        SILVER,
        BRONZE,
        STANDARD
    }

    public record SalesLeaderboardItem(
            int rank,
            UUID salesId,
            String salesName,
            long dealsCount,
            BigDecimal totalRevenue,
            BigDecimal totalCommission,
            LeaderboardBadge badge
    ) {}

    public record SalesLeaderboardSummary(
            String period,
            int year,
            Integer month,
            BigDecimal teamTargetRevenue,
            BigDecimal teamAchievedRevenue,
            BigDecimal teamPacingRate,
            long totalDealsCount,
            List<SalesLeaderboardItem> items
    ) {}
}
