package com.estatecrm.crm_service.dto;

import com.estatecrm.crm_service.enums.ProjectAssignmentRole;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record AssignProjectMemberRequest(
        @NotNull UUID userId,
        @NotNull ProjectAssignmentRole assignedRole) {
}
