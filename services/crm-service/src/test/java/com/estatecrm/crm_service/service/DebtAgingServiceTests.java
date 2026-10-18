package com.estatecrm.crm_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.estatecrm.crm_service.dto.DebtAgingDtos.AgingBucket;
import com.estatecrm.crm_service.dto.DebtAgingDtos.DebtAgingSummaryResponse;
import com.estatecrm.crm_service.dto.DebtAgingDtos.RemindPaymentRequest;
import com.estatecrm.crm_service.dto.DebtAgingDtos.RemindPaymentResponse;
import com.estatecrm.crm_service.entity.Deal;
import com.estatecrm.crm_service.entity.DealPaymentSchedule;
import com.estatecrm.crm_service.repository.DealPaymentRepository;
import com.estatecrm.crm_service.repository.DealPaymentScheduleRepository;
import com.estatecrm.crm_service.repository.DealRepository;
import com.estatecrm.crm_service.repository.LeadRepository;
import com.estatecrm.crm_service.repository.ProductRepository;
import com.estatecrm.crm_service.repository.ProjectRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DebtAgingServiceTests {

    private DealRepository dealRepository;
    private DealPaymentScheduleRepository scheduleRepository;
    private DealPaymentRepository paymentRepository;
    private LeadRepository leadRepository;
    private ProductRepository productRepository;
    private ProjectRepository projectRepository;
    private DebtAgingService debtAgingService;

    @BeforeEach
    void setUp() {
        dealRepository = mock(DealRepository.class);
        scheduleRepository = mock(DealPaymentScheduleRepository.class);
        paymentRepository = mock(DealPaymentRepository.class);
        leadRepository = mock(LeadRepository.class);
        productRepository = mock(ProductRepository.class);
        projectRepository = mock(ProjectRepository.class);

        debtAgingService = new DebtAgingService(
                dealRepository,
                scheduleRepository,
                paymentRepository,
                leadRepository,
                productRepository,
                projectRepository
        );
    }

    @Test
    void getDebtAgingSummary_classifiesDueSoonAndOverdueBucketsCorrectly() {
        UUID dealId = UUID.randomUUID();
        Deal deal = new Deal();
        deal.setId(dealId);
        deal.setContractCode("HD-TEST-001");
        deal.setContractValue(new BigDecimal("2000000000"));

        LocalDate today = LocalDate.now();

        // Đợt 1: Đã trả hết
        DealPaymentSchedule sch1 = new DealPaymentSchedule();
        sch1.setId(UUID.randomUUID());
        sch1.setDealId(dealId);
        sch1.setInstallmentNo(1);
        sch1.setName("Đợt 1: Cọc");
        sch1.setAmountDue(new BigDecimal("100000000"));
        sch1.setDueDate(today.minusDays(40));

        // Đợt 2: Quá hạn 20 ngày (OVERDUE_1_30)
        DealPaymentSchedule sch2 = new DealPaymentSchedule();
        sch2.setId(UUID.randomUUID());
        sch2.setDealId(dealId);
        sch2.setInstallmentNo(2);
        sch2.setName("Đợt 2: Ký HĐMB");
        sch2.setAmountDue(new BigDecimal("200000000"));
        sch2.setDueDate(today.minusDays(20));

        // Đợt 3: Sắp đến hạn trong 5 ngày (DUE_SOON)
        DealPaymentSchedule sch3 = new DealPaymentSchedule();
        sch3.setId(UUID.randomUUID());
        sch3.setDealId(dealId);
        sch3.setInstallmentNo(3);
        sch3.setName("Đợt 3: Xây thô");
        sch3.setAmountDue(new BigDecimal("300000000"));
        sch3.setDueDate(today.plusDays(5));

        when(dealRepository.findAll()).thenReturn(List.of(deal));
        when(scheduleRepository.findByDealIdOrderByInstallmentNoAsc(dealId)).thenReturn(List.of(sch1, sch2, sch3));
        // Khách mới trả 100.000.000 đ (vừa đủ trả đợt 1)
        when(paymentRepository.totalPaidByDealId(dealId)).thenReturn(new BigDecimal("100000000"));
        when(paymentRepository.findAll()).thenReturn(List.of());

        DebtAgingSummaryResponse summary = debtAgingService.getDebtAgingSummary(UUID.randomUUID(), "MANAGER");

        assertThat(summary.totalReceivable()).isEqualTo(new BigDecimal("500000000")); // 200M + 300M
        assertThat(summary.overdueAmount()).isEqualTo(new BigDecimal("200000000"));
        assertThat(summary.dueSoonAmount()).isEqualTo(new BigDecimal("300000000"));
        assertThat(summary.overdueCount()).isEqualTo(1);
        assertThat(summary.items()).hasSize(2);

        // Check sch2 bucket
        assertThat(summary.items().get(0).agingBucket()).isEqualTo(AgingBucket.OVERDUE_1_30);
        assertThat(summary.items().get(0).daysDiff()).isEqualTo(20);

        // Check sch3 bucket
        assertThat(summary.items().get(1).agingBucket()).isEqualTo(AgingBucket.DUE_SOON);
        assertThat(summary.items().get(1).daysDiff()).isEqualTo(5);
    }

    @Test
    void remindPayment_triggersSuccessfully() {
        UUID scheduleId = UUID.randomUUID();
        UUID dealId = UUID.randomUUID();
        DealPaymentSchedule sch = new DealPaymentSchedule();
        sch.setId(scheduleId);
        sch.setDealId(dealId);

        Deal deal = new Deal();
        deal.setId(dealId);
        deal.setContractCode("HD-TEST-REMIND");
        deal.setSalesId(UUID.randomUUID());

        when(scheduleRepository.findById(scheduleId)).thenReturn(Optional.of(sch));
        when(dealRepository.findById(dealId)).thenReturn(Optional.of(deal));

        RemindPaymentResponse resp = debtAgingService.remindPayment(
                scheduleId, new RemindPaymentRequest("Khách hẹn nộp"), UUID.randomUUID(), "MANAGER");

        assertThat(resp.success()).isTrue();
        assertThat(resp.scheduleId()).isEqualTo(scheduleId);
        assertThat(resp.dealId()).isEqualTo(dealId);
        assertThat(resp.inAppSent()).isTrue();
        assertThat(resp.emailSent()).isTrue();
        assertThat(resp.remindedAt()).isNotNull();
    }
}
