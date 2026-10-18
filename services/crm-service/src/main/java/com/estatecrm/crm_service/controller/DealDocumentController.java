package com.estatecrm.crm_service.controller;

import com.estatecrm.crm_service.dto.DealPrintDataResponse;
import com.estatecrm.crm_service.service.DealDocumentService;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/deals")
public class DealDocumentController {

    private final DealDocumentService dealDocumentService;

    public DealDocumentController(DealDocumentService dealDocumentService) {
        this.dealDocumentService = dealDocumentService;
    }

    @GetMapping("/{dealId}/print-data")
    public DealPrintDataResponse getPrintData(@PathVariable UUID dealId) {
        return dealDocumentService.getDealPrintData(dealId);
    }
}
