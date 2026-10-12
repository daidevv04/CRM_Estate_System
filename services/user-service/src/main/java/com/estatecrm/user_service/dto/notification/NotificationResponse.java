package com.estatecrm.user_service.dto.notification;

import com.estatecrm.user_service.entity.Notification;
import com.estatecrm.user_service.enums.NotificationRefType;
import com.estatecrm.user_service.enums.NotificationType;
import java.time.LocalDateTime;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        UUID userId,
        NotificationType type,
        String title,
        String body,
        NotificationRefType refType,
        UUID refId,
        LocalDateTime readAt,
        LocalDateTime createdAt) {

    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(
                n.getId(),
                n.getUserId(),
                n.getType(),
                n.getTitle(),
                n.getBody(),
                n.getRefType(),
                n.getRefId(),
                n.getReadAt(),
                n.getCreatedAt());
    }
}
