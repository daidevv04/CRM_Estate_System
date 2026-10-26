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
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LeadRepository extends JpaRepository<Lead, UUID>, JpaSpecificationExecutor<Lead> {

    boolean existsByProductId(UUID productId);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    java.util.Optional<Lead> findWithLockById(UUID leadId);

    List<Lead> findByAssignedTo(UUID assignedTo);

    List<Lead> findByProductIdIn(java.util.Collection<UUID> productIds);

    List<Lead> findByProductIdInAndAssignedTo(java.util.Collection<UUID> productIds, UUID assignedTo);

    @Query("SELECT l.id FROM Lead l WHERE l.productId IN :productIds")
    List<UUID> findIdsByProductIdIn(@Param("productIds") java.util.Collection<UUID> productIds);

    @Query("SELECT l.id FROM Lead l WHERE l.projectId IN :projectIds")
    List<UUID> findIdsByProjectIdIn(@Param("projectIds") java.util.Collection<UUID> projectIds);

    static Specification<Lead> filter(
            UUID customerId,
            UUID productId,
            UUID assignedTo,
            LeadStage stage,
            LocalDate closeDate) {
        return filter(customerId, null, productId, assignedTo, stage, closeDate, null);
    }

    static Specification<Lead> filter(
            UUID customerId,
            UUID projectId,
            UUID productId,
            UUID assignedTo,
            LeadStage stage,
            LocalDate closeDate) {
        return filter(customerId, projectId, productId, assignedTo, stage, closeDate, null);
    }

    static Specification<Lead> filter(
            UUID customerId,
            UUID projectId,
            UUID productId,
            UUID assignedTo,
            LeadStage stage,
            LocalDate closeDate,
            java.util.Collection<UUID> allowedProjectIds) {
        return (root, query, cb) -> {
            List<Predicate> conditions = new ArrayList<>();
            if (allowedProjectIds != null) {
                if (allowedProjectIds.isEmpty()) {
                    return cb.disjunction();
                }
                conditions.add(root.get("projectId").in(allowedProjectIds));
            }
            if (customerId != null) {
                conditions.add(cb.equal(root.get("customerId"), customerId));
            }
            if (projectId != null) {
                conditions.add(cb.equal(root.get("projectId"), projectId));
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
