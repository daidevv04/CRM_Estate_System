package com.estatecrm.customer_service.repository;

import com.estatecrm.customer_service.entity.EmailCare;
import com.estatecrm.customer_service.enums.EmailCareStatus;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface EmailCareRepository
        extends JpaRepository<EmailCare, UUID>, JpaSpecificationExecutor<EmailCare> {

    /** Dem email cua khach (qua nhat ky), de chan xoa khach khi con du lieu con. */
    long countByCustomerCareCustomerId(UUID customerId);

    /** Loc theo nhat ky cham soc va status. status null = lay tat ca. */
    static Specification<EmailCare> filter(UUID customerCareId, EmailCareStatus status) {
        return (root, query, cb) -> {
            List<Predicate> conditions = new ArrayList<>();
            conditions.add(cb.equal(root.get("customerCare").get("id"), customerCareId));
            if (status != null) {
                conditions.add(cb.equal(root.get("status"), status));
            }
            return cb.and(conditions.toArray(new Predicate[0]));
        };
    }
}
