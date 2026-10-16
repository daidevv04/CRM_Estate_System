package com.estatecrm.user_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.estatecrm.user_service.dto.notification.NotificationResponse;
import com.estatecrm.user_service.dto.notification.PublishEventRequest;
import com.estatecrm.user_service.entity.Notification;
import com.estatecrm.user_service.entity.User;
import com.estatecrm.user_service.enums.NotificationRefType;
import com.estatecrm.user_service.enums.NotificationType;
import com.estatecrm.user_service.enums.UserRole;
import com.estatecrm.user_service.enums.UserStatus;
import com.estatecrm.user_service.repository.NotificationRepository;
import com.estatecrm.user_service.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTests {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private NotificationSseService notificationSseService;

    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(notificationRepository, userRepository, notificationSseService);
    }

    @Test
    @DisplayName("publishEvent excludes actor and notifies admins, managers and assigned sale")
    void publishEvent_excludesActor_andNotifiesRecipients() {
        UUID actorId = UUID.randomUUID(); // Sale A vừa tạo lead
        UUID targetSaleId = UUID.randomUUID(); // Sale B được phân công
        UUID adminId = UUID.randomUUID();
        UUID managerId = UUID.randomUUID();

        User admin = new User();
        admin.setId(adminId);
        admin.setRole(UserRole.ADMIN);
        admin.setStatus(UserStatus.ACTIVE);

        User manager = new User();
        manager.setId(managerId);
        manager.setRole(UserRole.MANAGER);
        manager.setStatus(UserStatus.ACTIVE);

        when(userRepository.findByRoleInAndStatus(
                List.of(UserRole.ADMIN, UserRole.MANAGER), UserStatus.ACTIVE))
                .thenReturn(List.of(admin, manager));

        when(notificationRepository.saveAll(any())).thenAnswer(invocation -> {
            List<Notification> list = invocation.getArgument(0);
            for (Notification n : list) {
                n.setId(UUID.randomUUID());
                n.setCreatedAt(LocalDateTime.now());
            }
            return list;
        });

        PublishEventRequest request = new PublishEventRequest(
                NotificationType.LEAD_CREATED,
                "Lead mới: Nguyễn Văn A",
                "Sale vừa tạo lead mới",
                NotificationRefType.LEAD,
                UUID.randomUUID(),
                targetSaleId,
                true,
                null,
                true
        );

        List<NotificationResponse> responses = notificationService.publishEvent(request, actorId);

        // Có 3 người nhận: adminId, managerId, targetSaleId (actorId đã bị loại bỏ)
        assertThat(responses).hasSize(3);
        List<UUID> recipientIds = responses.stream().map(NotificationResponse::userId).toList();
        assertThat(recipientIds).containsExactlyInAnyOrder(adminId, managerId, targetSaleId);
        assertThat(recipientIds).doesNotContain(actorId);

        // SSE được bắn cho 3 người
        verify(notificationSseService, times(1)).sendToUser(eq(adminId), any());
        verify(notificationSseService, times(1)).sendToUser(eq(managerId), any());
        verify(notificationSseService, times(1)).sendToUser(eq(targetSaleId), any());
        verify(notificationSseService, never()).sendToUser(eq(actorId), any());
    }

    @Test
    @DisplayName("publishEvent when actor is an admin, actor is still excluded from receiving own event")
    void publishEvent_whenActorIsAdmin_actorIsExcluded() {
        UUID adminActorId = UUID.randomUUID();
        UUID managerId = UUID.randomUUID();

        User adminActor = new User();
        adminActor.setId(adminActorId);
        adminActor.setRole(UserRole.ADMIN);
        adminActor.setStatus(UserStatus.ACTIVE);

        User manager = new User();
        manager.setId(managerId);
        manager.setRole(UserRole.MANAGER);
        manager.setStatus(UserStatus.ACTIVE);

        when(userRepository.findByRoleInAndStatus(
                List.of(UserRole.ADMIN, UserRole.MANAGER), UserStatus.ACTIVE))
                .thenReturn(List.of(adminActor, manager));

        when(notificationRepository.saveAll(any())).thenAnswer(invocation -> {
            List<Notification> list = invocation.getArgument(0);
            for (Notification n : list) {
                n.setId(UUID.randomUUID());
                n.setCreatedAt(LocalDateTime.now());
            }
            return list;
        });

        PublishEventRequest request = new PublishEventRequest(
                NotificationType.DEAL_WON,
                "Deal chốt thành công",
                "Admin vừa phê duyệt chốt deal",
                NotificationRefType.DEAL,
                UUID.randomUUID(),
                null,
                true,
                null,
                true
        );

        List<NotificationResponse> responses = notificationService.publishEvent(request, adminActorId);

        // Chỉ có manager nhận, adminActor KHÔNG nhận
        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).userId()).isEqualTo(managerId);
        verify(notificationSseService, times(1)).sendToUser(eq(managerId), any());
        verify(notificationSseService, never()).sendToUser(eq(adminActorId), any());
    }

    @Test
    @DisplayName("publishEvent with targetScope ALL broadcasts instantly to sales and managers")
    void publishEvent_withTargetScopeAll_broadcastsToAllUsers() {
        UUID adminId = UUID.randomUUID();
        UUID managerId = UUID.randomUUID();
        UUID saleId = UUID.randomUUID();

        User admin = new User(); admin.setId(adminId); admin.setRole(UserRole.ADMIN); admin.setStatus(UserStatus.ACTIVE);
        User manager = new User(); manager.setId(managerId); manager.setRole(UserRole.MANAGER); manager.setStatus(UserStatus.ACTIVE);
        User sale = new User(); sale.setId(saleId); sale.setRole(UserRole.SALES); sale.setStatus(UserStatus.ACTIVE);

        when(userRepository.findByStatus(UserStatus.ACTIVE)).thenReturn(List.of(admin, manager, sale));

        when(notificationRepository.saveAll(any())).thenAnswer(invocation -> {
            List<Notification> list = invocation.getArgument(0);
            for (Notification n : list) {
                n.setId(UUID.randomUUID());
                n.setCreatedAt(LocalDateTime.now());
            }
            return list;
        });

        PublishEventRequest request = new PublishEventRequest(
                NotificationType.SYSTEM_ANNOUNCEMENT,
                "Thông báo toàn hệ thống",
                "Chính sách chiết khấu mới áp dụng từ hôm nay",
                null,
                null,
                null,
                null,
                "ALL",
                true
        );

        List<NotificationResponse> responses = notificationService.publishEvent(request, adminId);

        // Lưu vào DB cho cả 3 người (admin, manager, sale)
        assertThat(responses).hasSize(3);

        // SSE được bắn ngay lập tức cho Manager và Sale, Admin (người gửi) không nhận lại popup SSE
        verify(notificationSseService, times(1)).sendToUser(eq(managerId), any());
        verify(notificationSseService, times(1)).sendToUser(eq(saleId), any());
        verify(notificationSseService, never()).sendToUser(eq(adminId), any());
    }
}
