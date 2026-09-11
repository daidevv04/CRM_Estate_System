package com.estatecrm.user_service.dto.user;

import com.estatecrm.user_service.enums.UserRole;
import jakarta.validation.constraints.NotNull;

public record UpdateUserRoleRequest(@NotNull UserRole role) {
}
