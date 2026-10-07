package com.estatecrm.user_service.dto.user;

import com.estatecrm.user_service.entity.User;
import com.estatecrm.user_service.enums.UserRole;
import com.estatecrm.user_service.enums.UserStatus;
import java.time.LocalDateTime;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String username,
        String email,
        String fullName,
        String phone,
        String address,
        UserRole role,
        UserStatus status,
        boolean is2faEnabled,
        LocalDateTime emailVerifiedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        UUID createdBy,
        UUID updatedBy) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getFullName(),
                user.getPhone(),
                user.getAddress(),
                user.getRole(),
                user.getStatus(),
                user.isTwoFactorEnabled(),
                user.getEmailVerifiedAt(),
                user.getCreatedAt(),
                user.getUpdatedAt(),
                user.getCreatedBy() == null ? null : user.getCreatedBy().getId(),
                user.getUpdatedBy() == null ? null : user.getUpdatedBy().getId());
    }
}
