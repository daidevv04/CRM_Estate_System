package com.estatecrm.user_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.estatecrm.user_service.dto.user.CreateUserRequest;
import com.estatecrm.user_service.dto.user.UpdateUserProfileRequest;
import com.estatecrm.user_service.dto.user.UserResponse;
import com.estatecrm.user_service.entity.User;
import com.estatecrm.user_service.enums.UserRole;
import com.estatecrm.user_service.enums.UserStatus;
import com.estatecrm.user_service.repository.RefreshTokenRepository;
import com.estatecrm.user_service.repository.UserRepository;
import java.util.UUID;
import java.util.Optional;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

class UserServiceTests {

    @Test
    void createMapsAddressAndHashesInitialPassword() {
        UserRepository users = mock(UserRepository.class);
        RefreshTokenRepository tokens = mock(RefreshTokenRepository.class);
        PasswordEncoder passwords = mock(PasswordEncoder.class);
        UserService service = new UserService(users, tokens, passwords);
        UUID adminId = UUID.randomUUID();
        User admin = new User();
        admin.setId(adminId);
        when(users.getReferenceById(adminId)).thenReturn(admin);
        when(passwords.encode("Password1!")).thenReturn("encoded-password");
        when(users.save(any(User.class))).thenAnswer(call -> call.getArgument(0));

        service.create(new CreateUserRequest(
                "nguyen.an", "Password1!", "an@example.com", "Nguyễn Văn An", "+84912345678",
                "105 Nguyễn Giáp, Đà Nẵng", UserRole.SALES), adminId);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(users).save(captor.capture());
        User created = captor.getValue();
        assertThat(created.getAddress()).isEqualTo("105 Nguyễn Giáp, Đà Nẵng");
        assertThat(created.getPassword()).isEqualTo("encoded-password");
        assertThat(created.getRole()).isEqualTo(UserRole.SALES);
        assertThat(created.getStatus()).isEqualTo(UserStatus.ACTIVE);
        verify(passwords).encode(anyString());
    }

    @Test
    void createUserWithLeadingZeroPhoneSucceeds() {
        UserRepository users = mock(UserRepository.class);
        RefreshTokenRepository tokens = mock(RefreshTokenRepository.class);
        PasswordEncoder passwords = mock(PasswordEncoder.class);
        UserService service = new UserService(users, tokens, passwords);
        UUID adminId = UUID.randomUUID();
        User admin = new User();
        admin.setId(adminId);
        when(users.getReferenceById(adminId)).thenReturn(admin);
        when(passwords.encode(anyString())).thenReturn("encoded-password");
        when(users.save(any(User.class))).thenAnswer(call -> call.getArgument(0));

        service.create(new CreateUserRequest(
                "nguyen.an2", "Password1!", "an2@example.com", "Nguyễn Văn An", "0912345678",
                "105 Nguyễn Giáp, Đà Nẵng", UserRole.SALES), adminId);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(users).save(captor.capture());
        User created = captor.getValue();
        assertThat(created.getPhone()).isEqualTo("0912345678");
    }

    @Test
    void responseIncludesSafeSecurityAndAuditFieldsWithoutSecrets() {
        User user = new User();
        User creator = new User();
        User updater = new User();
        UUID creatorId = UUID.randomUUID();
        UUID updaterId = UUID.randomUUID();
        LocalDateTime createdAt = LocalDateTime.of(2026, 10, 6, 9, 0);
        LocalDateTime updatedAt = LocalDateTime.of(2026, 10, 6, 10, 0);
        creator.setId(creatorId);
        updater.setId(updaterId);
        user.setTwoFactorEnabled(true);
        user.setCreatedAt(createdAt);
        user.setUpdatedAt(updatedAt);
        user.setCreatedBy(creator);
        user.setUpdatedBy(updater);

        UserResponse response = UserResponse.from(user);

        assertThat(response.is2faEnabled()).isTrue();
        assertThat(response.createdAt()).isEqualTo(createdAt);
        assertThat(response.updatedAt()).isEqualTo(updatedAt);
        assertThat(response.createdBy()).isEqualTo(creatorId);
        assertThat(response.updatedBy()).isEqualTo(updaterId);
    }

