package com.estatecrm.user_service.dto.auth;

import com.estatecrm.user_service.entity.User;
import com.estatecrm.user_service.enums.UserRole;

public record PasswordResetVerificationResponse(
        String resetToken,
        String fullName,
        String username,
        String email,
        UserRole role) {

    public static PasswordResetVerificationResponse from(User user, String resetToken) {
        return new PasswordResetVerificationResponse(
                resetToken, user.getFullName(), user.getUsername(), user.getEmail(), user.getRole());
    }
}