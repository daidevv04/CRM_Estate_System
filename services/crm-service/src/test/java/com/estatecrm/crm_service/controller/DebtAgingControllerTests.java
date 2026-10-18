package com.estatecrm.crm_service.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.estatecrm.crm_service.dto.DebtAgingDtos.DebtAgingSummaryResponse;
import com.estatecrm.crm_service.dto.DebtAgingDtos.RemindPaymentRequest;
import com.estatecrm.crm_service.dto.DebtAgingDtos.RemindPaymentResponse;
import com.estatecrm.crm_service.service.DebtAgingService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DebtAgingControllerTests {

    @Mock
    private DebtAgingService debtAgingService;

    private DebtAgingController controller;

    @BeforeEach
    void setUp() {
        controller = new DebtAgingController(debtAgingService);
    }

    @Test
    void getDebtAgingSummary_delegatesToService() {
        DebtAgingSummaryResponse mockSummary = new DebtAgingSummaryResponse(
                new BigDecimal("1000000000"),
                BigDecimal.ZERO,
                new BigDecimal("200000000"),
                new BigDecimal("100000000"),
                new BigDecimal("100000000"),
                1,
                List.of()
        );

        when(debtAgingService.getDebtAgingSummary(any(), any())).thenReturn(mockSummary);

        DebtAgingSummaryResponse res = controller.getDebtAgingSummary(null);

        assertThat(res).isNotNull();
        assertThat(res.totalReceivable()).isEqualTo(new BigDecimal("1000000000"));
        verify(debtAgingService).getDebtAgingSummary(any(), any());
    }

    @Test
    void remindPayment_delegatesToService() {
        UUID scheduleId = UUID.randomUUID();
        UUID dealId = UUID.randomUUID();
        RemindPaymentResponse mockResp = new RemindPaymentResponse(
                true,
                scheduleId,
                dealId,
                "HD-2026-001",
                UUID.randomUUID(),
                true,
                true,
                LocalDateTime.now(),
                "Thành công"
        );

        when(debtAgingService.remindPayment(eq(scheduleId), any(), any(), any())).thenReturn(mockResp);

        RemindPaymentResponse res = controller.remindPayment(scheduleId, new RemindPaymentRequest("Nhắc cọc"), null);

        assertThat(res).isNotNull();
        assertThat(res.success()).isTrue();
        assertThat(res.scheduleId()).isEqualTo(scheduleId);
        verify(debtAgingService).remindPayment(eq(scheduleId), any(), any(), any());
    }
}
