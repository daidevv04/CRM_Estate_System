package com.estatecrm.crm_service.dto;

import com.estatecrm.crm_service.entity.ProjectAssignment;
import com.estatecrm.crm_service.enums.ProjectAssignmentRole;
import java.time.LocalDateTime;
import java.util.UUID;

public record ProjectMemberResponse(
        UUID id,
        UUID projectId,
        UUID userId,
        ProjectAssignmentRole assignedRole,
        LocalDateTime assignedAt,
        UUID assignedBy) {

    public static ProjectMemberResponse from(ProjectAssignment pa) {
        return new ProjectMemberResponse(
                pa.getId(),
                pa.getProject().getId(),
                pa.getUserId(),
                pa.getAssignedRole(),
                pa.getAssignedAt(),
                pa.getAssignedBy());
    }
}
