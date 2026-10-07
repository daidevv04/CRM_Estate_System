package com.estatecrm.crm_service.repository;

import com.estatecrm.crm_service.entity.LeadStageHistory;
import java.util.List;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LeadStageHistoryRepository extends JpaRepository<LeadStageHistory, UUID> {

    List<LeadStageHistory> findByLeadIdOrderByChangedAtAsc(UUID leadId);

    List<LeadStageHistory> findByLeadIdInAndChangedAtGreaterThanEqualAndChangedAtLessThan(
            List<UUID> leadIds, LocalDateTime from, LocalDateTime toExclusive);
}
