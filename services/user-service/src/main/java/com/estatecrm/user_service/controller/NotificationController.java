package com.estatecrm.user_service.controller;

import com.estatecrm.user_service.dto.notification.CreateNotificationRequest;
import com.estatecrm.user_service.dto.notification.NotificationResponse;
import com.estatecrm.user_service.service.NotificationService;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public Page<NotificationResponse> list(
            @RequestParam(required = false) Boolean unreadOnly,
            @PageableDefault(size = 20, sort = "createdAt", direction = org.springframework.data.domain.Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal Jwt jwt) {
        return notificationService.list(actorId(jwt), unreadOnly, pageable);
    }

    @GetMapping("/unread-count")
    public Map<String, Long> countUnread(@AuthenticationPrincipal Jwt jwt) {
        return Map.of("unreadCount", notificationService.countUnread(actorId(jwt)));
    }

    @PatchMapping("/{id}/read")
    public NotificationResponse markAsRead(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        return notificationService.markAsRead(id, actorId(jwt));
    }

    @PostMapping("/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markAllAsRead(@AuthenticationPrincipal Jwt jwt) {
        notificationService.markAllAsRead(actorId(jwt));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public NotificationResponse create(
            @Valid @RequestBody CreateNotificationRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return notificationService.create(request, actorId(jwt), role(jwt));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        notificationService.delete(id, actorId(jwt));
    }

    private UUID actorId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    private String role(Jwt jwt) {
        return jwt.getClaimAsString("role");
    }
}
