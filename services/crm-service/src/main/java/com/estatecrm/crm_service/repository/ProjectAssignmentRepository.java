package com.estatecrm.crm_service.repository;

import com.estatecrm.crm_service.entity.ProjectAssignment;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProjectAssignmentRepository extends JpaRepository<ProjectAssignment, UUID> {

    List<ProjectAssignment> findByProjectId(UUID projectId);

    List<ProjectAssignment> findByUserId(UUID userId);

    @Query("SELECT pa.project.id FROM ProjectAssignment pa WHERE pa.userId = :userId")
    List<UUID> findProjectIdsByUserId(@Param("userId") UUID userId);

    @Query("SELECT pa.project.id FROM ProjectAssignment pa WHERE pa.userId = :userId AND pa.assignedRole = com.estatecrm.crm_service.enums.ProjectAssignmentRole.MANAGER")
    List<UUID> findManagedProjectIdsByUserId(@Param("userId") UUID userId);

    @Query("SELECT DISTINCT pa.userId FROM ProjectAssignment pa WHERE pa.project.id IN :projectIds")
    List<UUID> findUserIdsByProjectIdIn(@Param("projectIds") java.util.Collection<UUID> projectIds);

    boolean existsByProjectIdAndUserId(UUID projectId, UUID userId);

    boolean existsByProjectIdAndUserIdAndAssignedRole(
            UUID projectId, UUID userId, com.estatecrm.crm_service.enums.ProjectAssignmentRole assignedRole);

    Optional<ProjectAssignment> findByProjectIdAndUserId(UUID projectId, UUID userId);

    void deleteByProjectIdAndUserId(UUID projectId, UUID userId);
}
