package com.estatecrm.user_service.controller;

import com.estatecrm.user_service.dto.user.ChangePasswordRequest;
import com.estatecrm.user_service.dto.user.CreateUserRequest;
import com.estatecrm.user_service.dto.user.ResetPasswordRequest;
import com.estatecrm.user_service.dto.user.UpdateUserProfileRequest;
import com.estatecrm.user_service.dto.user.UpdateUserRoleRequest;
import com.estatecrm.user_service.dto.user.UpdateUserStatusRequest;
import com.estatecrm.user_service.dto.user.UserResponse;
import com.estatecrm.user_service.service.UserService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse create(@Valid @RequestBody CreateUserRequest request, @AuthenticationPrincipal Jwt jwt) {
        return userService.create(request, adminId(jwt));
    }

    @GetMapping
    public List<UserResponse> list() {
        return userService.list();
    }

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal Jwt jwt) {
        return userService.get(UUID.fromString(jwt.getSubject()));
    }

    @PostMapping("/me/change-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        userService.changeOwnPassword(UUID.fromString(jwt.getSubject()), request);
    }

    @GetMapping("/{userId}")
    public UserResponse get(@PathVariable UUID userId) {
        return userService.get(userId);
    }

    @PatchMapping("/{userId}/profile")
    public UserResponse updateProfile(
            @PathVariable UUID userId,
            @Valid @RequestBody UpdateUserProfileRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return userService.updateProfile(userId, request, adminId(jwt));
    }

    @PatchMapping("/{userId}/role")
    public UserResponse updateRole(
            @PathVariable UUID userId,
            @Valid @RequestBody UpdateUserRoleRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return userService.updateRole(userId, request, adminId(jwt));
    }

    @PatchMapping("/{userId}/status")
    public UserResponse updateStatus(
            @PathVariable UUID userId,
            @Valid @RequestBody UpdateUserStatusRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return userService.updateStatus(userId, request, adminId(jwt));
    }

    @PostMapping("/{userId}/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(
            @PathVariable UUID userId,
            @Valid @RequestBody ResetPasswordRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        userService.resetPassword(userId, request, adminId(jwt));
    }

    @PostMapping("/{userId}/revoke-tokens")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeTokens(@PathVariable UUID userId, @AuthenticationPrincipal Jwt jwt) {
        userService.revokeTokens(userId, adminId(jwt));
    }

    @DeleteMapping("/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID userId, @AuthenticationPrincipal Jwt jwt) {
        userService.delete(userId, adminId(jwt));
    }

    private UUID adminId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
