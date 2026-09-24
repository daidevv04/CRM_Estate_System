package com.estatecrm.crm_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.estatecrm.crm_service.dto.CreateDealPaymentRequest;
import com.estatecrm.crm_service.dto.DealPaymentResponse;
import com.estatecrm.crm_service.entity.Deal;
import com.estatecrm.crm_service.entity.DealPayment;
import com.estatecrm.crm_service.enums.PaymentMethod;
import com.estatecrm.crm_service.enums.PaymentStatus;
import com.estatecrm.crm_service.repository.DealPaymentRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class DealPaymentServiceTests {

    private static final UUID DEAL_ID = UUID.randomUUID();
    private static final UUID ACTOR = UUID.randomUUID();

    @Mock
    private DealPaymentRepository paymentRepository;

    @Mock
    private DealService dealService;

    @InjectMocks
    private DealPaymentService paymentService;

    @Test
    void paymentEqualToContractValueMarksDealPaid() {
        Deal deal = deal("100");
        when(dealService.isPrivileged("MANAGER")).thenReturn(true);
        when(dealService.requireAccessForPayment(DEAL_ID, ACTOR, "MANAGER")).thenReturn(deal);
        when(paymentRepository.totalPaidByDealId(DEAL_ID)).thenReturn(new BigDecimal("60"));
        when(paymentRepository.save(any(DealPayment.class))).thenAnswer(call -> call.getArgument(0));

        DealPaymentResponse response = paymentService.create(
                DEAL_ID, request("40"), ACTOR, "MANAGER");

        assertThat(response.amount()).isEqualByComparingTo("40");
        assertThat(deal.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        ArgumentCaptor<DealPayment> captor = ArgumentCaptor.forClass(DealPayment.class);
        verify(paymentRepository).save(captor.capture());
        assertThat(captor.getValue().getCreatedBy()).isEqualTo(ACTOR);
    }

    @Test
    void partialPaymentMarksDealPartial() {
        Deal deal = deal("100");
        when(dealService.isPrivileged("MANAGER")).thenReturn(true);
        when(dealService.requireAccessForPayment(DEAL_ID, ACTOR, "MANAGER")).thenReturn(deal);
        when(paymentRepository.totalPaidByDealId(DEAL_ID)).thenReturn(BigDecimal.ZERO);
        when(paymentRepository.save(any(DealPayment.class))).thenAnswer(call -> call.getArgument(0));

        paymentService.create(DEAL_ID, request("20"), ACTOR, "MANAGER");

        assertThat(deal.getPaymentStatus()).isEqualTo(PaymentStatus.PARTIAL);
    }

    @Test
    void salesCannotRecordPayments() {
        assertThatThrownBy(() -> paymentService.create(DEAL_ID, request("20"), ACTOR, "SALES"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Only ADMIN/MANAGER");
    }

    @Test
    void rejectsPaymentAboveContractValue() {
        Deal deal = deal("100");
        when(dealService.isPrivileged("MANAGER")).thenReturn(true);
        when(dealService.requireAccessForPayment(DEAL_ID, ACTOR, "MANAGER")).thenReturn(deal);
        when(paymentRepository.totalPaidByDealId(DEAL_ID)).thenReturn(new BigDecimal("90"));

        assertThatThrownBy(() -> paymentService.create(DEAL_ID, request("20"), ACTOR, "MANAGER"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("must not exceed");
    }

    private Deal deal(String value) {
        Deal deal = new Deal();
        deal.setId(DEAL_ID);
        deal.setContractValue(new BigDecimal(value));
        deal.setPaymentStatus(PaymentStatus.UNPAID);
        return deal;
    }

    private CreateDealPaymentRequest request(String amount) {
        return new CreateDealPaymentRequest(
                new BigDecimal(amount), LocalDateTime.of(2026, 9, 24, 10, 0),
                PaymentMethod.BANK_TRANSFER, null, null);
    }
}
