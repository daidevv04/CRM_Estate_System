package com.estatecrm.user_service.service;

import com.estatecrm.user_service.dto.user.ChangePasswordRequest;
import com.estatecrm.user_service.dto.user.CreateUserRequest;
import com.estatecrm.user_service.dto.user.ResetPasswordRequest;
import com.estatecrm.user_service.dto.user.UpdateUserProfileRequest;
import com.estatecrm.user_service.dto.user.UpdateUserRoleRequest;
import com.estatecrm.user_service.dto.user.UpdateUserStatusRequest;
import com.estatecrm.user_service.dto.user.UserResponse;
import com.estatecrm.user_service.entity.User;
import com.estatecrm.user_service.enums.UserRole;
import com.estatecrm.user_service.enums.UserStatus;
import com.estatecrm.user_service.exception.ConflictException;
import com.estatecrm.user_service.repository.RefreshTokenRepository;
import com.estatecrm.user_service.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public UserResponse create(CreateUserRequest request, UUID adminId) {
        String username = request.username().trim();
        String email = normalizeEmail(request.email());
        if (userRepository.existsByUsername(username)
                || (email != null && userRepository.existsByEmail(email))
                || (request.phone() != null && userRepository.existsByPhone(request.phone()))) {
            throw new ConflictException("Account already exists");
        }
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setEmail(email);
        user.setFullName(request.fullName());
        user.setPhone(request.phone());
        user.setRole(request.role() == null ? UserRole.SALES : request.role());
        user.setStatus(UserStatus.ACTIVE);
        user.setCreatedBy(userRepository.getReferenceById(adminId));
        return UserResponse.from(userRepository.save(user));
    }

    @Transactional(readOnly = true)
    public List<UserResponse> list() {
        return userRepository.findAll().stream().map(UserResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public UserResponse get(UUID userId) {
        return UserResponse.from(find(userId));
    }

    @Transactional
    public UserResponse updateProfile(UUID userId, UpdateUserProfileRequest request, UUID adminId) {
        User user = find(userId);
        if (request.email() != null) {
            String email = normalizeEmail(request.email());
            if (!email.equals(user.getEmail()) && userRepository.existsByEmail(email)) {
                throw new ConflictException("Email already exists");
            }
            user.setEmail(email);
        }
        if (request.phone() != null) {
            if (!request.phone().equals(user.getPhone()) && userRepository.existsByPhone(request.phone())) {
                throw new ConflictException("Phone already exists");
            }
            user.setPhone(request.phone());
        }
        if (request.fullName() != null) {
            user.setFullName(request.fullName());
        }
        user.setUpdatedBy(userRepository.getReferenceById(adminId));
        return UserResponse.from(userRepository.save(user));
    }

    @Transactional
    public UserResponse updateRole(UUID userId, UpdateUserRoleRequest request, UUID adminId) {
        rejectSelf(userId, adminId);
        User user = find(userId);
        user.setRole(request.role());
        user.setUpdatedBy(userRepository.getReferenceById(adminId));
        revoke(userId);
        return UserResponse.from(userRepository.save(user));
    }

    @Transactional
    public UserResponse updateStatus(UUID userId, UpdateUserStatusRequest request, UUID adminId) {
        rejectSelf(userId, adminId);
        User user = find(userId);
        user.setStatus(request.status());
        user.setUpdatedBy(userRepository.getReferenceById(adminId));
        if (request.status() != UserStatus.ACTIVE) {
            revoke(userId);
        }
        return UserResponse.from(userRepository.save(user));
    }

    @Transactional
    public void changeOwnPassword(UUID userId, ChangePasswordRequest request) {
        User user = find(userId);
        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            throw new ResponseStatusException(BAD_REQUEST, "Current password is incorrect");
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPassword())) {
            throw new ResponseStatusException(BAD_REQUEST, "New password must differ from current password");
        }
        user.setPassword(passwordEncoder.encode(request.newPassword()));
        user.setUpdatedBy(user);
        revoke(userId);
    }

    @Transactional
    public void resetPassword(UUID userId, ResetPasswordRequest request, UUID adminId) {
        rejectSelf(userId, adminId);
        User user = find(userId);
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setUpdatedBy(userRepository.getReferenceById(adminId));
        revoke(userId);
    }

    @Transactional
    public void revokeTokens(UUID userId, UUID adminId) {
        rejectSelf(userId, adminId);
        find(userId);
        revoke(userId);
    }

    @Transactional
    public void delete(UUID userId, UUID adminId) {
        rejectSelf(userId, adminId);
        find(userId);
        userRepository.clearCreatedBy(userId);
        userRepository.clearUpdatedBy(userId);
        userRepository.deleteById(userId);
    }

    private User find(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "User not found"));
    }

    private void revoke(UUID userId) {
        refreshTokenRepository.revokeAllForUser(userId, LocalDateTime.now());
    }

    private void rejectSelf(UUID userId, UUID adminId) {
        if (userId.equals(adminId)) {
            throw new ResponseStatusException(BAD_REQUEST, "Admin cannot perform this action on itself");
        }
    }

    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
