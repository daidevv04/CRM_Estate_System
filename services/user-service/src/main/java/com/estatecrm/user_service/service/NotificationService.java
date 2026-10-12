package com.estatecrm.user_service.service;

import com.estatecrm.user_service.dto.notification.CreateNotificationRequest;
import com.estatecrm.user_service.dto.notification.NotificationResponse;
import com.estatecrm.user_service.entity.Notification;
import com.estatecrm.user_service.enums.UserStatus;
import com.estatecrm.user_service.repository.NotificationRepository;
import com.estatecrm.user_service.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    public NotificationService(
            NotificationRepository notificationRepository,
            UserRepository userRepository) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public Page<NotificationResponse> list(UUID actorId, Boolean unreadOnly, Pageable pageable) {
        if (Boolean.TRUE.equals(unreadOnly)) {
            return notificationRepository
                    .findByUserIdAndReadAtIsNullOrderByCreatedAtDesc(actorId, pageable)
                    .map(NotificationResponse::from);
        }
        return notificationRepository
                .findByUserIdOrderByCreatedAtDesc(actorId, pageable)
                .map(NotificationResponse::from);
    }

    @Transactional(readOnly = true)
    public long countUnread(UUID actorId) {
        return notificationRepository.countByUserIdAndReadAtIsNull(actorId);
    }

    @Transactional
    public NotificationResponse markAsRead(UUID id, UUID actorId) {
        Notification notification = notificationRepository.findByIdAndUserId(id, actorId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"));
        if (notification.getReadAt() == null) {
            notification.setReadAt(LocalDateTime.now());
            notification = notificationRepository.save(notification);
        }
        return NotificationResponse.from(notification);
    }

    @Transactional
    public void markAllAsRead(UUID actorId) {
        notificationRepository.markAllAsRead(actorId, LocalDateTime.now());
    }

    @Transactional
    public NotificationResponse create(CreateNotificationRequest request, UUID actorId, String role) {
        UUID targetUserId = request.userId() != null ? request.userId() : actorId;
        if (!userRepository.existsById(targetUserId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Target user not found");
        }

        Notification notification = new Notification();
        notification.setUserId(targetUserId);
        notification.setType(request.type());
        notification.setTitle(request.title());
        notification.setBody(request.body());
        notification.setRefType(request.refType());
        notification.setRefId(request.refId());
        notification.setCreatedAt(LocalDateTime.now());

        return NotificationResponse.from(notificationRepository.save(notification));
    }

    @Transactional
    public void delete(UUID id, UUID actorId) {
        Notification notification = notificationRepository.findByIdAndUserId(id, actorId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"));
        notificationRepository.delete(notification);
    }
}
