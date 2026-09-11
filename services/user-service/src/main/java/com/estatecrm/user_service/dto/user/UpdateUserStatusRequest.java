package com.estatecrm.user_service.dto.user;

import com.estatecrm.user_service.enums.UserStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateUserStatusRequest(@NotNull UserStatus status) {
}
