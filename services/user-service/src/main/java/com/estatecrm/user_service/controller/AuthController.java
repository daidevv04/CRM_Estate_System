package com.estatecrm.user_service.controller;

import com.estatecrm.user_service.dto.auth.LoginRequest;
import com.estatecrm.user_service.dto.auth.RefreshTokenRequest;
import com.estatecrm.user_service.dto.auth.TokenResponse;
import com.estatecrm.user_service.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        return authService.login(request, clientIp(httpRequest));
    }

    @PostMapping("/refresh")
    public TokenResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return authService.refresh(request);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@Valid @RequestBody RefreshTokenRequest request) {
        authService.logout(request);
    }

    /**
     * IP dung cho throttle. Gateway chay trong cung docker network nen
     * getRemoteAddr() mac dinh la IP cua gateway, khong phai IP khach. Bat
     * server.forward-headers-strategy=native de Tomcat tu doc X-Forwarded-For
     * va tra ve IP khach, nhung chi khi request den tu proxy tin cay.
     *
     * Xem review_full_system_v2.md muc N1.
     */
    private String clientIp(HttpServletRequest request) {
        return request.getRemoteAddr();
    }
}