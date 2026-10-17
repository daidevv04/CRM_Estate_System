package com.estatecrm.crm_service.service;

import com.estatecrm.crm_service.dto.CreateDealRequest;
import com.estatecrm.crm_service.dto.DealResponse;
import com.estatecrm.crm_service.dto.UpdateDealRequest;
import com.estatecrm.crm_service.entity.Deal;
import com.estatecrm.crm_service.entity.Lead;
import com.estatecrm.crm_service.entity.Product;
import com.estatecrm.crm_service.enums.ApprovalStatus;
import com.estatecrm.crm_service.enums.DealStatus;
import com.estatecrm.crm_service.enums.LeadStage;
import com.estatecrm.crm_service.enums.PaymentStatus;
import com.estatecrm.crm_service.enums.ProductStatus;
import com.estatecrm.crm_service.exception.ConflictException;
import com.estatecrm.crm_service.repository.DealRepository;
import com.estatecrm.crm_service.repository.LeadRepository;
import com.estatecrm.crm_service.repository.ProductRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class DealService {

    private static final String ADMIN = "ADMIN";
    private static final String MANAGER = "MANAGER";

    private final DealRepository dealRepository;
    private final LeadRepository leadRepository;
    private final ProductRepository productRepository;
    private final ProjectAssignmentService projectAssignmentService;

    public DealService(
            DealRepository dealRepository,
            LeadRepository leadRepository,
            ProductRepository productRepository,
            ProjectAssignmentService projectAssignmentService) {
        this.dealRepository = dealRepository;
        this.leadRepository = leadRepository;
        this.productRepository = productRepository;
        this.projectAssignmentService = projectAssignmentService;
    }

    @Transactional
    public DealResponse create(CreateDealRequest request, UUID actorId, String role) {
        Lead lead = leadRepository.findById(request.leadId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Lead not found"));
        if (lead.getStage() != LeadStage.WON) {
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "Lead must be WON before creating a contract");
        }
        if (lead.getProductId() == null) {
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "Lead must have an assigned product before creating a contract");
        }
        if (MANAGER.equals(role) && actorId != null) {
            if (!projectAssignmentService.isAssignedToProject(actorId, lead.getProjectId())) {
                throw new ResponseStatusException(FORBIDDEN, "Lead belongs to a project you are not assigned to");
            }
        }
        Product product = productRepository.findById(lead.getProductId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Product not found"));
        if (product.getStatus() == ProductStatus.SOLD) {
            throw new ConflictException("Product has already been sold");
        }
        if (dealRepository.existsByLeadId(request.leadId())) {
            throw new ConflictException("Lead already has a contract");
        }
        if (dealRepository.existsByContractCode(request.contractCode())) {
            throw new ConflictException("Contract code already exists");
        }
        requireDepositWithinValue(request.depositAmount(), request.contractValue());

        UUID salesId = isPrivileged(role) && request.salesId() != null
                ? request.salesId()
                : actorId;
        Deal deal = new Deal();
        deal.setLeadId(request.leadId());
        deal.setSalesId(salesId);
        deal.setContractCode(request.contractCode());
        deal.setContractValue(request.contractValue());
        deal.setDepositAmount(request.depositAmount());
        deal.setDepositDate(request.depositDate());
        deal.setSignedDate(request.signedDate());
        if (request.paymentStatus() != null) {
            deal.setPaymentStatus(request.paymentStatus());
        }
        deal.setPaymentMethod(request.paymentMethod() == null ? null : request.paymentMethod().name());
        deal.setFileUrl(request.fileUrl());
        if (request.status() != null) {
            deal.setStatus(request.status());
        }
        deal.setNote(request.note());
        deal.setCreatedBy(actorId);
        return DealResponse.from(dealRepository.save(deal));
    }

    @Transactional(readOnly = true)
    public Page<DealResponse> list(
            UUID leadId,
            UUID salesId,
            DealStatus status,
            PaymentStatus paymentStatus,
            ApprovalStatus approvalStatus,
            UUID actorId,
            String role,
            Pageable pageable) {
        UUID effectiveSalesId = isPrivileged(role) ? salesId : actorId;
        List<UUID> allowedLeadIds = null;
        if (MANAGER.equals(role) && actorId != null) {
            List<UUID> assignedProjectIds = projectAssignmentService.getAssignedProjectIds(actorId);
            if (assignedProjectIds.isEmpty()) {
                return Page.empty(pageable);
            }
            allowedLeadIds = leadRepository.findIdsByProjectIdIn(assignedProjectIds);
            if (allowedLeadIds.isEmpty()) {
                return Page.empty(pageable);
            }
        }
        return dealRepository
                .findAll(DealRepository.filter(
                        leadId, effectiveSalesId, status, paymentStatus, approvalStatus, allowedLeadIds),
                        pageable)
                .map(DealResponse::from);
    }

    @Transactional(readOnly = true)
    public DealResponse get(UUID dealId, UUID actorId, String role) {
        return DealResponse.from(requireAccess(dealId, actorId, role));
    }

    @Transactional
    public DealResponse update(
            UUID dealId, UpdateDealRequest request, UUID actorId, String role) {
        Deal deal = requireAccess(dealId, actorId, role);
        boolean privileged = isPrivileged(role);
        ApprovalStatus previousApproval = deal.getApprovalStatus();
        DealStatus previousStatus = deal.getStatus();

        if (request.contractValue() != null) {
            BigDecimal effectiveDeposit = request.depositAmount() != null
                    ? request.depositAmount()
                    : deal.getDepositAmount();
            requireDepositWithinValue(effectiveDeposit, request.contractValue());
            deal.setContractValue(request.contractValue());
        }
        if (request.depositAmount() != null) {
            BigDecimal effectiveValue = request.contractValue() != null
                    ? request.contractValue()
                    : deal.getContractValue();
            requireDepositWithinValue(request.depositAmount(), effectiveValue);
            deal.setDepositAmount(request.depositAmount());
        }
        if (request.depositDate() != null) {
            deal.setDepositDate(request.depositDate());
        }
        if (request.signedDate() != null) {
            deal.setSignedDate(request.signedDate());
        }
        if (request.paymentStatus() != null) {
            deal.setPaymentStatus(request.paymentStatus());
        }
        if (request.paymentMethod() != null) {
            deal.setPaymentMethod(request.paymentMethod().name());
        }
        if (request.fileUrl() != null) {
            deal.setFileUrl(request.fileUrl());
        }
        if (request.status() != null) {
            deal.setStatus(request.status());
        }
        if (request.note() != null) {
            deal.setNote(request.note());
        }
        if (request.salesId() != null && privileged) {
            deal.setSalesId(request.salesId());
        }

        if (request.approvalStatus() != null
                && request.approvalStatus() != deal.getApprovalStatus()) {
            if (!privileged) {
                throw new ResponseStatusException(
                        FORBIDDEN, "Only ADMIN/MANAGER can approve or reject contracts");
            }
            deal.setApprovalStatus(request.approvalStatus());
            if (request.approvalStatus() == ApprovalStatus.APPROVED) {
                deal.setApprovedBy(actorId);
                deal.setApprovedAt(LocalDateTime.now());
                syncProductStatus(deal.getLeadId(), ProductStatus.SOLD);
            } else if (previousApproval == ApprovalStatus.APPROVED && request.approvalStatus() == ApprovalStatus.REJECTED) {
                syncProductStatus(deal.getLeadId(), ProductStatus.AVAILABLE);
            }
        }

        // If status changed to CANCELLED on an already approved deal, revert product to AVAILABLE
        if (deal.getApprovalStatus() == ApprovalStatus.APPROVED && deal.getStatus() == DealStatus.CANCELLED && previousStatus != DealStatus.CANCELLED) {
            syncProductStatus(deal.getLeadId(), ProductStatus.AVAILABLE);
        }

        deal.setUpdatedBy(actorId);
        return DealResponse.from(dealRepository.save(deal));
    }

    @Transactional
    public void delete(UUID dealId, UUID actorId, String role) {
        if (!isPrivileged(role)) {
            throw new ResponseStatusException(FORBIDDEN, "Only ADMIN/MANAGER can delete contracts");
        }
        Deal deal = requireAccess(dealId, actorId, role);
        if (deal.getApprovalStatus() == ApprovalStatus.APPROVED) {
            syncProductStatus(deal.getLeadId(), ProductStatus.AVAILABLE);
        }
        dealRepository.delete(deal);
    }

    private void syncProductStatus(UUID leadId, ProductStatus targetStatus) {
        Lead lead = leadRepository.findById(leadId).orElse(null);
        if (lead != null && lead.getProductId() != null) {
            Product product = productRepository.findById(lead.getProductId()).orElse(null);
            if (product != null) {
                product.setStatus(targetStatus);
                productRepository.save(product);
            }
        }
    }

    @Transactional(readOnly = true)
    public Deal requireAccess(UUID dealId, UUID actorId, String role) {
        Deal deal = find(dealId);
        if (ADMIN.equals(role)) {
            return deal;
        }
        if (MANAGER.equals(role) && actorId != null) {
            Lead lead = leadRepository.findById(deal.getLeadId()).orElse(null);
            if (lead == null || !projectAssignmentService.isAssignedToProject(actorId, lead.getProjectId())) {
                throw new ResponseStatusException(FORBIDDEN, "Deal belongs to a project you are not assigned to");
            }
            return deal;
        }
        if (!deal.getSalesId().equals(actorId)) {
            throw new ResponseStatusException(FORBIDDEN, "Contract is not assigned to you");
        }
        return deal;
    }

    @Transactional
    public Deal requireAccessForPayment(UUID dealId, UUID actorId, String role) {
        Deal deal = dealRepository.findWithLockById(dealId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Deal not found"));
        if (ADMIN.equals(role)) {
            return deal;
        }
        if (MANAGER.equals(role) && actorId != null) {
            Lead lead = leadRepository.findById(deal.getLeadId()).orElse(null);
            if (lead == null || !projectAssignmentService.isAssignedToProject(actorId, lead.getProjectId())) {
                throw new ResponseStatusException(FORBIDDEN, "Deal belongs to a project you are not assigned to");
            }
            return deal;
        }
        if (!deal.getSalesId().equals(actorId)) {
            throw new ResponseStatusException(FORBIDDEN, "Contract is not assigned to you");
        }
        return deal;
    }

    private Deal find(UUID id) {
        return dealRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Deal not found"));
    }

    private void requireDepositWithinValue(BigDecimal deposit, BigDecimal value) {
        if (deposit != null && value != null && deposit.compareTo(value) > 0) {
            throw new ResponseStatusException(
                    BAD_REQUEST, "Deposit amount must not exceed contract value");
        }
    }

    public boolean isPrivileged(String role) {
        return ADMIN.equals(role) || MANAGER.equals(role);
    }
}
