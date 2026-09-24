package com.estatecrm.crm_service.service;

import com.estatecrm.crm_service.dto.CreateDealPaymentRequest;
import com.estatecrm.crm_service.dto.DealPaymentResponse;
import com.estatecrm.crm_service.entity.Deal;
import com.estatecrm.crm_service.entity.DealPayment;
import com.estatecrm.crm_service.enums.PaymentStatus;
import com.estatecrm.crm_service.repository.DealPaymentRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Thu tien append-only. Khong co update/delete: sua giao dich tai chinh phai la
 * nghiep vu dieu chinh rieng, khong duoc im lang viet lai lich su.
 */
@Service
public class DealPaymentService {

    private final DealPaymentRepository paymentRepository;
    private final DealService dealService;

    public DealPaymentService(DealPaymentRepository paymentRepository, DealService dealService) {
        this.paymentRepository = paymentRepository;
        this.dealService = dealService;
    }

    @Transactional
    public DealPaymentResponse create(
            UUID dealId, CreateDealPaymentRequest request, UUID actorId, String role) {
        if (!dealService.isPrivileged(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only ADMIN/MANAGER can record payments");
        }
        Deal deal = dealService.requireAccessForPayment(dealId, actorId, role);
        BigDecimal paid = paymentRepository.totalPaidByDealId(dealId);
        BigDecimal totalAfterPayment = paid.add(request.amount());
        if (deal.getContractValue() != null && totalAfterPayment.compareTo(deal.getContractValue()) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payment total must not exceed contract value");
        }

        DealPayment payment = new DealPayment();
        payment.setDealId(dealId);
        payment.setAmount(request.amount());
        payment.setPaidAt(request.paidAt());
        payment.setMethod(request.method());
        payment.setReceiptUrl(request.receiptUrl());
        payment.setNote(request.note());
        payment.setCreatedBy(actorId);
        DealPayment saved = paymentRepository.save(payment);

        if (totalAfterPayment.signum() > 0) {
            deal.setPaymentStatus(deal.getContractValue() != null
                    && totalAfterPayment.compareTo(deal.getContractValue()) == 0
                    ? PaymentStatus.PAID
                    : PaymentStatus.PARTIAL);
            deal.setUpdatedBy(actorId);
        }
        return DealPaymentResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<DealPaymentResponse> list(UUID dealId, UUID actorId, String role) {
        dealService.requireAccess(dealId, actorId, role);
        return paymentRepository.findByDealIdOrderByPaidAtAsc(dealId).stream()
                .map(DealPaymentResponse::from)
                .toList();
    }
}
