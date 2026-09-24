package com.estatecrm.crm_service.service;

import com.estatecrm.crm_service.dto.CreateDealRequest;
import com.estatecrm.crm_service.dto.DealResponse;
import com.estatecrm.crm_service.dto.UpdateDealRequest;
import com.estatecrm.crm_service.entity.Deal;
import com.estatecrm.crm_service.entity.Lead;
import com.estatecrm.crm_service.enums.ApprovalStatus;
import com.estatecrm.crm_service.enums.DealStatus;
import com.estatecrm.crm_service.enums.LeadStage;
import com.estatecrm.crm_service.enums.PaymentStatus;
import com.estatecrm.crm_service.exception.ConflictException;
import com.estatecrm.crm_service.repository.DealRepository;
import com.estatecrm.crm_service.repository.LeadRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;

/**
 * Hop dong. SALES chi thay hop dong minh (salesId); ADMIN/MANAGER thay tat ca.
 * Duyet hop dong (APPROVED/REJECTED) chi ADMIN/MANAGER; khi APPROVED tu dien
 * approvedBy/approvedAt cho khop chk_deals_approved_fields.
 */
@Service
public class DealService {

    private static final String ADMIN = "ADMIN";
    private static final String MANAGER = "MANAGER";

    private final DealRepository dealRepository;
    private final LeadRepository leadRepository;

    public DealService(DealRepository dealRepository, LeadRepository leadRepository) {
        this.dealRepository = dealRepository;
        this.leadRepository = leadRepository;
    }

    /**
     * Tao hop dong cho mot lead. Lead phai dat stage WON (muc 30 docs);
     * 1 lead chi ra 1 hop dong; ma hop dong duy nhat; dat coc khong duoc lon
     * hon gia tri hop dong (truoc khi nem ra DB).
     */
    @Transactional
    public DealResponse create(CreateDealRequest request, UUID actorId, String role) {
        Lead lead = leadRepository.findById(request.leadId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Lead not found"));
        if (lead.getStage() != LeadStage.WON) {
            throw new ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Lead must be WON before creating a contract");
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

    /** SALES bi ep salesId = chinh ho; ADMIN/MANAGER loc tuy chon. */
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
        return dealRepository
                .findAll(DealRepository.filter(
                        leadId, effectiveSalesId, status, paymentStatus, approvalStatus),
                        pageable)
                .map(DealResponse::from);
    }

    @Transactional(readOnly = true)
    public DealResponse get(UUID dealId, UUID actorId, String role) {
        return DealResponse.from(requireAccess(dealId, actorId, role));
    }

    /**
     * Chinh sua hop dong. Doi ma/ nguoi ban/ duyet chi ADMIN/MANAGER; khi chuyen
     * sang APPROVED tu ghi approvedBy/approvedAt.
     */
    @Transactional
    public DealResponse update(
            UUID dealId, UpdateDealRequest request, UUID actorId, String role) {
        Deal deal = requireAccess(dealId, actorId, role);
        boolean privileged = isPrivileged(role);

        if (request.contractCode() != null
                && !request.contractCode().equals(deal.getContractCode())) {
            if (!privileged) {
                throw new ResponseStatusException(
                        FORBIDDEN, "Only ADMIN/MANAGER can change contract code");
            }
            if (dealRepository.existsByContractCode(request.contractCode())) {
                throw new ConflictException("Contract code already exists");
            }
            deal.setContractCode(request.contractCode());
        }
        if (request.contractValue() != null) {
            deal.setContractValue(request.contractValue());
        }
        if (request.depositAmount() != null) {
            deal.setDepositAmount(request.depositAmount());
        }
        // Kiem tra sau khi da gop gia tri moi, truoc khi save.
        requireDepositWithinValue(deal.getDepositAmount(), deal.getContractValue());
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
            }
        }
        deal.setUpdatedBy(actorId);
        return DealResponse.from(dealRepository.save(deal));
    }

    /** Chi ADMIN/MANAGER duoc xoa hop dong (ho so tai chinh khong de chu so huu xoa). */
    @Transactional
    public void delete(UUID dealId, UUID actorId, String role) {
        if (!isPrivileged(role)) {
            throw new ResponseStatusException(FORBIDDEN, "Only ADMIN/MANAGER can delete contracts");
        }
        dealRepository.delete(find(dealId));
    }

    /**
     * Nap hop dong va chan truy cap neu khong phai sales phu trach va khong phai
     * ADMIN/MANAGER. Dung chung cho ContactDetailService de quyen rieng chi
     * nam mot cho (giong CustomerService.requireAccess).
     */
    @Transactional(readOnly = true)
    public Deal requireAccess(UUID dealId, UUID actorId, String role) {
        Deal deal = find(dealId);
        if (!isPrivileged(role) && !deal.getSalesId().equals(actorId)) {
            throw new ResponseStatusException(FORBIDDEN, "Contract is not assigned to you");
        }
        return deal;
    }

    /**
     * Dung cho giao dich tai chinh. PESSIMISTIC_WRITE khoa row deal cho den khi
     * transaction commit, serialize total payment check + insert payment.
     */
    @Transactional
    public Deal requireAccessForPayment(UUID dealId, UUID actorId, String role) {
        Deal deal = dealRepository.findWithLockById(dealId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Contract not found"));
        if (!isPrivileged(role) && !deal.getSalesId().equals(actorId)) {
            throw new ResponseStatusException(FORBIDDEN, "Contract is not assigned to you");
        }
        return deal;
    }

    public boolean isPrivileged(String role) {
        return ADMIN.equals(role) || MANAGER.equals(role);
    }

    private Deal find(UUID dealId) {
        return dealRepository.findById(dealId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Contract not found"));
    }

    /** chk_deals_deposit_within_value: dat coc <= gia tri hop dong khi ca hai co gia tri. */
    private void requireDepositWithinValue(BigDecimal deposit, BigDecimal value) {
        if (deposit != null && value != null && deposit.compareTo(value) > 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Deposit amount must not exceed contract value");
        }
    }
}
