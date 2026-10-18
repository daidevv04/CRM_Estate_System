package com.estatecrm.crm_service.repository;

import com.estatecrm.crm_service.entity.ProjectCommissionPolicy;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectCommissionPolicyRepository extends JpaRepository<ProjectCommissionPolicy, UUID> {

    Optional<ProjectCommissionPolicy> findByProjectId(UUID projectId);
}
