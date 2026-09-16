package com.estatecrm.customer_service.repository;

import com.estatecrm.customer_service.entity.EmailTemplate;
import com.estatecrm.customer_service.enums.EmailTemplateCategory;
import com.estatecrm.customer_service.enums.EmailTemplateStatus;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface EmailTemplateRepository
        extends JpaRepository<EmailTemplate, UUID>, JpaSpecificationExecutor<EmailTemplate> {

    boolean existsByName(String name);

    /** Loc dong cho GET /email-templates. Tham so null = bo qua dieu kien do. */
    static Specification<EmailTemplate> filter(
            EmailTemplateCategory category, EmailTemplateStatus status, String keyword) {
        return (root, query, cb) -> {
            List<Predicate> conditions = new ArrayList<>();
            if (category != null) {
                conditions.add(cb.equal(root.get("category"), category));
            }
            if (status != null) {
                conditions.add(cb.equal(root.get("status"), status));
            }
            if (keyword != null && !keyword.isBlank()) {
                String pattern = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
                conditions.add(cb.or(
                        cb.like(cb.lower(root.get("name")), pattern),
                        cb.like(cb.lower(root.get("subject")), pattern)));
            }
            return cb.and(conditions.toArray(new Predicate[0]));
        };
    }
}
