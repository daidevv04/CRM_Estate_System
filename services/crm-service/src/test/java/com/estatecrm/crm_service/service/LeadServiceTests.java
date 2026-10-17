package com.estatecrm.crm_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.estatecrm.crm_service.dto.CreateLeadRequest;
import com.estatecrm.crm_service.dto.LeadResponse;
import com.estatecrm.crm_service.dto.PipelineSummaryResponse;
import com.estatecrm.crm_service.entity.Lead;
import com.estatecrm.crm_service.entity.LeadStageHistory;
import com.estatecrm.crm_service.entity.Product;
import com.estatecrm.crm_service.enums.LeadStage;
import com.estatecrm.crm_service.repository.DealRepository;
import com.estatecrm.crm_service.repository.LeadRepository;
import com.estatecrm.crm_service.repository.LeadStageHistoryRepository;
import com.estatecrm.crm_service.repository.ProductRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class LeadServiceTests {

    private static final UUID SALES = UUID.randomUUID();

    @Mock private LeadRepository leadRepository;
    @Mock private LeadStageHistoryRepository historyRepository;
    @Mock private ProductRepository productRepository;
    @Mock private DealRepository dealRepository;
    @Mock private ProjectAssignmentService projectAssignmentService;
    @InjectMocks private LeadService leadService;

    @Test
    void managerWithNoAssignedProjectsGetsEmptyLeadList() {
        UUID managerId = UUID.randomUUID();
        when(projectAssignmentService.getAssignedProjectIds(managerId)).thenReturn(List.of());

        Page<LeadResponse> result =
                leadService.list(null, null, null, null, null, null, managerId, "MANAGER", PageRequest.of(0, 10));

        assertThat(result.getContent()).isEmpty();
    }

    @Test
    void createLeadWithoutProductSucceeds() {
        UUID customerId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        CreateLeadRequest request = new CreateLeadRequest(
                customerId,
                projectId,
                null, // productId is null
                "Khách tìm căn 2PN hướng Đông Nam",
                LeadStage.NEW,
                new BigDecimal("3500000000"),
                LocalDate.now().plusMonths(1),
                null);

        when(leadRepository.save(any(Lead.class))).thenAnswer(inv -> {
            Lead l = inv.getArgument(0);
            l.setId(UUID.randomUUID());
            return l;
        });

        LeadResponse response = leadService.create(request, SALES, "SALES");

        assertThat(response).isNotNull();
        assertThat(response.projectId()).isEqualTo(projectId);
        assertThat(response.productId()).isNull();
        assertThat(response.demandNotes()).isEqualTo("Khách tìm căn 2PN hướng Đông Nam");
        assertThat(response.assignedTo()).isEqualTo(SALES);

        ArgumentCaptor<Lead> captor = ArgumentCaptor.forClass(Lead.class);
        verify(leadRepository).save(captor.capture());
        assertThat(captor.getValue().getProjectId()).isEqualTo(projectId);
        assertThat(captor.getValue().getProductId()).isNull();
        assertThat(captor.getValue().getDemandNotes()).isEqualTo("Khách tìm căn 2PN hướng Đông Nam");
    }

    @Test
    void createLeadWithProductFromDifferentProjectThrowsBadRequest() {
        UUID customerId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID otherProjectId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        Product product = new Product();
        product.setId(productId);
        product.setProjectId(otherProjectId);

        when(productRepository.findById(productId)).thenReturn(Optional.of(product));

        CreateLeadRequest request = new CreateLeadRequest(
                customerId,
                projectId,
                productId,
                null,
                LeadStage.NEW,
                null,
                null,
                null);

        assertThatThrownBy(() -> leadService.create(request, SALES, "SALES"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Product does not belong to the selected project");
    }

    @Test
    void managerCreatingLeadForUnassignedProjectThrowsForbidden() {
        UUID managerId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();

        when(projectAssignmentService.isAssignedToProject(managerId, projectId)).thenReturn(false);

        CreateLeadRequest request = new CreateLeadRequest(
                UUID.randomUUID(),
                projectId,
                null,
                null,
                LeadStage.NEW,
                null,
                null,
                null);

        assertThatThrownBy(() -> leadService.create(request, managerId, "MANAGER"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Project you are not assigned to");
    }

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