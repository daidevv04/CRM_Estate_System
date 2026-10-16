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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
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
    private final CrmClient crmClient;

    public UserService(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder) {
        this(userRepository, refreshTokenRepository, passwordEncoder, null);
    }

    @Autowired
    public UserService(
            UserRepository userRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            CrmClient crmClient) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.crmClient = crmClient;
    }

    public Set<UUID> getAllowedUserIds(UUID actorId, String role) {
        if ("ADMIN".equalsIgnoreCase(role)) {
            return Set.of();
        }
        Set<UUID> allowed = new HashSet<>();
        if (actorId != null) {
            allowed.add(actorId);
        }
        if ("MANAGER".equalsIgnoreCase(role) && actorId != null) {
            if (crmClient != null) {
                allowed.addAll(crmClient.getManagedUserIds(actorId));
            }
            allowed.addAll(userRepository.findIdsByCreatedById(actorId));
        }
        return allowed;
    }

    @Transactional
    public UserResponse create(CreateUserRequest request, UUID actorId, String role) {
        boolean isManager = "MANAGER".equalsIgnoreCase(role);
        boolean isAdmin = "ADMIN".equalsIgnoreCase(role);

        if (!isAdmin && !isManager) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only Admin or Manager can create users");
        }

        if (isManager && request.role() != null && request.role() != UserRole.SALES) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Manager can only create SALES accounts");
        }

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
        user.setAddress(request.address());

        if (isManager) {
            user.setRole(UserRole.SALES);
        } else {
            user.setRole(request.role() == null ? UserRole.SALES : request.role());
        }

        user.setStatus(UserStatus.ACTIVE);
        user.setCreatedBy(userRepository.getReferenceById(actorId));
        return UserResponse.from(userRepository.save(user));
    }

    @Transactional
    public UserResponse create(CreateUserRequest request, UUID adminId) {
        return create(request, adminId, "ADMIN");
    }

    @Transactional(readOnly = true)
    public List<UserResponse> list(UUID actorId, String role) {
        if ("ADMIN".equalsIgnoreCase(role)) {
            return userRepository.findAll().stream().map(UserResponse::from).toList();
        }
        Set<UUID> allowed = getAllowedUserIds(actorId, role);
        return userRepository.findAllById(allowed).stream().map(UserResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<UserResponse> list() {
        return userRepository.findAll().stream().map(UserResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public UserResponse get(UUID userId, UUID actorId, String role) {
        User user = find(userId);
        if (!"ADMIN".equalsIgnoreCase(role)) {
            Set<UUID> allowed = getAllowedUserIds(actorId, role);
            if (!allowed.contains(userId)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied to user " + userId);
            }
        }
        return UserResponse.from(user);
    }

    @Transactional(readOnly = true)
    public UserResponse get(UUID userId) {
        return UserResponse.from(find(userId));
    }

    @Transactional
    public UserResponse updateProfile(UUID userId, UpdateUserProfileRequest request, UUID actorId, String role) {
        User user = find(userId);
        boolean isSelf = userId.equals(actorId);
        boolean isAdmin = "ADMIN".equalsIgnoreCase(role);
        boolean isManager = "MANAGER".equalsIgnoreCase(role);

        if (!isAdmin && !isSelf) {
            if (isManager) {
                if (user.getRole() != UserRole.SALES) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Manager can only manage SALES accounts");
                }
                Set<UUID> allowed = getAllowedUserIds(actorId, role);
                if (!allowed.contains(userId)) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User is not in your managed scope");
                }
            } else {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
            }
        }

        if (request.email() != null) {
            String email = normalizeEmail(request.email());
            if (!email.equals(user.getEmail()) && userRepository.existsByEmail(email)) {
                throw new ConflictException("Email already exists");
            }
            user.setEmail(email);
        } else {
            user.setEmail(null);
        }
        if (request.phone() != null) {
            if (!request.phone().equals(user.getPhone()) && userRepository.existsByPhone(request.phone())) {
                throw new ConflictException("Phone already exists");
            }
            user.setPhone(request.phone());
        } else {
            user.setPhone(null);
        }
        if (request.fullName() != null) {
            user.setFullName(request.fullName());
        }
        user.setAddress(request.address());
        user.setUpdatedBy(userRepository.getReferenceById(actorId));
        return UserResponse.from(userRepository.save(user));
    }

    @Transactional
    public UserResponse updateProfile(UUID userId, UpdateUserProfileRequest request, UUID adminId) {
        return updateProfile(userId, request, adminId, "ADMIN");
    }

    @Transactional
    public UserResponse updateRole(UUID userId, UpdateUserRoleRequest request, UUID actorId, String role) {
        if (!"ADMIN".equalsIgnoreCase(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only Admin can change user roles");
        }
        rejectSelf(userId, actorId);
        User user = find(userId);
        user.setRole(request.role());
        user.setUpdatedBy(userRepository.getReferenceById(actorId));
        revoke(userId);
        return UserResponse.from(userRepository.save(user));
    }

    @Transactional
    public UserResponse updateRole(UUID userId, UpdateUserRoleRequest request, UUID adminId) {
        return updateRole(userId, request, adminId, "ADMIN");
    }

    @Transactional
    public UserResponse updateStatus(UUID userId, UpdateUserStatusRequest request, UUID actorId, String role) {
        rejectSelf(userId, actorId);
        User user = find(userId);
        if (!"ADMIN".equalsIgnoreCase(role)) {
            if ("MANAGER".equalsIgnoreCase(role)) {
                if (user.getRole() != UserRole.SALES) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Manager can only manage SALES accounts");
                }
                Set<UUID> allowed = getAllowedUserIds(actorId, role);
                if (!allowed.contains(userId)) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User is not in your managed scope");
                }
            } else {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
            }
        }
        user.setStatus(request.status());
        user.setUpdatedBy(userRepository.getReferenceById(actorId));
        if (request.status() != UserStatus.ACTIVE) {
            revoke(userId);
        }
        return UserResponse.from(userRepository.save(user));
    }

    @Transactional
    public UserResponse updateStatus(UUID userId, UpdateUserStatusRequest request, UUID adminId) {
        return updateStatus(userId, request, adminId, "ADMIN");
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
    public void resetPassword(UUID userId, ResetPasswordRequest request, UUID actorId, String role) {
        rejectSelf(userId, actorId);
        User user = find(userId);
        if (!"ADMIN".equalsIgnoreCase(role)) {
            if ("MANAGER".equalsIgnoreCase(role)) {
                if (user.getRole() != UserRole.SALES) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Manager can only manage SALES accounts");
                }
                Set<UUID> allowed = getAllowedUserIds(actorId, role);
                if (!allowed.contains(userId)) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User is not in your managed scope");
                }
            } else {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
            }
        }
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setUpdatedBy(userRepository.getReferenceById(actorId));
        revoke(userId);
    }

    @Transactional
    public void resetPassword(UUID userId, ResetPasswordRequest request, UUID adminId) {
        resetPassword(userId, request, adminId, "ADMIN");
    }

    @Transactional
    public void revokeTokens(UUID userId, UUID adminId) {
        rejectSelf(userId, adminId);
        find(userId);
        revoke(userId);
    }

    @Transactional
    public void delete(UUID userId, UUID actorId, String role) {
        rejectSelf(userId, actorId);
        User user = find(userId);
        if (!"ADMIN".equalsIgnoreCase(role)) {
            if ("MANAGER".equalsIgnoreCase(role)) {
                if (user.getRole() != UserRole.SALES) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Manager can only delete SALES accounts");
                }
                Set<UUID> allowed = getAllowedUserIds(actorId, role);
                if (!allowed.contains(userId)) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User is not in your managed scope");
                }
            } else {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
            }
        }
        userRepository.clearCreatedBy(userId);
        userRepository.clearUpdatedBy(userId);
        userRepository.deleteById(userId);
    }

    @Transactional
    public void delete(UUID userId, UUID adminId) {
        delete(userId, adminId, "ADMIN");
    }

    private User find(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "User not found"));
    }

    private void revoke(UUID userId) {
        refreshTokenRepository.revokeAllForUser(userId, LocalDateTime.now());
    }

    private void rejectSelf(UUID userId, UUID actorId) {
        if (userId.equals(actorId)) {
            throw new ResponseStatusException(BAD_REQUEST, "Cannot perform this action on yourself");
        }
    }

    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
