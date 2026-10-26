package com.estatecrm.crm_service.service;

import com.estatecrm.crm_service.dto.DealPaymentScheduleResponse;
import com.estatecrm.crm_service.dto.PaymentScheduleItemDto;
import com.estatecrm.crm_service.dto.SaveDealScheduleRequest;
import com.estatecrm.crm_service.entity.Deal;
import com.estatecrm.crm_service.entity.DealPaymentSchedule;
import com.estatecrm.crm_service.enums.ScheduleStatus;
import com.estatecrm.crm_service.repository.DealPaymentRepository;
import com.estatecrm.crm_service.repository.DealPaymentScheduleRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DealPaymentScheduleService {

    public static final String TEMPLATE_CONSTRUCTION_7 = "TEMPLATE_CONSTRUCTION_7";
    public static final String TEMPLATE_FAST_DISCOUNT_3 = "TEMPLATE_FAST_DISCOUNT_3";
    public static final String TEMPLATE_BANK_LOAN_4 = "TEMPLATE_BANK_LOAN_4";

    private final DealPaymentScheduleRepository scheduleRepository;
    private final DealPaymentRepository paymentRepository;
    private final DealService dealService;

    public DealPaymentScheduleService(
            DealPaymentScheduleRepository scheduleRepository,
            DealPaymentRepository paymentRepository,
            DealService dealService) {
        this.scheduleRepository = scheduleRepository;
        this.paymentRepository = paymentRepository;
        this.dealService = dealService;
    }

    @Transactional(readOnly = true)
    public List<DealPaymentScheduleResponse> getSchedules(UUID dealId, UUID actorId, String role) {
        dealService.requireAccess(dealId, actorId, role);
        BigDecimal totalPaid = paymentRepository.totalPaidByDealId(dealId);
        List<DealPaymentSchedule> items = scheduleRepository.findByDealIdOrderByInstallmentNoAsc(dealId);
        return calculateFifo(items, totalPaid);
    }

    @Transactional
    public List<DealPaymentScheduleResponse> saveCustomSchedules(
            UUID dealId, SaveDealScheduleRequest request, UUID actorId, String role) {
        dealService.requireAccess(dealId, actorId, role);
        scheduleRepository.deleteByDealId(dealId);

        List<DealPaymentSchedule> toSave = new ArrayList<>();
        int defaultNo = 1;
        for (PaymentScheduleItemDto dto : request.installments()) {
            DealPaymentSchedule entity = new DealPaymentSchedule();
            entity.setDealId(dealId);
            entity.setInstallmentNo(dto.installmentNo() > 0 ? dto.installmentNo() : defaultNo);
            entity.setName(dto.name());
            entity.setPercentage(dto.percentage());
            entity.setAmountDue(dto.amountDue());
            entity.setDueDate(dto.dueDate());
            entity.setNotes(dto.notes());
            toSave.add(entity);
            defaultNo++;
        }
        scheduleRepository.saveAll(toSave);
        return getSchedules(dealId, actorId, role);
    }

    @Transactional
    public List<DealPaymentScheduleResponse> applyTemplate(
            UUID dealId, String templateCode, UUID actorId, String role) {
        Deal deal = dealService.requireAccess(dealId, actorId, role);
        if (!TEMPLATE_CONSTRUCTION_7.equalsIgnoreCase(templateCode)
                && !TEMPLATE_BANK_LOAN_4.equalsIgnoreCase(templateCode)
                && !TEMPLATE_FAST_DISCOUNT_3.equalsIgnoreCase(templateCode)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mã mẫu tiến độ không hợp lệ");
        }
        BigDecimal contractValue = deal.getContractValue() != null ? deal.getContractValue() : BigDecimal.ZERO;
        if (contractValue.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Giá trị hợp đồng phải lớn hơn 0 để tạo tiến độ");
        }

        LocalDate baseDate = deal.getSignedDate() != null ? deal.getSignedDate() : LocalDate.now();
        BigDecimal deposit = deal.getDepositAmount() != null ? deal.getDepositAmount() : BigDecimal.ZERO;
        BigDecimal maxRatio = TEMPLATE_FAST_DISCOUNT_3.equalsIgnoreCase(templateCode) ? new BigDecimal("0.95")
                : TEMPLATE_BANK_LOAN_4.equalsIgnoreCase(templateCode) ? new BigDecimal("0.30") : new BigDecimal("0.15");
        if (deposit.signum() < 0 || deposit.compareTo(contractValue.multiply(maxRatio)) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Tiền cọc vượt giới hạn của mẫu này. Vui lòng sử dụng tiến độ tùy chỉnh.");
        }

        List<DealPaymentSchedule> items = new ArrayList<>();

        if (TEMPLATE_FAST_DISCOUNT_3.equalsIgnoreCase(templateCode)) {
            // Mẫu nhanh 95%: Đợt 1 Cọc -> Đợt 2: 95% trừ cọc (+15 ngày) -> Đợt 3: 5% Sổ hồng (+180 ngày)
            BigDecimal dot1 = deposit;
            BigDecimal dot3 = contractValue.multiply(new BigDecimal("0.05")).setScale(2, RoundingMode.HALF_UP);
            BigDecimal dot2 = contractValue.subtract(dot1).subtract(dot3);

            items.add(createSchedule(dealId, 1, "Đợt 1: Đặt cọc giữ chỗ căn hộ", calcPct(dot1, contractValue), dot1, baseDate, "Tiền cọc theo hợp đồng; số đã thu được tính từ khoản thanh toán"));
            items.add(createSchedule(dealId, 2, "Đợt 2: Ký HĐMB & Thanh toán 95% (Chiết khấu)", calcPct(dot2, contractValue), dot2, baseDate.plusDays(15), "Thanh toán sớm nhận chiết khấu mở bán"));
            items.add(createSchedule(dealId, 3, "Đợt 3: Nhận Giấy chứng nhận quyền sở hữu (Sổ hồng)", new BigDecimal("5.00"), dot3, baseDate.plusDays(180), "Bàn giao sổ hồng căn hộ"));

        } else if (TEMPLATE_BANK_LOAN_4.equalsIgnoreCase(templateCode)) {
            // Mẫu vay ngân hàng: Đợt 1 Cọc -> Đợt 2 Vốn tự có 30% -> Đợt 3 Ngân hàng giải ngân 65% -> Đợt 4 Sổ hồng 5%
            BigDecimal dot1 = deposit;
            BigDecimal dot2 = contractValue.multiply(new BigDecimal("0.30")).subtract(dot1).setScale(2, RoundingMode.HALF_UP);
            BigDecimal dot3 = contractValue.multiply(new BigDecimal("0.65")).setScale(2, RoundingMode.HALF_UP);
            BigDecimal dot4 = contractValue.subtract(dot1).subtract(dot2).subtract(dot3);

            items.add(createSchedule(dealId, 1, "Đợt 1: Đặt cọc giữ chỗ căn hộ", calcPct(dot1, contractValue), dot1, baseDate, "Tiền cọc giữ chỗ"));
            items.add(createSchedule(dealId, 2, "Đợt 2: Vốn tự có ký HĐMB (Đủ 30%)", calcPct(dot2, contractValue), dot2, baseDate.plusDays(15), "Vốn tự có khách hàng nộp"));
            items.add(createSchedule(dealId, 3, "Đợt 3: Ngân hàng giải ngân 65% (HTLS 0%)", new BigDecimal("65.00"), dot3, baseDate.plusDays(35), "Ngân hàng đối tác giải ngân"));
            items.add(createSchedule(dealId, 4, "Đợt 4: Nhận Sổ hồng căn hộ (5%)", calcPct(dot4, contractValue), dot4, baseDate.plusDays(270), "Nhận sổ hồng hoặc ngân hàng phong tỏa"));

        } else {
            // Mặc định: TEMPLATE_CONSTRUCTION_7 (Tiến độ xây dựng chuẩn 7 đợt)
            BigDecimal dot1 = deposit;
            BigDecimal dot2 = contractValue.multiply(new BigDecimal("0.15")).subtract(dot1).setScale(2, RoundingMode.HALF_UP);
            BigDecimal dot3 = contractValue.multiply(new BigDecimal("0.10")).setScale(2, RoundingMode.HALF_UP);
            BigDecimal dot4 = contractValue.multiply(new BigDecimal("0.10")).setScale(2, RoundingMode.HALF_UP);
            BigDecimal dot5 = contractValue.multiply(new BigDecimal("0.15")).setScale(2, RoundingMode.HALF_UP);
            BigDecimal dot6 = contractValue.multiply(new BigDecimal("0.45")).setScale(2, RoundingMode.HALF_UP);
            BigDecimal dot7 = contractValue.subtract(dot1).subtract(dot2).subtract(dot3).subtract(dot4).subtract(dot5).subtract(dot6);

            items.add(createSchedule(dealId, 1, "Đợt 1: Đặt cọc giữ chỗ căn hộ", calcPct(dot1, contractValue), dot1, baseDate, "Tiền cọc giữ chỗ căn"));
            items.add(createSchedule(dealId, 2, "Đợt 2: Ký HĐMB (Đủ 15% gồm cọc)", calcPct(dot2, contractValue), dot2, baseDate.plusDays(10), "Ký hợp đồng mua bán căn hộ"));
            items.add(createSchedule(dealId, 3, "Đợt 3: Hoàn thành phần móng hầm (10%)", new BigDecimal("10.00"), dot3, baseDate.plusDays(60), "Nghiệm thu phần móng"));
            items.add(createSchedule(dealId, 4, "Đợt 4: Đổ sàn kết cấu tầng 10 (10%)", new BigDecimal("10.00"), dot4, baseDate.plusDays(120), "Thi công phần thân"));
            items.add(createSchedule(dealId, 5, "Đợt 5: Cất nóc tòa nhà (15%)", new BigDecimal("15.00"), dot5, baseDate.plusDays(180), "Hoàn thành cất nóc"));
            items.add(createSchedule(dealId, 6, "Đợt 6: Bàn giao nhận nhà (45%)", new BigDecimal("45.00"), dot6, baseDate.plusDays(270), "Nhận bàn giao căn hộ + phí bảo trì"));
            items.add(createSchedule(dealId, 7, "Đợt 7: Nhận Giấy chứng nhận quyền sở hữu (5%)", calcPct(dot7, contractValue), dot7, baseDate.plusDays(360), "Bàn giao sổ hồng"));
        }

        if (items.stream().anyMatch(item -> item.getAmountDue().signum() < 0)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Không thể chia tiến độ với số tiền hiện tại");
        }
        scheduleRepository.deleteByDealId(dealId);
        scheduleRepository.flush();
        scheduleRepository.saveAll(items);
        return getSchedules(dealId, actorId, role);
    }

    private DealPaymentSchedule createSchedule(
            UUID dealId, int no, String name, BigDecimal pct, BigDecimal amount, LocalDate due, String note) {
        DealPaymentSchedule s = new DealPaymentSchedule();
        s.setDealId(dealId);
        s.setInstallmentNo(no);
        s.setName(name);
        s.setPercentage(pct);
        s.setAmountDue(amount);
        s.setDueDate(due);
        s.setNotes(note);
        return s;
    }

    private BigDecimal calcPct(BigDecimal part, BigDecimal whole) {
        if (whole.compareTo(BigDecimal.ZERO) <= 0) return BigDecimal.ZERO;
        return part.multiply(new BigDecimal("100")).divide(whole, 2, RoundingMode.HALF_UP);
    }

    private List<DealPaymentScheduleResponse> calculateFifo(List<DealPaymentSchedule> items, BigDecimal totalPaid) {
        BigDecimal remainingPool = totalPaid != null ? totalPaid : BigDecimal.ZERO;
        LocalDate today = LocalDate.now();
        List<DealPaymentScheduleResponse> result = new ArrayList<>();

        for (DealPaymentSchedule item : items) {
            BigDecimal allocated = remainingPool.min(item.getAmountDue());
            remainingPool = remainingPool.subtract(allocated);
            BigDecimal amountRemaining = item.getAmountDue().subtract(allocated);

            ScheduleStatus status;
            Long daysDiff = null;

            if (amountRemaining.compareTo(BigDecimal.ZERO) <= 0) {
                status = ScheduleStatus.PAID;
            } else if (item.getDueDate().isBefore(today)) {
                status = ScheduleStatus.OVERDUE;
                daysDiff = ChronoUnit.DAYS.between(item.getDueDate(), today); // > 0: số ngày quá hạn
            } else if (!item.getDueDate().isAfter(today.plusDays(7))) {
                status = ScheduleStatus.DUE_SOON;
                daysDiff = ChronoUnit.DAYS.between(today, item.getDueDate()); // >= 0: số ngày còn lại
            } else {
                status = ScheduleStatus.PENDING;
                daysDiff = ChronoUnit.DAYS.between(today, item.getDueDate());
            }

            result.add(new DealPaymentScheduleResponse(
                    item.getId(),
                    item.getDealId(),
                    item.getInstallmentNo(),
                    item.getName(),
                    item.getPercentage(),
                    item.getAmountDue(),
                    item.getDueDate(),
                    item.getNotes(),
                    allocated,
                    amountRemaining,
                    status,
                    daysDiff
            ));
        }
        return result;
    }
}
