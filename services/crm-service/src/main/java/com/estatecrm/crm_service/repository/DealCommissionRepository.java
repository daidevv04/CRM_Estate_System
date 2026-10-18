package com.estatecrm.crm_service.repository;

import com.estatecrm.crm_service.entity.DealCommission;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DealCommissionRepository extends JpaRepository<DealCommission, UUID> {

    Optional<DealCommission> findByDealId(UUID dealId);

    List<DealCommission> findBySalesIdOrderByCreatedAtDesc(UUID salesId);

    List<DealCommission> findAllByOrderByCreatedAtDesc();
}
