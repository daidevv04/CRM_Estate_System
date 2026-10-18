package com.estatecrm.crm_service.service;

import com.estatecrm.crm_service.dto.LeaderboardDtos.LeaderboardBadge;
import com.estatecrm.crm_service.dto.LeaderboardDtos.SalesLeaderboardItem;
import com.estatecrm.crm_service.dto.LeaderboardDtos.SalesLeaderboardSummary;
import com.estatecrm.crm_service.entity.Deal;
import com.estatecrm.crm_service.entity.DealCommission;
import com.estatecrm.crm_service.enums.DealStatus;
import com.estatecrm.crm_service.repository.DealCommissionRepository;
import com.estatecrm.crm_service.repository.DealRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class SalesLeaderboardService {

    private final DealRepository dealRepository;
    private final DealCommissionRepository commissionRepository;

    private static final BigDecimal DEFAULT_MONTHLY_TARGET = new BigDecimal("30000000000"); // 30 tỷ

    public SalesLeaderboardService(
            DealRepository dealRepository,
            DealCommissionRepository commissionRepository) {
        this.dealRepository = dealRepository;
        this.commissionRepository = commissionRepository;
    }

    public SalesLeaderboardSummary getLeaderboard(String period, Integer year, Integer month, Map<UUID, String> staffNames) {
        String safePeriod = (period != null && !period.isBlank()) ? period.toUpperCase().trim() : "MONTH";
        int safeYear = (year != null && year > 2000) ? year : LocalDate.now().getYear();
        Integer safeMonth = month;
        if ("MONTH".equals(safePeriod) && safeMonth == null) {
            safeMonth = LocalDate.now().getMonthValue();
        }

        List<Deal> allDeals = dealRepository.findAll();
        List<DealCommission> allCommissions = commissionRepository.findAll();

        Map<UUID, BigDecimal> commissionBySales = new HashMap<>();
        for (DealCommission comm : allCommissions) {
            if (comm.getSalesId() != null && comm.getCommissionAmount() != null) {
                commissionBySales.merge(comm.getSalesId(), comm.getCommissionAmount(), BigDecimal::add);
            }
        }

        Integer finalSafeMonth = safeMonth;
        List<Deal> filteredDeals = allDeals.stream()
                .filter(d -> d.getStatus() != DealStatus.CANCELLED)
                .filter(d -> matchPeriod(d, safePeriod, safeYear, finalSafeMonth))
                .toList();

        Map<UUID, List<Deal>> dealsBySales = new HashMap<>();
        for (Deal d : filteredDeals) {
            if (d.getSalesId() != null) {
                dealsBySales.computeIfAbsent(d.getSalesId(), k -> new ArrayList<>()).add(d);
            }
        }

        BigDecimal totalAchievedRevenue = BigDecimal.ZERO;
        long totalDealsCount = filteredDeals.size();

        class SalesAggregate {
            final UUID salesId;
            long count = 0;
            BigDecimal revenue = BigDecimal.ZERO;
            BigDecimal commission = BigDecimal.ZERO;

            SalesAggregate(UUID salesId) {
                this.salesId = salesId;
            }
        }

        List<SalesAggregate> aggregates = new ArrayList<>();
        for (Map.Entry<UUID, List<Deal>> entry : dealsBySales.entrySet()) {
            UUID salesId = entry.getKey();
            List<Deal> deals = entry.getValue();

            SalesAggregate agg = new SalesAggregate(salesId);
            agg.count = deals.size();
            for (Deal d : deals) {
                BigDecimal val = d.getContractValue() != null ? d.getContractValue() : (d.getDepositAmount() != null ? d.getDepositAmount() : BigDecimal.ZERO);
                agg.revenue = agg.revenue.add(val);
                totalAchievedRevenue = totalAchievedRevenue.add(val);
            }
            agg.commission = commissionBySales.getOrDefault(salesId, BigDecimal.ZERO);
            aggregates.add(agg);
        }

        aggregates.sort(
                Comparator.comparing((SalesAggregate a) -> a.revenue)
                        .reversed()
                        .thenComparing(Comparator.comparing((SalesAggregate a) -> a.count).reversed())
        );

        List<SalesLeaderboardItem> items = new ArrayList<>();
        int rank = 1;
        for (SalesAggregate agg : aggregates) {
            LeaderboardBadge badge = switch (rank) {
                case 1 -> LeaderboardBadge.GOLD;
                case 2 -> LeaderboardBadge.SILVER;
                case 3 -> LeaderboardBadge.BRONZE;
                default -> LeaderboardBadge.STANDARD;
            };

            String name = null;
            if (staffNames != null) {
                name = staffNames.get(agg.salesId);
            }
            if (name == null || name.isBlank()) {
                name = "Sales " + agg.salesId.toString().substring(0, 8);
            }

            items.add(new SalesLeaderboardItem(
                    rank,
                    agg.salesId,
                    name,
                    agg.count,
                    agg.revenue,
                    agg.commission,
                    badge
            ));
            rank++;
        }

        BigDecimal pacingRate = BigDecimal.ZERO;
        if (DEFAULT_MONTHLY_TARGET.compareTo(BigDecimal.ZERO) > 0) {
            pacingRate = totalAchievedRevenue
                    .multiply(new BigDecimal("100"))
                    .divide(DEFAULT_MONTHLY_TARGET, 1, RoundingMode.HALF_UP);
        }

        return new SalesLeaderboardSummary(
                safePeriod,
                safeYear,
                safeMonth,
                DEFAULT_MONTHLY_TARGET,
                totalAchievedRevenue,
                pacingRate,
                totalDealsCount,
                items
        );
    }

    private boolean matchPeriod(Deal d, String period, int year, Integer month) {
        LocalDate date = d.getSignedDate() != null ? d.getSignedDate() : (d.getDepositDate() != null ? d.getDepositDate() : (d.getCreatedAt() != null ? d.getCreatedAt().toLocalDate() : null));
        if (date == null) {
            return true; // include if no explicit date
        }

        if (date.getYear() != year) {
            return false;
        }

        if ("MONTH".equals(period) && month != null) {
            return date.getMonthValue() == month;
        }

        if ("QUARTER".equals(period) && month != null) {
            int targetQuarter = (month - 1) / 3 + 1;
            int dealQuarter = (date.getMonthValue() - 1) / 3 + 1;
            return dealQuarter == targetQuarter;
        }

        return true;
    }
}
