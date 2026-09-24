package com.estatecrm.crm_service.repository;

import com.estatecrm.crm_service.entity.DealPayment;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DealPaymentRepository extends JpaRepository<DealPayment, UUID> {

    List<DealPayment> findByDealIdOrderByPaidAtAsc(UUID dealId);

    @Query("select coalesce(sum(p.amount), 0) from DealPayment p where p.dealId = :dealId")
    BigDecimal totalPaidByDealId(@Param("dealId") UUID dealId);
}
