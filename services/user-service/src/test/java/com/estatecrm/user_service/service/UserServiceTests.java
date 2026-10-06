package com.estatecrm.user_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.estatecrm.user_service.dto.user.CreateUserRequest;
import com.estatecrm.user_service.entity.User;
import com.estatecrm.user_service.enums.UserRole;
import com.estatecrm.user_service.enums.UserStatus;
import com.estatecrm.user_service.repository.RefreshTokenRepository;
import com.estatecrm.user_service.repository.UserRepository;
import java.util.UUID;
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
}