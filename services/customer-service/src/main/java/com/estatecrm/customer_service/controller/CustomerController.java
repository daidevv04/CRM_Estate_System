package com.estatecrm.customer_service.controller;

import com.estatecrm.customer_service.dto.CreateCustomerRequest;
import com.estatecrm.customer_service.dto.CustomerResponse;
import com.estatecrm.customer_service.dto.UpdateCustomerRequest;
import com.estatecrm.customer_service.enums.CustomerStatus;
import com.estatecrm.customer_service.enums.DemandType;
import com.estatecrm.customer_service.service.CustomerService;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/customers")
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerResponse create(@Valid @RequestBody CreateCustomerRequest request, @AuthenticationPrincipal Jwt jwt) {
        return customerService.create(request, actorId(jwt), role(jwt));
    }

    @GetMapping
    public Page<CustomerResponse> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) UUID ownerId,
            @RequestParam(required = false) CustomerStatus status,
            @RequestParam(required = false) DemandType demandType,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String keyword,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return customerService.list(
                actorId(jwt), role(jwt), ownerId, status, demandType, source, keyword, pageable);
    }

    @GetMapping("/{customerId}")
    public CustomerResponse get(@PathVariable UUID customerId, @AuthenticationPrincipal Jwt jwt) {
        return customerService.get(customerId, actorId(jwt), role(jwt));
    }

    @PatchMapping("/{customerId}")
    public CustomerResponse update(
            @PathVariable UUID customerId,
            @Valid @RequestBody UpdateCustomerRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return customerService.update(customerId, request, actorId(jwt), role(jwt));
    }

    @DeleteMapping("/{customerId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID customerId, @AuthenticationPrincipal Jwt jwt) {
        customerService.delete(customerId, actorId(jwt), role(jwt));
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
