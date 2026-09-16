package com.estatecrm.customer_service.repository;

import com.estatecrm.customer_service.entity.Customer;
import com.estatecrm.customer_service.enums.CustomerStatus;
import com.estatecrm.customer_service.enums.DemandType;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface CustomerRepository
        extends JpaRepository<Customer, UUID>, JpaSpecificationExecutor<Customer> {

    boolean existsByPhone(String phone);

    boolean existsByEmail(String email);

    /**
     * Loc dong cho GET /customers. Tham so null/rong = bo qua dieu kien do.
     * keyword tim tren full_name, phone, email (khong phan biet hoa thuong).
     */
    static Specification<Customer> filter(
            UUID ownerId, CustomerStatus status, DemandType demandType, String source, String keyword) {
        return (root, query, cb) -> {
            List<Predicate> conditions = new ArrayList<>();
            if (ownerId != null) {
                conditions.add(cb.equal(root.get("ownerId"), ownerId));
            }
            if (status != null) {
                conditions.add(cb.equal(root.get("status"), status));
            }
            if (demandType != null) {
                conditions.add(cb.equal(root.get("demandType"), demandType));
            }
            if (source != null && !source.isBlank()) {
                conditions.add(cb.equal(root.get("source"), source));
            }
            if (keyword != null && !keyword.isBlank()) {
                String pattern = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
                conditions.add(cb.or(
                        cb.like(cb.lower(root.get("fullName")), pattern),
                        cb.like(cb.lower(root.get("phone")), pattern),
                        cb.like(cb.lower(root.get("email")), pattern)));
            }
            return cb.and(conditions.toArray(new Predicate[0]));
        };
    }
}
