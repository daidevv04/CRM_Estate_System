package com.estatecrm.customer_service.repository;

import com.estatecrm.customer_service.entity.CustomerCare;
import com.estatecrm.customer_service.enums.CareType;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface CustomerCareRepository
        extends JpaRepository<CustomerCare, UUID>, JpaSpecificationExecutor<CustomerCare> {

    long countByCustomerId(UUID customerId);

    static Specification<CustomerCare> filter(UUID customerId, CareType type) {
        return (root, query, cb) -> {
            List<Predicate> conditions = new ArrayList<>();
            conditions.add(cb.equal(root.get("customer").get("id"), customerId));
            if (type != null) {
                conditions.add(cb.equal(root.get("type"), type));
            }
            return cb.and(conditions.toArray(new Predicate[0]));
        };
    }

    static Specification<CustomerCare> filterAll(
            UUID ownerId, java.util.Collection<UUID> allowedOwnerIds, CareType type, String keyword) {
        return (root, query, cb) -> {
            if (query != null && Long.class != query.getResultType() && long.class != query.getResultType()) {
                root.fetch("customer", jakarta.persistence.criteria.JoinType.LEFT);
            }
            List<Predicate> conditions = new ArrayList<>();
            if (allowedOwnerIds != null) {
                conditions.add(root.get("customer").get("ownerId").in(allowedOwnerIds));
            }
            if (ownerId != null) {
                conditions.add(cb.equal(root.get("customer").get("ownerId"), ownerId));
            }
            if (type != null) {
                conditions.add(cb.equal(root.get("type"), type));
            }
            if (keyword != null && !keyword.isBlank()) {
                String pattern = "%" + keyword.trim().toLowerCase(java.util.Locale.ROOT) + "%";
                conditions.add(cb.or(
                        cb.like(cb.lower(root.get("content")), pattern),
                        cb.like(cb.lower(root.get("customer").get("fullName")), pattern),
                        cb.like(cb.lower(root.get("customer").get("phone")), pattern)));
            }
            return cb.and(conditions.toArray(new Predicate[0]));
        };
    }
}
