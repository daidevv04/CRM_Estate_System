package com.estatecrm.crm_service.service;

import com.estatecrm.crm_service.dto.CreateLeadRequest;
import com.estatecrm.crm_service.dto.LeadResponse;
import com.estatecrm.crm_service.dto.PipelineStageSummaryResponse;
import com.estatecrm.crm_service.dto.PipelineSummaryResponse;
import com.estatecrm.crm_service.dto.UpdateLeadRequest;
import com.estatecrm.crm_service.entity.Lead;
import com.estatecrm.crm_service.entity.LeadStageHistory;
import com.estatecrm.crm_service.entity.Product;
import com.estatecrm.crm_service.enums.LeadStage;
import com.estatecrm.crm_service.exception.ConflictException;
import com.estatecrm.crm_service.repository.DealRepository;
import com.estatecrm.crm_service.repository.LeadRepository;
import com.estatecrm.crm_service.repository.LeadStageHistoryRepository;
import com.estatecrm.crm_service.repository.ProductRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.BAD_REQUEST;

@Service
public class LeadService {

    private static final String ADMIN = "ADMIN";
    private static final String MANAGER = "MANAGER";

    private final LeadRepository leadRepository;
    private final LeadStageHistoryRepository leadStageHistoryRepository;
    private final ProductRepository productRepository;
    private final DealRepository dealRepository;
    private final ProjectAssignmentService projectAssignmentService;

    public LeadService(
            LeadRepository leadRepository,
            LeadStageHistoryRepository leadStageHistoryRepository,
            ProductRepository productRepository,
            DealRepository dealRepository,
            ProjectAssignmentService projectAssignmentService) {
        this.leadRepository = leadRepository;
        this.leadStageHistoryRepository = leadStageHistoryRepository;
        this.productRepository = productRepository;
        this.dealRepository = dealRepository;
        this.projectAssignmentService = projectAssignmentService;
    }

