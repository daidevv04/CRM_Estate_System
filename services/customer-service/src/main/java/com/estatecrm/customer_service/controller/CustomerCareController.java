package com.estatecrm.customer_service.controller;

import com.estatecrm.customer_service.dto.CreateCustomerCareRequest;
import com.estatecrm.customer_service.dto.CustomerCareResponse;
import com.estatecrm.customer_service.enums.CareType;
import com.estatecrm.customer_service.service.CustomerCareService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/customers")
public class CustomerCareController {

    private final CustomerCareService customerCareService;

    public CustomerCareController(CustomerCareService customerCareService) {
        this.customerCareService = customerCareService;
    }

    @GetMapping("/cares")
    public Page<CustomerCareResponse> listAll(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) UUID ownerId,
            @RequestParam(required = false) CareType type,
            @RequestParam(required = false) String keyword,
            @PageableDefault(size = 20, sort = "createdAt", direction = org.springframework.data.domain.Sort.Direction.DESC) Pageable pageable) {
        return customerCareService.listAll(actorId(jwt), role(jwt), ownerId, type, keyword, pageable);
    }

    @PostMapping("/{customerId}/cares")
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerCareResponse create(
            @PathVariable UUID customerId,
            @Valid @RequestBody CreateCustomerCareRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return customerCareService.create(customerId, request, actorId(jwt), role(jwt));
    }

    @GetMapping("/{customerId}/cares")
    public Page<CustomerCareResponse> list(
            @PathVariable UUID customerId,
            @RequestParam(required = false) CareType type,
            @AuthenticationPrincipal Jwt jwt,
            @PageableDefault(size = 20, sort = "createdAt", direction = org.springframework.data.domain.Sort.Direction.DESC) Pageable pageable) {
        return customerCareService.list(customerId, type, actorId(jwt), role(jwt), pageable);
    }

    @DeleteMapping("/{customerId}/cares/{careId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @PathVariable UUID customerId, @PathVariable UUID careId, @AuthenticationPrincipal Jwt jwt) {
        customerCareService.delete(customerId, careId, actorId(jwt), role(jwt));
    }

    private UUID actorId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    private String role(Jwt jwt) {
        return jwt.getClaimAsString("role");
    }
}
