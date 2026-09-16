package com.estatecrm.crm_service.repository;

import com.estatecrm.crm_service.entity.Deal;
import com.estatecrm.crm_service.enums.ApprovalStatus;
import com.estatecrm.crm_service.enums.DealStatus;
import com.estatecrm.crm_service.enums.PaymentStatus;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface DealRepository extends JpaRepository<Deal, UUID>, JpaSpecificationExecutor<Deal> {

    boolean existsByContractCode(String contractCode);

    boolean existsByLeadId(UUID leadId);

    /** Loc dong cho GET /deals. Tham so null/rong = bo qua dieu kien do. */
    static Specification<Deal> filter(
            UUID leadId,
            UUID salesId,
            DealStatus status,
            PaymentStatus paymentStatus,
            ApprovalStatus approvalStatus) {
        return (root, query, cb) -> {
            List<Predicate> conditions = new ArrayList<>();
            if (leadId != null) {
                conditions.add(cb.equal(root.get("leadId"), leadId));
            }
            if (salesId != null) {
                conditions.add(cb.equal(root.get("salesId"), salesId));
            }
            if (status != null) {
                conditions.add(cb.equal(root.get("status"), status));
            }
            if (paymentStatus != null) {
                conditions.add(cb.equal(root.get("paymentStatus"), paymentStatus));
            }
            if (approvalStatus != null) {
                conditions.add(cb.equal(root.get("approvalStatus"), approvalStatus));
            }
            return cb.and(conditions.toArray(new Predicate[0]));
        };
    }
}