    @Test
    void updateProfileAllowsAccountOwnerToUpdateOnlyOwnContactDetails() {
        UserRepository users = mock(UserRepository.class);
        RefreshTokenRepository tokens = mock(RefreshTokenRepository.class);
        PasswordEncoder passwords = mock(PasswordEncoder.class);
        UserService service = new UserService(users, tokens, passwords);
        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);
        user.setEmail("old@example.com");
        when(users.findById(userId)).thenReturn(Optional.of(user));
        when(users.existsByEmail("new@example.com")).thenReturn(false);
        when(users.existsByPhone("+84912345678")).thenReturn(false);
        when(users.getReferenceById(userId)).thenReturn(user);
        when(users.save(any(User.class))).thenAnswer(call -> call.getArgument(0));

        service.updateProfile(userId, new UpdateUserProfileRequest(
                "new@example.com", "Nguyễn Văn An", "+84912345678", "Đà Nẵng"), userId);

        assertThat(user.getEmail()).isEqualTo("new@example.com");
        assertThat(user.getFullName()).isEqualTo("Nguyễn Văn An");
        assertThat(user.getPhone()).isEqualTo("+84912345678");
        assertThat(user.getAddress()).isEqualTo("Đà Nẵng");
        assertThat(user.getUpdatedBy()).isSameAs(user);
    }

    @Test
    void updateProfileClearsNullableContactFieldsWhenRequested() {
        UserRepository users = mock(UserRepository.class);
        RefreshTokenRepository tokens = mock(RefreshTokenRepository.class);
        PasswordEncoder passwords = mock(PasswordEncoder.class);
        UserService service = new UserService(users, tokens, passwords);
        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);
        user.setEmail("an@example.com");
        user.setPhone("+84912345678");
        user.setAddress("Đà Nẵng");
        when(users.findById(userId)).thenReturn(Optional.of(user));
        when(users.getReferenceById(userId)).thenReturn(user);
        when(users.save(any(User.class))).thenAnswer(call -> call.getArgument(0));

        service.updateProfile(userId, new UpdateUserProfileRequest(null, null, null, null), userId);

        assertThat(user.getEmail()).isNull();
        assertThat(user.getPhone()).isNull();
        assertThat(user.getAddress()).isNull();
    }

    @Test
    void managerCannotCreateAdminOrManager() {
        UserRepository users = mock(UserRepository.class);
        RefreshTokenRepository tokens = mock(RefreshTokenRepository.class);
        PasswordEncoder passwords = mock(PasswordEncoder.class);
        CrmClient crmClient = mock(CrmClient.class);
        UserService service = new UserService(users, tokens, passwords, crmClient);
        UUID managerId = UUID.randomUUID();

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.create(new CreateUserRequest(
                "sub.admin", "Password1!", "admin2@example.com", "Admin 2", "+84912345679",
                "Hà Nội", UserRole.ADMIN), managerId, "MANAGER"))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("Manager can only create SALES accounts");
    }

    @Test
    void managerCanCreateSalesUser() {
        UserRepository users = mock(UserRepository.class);
        RefreshTokenRepository tokens = mock(RefreshTokenRepository.class);
        PasswordEncoder passwords = mock(PasswordEncoder.class);
        CrmClient crmClient = mock(CrmClient.class);
        UserService service = new UserService(users, tokens, passwords, crmClient);
        UUID managerId = UUID.randomUUID();
        User manager = new User();
        manager.setId(managerId);

        when(users.getReferenceById(managerId)).thenReturn(manager);
        when(passwords.encode("Password1!")).thenReturn("encoded-password");
        when(users.save(any(User.class))).thenAnswer(call -> call.getArgument(0));

        UserResponse res = service.create(new CreateUserRequest(
                "sales.one", "Password1!", "sales1@example.com", "Sales One", "+84912345670",
                "HCM", UserRole.SALES), managerId, "MANAGER");

        assertThat(res.role()).isEqualTo(UserRole.SALES);
        assertThat(res.username()).isEqualTo("sales.one");
    }

    @Test
    void managerCannotDeleteUserOutsideScope() {
        UserRepository users = mock(UserRepository.class);
        RefreshTokenRepository tokens = mock(RefreshTokenRepository.class);
        PasswordEncoder passwords = mock(PasswordEncoder.class);
        CrmClient crmClient = mock(CrmClient.class);
        UserService service = new UserService(users, tokens, passwords, crmClient);

        UUID managerId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();

        User targetSales = new User();
        targetSales.setId(otherUserId);
        targetSales.setRole(UserRole.SALES);

        when(users.findById(otherUserId)).thenReturn(Optional.of(targetSales));
        when(crmClient.getManagedUserIds(managerId)).thenReturn(java.util.List.of(managerId));
        when(users.findIdsByCreatedById(managerId)).thenReturn(java.util.List.of());

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.delete(otherUserId, managerId, "MANAGER"))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("User is not in your managed scope");
    }
}