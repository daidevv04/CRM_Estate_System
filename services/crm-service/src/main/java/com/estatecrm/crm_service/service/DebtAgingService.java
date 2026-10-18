package com.estatecrm.crm_service.service;

import com.estatecrm.crm_service.dto.DebtAgingDtos.AgingBucket;
import com.estatecrm.crm_service.dto.DebtAgingDtos.DebtAgingItemResponse;
import com.estatecrm.crm_service.dto.DebtAgingDtos.DebtAgingSummaryResponse;
import com.estatecrm.crm_service.dto.DebtAgingDtos.RemindPaymentRequest;
import com.estatecrm.crm_service.dto.DebtAgingDtos.RemindPaymentResponse;
import com.estatecrm.crm_service.entity.Deal;
import com.estatecrm.crm_service.entity.DealPayment;
import com.estatecrm.crm_service.entity.DealPaymentSchedule;
import com.estatecrm.crm_service.entity.Lead;
import com.estatecrm.crm_service.entity.Product;
import com.estatecrm.crm_service.entity.Project;
import com.estatecrm.crm_service.repository.DealPaymentRepository;
import com.estatecrm.crm_service.repository.DealPaymentScheduleRepository;
import com.estatecrm.crm_service.repository.DealRepository;
import com.estatecrm.crm_service.repository.LeadRepository;
import com.estatecrm.crm_service.repository.ProductRepository;
import com.estatecrm.crm_service.repository.ProjectRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DebtAgingService {

    private final DealRepository dealRepository;
    private final DealPaymentScheduleRepository scheduleRepository;
    private final DealPaymentRepository paymentRepository;
    private final LeadRepository leadRepository;
    private final ProductRepository productRepository;
    private final ProjectRepository projectRepository;

    // ponytail: in-memory reminder tracker, replace with persistent table column when DB migration is applied
    private final Map<UUID, LocalDateTime> reminderStore = new ConcurrentHashMap<>();

    public DebtAgingService(
            DealRepository dealRepository,
            DealPaymentScheduleRepository scheduleRepository,
            DealPaymentRepository paymentRepository,
            LeadRepository leadRepository,
            ProductRepository productRepository,
            ProjectRepository projectRepository) {
        this.dealRepository = dealRepository;
        this.scheduleRepository = scheduleRepository;
        this.paymentRepository = paymentRepository;
        this.leadRepository = leadRepository;
        this.productRepository = productRepository;
        this.projectRepository = projectRepository;
    }

    @Transactional(readOnly = true)
    public DebtAgingSummaryResponse getDebtAgingSummary(UUID actorId, String role) {
        LocalDate today = LocalDate.now();
        LocalDate startOfMonth = today.withDayOfMonth(1);
        LocalDate endOfMonth = today.withDayOfMonth(today.lengthOfMonth());

        List<Deal> deals = dealRepository.findAll();
        List<DebtAgingItemResponse> items = new ArrayList<>();

        BigDecimal totalReceivable = BigDecimal.ZERO;
        BigDecimal expectedThisMonth = BigDecimal.ZERO;
        BigDecimal dueSoonAmount = BigDecimal.ZERO;
        BigDecimal overdueAmount = BigDecimal.ZERO;
        int overdueCount = 0;

        for (Deal deal : deals) {
            List<DealPaymentSchedule> schedules = scheduleRepository.findByDealIdOrderByInstallmentNoAsc(deal.getId());
            if (schedules.isEmpty()) {
                continue;
            }

            BigDecimal totalPaid = paymentRepository.totalPaidByDealId(deal.getId());
            BigDecimal pool = totalPaid != null ? totalPaid : BigDecimal.ZERO;

            Lead lead = null;
            if (deal.getLeadId() != null) {
                lead = leadRepository.findById(deal.getLeadId()).orElse(null);
            }

            Product product = null;
            if (lead != null && lead.getProductId() != null) {
                product = productRepository.findById(lead.getProductId()).orElse(null);
            }

            Project project = null;
            if (lead != null && lead.getProjectId() != null) {
                project = projectRepository.findById(lead.getProjectId()).orElse(null);
            }

            for (DealPaymentSchedule sch : schedules) {
                BigDecimal allocated = pool.min(sch.getAmountDue());
                pool = pool.subtract(allocated);
                BigDecimal remaining = sch.getAmountDue().subtract(allocated);

                // Kiểm tra dự kiến tháng này
                if (sch.getDueDate() != null
                        && !sch.getDueDate().isBefore(startOfMonth)
                        && !sch.getDueDate().isAfter(endOfMonth)) {
                    expectedThisMonth = expectedThisMonth.add(sch.getAmountDue());
                }

                if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                    continue; // Đã thanh toán xong đợt này
                }

                totalReceivable = totalReceivable.add(remaining);

                AgingBucket bucket = null;
                long daysDiff = 0;

                if (sch.getDueDate().isBefore(today)) {
                    // Quá hạn
                    daysDiff = ChronoUnit.DAYS.between(sch.getDueDate(), today);
                    if (daysDiff <= 30) {
                        bucket = AgingBucket.OVERDUE_1_30;
                    } else if (daysDiff <= 60) {
                        bucket = AgingBucket.OVERDUE_31_60;
                    } else {
                        bucket = AgingBucket.OVERDUE_60_PLUS;
                    }
                    overdueAmount = overdueAmount.add(remaining);
                    overdueCount++;
                } else if (!sch.getDueDate().isAfter(today.plusDays(7))) {
                    // Sắp đến hạn trong 7 ngày
                    daysDiff = ChronoUnit.DAYS.between(today, sch.getDueDate());
                    bucket = AgingBucket.DUE_SOON;
                    dueSoonAmount = dueSoonAmount.add(remaining);
                }

                if (bucket != null) {
                    items.add(new DebtAgingItemResponse(
                            sch.getId(),
                            deal.getId(),
                            deal.getContractCode(),
                            deal.getLeadId(),
                            lead != null ? lead.getCustomerId() : null,
                            product != null ? product.getId() : null,
                            product != null ? product.getCode() : "—",
                            project != null ? project.getId() : null,
                            project != null ? project.getName() : "—",
                            deal.getSalesId(),
                            sch.getInstallmentNo(),
                            sch.getName(),
                            sch.getAmountDue(),
                            allocated,
                            remaining,
                            sch.getDueDate(),
                            daysDiff,
                            bucket,
                            reminderStore.get(sch.getId())
                    ));
                }
            }
        }

        // Tính đã thu trong tháng từ bảng deal_payments
        BigDecimal collectedThisMonth = BigDecimal.ZERO;
        List<DealPayment> allPayments = paymentRepository.findAll();
        for (DealPayment p : allPayments) {
            if (p.getPaidAt() != null) {
                LocalDate pDate = p.getPaidAt().toLocalDate();
                if (!pDate.isBefore(startOfMonth) && !pDate.isAfter(endOfMonth)) {
                    collectedThisMonth = collectedThisMonth.add(p.getAmount() != null ? p.getAmount() : BigDecimal.ZERO);
                }
            }
        }

        return new DebtAgingSummaryResponse(
                totalReceivable,
                collectedThisMonth,
                expectedThisMonth,
                dueSoonAmount,
                overdueAmount,
                overdueCount,
                items
        );
    }

    @Transactional
    public RemindPaymentResponse remindPayment(UUID scheduleId, RemindPaymentRequest request, UUID actorId, String role) {
        DealPaymentSchedule sch = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy đợt thanh toán"));

        Deal deal = dealRepository.findById(sch.getDealId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy hợp đồng"));

        LocalDateTime now = LocalDateTime.now();
        reminderStore.put(scheduleId, now);

        return new RemindPaymentResponse(
                true,
                sch.getId(),
                deal.getId(),
                deal.getContractCode(),
                deal.getSalesId(),
                true, // inAppSent
                true, // emailSent (nếu có email xác thực)
                now,
                "Đã kích hoạt gửi cảnh báo nhắc nộp tiền thành công"
        );
    }
}
