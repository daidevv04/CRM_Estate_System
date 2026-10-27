package com.estatecrm.crm_service.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.estatecrm.crm_service.dto.CommissionDtos.*;
import com.estatecrm.crm_service.entity.*;
import com.estatecrm.crm_service.enums.CommissionMilestoneStatus;
import com.estatecrm.crm_service.enums.CommissionStatus;
import com.estatecrm.crm_service.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DealCommissionServiceTests {

    @Mock
    private DealCommissionRepository commissionRepository;

    @Mock
    private DealCommissionMilestoneRepository milestoneRepository;

    @Mock
    private ProjectCommissionPolicyRepository policyRepository;

    @Mock
    private DealPaymentRepository paymentRepository;

    @Mock
    private LeadRepository leadRepository;

    @Mock
    private DealRepository dealRepository;

    private DealCommissionService service;

    @BeforeEach
    void setUp() {
        service = new DealCommissionService(
                commissionRepository,
                milestoneRepository,
                policyRepository,
                paymentRepository,
                leadRepository,
                dealRepository,
                new ObjectMapper()
        );
    }

    @Test
    void legacyApprovedCommissionCanBeReadWithoutChangingStatusOrAmounts() {
        DealCommission commission = new DealCommission();
        commission.setId(UUID.randomUUID());
        commission.setStatus(new org.hibernate.type.descriptor.java.EnumJavaType<>(CommissionStatus.class)
                .fromName("APPROVED"));
        commission.setTotalExpected(new BigDecimal("87500000"));
        commission.setTotalPaid(BigDecimal.ZERO);
        when(commissionRepository.count()).thenReturn(1L);
        when(commissionRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(commission));

        Map<String, Object> payload = service.getAllCommissions();
        var response = ((List<DealCommissionResponse>) payload.get("commissions")).get(0);
        assertEquals("APPROVED", response.status().name());
        assertEquals(new BigDecimal("87500000"), response.totalExpected());
        assertEquals(BigDecimal.ZERO, response.totalPaid());
        assertTrue(response.milestones().isEmpty());
        verify(commissionRepository, never()).save(any());
        verify(milestoneRepository, never()).saveAll(any());
    }

    @Test
    void testInitCommissionForDeal_createsCommissionAndMilestones() {
        UUID dealId = UUID.randomUUID();
        UUID salesId = UUID.randomUUID();
        UUID leadId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();

        Deal deal = new Deal();
        deal.setId(dealId);
        deal.setSalesId(salesId);
        deal.setLeadId(leadId);
        deal.setContractValue(new BigDecimal("3500000000")); // 3.5 tỷ

        Lead lead = new Lead();
        lead.setId(leadId);
        lead.setProjectId(projectId);

        when(commissionRepository.findByDealId(dealId)).thenReturn(Optional.empty());
        when(leadRepository.findById(leadId)).thenReturn(Optional.of(lead));
        when(policyRepository.findByProjectId(projectId)).thenReturn(Optional.empty());

        when(commissionRepository.save(any(DealCommission.class))).thenAnswer(inv -> {
            DealCommission c = inv.getArgument(0);
            c.setId(UUID.randomUUID());
            return c;
        });

        when(milestoneRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        DealCommissionResponse res = service.initCommissionForDeal(deal);

        assertNotNull(res);
        assertEquals(0, new BigDecimal("2.50").compareTo(res.commissionRate()));
        assertEquals(0, new BigDecimal("87500000").compareTo(res.commissionAmount())); // 3.5B * 2.5% = 87.5M
        assertEquals(2, res.milestones().size());
        assertEquals(CommissionMilestoneStatus.PENDING_MILESTONE, res.milestones().get(0).status());
    }

    @Test
    void testSyncMilestonesFromPayments_triggersEligibleWhenThresholdReached() {
        UUID dealId = UUID.randomUUID();
        UUID commId = UUID.randomUUID();

        DealCommission commission = new DealCommission();
        commission.setId(commId);
        commission.setDealId(dealId);
        commission.setContractValue(new BigDecimal("3500000000"));

        DealCommissionMilestone m1 = new DealCommissionMilestone();
        m1.setId(UUID.randomUUID());
        m1.setDealCommissionId(commId);
        m1.setMilestoneNo(1);
        m1.setCustomerPaidThresholdPct(new BigDecimal("15.0"));
        m1.setStatus(CommissionMilestoneStatus.PENDING_MILESTONE);

        when(commissionRepository.findByDealId(dealId)).thenReturn(Optional.of(commission));
        // Khách nộp 525 triệu = đúng 15% của 3.5 tỷ
        when(paymentRepository.totalPaidByDealId(dealId)).thenReturn(new BigDecimal("525000000"));
        when(milestoneRepository.findByDealCommissionIdOrderByMilestoneNoAsc(commId)).thenReturn(List.of(m1));

        service.syncMilestonesFromPayments(dealId);

        assertEquals(CommissionMilestoneStatus.ELIGIBLE, m1.getStatus());
        verify(milestoneRepository).saveAll(List.of(m1));
    }

    @Test
    void testApproveAndPayoutMilestone_updatesTotalPaidAndStatus() {
        UUID milestoneId = UUID.randomUUID();
        UUID commId = UUID.randomUUID();
        UUID managerId = UUID.randomUUID();

        DealCommissionMilestone m = new DealCommissionMilestone();
        m.setId(milestoneId);
        m.setDealCommissionId(commId);
        m.setStatus(CommissionMilestoneStatus.ELIGIBLE);
        m.setAmountExpected(new BigDecimal("43750000"));

        DealCommission c = new DealCommission();
        c.setId(commId);
        c.setTotalExpected(new BigDecimal("87500000"));
        c.setTotalPaid(BigDecimal.ZERO);
        c.setStatus(CommissionStatus.PENDING);

        when(milestoneRepository.findById(milestoneId)).thenReturn(Optional.of(m));
        when(milestoneRepository.save(m)).thenReturn(m);

        // Bước 1: Manager duyệt
        DealCommissionMilestoneResponse approvedRes = service.approveMilestone(milestoneId, managerId);
        assertEquals(CommissionMilestoneStatus.APPROVED, approvedRes.status());
        assertEquals(managerId, approvedRes.approvedBy());

        // Bước 2: Kế toán chi trả
        when(commissionRepository.findById(commId)).thenReturn(Optional.of(c));
        when(milestoneRepository.findByDealCommissionIdOrderByMilestoneNoAsc(commId)).thenReturn(List.of(m));
        when(commissionRepository.save(c)).thenReturn(c);

        DealCommissionMilestoneResponse paidRes = service.payoutMilestone(milestoneId, managerId, "UNC số 1024");
        assertEquals(CommissionMilestoneStatus.PAID, paidRes.status());
        assertEquals(new BigDecimal("43750000"), paidRes.amountPaid());
        assertEquals(new BigDecimal("43750000"), c.getTotalPaid());
        assertEquals(CommissionStatus.PARTIALLY_PAID, c.getStatus());
    }
}
