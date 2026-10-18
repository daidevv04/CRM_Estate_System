package com.estatecrm.crm_service.service;

import com.estatecrm.crm_service.dto.SalesPolicyDtos.DiscountCalculateRequest;
import com.estatecrm.crm_service.dto.SalesPolicyDtos.DiscountCalculateResponse;
import com.estatecrm.crm_service.dto.SalesPolicyDtos.PolicyOption;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class SalesPolicyService {

    private static final List<PolicyOption> AVAILABLE_POLICIES = List.of(
            new PolicyOption(
                    "STANDARD",
                    "Thanh toán theo tiến độ chuẩn",
                    "Thanh toán chia thành 7 đợt theo tiến độ hạ tầng / xây dựng, không chiết khấu",
                    new BigDecimal("0.0"),
                    "Đợt 1 (15%) -> Đợt 2-5 (mỗi đợt 15%) -> Đợt 6 (20%) -> Đợt 7 (5%)"
            ),
            new PolicyOption(
                    "FAST_70",
                    "Thanh toán nhanh 70% đợt đầu",
                    "Thanh toán 70% trong vòng 15 ngày kể từ ngày ký HĐMB, chiết khấu 3.5%",
                    new BigDecimal("3.5"),
                    "Đợt 1 nộp 70% (đã trừ CK 3.5%), các đợt sau nộp theo tiến độ nhận sổ"
            ),
            new PolicyOption(
                    "FAST_95",
                    "Thanh toán sớm 95% đợt đầu",
                    "Thanh toán 95% giá trị hợp đồng trong vòng 15 ngày, chiết khấu 6.0%",
                    new BigDecimal("6.0"),
                    "Đợt 1 nộp 95% (đã trừ CK 6.0%), đợt cuối 5% nhận Giấy chứng nhận QSH"
            ),
            new PolicyOption(
                    "BANK_LOAN_SUPPORT",
                    "Gói Vay Ngân Hàng Hỗ Trợ Lãi Suất 0%",
                    "Ngân hàng liên kết giải ngân 70%, CĐT hỗ trợ lãi suất 0% và ân hạn nợ gốc 12 tháng",
                    new BigDecimal("0.0"),
                    "Vốn tự có 30%, ngân hàng giải ngân 70% với lãi suất 0% trong 1 năm đầu"
            )
    );

    public List<PolicyOption> getAvailablePolicies() {
        return AVAILABLE_POLICIES;
    }

    public DiscountCalculateResponse calculateDiscount(DiscountCalculateRequest req) {
        BigDecimal listedPrice = req.listedPrice() != null ? req.listedPrice() : BigDecimal.ZERO;
        if (listedPrice.compareTo(BigDecimal.ZERO) < 0) {
            listedPrice = BigDecimal.ZERO;
        }

        String targetPolicyId = req.policyId() != null ? req.policyId().trim() : "STANDARD";
        PolicyOption selectedPolicy = AVAILABLE_POLICIES.stream()
                .filter(p -> p.id().equalsIgnoreCase(targetPolicyId))
                .findFirst()
                .orElse(AVAILABLE_POLICIES.get(0));

        BigDecimal baseRate = selectedPolicy.discountRate();
        BigDecimal bulkRate = BigDecimal.ZERO;
        int qty = req.bulkQuantity() != null ? req.bulkQuantity() : 1;
        if (qty >= 3) {
            bulkRate = new BigDecimal("2.0");
        } else if (qty == 2) {
            bulkRate = new BigDecimal("1.0");
        }

        BigDecimal loyalRate = Boolean.TRUE.equals(req.isLoyalCustomer()) ? new BigDecimal("1.0") : BigDecimal.ZERO;

        BigDecimal totalRate = baseRate.add(bulkRate).add(loyalRate);

        BigDecimal percentageDiscount = listedPrice.multiply(totalRate)
                .divide(new BigDecimal("100"), 0, RoundingMode.HALF_UP);

        BigDecimal voucher = req.customVoucherAmount() != null && req.customVoucherAmount().compareTo(BigDecimal.ZERO) > 0
                ? req.customVoucherAmount().setScale(0, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        BigDecimal totalDiscount = percentageDiscount.add(voucher);
        if (totalDiscount.compareTo(listedPrice) > 0) {
            totalDiscount = listedPrice;
        }

        BigDecimal netValue = listedPrice.subtract(totalDiscount);
        if (netValue.compareTo(BigDecimal.ZERO) < 0) {
            netValue = BigDecimal.ZERO;
        }

        List<String> breakdowns = new ArrayList<>();
        if (baseRate.compareTo(BigDecimal.ZERO) > 0) {
            breakdowns.add(String.format("Chính sách '%s': Giảm %s%%", selectedPolicy.name(), baseRate));
        }
        if (bulkRate.compareTo(BigDecimal.ZERO) > 0) {
            breakdowns.add(String.format("Chiết khấu mua sỉ (%d sản phẩm): Giảm %s%%", qty, bulkRate));
        }
        if (loyalRate.compareTo(BigDecimal.ZERO) > 0) {
            breakdowns.add("Ưu đãi khách hàng thân thiết / Tri ân: Giảm 1.0%");
        }
        if (voucher.compareTo(BigDecimal.ZERO) > 0) {
            breakdowns.add(String.format("Voucher / Quà tặng mở bán: Trừ thẳng %,d đ", voucher.longValue()));
        }
        if (breakdowns.isEmpty()) {
            breakdowns.add("Áp dụng giá niêm yết chuẩn, không chiết khấu");
        }

        return new DiscountCalculateResponse(
                listedPrice,
                baseRate,
                bulkRate,
                loyalRate,
                totalRate,
                percentageDiscount,
                voucher,
                totalDiscount,
                netValue,
                selectedPolicy.name(),
                breakdowns
        );
    }
}
