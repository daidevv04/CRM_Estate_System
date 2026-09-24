package com.estatecrm.crm_service.dto;

import com.estatecrm.crm_service.enums.ProjectStatus;
import jakarta.validation.constraints.Size;

public record UpdateProjectRequest(
        @Size(max = 200) String name,
        @Size(max = 255) String location,
        @Size(max = 150) String investor,
        String description,
        ProjectStatus status) {
}
