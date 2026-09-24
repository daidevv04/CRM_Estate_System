package com.estatecrm.crm_service.service;

import com.estatecrm.crm_service.dto.CreateLeadRequest;
import com.estatecrm.crm_service.dto.LeadResponse;
import com.estatecrm.crm_service.dto.UpdateLeadRequest;
import com.estatecrm.crm_service.entity.Lead;
import com.estatecrm.crm_service.entity.LeadStageHistory;
import com.estatecrm.crm_service.enums.LeadStage;
import com.estatecrm.crm_service.exception.ConflictException;
import com.estatecrm.crm_service.repository.DealRepository;
import com.estatecrm.crm_service.repository.LeadRepository;
import com.estatecrm.crm_service.repository.LeadStageHistoryRepository;
import com.estatecrm.crm_service.repository.ProductRepository;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;

/**
 * Lead tiem nang. SALES chi thay/chinh sua lead minh duoc gan (assignedTo),
 * ADMIN/MANAGER thay tat ca. customer_id la UUID tho tro sang customer DB,
 * khong kiem tra ton tai duoc o day (khong co FK va khong goi service khac).
 */
@Service
public class LeadService {

    private static final String ADMIN = "ADMIN";
    private static final String MANAGER = "MANAGER";

    private final LeadRepository leadRepository;
    private final LeadStageHistoryRepository leadStageHistoryRepository;
    private final ProductRepository productRepository;
    private final DealRepository dealRepository;

    public LeadService(
            LeadRepository leadRepository,
            LeadStageHistoryRepository leadStageHistoryRepository,
            ProductRepository productRepository,
            DealRepository dealRepository) {
        this.leadRepository = leadRepository;
        this.leadStageHistoryRepository = leadStageHistoryRepository;
        this.productRepository = productRepository;
        this.dealRepository = dealRepository;
    }

    /**
     * Tao lead. Product phai ton tai (FK cung DB). SALES luon la nguoi duoc gan;
     * ADMIN/MANAGER co the gan cho sales khac hoac de mac dinh la chinh minh.
     */
    @Transactional
    public LeadResponse create(CreateLeadRequest request, UUID actorId, String role) {
        if (!productRepository.existsById(request.productId())) {
            throw new ResponseStatusException(NOT_FOUND, "Product not found");
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

    /** SALES bi ep assignedTo = chinh ho; ADMIN/MANAGER loc tuy chon. */
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
        return leadRepository
                .findAll(LeadRepository.filter(customerId, productId, effectiveAssignedTo, stage, closeDate),
                        pageable)
                .map(LeadResponse::from);
    }

    @Transactional(readOnly = true)
    public LeadResponse get(UUID leadId, UUID actorId, String role) {
        return LeadResponse.from(requireAccess(leadId, actorId, role));
    }

    /**
     * Chinh sua lead. SALES chinh duoc stage/gia/han chot tren lead cua minh;
     * doi nguoi duoc gan (assignedTo) chi ADMIN/MANAGER.
     */
    @Transactional
    public LeadResponse update(
            UUID leadId, UpdateLeadRequest request, UUID actorId, String role) {
        Lead lead = requireAccess(leadId, actorId, role);
        LeadStage previousStage = lead.getStage();
        if (request.customerId() != null) {
            lead.setCustomerId(request.customerId());
        }
        if (request.productId() != null) {
            if (!productRepository.existsById(request.productId())) {
                throw new ResponseStatusException(NOT_FOUND, "Product not found");
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

    /**
     * Xoa lead. Chan truoc neu lead da co hop dong (FK khong cascade) vi mot
     * hop dong da chot khong nen mat doi suon.
     */
    @Transactional
    public void delete(UUID leadId, UUID actorId, String role) {
        Lead lead = requireAccess(leadId, actorId, role);
        if (dealRepository.existsByLeadId(leadId)) {
            throw new ConflictException("Lead already has a contract; delete the contract first");
        }
        leadRepository.delete(lead);
    }

    /** Lich su pipeline, ke thua quyen xem lead hien tai. */
    @Transactional(readOnly = true)
    public java.util.List<com.estatecrm.crm_service.dto.LeadStageHistoryResponse> history(
            UUID leadId, UUID actorId, String role) {
        requireAccess(leadId, actorId, role);
        return leadStageHistoryRepository.findByLeadIdOrderByChangedAtAsc(leadId).stream()
                .map(com.estatecrm.crm_service.dto.LeadStageHistoryResponse::from)
                .toList();
    }

    private void recordStage(UUID leadId, LeadStage fromStage, LeadStage toStage, UUID actorId) {
        LeadStageHistory history = new LeadStageHistory();
        history.setLeadId(leadId);
        history.setFromStage(fromStage);
        history.setToStage(toStage);
        history.setChangedBy(actorId);
        leadStageHistoryRepository.save(history);
    }

    /** SALES chi thay lead minh; ADMIN/MANAGER thay tat ca. */
    @Transactional(readOnly = true)
    public Lead requireAccess(UUID leadId, UUID actorId, String role) {
        Lead lead = leadRepository.findById(leadId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Lead not found"));
        if (!isPrivileged(role) && !lead.getAssignedTo().equals(actorId)) {
            throw new ResponseStatusException(FORBIDDEN, "Lead is not assigned to you");
        }
        return lead;
    }

    public boolean isPrivileged(String role) {
        return ADMIN.equals(role) || MANAGER.equals(role);
    }
}
