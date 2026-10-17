package com.estatecrm.crm_service.repository;

import com.estatecrm.crm_service.entity.DealPaymentSchedule;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface DealPaymentScheduleRepository extends JpaRepository<DealPaymentSchedule, UUID> {

    List<DealPaymentSchedule> findByDealIdOrderByInstallmentNoAsc(UUID dealId);

    @Modifying
    @Query("delete from DealPaymentSchedule s where s.dealId = :dealId")
    void deleteByDealId(UUID dealId);
}