    @Transactional
    public LeadResponse create(CreateLeadRequest request, UUID actorId, String role) {
        Product product = productRepository.findById(request.productId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Product not found"));
        if (MANAGER.equals(role) && !projectAssignmentService.isAssignedToProject(actorId, product.getProjectId())) {
            throw new ResponseStatusException(FORBIDDEN, "Product belongs to a project you are not assigned to");
        }
        UUID assignedTo = isPrivileged(role) && request.assignedTo() != null
                ? request.assignedTo()
                : actorId;
        Lead lead = new Lead();
        lead.setCustomerId(request.customerId());
        lead.setProductId(request.productId());
        if (request.stage() != null) {
            lead.setStage(request.stage());
        }
        lead.setExpectedValue(request.expectedValue());
        lead.setCloseDate(request.closeDate());
        lead.setAssignedTo(assignedTo);
        lead.setCreatedBy(actorId);
        Lead saved = leadRepository.save(lead);
        recordStage(saved.getId(), null, saved.getStage(), actorId);
        return LeadResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public Page<LeadResponse> list(
            UUID customerId,
            UUID productId,
            UUID assignedTo,
            LeadStage stage,
            LocalDate closeDate,
            UUID actorId,
            String role,
            Pageable pageable) {
        UUID effectiveAssignedTo = isPrivileged(role) ? assignedTo : actorId;
        List<UUID> allowedProductIds = null;
        if (MANAGER.equals(role) && actorId != null) {
            List<UUID> assignedProjectIds = projectAssignmentService.getAssignedProjectIds(actorId);
            if (assignedProjectIds.isEmpty()) {
                return Page.empty(pageable);
            }
            allowedProductIds = productRepository.findIdsByProjectIdIn(assignedProjectIds);
            if (allowedProductIds.isEmpty()) {
                return Page.empty(pageable);
            }
        }
        return leadRepository
                .findAll(LeadRepository.filter(customerId, productId, effectiveAssignedTo, stage, closeDate, allowedProductIds),
                        pageable)
                .map(LeadResponse::from);
    }

    @Transactional(readOnly = true)
    public LeadResponse get(UUID leadId, UUID actorId, String role) {
        return LeadResponse.from(requireAccess(leadId, actorId, role));
    }

    @Transactional
    public LeadResponse update(
            UUID leadId, UpdateLeadRequest request, UUID actorId, String role) {
        Lead lead = requireAccess(leadId, actorId, role);
        LeadStage previousStage = lead.getStage();
        if (request.customerId() != null) {
            lead.setCustomerId(request.customerId());
        }
        if (request.productId() != null) {
            Product newProduct = productRepository.findById(request.productId())
                    .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Product not found"));
            if (MANAGER.equals(role) && !projectAssignmentService.isAssignedToProject(actorId, newProduct.getProjectId())) {
                throw new ResponseStatusException(FORBIDDEN, "Product belongs to a project you are not assigned to");
            }
            lead.setProductId(request.productId());
        }
        if (request.stage() != null) {
            lead.setStage(request.stage());
        }
        if (request.expectedValue() != null) {
            lead.setExpectedValue(request.expectedValue());
        }
        if (request.closeDate() != null) {
            lead.setCloseDate(request.closeDate());
        }
        if (request.assignedTo() != null && isPrivileged(role)) {
            lead.setAssignedTo(request.assignedTo());
        }
        lead.setUpdatedBy(actorId);
        Lead saved = leadRepository.save(lead);
        if (previousStage != saved.getStage()) {
            recordStage(saved.getId(), previousStage, saved.getStage(), actorId);
        }
        return LeadResponse.from(saved);
    }

    @Transactional
    public void delete(UUID leadId, UUID actorId, String role) {
        Lead lead = requireAccess(leadId, actorId, role);
        if (dealRepository.existsByLeadId(leadId)) {
            throw new ConflictException("Lead already has a contract; delete the contract first");
        }
        leadRepository.delete(lead);
    }

    @Transactional(readOnly = true)
    public java.util.List<com.estatecrm.crm_service.dto.LeadStageHistoryResponse> history(
            UUID leadId, UUID actorId, String role) {
        requireAccess(leadId, actorId, role);
        return leadStageHistoryRepository.findByLeadIdOrderByChangedAtAsc(leadId).stream()
                .map(com.estatecrm.crm_service.dto.LeadStageHistoryResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public PipelineSummaryResponse pipelineSummary(
            LocalDate from, LocalDate to, UUID assignedTo, UUID actorId, String role) {
        if (to.isBefore(from)) {
            throw new ResponseStatusException(BAD_REQUEST, "to must be on or after from");
        }
        UUID effectiveAssignedTo = isPrivileged(role) ? assignedTo : actorId;
        List<Lead> leads;
        if (MANAGER.equals(role) && actorId != null) {
            List<UUID> assignedProjectIds = projectAssignmentService.getAssignedProjectIds(actorId);
            if (assignedProjectIds.isEmpty()) {
                leads = List.of();
            } else {
                List<UUID> allowedProductIds = productRepository.findIdsByProjectIdIn(assignedProjectIds);
                if (allowedProductIds.isEmpty()) {
                    leads = List.of();
                } else if (effectiveAssignedTo != null) {
                    leads = leadRepository.findByProductIdInAndAssignedTo(allowedProductIds, effectiveAssignedTo);
                } else {
                    leads = leadRepository.findByProductIdIn(allowedProductIds);
                }
            }
        } else if (effectiveAssignedTo == null) {
            leads = leadRepository.findAll();
        } else {
            leads = leadRepository.findByAssignedTo(effectiveAssignedTo);
        }
        List<UUID> leadIds = leads.stream().map(Lead::getId).toList();
        LocalDateTime fromAt = from.atStartOfDay();
        LocalDateTime toExclusive = to.plusDays(1).atStartOfDay();
        List<LeadStageHistory> history = leadIds.isEmpty()
                ? List.of()
                : leadStageHistoryRepository.findByLeadIdInAndChangedAtGreaterThanEqualAndChangedAtLessThan(
                        leadIds, fromAt, toExclusive);
        Map<LeadStage, List<Lead>> leadsByStage = leads.stream()
                .collect(Collectors.groupingBy(Lead::getStage));
        Map<LeadStage, Long> entriesByStage = history.stream().collect(Collectors.groupingBy(
                LeadStageHistory::getToStage, Collectors.counting()));
        Map<LeadStage, Long> exitsByStage = history.stream()
                .filter(item -> item.getFromStage() != null)
                .collect(Collectors.groupingBy(LeadStageHistory::getFromStage, Collectors.counting()));

        LeadStage[] stages = LeadStage.values();
        List<PipelineStageSummaryResponse> rows = java.util.Arrays.stream(stages).map(stage -> {
            List<Lead> snapshot = leadsByStage.getOrDefault(stage, List.of());
            BigDecimal expected = snapshot.stream().map(Lead::getExpectedValue)
                    .filter(java.util.Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
            int index = stage.ordinal();
            LeadStage previous = index == 0 ? null : stages[index - 1];
            long transitioned = previous == null ? 0 : history.stream()
                    .filter(item -> item.getFromStage() == previous && item.getToStage() == stage).count();
            long leftPrevious = previous == null ? 0 : exitsByStage.getOrDefault(previous, 0L);
            Double rate = previous == null || leftPrevious == 0 ? null : (transitioned * 100.0) / leftPrevious;
            return new PipelineStageSummaryResponse(
                    stage, snapshot.size(), expected, entriesByStage.getOrDefault(stage, 0L), transitioned, rate);
        }).toList();
        return new PipelineSummaryResponse(rows);
    }

    private void recordStage(UUID leadId, LeadStage fromStage, LeadStage toStage, UUID actorId) {
        LeadStageHistory history = new LeadStageHistory();
        history.setLeadId(leadId);
        history.setFromStage(fromStage);
        history.setToStage(toStage);
        history.setChangedBy(actorId);
        leadStageHistoryRepository.save(history);
    }

    @Transactional(readOnly = true)
    public Lead requireAccess(UUID leadId, UUID actorId, String role) {
        Lead lead = leadRepository.findById(leadId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Lead not found"));
        if (ADMIN.equals(role)) {
            return lead;
        }
        if (MANAGER.equals(role) && actorId != null) {
            Product product = productRepository.findById(lead.getProductId()).orElse(null);
            if (product == null || !projectAssignmentService.isAssignedToProject(actorId, product.getProjectId())) {
                throw new ResponseStatusException(FORBIDDEN, "Lead belongs to a project you are not assigned to");
            }
            return lead;
        }
        if (!lead.getAssignedTo().equals(actorId)) {
            throw new ResponseStatusException(FORBIDDEN, "Lead is not assigned to you");
        }
        return lead;
    }

    public boolean isPrivileged(String role) {
        return ADMIN.equals(role) || MANAGER.equals(role);
    }
}
