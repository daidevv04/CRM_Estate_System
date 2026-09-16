package com.estatecrm.crm_service.repository;

import com.estatecrm.crm_service.entity.Lead;
import com.estatecrm.crm_service.enums.LeadStage;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface LeadRepository extends JpaRepository<Lead, UUID>, JpaSpecificationExecutor<Lead> {

    /**
     * Loc dong cho GET /leads. Tham so null/rong = bo qua dieu kien do.
     * closeDate = han chot muon nhat, tra ve ca lead khong dat han.
     */
    static Specification<Lead> filter(
            UUID customerId,
            UUID productId,
            UUID assignedTo,
            LeadStage stage,
            LocalDate closeDate) {
        return (root, query, cb) -> {
            List<Predicate> conditions = new ArrayList<>();
            if (customerId != null) {
                conditions.add(cb.equal(root.get("customerId"), customerId));
            }
            if (productId != null) {
                conditions.add(cb.equal(root.get("productId"), productId));
            }
            if (assignedTo != null) {
                conditions.add(cb.equal(root.get("assignedTo"), assignedTo));
            }
            if (stage != null) {
                conditions.add(cb.equal(root.get("stage"), stage));
            }
            if (closeDate != null) {
                conditions.add(cb.or(
                        cb.lessThanOrEqualTo(root.get("closeDate"), closeDate),
                        cb.isNull(root.get("closeDate"))));
            }
            return cb.and(conditions.toArray(new Predicate[0]));
        };
    }
}
