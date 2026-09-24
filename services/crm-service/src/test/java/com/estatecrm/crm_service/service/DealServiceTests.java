package com.estatecrm.crm_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.estatecrm.crm_service.dto.CreateDealRequest;
import com.estatecrm.crm_service.dto.DealResponse;
import com.estatecrm.crm_service.dto.UpdateDealRequest;
import com.estatecrm.crm_service.entity.Deal;
import com.estatecrm.crm_service.entity.Lead;
import com.estatecrm.crm_service.enums.ApprovalStatus;
import com.estatecrm.crm_service.enums.LeadStage;
import com.estatecrm.crm_service.exception.ConflictException;
import com.estatecrm.crm_service.repository.DealRepository;
import com.estatecrm.crm_service.repository.LeadRepository;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class DealServiceTests {

    private static final UUID ACTOR = UUID.randomUUID();
    private static final UUID LEAD_ID = UUID.randomUUID();

    @Mock
    private DealRepository dealRepository;

    @Mock
    private LeadRepository leadRepository;

    @InjectMocks
    private DealService dealService;

    private Lead wonLead() {
        Lead lead = new Lead();
        lead.setId(LEAD_ID);
        lead.setStage(LeadStage.WON);
        lead.setAssignedTo(ACTOR);
        return lead;
    }

    @Test
    void rejectsDealFromLeadThatIsNotWon() {
        Lead lead = wonLead();
        lead.setStage(LeadStage.NEGOTIATION);
        when(leadRepository.findById(LEAD_ID)).thenReturn(Optional.of(lead));

        assertThatThrownBy(() -> dealService.create(request("HD-00", null, null), ACTOR, "SALES"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("must be WON");
        verify(dealRepository, never()).save(any());
    }

    @Test
    void rejectsSecondContractForSameLead() {
        when(leadRepository.findById(LEAD_ID)).thenReturn(Optional.of(wonLead()));
        when(dealRepository.existsByLeadId(LEAD_ID)).thenReturn(true);

        assertThatThrownBy(() -> dealService.create(request("HD-01", null, null), ACTOR, "SALES"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already has a contract");
        verify(dealRepository, never()).save(any());
    }

    @Test
    void salesCannotAssignContractToSomeoneElse() {
        UUID otherSales = UUID.randomUUID();
        when(leadRepository.findById(LEAD_ID)).thenReturn(Optional.of(wonLead()));
        when(dealRepository.existsByLeadId(LEAD_ID)).thenReturn(false);
        when(dealRepository.existsByContractCode("HD-02")).thenReturn(false);
        when(dealRepository.save(any(Deal.class))).thenAnswer(invocation -> invocation.getArgument(0));

        dealService.create(request("HD-02", null, otherSales), ACTOR, "SALES");

        ArgumentCaptor<Deal> captor = ArgumentCaptor.forClass(Deal.class);
        verify(dealRepository).save(captor.capture());
        assertThat(captor.getValue().getSalesId()).isEqualTo(ACTOR);
    }

    @Test
    void rejectsDepositLargerThanContractValue() {
        when(leadRepository.findById(LEAD_ID)).thenReturn(Optional.of(wonLead()));
        when(dealRepository.existsByLeadId(LEAD_ID)).thenReturn(false);
        when(dealRepository.existsByContractCode("HD-03")).thenReturn(false);

        assertThatThrownBy(() -> dealService.create(
                        request("HD-03", new BigDecimal("120"), null), ACTOR, "SALES"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Deposit amount must not exceed contract value");
        verify(dealRepository, never()).save(any());
    }

    @Test
    void salesCannotApproveContract() {
        Deal deal = new Deal();
        deal.setId(UUID.randomUUID());
        deal.setLeadId(LEAD_ID);
        deal.setSalesId(ACTOR);
        deal.setContractCode("HD-04");
        when(dealRepository.findById(deal.getId())).thenReturn(Optional.of(deal));

        assertThatThrownBy(() -> dealService.update(
                        deal.getId(),
                        new UpdateDealRequest(
                                null, null, null, null, null, null, null,
                                ApprovalStatus.APPROVED, null, null, null, null),
                        ACTOR, "SALES"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Only ADMIN/MANAGER");
        assertThat(deal.getApprovalStatus()).isEqualTo(ApprovalStatus.PENDING);
    }

    @Test
    void managerApprovalStampsApprovedByAndAt() {
        Deal deal = new Deal();
        deal.setId(UUID.randomUUID());
        deal.setLeadId(LEAD_ID);
        deal.setSalesId(UUID.randomUUID());
        deal.setContractCode("HD-05");
        when(dealRepository.findById(deal.getId())).thenReturn(Optional.of(deal));
        when(dealRepository.save(any(Deal.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DealResponse response = dealService.update(
                deal.getId(),
                new UpdateDealRequest(
                        null, null, null, null, null, null, null,
                        ApprovalStatus.APPROVED, null, null, null, null),
                ACTOR, "MANAGER");

        assertThat(response.approvalStatus()).isEqualTo(ApprovalStatus.APPROVED);
        assertThat(response.approvedBy()).isEqualTo(ACTOR);
        assertThat(response.approvedAt()).isNotNull();
    }

    private CreateDealRequest request(String code, BigDecimal deposit, UUID salesId) {
        return new CreateDealRequest(
                LEAD_ID, code, new BigDecimal("100"), deposit,
                null, null, null, null, null, null, null, salesId);
    }
}
