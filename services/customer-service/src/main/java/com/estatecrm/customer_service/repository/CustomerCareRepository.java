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

    /** Dem nhat ky cua khach, de chan xoa khach khi con du lieu con. */
    long countByCustomerId(UUID customerId);

    /** Loc theo khach hang, type null = lay tat ca. */
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
}
