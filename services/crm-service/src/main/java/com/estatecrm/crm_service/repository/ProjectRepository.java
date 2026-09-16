package com.estatecrm.crm_service.repository;

import com.estatecrm.crm_service.entity.Project;
import com.estatecrm.crm_service.enums.ProjectStatus;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ProjectRepository
        extends JpaRepository<Project, UUID>, JpaSpecificationExecutor<Project> {

    /**
     * Loc dong cho GET /projects. Tham so null/rong = bo qua dieu kien do.
     * keyword tim tren name, location, investor (khong phan biet hoa thuong).
     */
    static Specification<Project> filter(ProjectStatus status, String investor, String keyword) {
        return (root, query, cb) -> {
            List<Predicate> conditions = new ArrayList<>();
            if (status != null) {
                conditions.add(cb.equal(root.get("status"), status));
            }
            if (investor != null && !investor.isBlank()) {
                conditions.add(cb.equal(root.get("investor"), investor));
            }
            if (keyword != null && !keyword.isBlank()) {
                String pattern = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
                conditions.add(cb.or(
                        cb.like(cb.lower(root.get("name")), pattern),
                        cb.like(cb.lower(root.get("location")), pattern),
                        cb.like(cb.lower(root.get("investor")), pattern)));
            }
            return cb.and(conditions.toArray(new Predicate[0]));
        };
    }
}
