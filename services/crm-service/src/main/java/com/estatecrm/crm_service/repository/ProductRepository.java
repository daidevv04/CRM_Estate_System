package com.estatecrm.crm_service.repository;

import com.estatecrm.crm_service.entity.Product;
import com.estatecrm.crm_service.enums.ProductStatus;
import com.estatecrm.crm_service.enums.ProductType;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ProductRepository
        extends JpaRepository<Product, UUID>, JpaSpecificationExecutor<Product> {

    boolean existsByProjectIdAndCode(UUID projectId, String code);

    /**
     * Loc dong cho GET /products. Tham so null/rong = bo qua dieu kien do.
     * keyword tim tren code va block; khoang gia la [minPrice, maxPrice].
     */
    static Specification<Product> filter(
            UUID projectId,
            ProductType type,
            ProductStatus status,
            String block,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            String keyword) {
        return (root, query, cb) -> {
            List<Predicate> conditions = new ArrayList<>();
            if (projectId != null) {
                conditions.add(cb.equal(root.get("projectId"), projectId));
            }
            if (type != null) {
                conditions.add(cb.equal(root.get("type"), type));
            }
            if (status != null) {
                conditions.add(cb.equal(root.get("status"), status));
            }
            if (block != null && !block.isBlank()) {
                conditions.add(cb.equal(root.get("block"), block));
            }
            if (minPrice != null) {
                conditions.add(cb.greaterThanOrEqualTo(root.get("price"), minPrice));
            }
            if (maxPrice != null) {
                conditions.add(cb.lessThanOrEqualTo(root.get("price"), maxPrice));
            }
            if (keyword != null && !keyword.isBlank()) {
                String pattern = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
                conditions.add(cb.or(
                        cb.like(cb.lower(root.get("code")), pattern),
                        cb.like(cb.lower(root.get("block")), pattern)));
            }
            return cb.and(conditions.toArray(new Predicate[0]));
        };
    }
}
