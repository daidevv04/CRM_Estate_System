package com.estatecrm.user_service.dto.notification;

import com.estatecrm.user_service.enums.NotificationRefType;
import com.estatecrm.user_service.enums.NotificationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record PublishEventRequest(
        @NotNull NotificationType type,
        @NotBlank @Size(max = 200) String title,
        String body,
        NotificationRefType refType,
        UUID refId,
        UUID targetUserId,
        Boolean broadcastToManagersAndAdmins,
        String targetScope, // "ALL", "SALES", "MANAGER", "ADMIN"
        Boolean excludeActor // default true
) {}
