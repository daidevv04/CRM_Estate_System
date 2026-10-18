package com.estatecrm.crm_service.controller;

import com.estatecrm.crm_service.dto.SalesPolicyDtos.DiscountCalculateRequest;
import com.estatecrm.crm_service.dto.SalesPolicyDtos.DiscountCalculateResponse;
import com.estatecrm.crm_service.dto.SalesPolicyDtos.PolicyOption;
import com.estatecrm.crm_service.service.SalesPolicyService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/sales-policies")
public class SalesPolicyController {

    private final SalesPolicyService salesPolicyService;

    public SalesPolicyController(SalesPolicyService salesPolicyService) {
        this.salesPolicyService = salesPolicyService;
    }

    @GetMapping
    public List<PolicyOption> getAvailablePolicies() {
        return salesPolicyService.getAvailablePolicies();
    }

    @PostMapping("/calculate")
    public DiscountCalculateResponse calculateDiscount(@RequestBody DiscountCalculateRequest request) {
        return salesPolicyService.calculateDiscount(request);
    }
}
