package com.estatecrm.customer_service.controller;

import com.estatecrm.customer_service.dto.AppointmentResponse;
import com.estatecrm.customer_service.dto.CreateAppointmentRequest;
import com.estatecrm.customer_service.dto.UpdateAppointmentRequest;
import com.estatecrm.customer_service.enums.AppointmentStatus;
import com.estatecrm.customer_service.service.AppointmentService;
import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lich hen. SALES chi thay lich cua minh; ADMIN/MANAGER thay tat ca.
 * /appointments/customer/{id} xem lich theo tung khach hang.
 */
@RestController
@RequestMapping("/appointments")
public class AppointmentController {

    private final AppointmentService appointmentService;

    public AppointmentController(AppointmentService appointmentService) {
        this.appointmentService = appointmentService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AppointmentResponse create(
            @Valid @RequestBody CreateAppointmentRequest request, @AuthenticationPrincipal Jwt jwt) {
        return appointmentService.create(request, actorId(jwt), role(jwt));
    }

    @GetMapping
    public Page<AppointmentResponse> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) UUID salesId,
            @RequestParam(required = false) UUID customerId,
            @RequestParam(required = false) AppointmentStatus status,
            @RequestParam(required = false) LocalDateTime from,
            @RequestParam(required = false) LocalDateTime to,
            @PageableDefault(size = 20, sort = "startTime") Pageable pageable) {
        return appointmentService.list(actorId(jwt), role(jwt), salesId, customerId, status, from, to, pageable);
    }

    @GetMapping("/{appointmentId}")
    public AppointmentResponse get(@PathVariable UUID appointmentId, @AuthenticationPrincipal Jwt jwt) {
        return appointmentService.get(appointmentId, actorId(jwt), role(jwt));
    }

    @GetMapping("/customer/{customerId}")
    public Page<AppointmentResponse> listByCustomer(
            @PathVariable UUID customerId,
            @AuthenticationPrincipal Jwt jwt,
            @PageableDefault(size = 20, sort = "startTime") Pageable pageable) {
        return appointmentService.listByCustomer(customerId, actorId(jwt), role(jwt), pageable);
    }

    @PatchMapping("/{appointmentId}")
    public AppointmentResponse update(
            @PathVariable UUID appointmentId,
            @Valid @RequestBody UpdateAppointmentRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return appointmentService.update(appointmentId, request, actorId(jwt), role(jwt));
    }

    @DeleteMapping("/{appointmentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID appointmentId, @AuthenticationPrincipal Jwt jwt) {
        appointmentService.delete(appointmentId, actorId(jwt), role(jwt));
    }

    /** Lay id nguoi dang nhap tu claim subject cua JWT da verify. */
    private UUID actorId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    /** Lay vai tro tu claim "role" do user-service phat hanh. */
    private String role(Jwt jwt) {
        return jwt.getClaimAsString("role");
    }
}
