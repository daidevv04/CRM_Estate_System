package com.estatecrm.crm_service.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.estatecrm.crm_service.dto.DealPrintDataResponse;
import com.estatecrm.crm_service.service.DealDocumentService;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DealDocumentControllerTests {

    @Mock
    private DealDocumentService dealDocumentService;

    private DealDocumentController controller;

    @BeforeEach
    void setUp() {
        controller = new DealDocumentController(dealDocumentService);
    }

    @Test
    void testGetPrintData_delegatesToService() {
        UUID dealId = UUID.randomUUID();
        DealPrintDataResponse mockResponse = DealPrintDataResponse.builder()
                .dealId(dealId)
                .contractCode("HD-TEST-001")
                .build();

        when(dealDocumentService.getDealPrintData(dealId)).thenReturn(mockResponse);

        DealPrintDataResponse res = controller.getPrintData(dealId);

        assertNotNull(res);
        assertEquals(dealId, res.getDealId());
        assertEquals("HD-TEST-001", res.getContractCode());
        verify(dealDocumentService).getDealPrintData(dealId);
    }
}
