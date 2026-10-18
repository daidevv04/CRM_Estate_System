package com.estatecrm.crm_service.repository;

import com.estatecrm.crm_service.entity.DealCommissionMilestone;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface DealCommissionMilestoneRepository extends JpaRepository<DealCommissionMilestone, UUID> {

    List<DealCommissionMilestone> findByDealCommissionIdOrderByMilestoneNoAsc(UUID dealCommissionId);

    List<DealCommissionMilestone> findByDealCommissionIdInOrderByMilestoneNoAsc(Collection<UUID> dealCommissionIds);

    @Modifying
    @Query("delete from DealCommissionMilestone m where m.dealCommissionId = :dealCommissionId")
    void deleteByDealCommissionId(UUID dealCommissionId);
}
