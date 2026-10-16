package com.estatecrm.user_service.service;

import com.estatecrm.user_service.dto.notification.CreateNotificationRequest;
import com.estatecrm.user_service.dto.notification.NotificationResponse;
import com.estatecrm.user_service.dto.notification.PublishEventRequest;
import com.estatecrm.user_service.entity.Notification;
import com.estatecrm.user_service.entity.User;
import com.estatecrm.user_service.enums.UserRole;
import com.estatecrm.user_service.enums.UserStatus;
import com.estatecrm.user_service.repository.NotificationRepository;
import com.estatecrm.user_service.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
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
    private final NotificationSseService notificationSseService;

    public NotificationService(
            NotificationRepository notificationRepository,
            UserRepository userRepository,
            NotificationSseService notificationSseService) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.notificationSseService = notificationSseService;
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
        if (!targetUserId.equals(actorId) && !"ADMIN".equals(role) && !"MANAGER".equals(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only ADMIN/MANAGER can create notifications for other users");
        }
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
        NotificationResponse response = NotificationResponse.from(notificationRepository.save(notification));
        notificationSseService.sendToUser(targetUserId, response);
        return response;
    }

    @Transactional
    public void delete(UUID id, UUID actorId) {
        Notification notification = notificationRepository.findByIdAndUserId(id, actorId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"));
        notificationRepository.delete(notification);
    }

    @Transactional
    public List<NotificationResponse> publishEvent(PublishEventRequest request, UUID actorId) {
        Set<UUID> recipientIds = new LinkedHashSet<>();

        String scope = request.targetScope() != null ? request.targetScope().toUpperCase(Locale.ROOT).trim() : null;
        if ("ALL".equals(scope)) {
            List<User> allActive = userRepository.findByStatus(UserStatus.ACTIVE);
            for (User u : allActive) {
                recipientIds.add(u.getId());
            }
        } else if ("SALES".equals(scope)) {
            List<User> sales = userRepository.findByRoleInAndStatus(
                    List.of(UserRole.SALES), UserStatus.ACTIVE);
            for (User u : sales) {
                recipientIds.add(u.getId());
            }
        } else if ("MANAGER".equals(scope)) {
            List<User> managers = userRepository.findByRoleInAndStatus(
                    List.of(UserRole.MANAGER), UserStatus.ACTIVE);
            for (User u : managers) {
                recipientIds.add(u.getId());
            }
        } else if ("ADMIN".equals(scope)) {
            List<User> admins = userRepository.findByRoleInAndStatus(
                    List.of(UserRole.ADMIN), UserStatus.ACTIVE);
            for (User u : admins) {
                recipientIds.add(u.getId());
            }
        } else {
            boolean broadcast = request.broadcastToManagersAndAdmins() == null || Boolean.TRUE.equals(request.broadcastToManagersAndAdmins());
            if (broadcast) {
                List<User> managersAndAdmins = userRepository.findByRoleInAndStatus(
                        List.of(UserRole.ADMIN, UserRole.MANAGER), UserStatus.ACTIVE);
                for (User u : managersAndAdmins) {
                    recipientIds.add(u.getId());
                }
            }
        }

        if (request.targetUserId() != null) {
            recipientIds.add(request.targetUserId());
        }

        boolean shouldExcludeActor = request.excludeActor() == null || Boolean.TRUE.equals(request.excludeActor());
        boolean isSystemAnnouncement = scope != null && !scope.isBlank();

        // Với sự kiện nghiệp vụ thông thường, người làm không nhận thông báo
        if (shouldExcludeActor && !isSystemAnnouncement && actorId != null) {
            recipientIds.remove(actorId);
        }

        if (recipientIds.isEmpty()) {
            return List.of();
        }

        LocalDateTime now = LocalDateTime.now();
        List<Notification> toSave = new ArrayList<>();
        for (UUID recipientId : recipientIds) {
            Notification notification = new Notification();
            notification.setUserId(recipientId);
            notification.setType(request.type());
            notification.setTitle(request.title());
            notification.setBody(request.body());
            notification.setRefType(request.refType());
            notification.setRefId(request.refId());
            if (actorId != null && recipientId.equals(actorId)) {
                notification.setReadAt(now);
            }
            notification.setCreatedAt(now);
            toSave.add(notification);
        }

        List<Notification> saved = notificationRepository.saveAll(toSave);
        List<NotificationResponse> responses = saved.stream()
                .map(NotificationResponse::from)
                .toList();

        // Gửi realtime SSE tới từng recipient đang kết nối (loại trừ actorId nếu shouldExcludeActor)
        for (NotificationResponse response : responses) {
            if (shouldExcludeActor && actorId != null && response.userId().equals(actorId)) {
                continue;
            }
            notificationSseService.sendToUser(response.userId(), response);
        }

        return responses;
    }
}
