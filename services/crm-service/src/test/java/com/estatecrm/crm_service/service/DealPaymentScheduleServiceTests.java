package com.estatecrm.crm_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.estatecrm.crm_service.dto.DealPaymentScheduleResponse;
import com.estatecrm.crm_service.entity.Deal;
import com.estatecrm.crm_service.entity.DealPaymentSchedule;
import com.estatecrm.crm_service.enums.ScheduleStatus;
import com.estatecrm.crm_service.repository.DealPaymentRepository;
import com.estatecrm.crm_service.repository.DealPaymentScheduleRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DealPaymentScheduleServiceTests {

    @Mock
    private DealPaymentScheduleRepository scheduleRepository;

    @Mock
    private DealPaymentRepository paymentRepository;

    @Mock
    private DealService dealService;

    @InjectMocks
    private DealPaymentScheduleService service;

    private UUID dealId;
    private UUID actorId;
    private Deal deal;

    @BeforeEach
    void setUp() {
        dealId = UUID.randomUUID();
        actorId = UUID.randomUUID();

        deal = new Deal();
        deal.setId(dealId);
        deal.setContractCode("HD-TEST-001");
        deal.setContractValue(new BigDecimal("3000000000")); // 3 tỷ
        deal.setDepositAmount(new BigDecimal("100000000")); // 100 tr
        deal.setSignedDate(LocalDate.now());
    }

    @Test
    void testApplyTemplate_Construction7_generates7Installments() {
        when(dealService.requireAccess(dealId, actorId, "SALES")).thenReturn(deal);
        List<DealPaymentSchedule> savedList = new ArrayList<>();
        when(scheduleRepository.saveAll(any())).thenAnswer(inv -> {
            List<DealPaymentSchedule> list = inv.getArgument(0);
            savedList.addAll(list);
            return list;
        });
        when(paymentRepository.totalPaidByDealId(dealId)).thenReturn(new BigDecimal("100000000"));
        when(scheduleRepository.findByDealIdOrderByInstallmentNoAsc(dealId)).thenAnswer(inv -> savedList);

        List<DealPaymentScheduleResponse> schedules = service.applyTemplate(dealId, "TEMPLATE_CONSTRUCTION_7", actorId, "SALES");

        assertThat(schedules).hasSize(7);
        assertThat(schedules.get(0).installmentNo()).isEqualTo(1);
        assertThat(schedules.get(0).name()).contains("1");
        assertThat(schedules.get(0).amountDue()).isEqualByComparingTo("100000000");
        assertThat(schedules.get(0).amountPaid()).isEqualByComparingTo("100000000");
        assertThat(schedules.get(0).status()).isEqualTo(ScheduleStatus.PAID);

        // Tổng tiền của 7 đợt phải đúng bằng 3 tỷ
        BigDecimal totalDue = schedules.stream()
                .map(DealPaymentScheduleResponse::amountDue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(totalDue).isEqualByComparingTo("3000000000");
    }

    @Test
    void testFifoCalculation_allocatesPaidAmountAcrossInstallments() {
        when(dealService.requireAccess(dealId, actorId, "SALES")).thenReturn(deal);

        DealPaymentSchedule s1 = new DealPaymentSchedule();
        s1.setId(UUID.randomUUID());
        s1.setDealId(dealId);
        s1.setInstallmentNo(1);
        s1.setName("Đợt 1");
        s1.setAmountDue(new BigDecimal("100000000"));
        s1.setDueDate(LocalDate.now().minusDays(10));

        DealPaymentSchedule s2 = new DealPaymentSchedule();
        s2.setId(UUID.randomUUID());
        s2.setDealId(dealId);
        s2.setInstallmentNo(2);
        s2.setName("Đợt 2");
        s2.setAmountDue(new BigDecimal("350000000"));
        s2.setDueDate(LocalDate.now().plusDays(2)); // Due soon

        when(scheduleRepository.findByDealIdOrderByInstallmentNoAsc(dealId)).thenReturn(List.of(s1, s2));
        // Khách nộp 200tr -> đủ 100tr đợt 1, còn 100tr sang đợt 2 (thiếu 250tr)
        when(paymentRepository.totalPaidByDealId(dealId)).thenReturn(new BigDecimal("200000000"));

        List<DealPaymentScheduleResponse> result = service.getSchedules(dealId, actorId, "SALES");

        assertThat(result).hasSize(2);
        // Đợt 1
        assertThat(result.get(0).amountPaid()).isEqualByComparingTo("100000000");
        assertThat(result.get(0).amountRemaining()).isEqualByComparingTo("0");
        assertThat(result.get(0).status()).isEqualTo(ScheduleStatus.PAID);

        // Đợt 2
        assertThat(result.get(1).amountPaid()).isEqualByComparingTo("100000000");
        assertThat(result.get(1).amountRemaining()).isEqualByComparingTo("250000000");
        assertThat(result.get(1).status()).isEqualTo(ScheduleStatus.DUE_SOON);
    }

    @Test
    void testStatusCalculation_identifiesOverdueWhenDueDatePastAndUnpaid() {
        when(dealService.requireAccess(dealId, actorId, "SALES")).thenReturn(deal);

        DealPaymentSchedule s1 = new DealPaymentSchedule();
        s1.setId(UUID.randomUUID());
        s1.setDealId(dealId);
        s1.setInstallmentNo(1);
        s1.setName("Đợt 1 quá hạn");
        s1.setAmountDue(new BigDecimal("500000000"));
        s1.setDueDate(LocalDate.now().minusDays(5));

        when(scheduleRepository.findByDealIdOrderByInstallmentNoAsc(dealId)).thenReturn(List.of(s1));
        when(paymentRepository.totalPaidByDealId(dealId)).thenReturn(BigDecimal.ZERO);

        List<DealPaymentScheduleResponse> result = service.getSchedules(dealId, actorId, "SALES");

        assertThat(result.get(0).status()).isEqualTo(ScheduleStatus.OVERDUE);
        assertThat(result.get(0).daysDiff()).isEqualTo(5);
    }
}
