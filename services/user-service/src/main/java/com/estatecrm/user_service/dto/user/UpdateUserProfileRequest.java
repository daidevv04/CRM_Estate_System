package com.estatecrm.user_service.dto.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateUserProfileRequest(
        @Email @Size(max = 100) String email,
        @Size(max = 100) String fullName,
        @Pattern(regexp = "^\\+[1-9]\\d{7,14}$") String phone) {
}
