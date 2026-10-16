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

    boolean existsByProductId(UUID productId);

    List<Lead> findByAssignedTo(UUID assignedTo);

    List<Lead> findByProductIdIn(java.util.Collection<UUID> productIds);

    List<Lead> findByProductIdInAndAssignedTo(java.util.Collection<UUID> productIds, UUID assignedTo);

    @org.springframework.data.jpa.repository.Query("SELECT l.id FROM Lead l WHERE l.productId IN :productIds")
    List<UUID> findIdsByProductIdIn(@org.springframework.data.repository.query.Param("productIds") java.util.Collection<UUID> productIds);

    static Specification<Lead> filter(
            UUID customerId,
            UUID productId,
            UUID assignedTo,
            LeadStage stage,
            LocalDate closeDate) {
        return filter(customerId, productId, assignedTo, stage, closeDate, null);
    }

    static Specification<Lead> filter(
            UUID customerId,
            UUID productId,
            UUID assignedTo,
            LeadStage stage,
            LocalDate closeDate,
            java.util.Collection<UUID> allowedProductIds) {
        return (root, query, cb) -> {
            List<Predicate> conditions = new ArrayList<>();
            if (allowedProductIds != null) {
                if (allowedProductIds.isEmpty()) {
                    return cb.disjunction();
                }
                conditions.add(root.get("productId").in(allowedProductIds));
            }
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
