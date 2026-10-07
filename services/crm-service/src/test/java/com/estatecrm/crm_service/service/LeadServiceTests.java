package com.estatecrm.crm_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.estatecrm.crm_service.dto.PipelineSummaryResponse;
import com.estatecrm.crm_service.entity.Lead;
import com.estatecrm.crm_service.entity.LeadStageHistory;
import com.estatecrm.crm_service.enums.LeadStage;
import com.estatecrm.crm_service.repository.DealRepository;
import com.estatecrm.crm_service.repository.LeadRepository;
import com.estatecrm.crm_service.repository.LeadStageHistoryRepository;
import com.estatecrm.crm_service.repository.ProductRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LeadServiceTests {

    private static final UUID SALES = UUID.randomUUID();

    @Mock private LeadRepository leadRepository;
    @Mock private LeadStageHistoryRepository historyRepository;
    @Mock private ProductRepository productRepository;
    @Mock private DealRepository dealRepository;
    @InjectMocks private LeadService leadService;

    @Test
    void pipelineUsesStageHistoryAndLimitsSalesToOwnLeads() {
        Lead lead = new Lead();
        lead.setId(UUID.randomUUID());
        lead.setAssignedTo(SALES);
        lead.setStage(LeadStage.CONTACTED);
        lead.setExpectedValue(new BigDecimal("2500000000"));
        LeadStageHistory created = history(lead.getId(), null, LeadStage.NEW);
        LeadStageHistory advanced = history(lead.getId(), LeadStage.NEW, LeadStage.CONTACTED);
        when(leadRepository.findByAssignedTo(SALES)).thenReturn(List.of(lead));
        when(historyRepository.findByLeadIdInAndChangedAtGreaterThanEqualAndChangedAtLessThan(
                org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(created, advanced));

        PipelineSummaryResponse result = leadService.pipelineSummary(
                LocalDate.now(), LocalDate.now(), UUID.randomUUID(), SALES, "SALES");

        assertThat(result.stages().get(1).currentLeadCount()).isEqualTo(1);
        assertThat(result.stages().get(1).transitionFromPreviousCount()).isEqualTo(1);
        assertThat(result.stages().get(1).transitionRate()).isEqualTo(100d);
        assertThat(result.stages().get(1).expectedValue()).isEqualByComparingTo("2500000000");
    }

    private LeadStageHistory history(UUID leadId, LeadStage from, LeadStage to) {
        LeadStageHistory item = new LeadStageHistory();
        item.setLeadId(leadId);
        item.setFromStage(from);
        item.setToStage(to);
        item.setChangedAt(LocalDateTime.now());
        return item;
    }
}