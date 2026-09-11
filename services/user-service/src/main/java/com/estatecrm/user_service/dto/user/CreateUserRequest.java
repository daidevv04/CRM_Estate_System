package com.estatecrm.user_service.dto.user;

import com.estatecrm.user_service.enums.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(
        @NotBlank @Size(min = 3, max = 50) String username,
        @NotBlank @Size(min = 8, max = 72) String password,
        @Email @Size(max = 100) String email,
        @Size(max = 100) String fullName,
        @Pattern(regexp = "^\\+[1-9]\\d{7,14}$") String phone,
        UserRole role) {
}
