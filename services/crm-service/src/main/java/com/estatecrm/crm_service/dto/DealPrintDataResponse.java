package com.estatecrm.crm_service.dto;

import com.estatecrm.crm_service.enums.ApprovalStatus;
import com.estatecrm.crm_service.enums.DealStatus;
import com.estatecrm.crm_service.enums.PaymentStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DealPrintDataResponse {

    private UUID dealId;
    private String contractCode;
    private BigDecimal contractValue;
    private BigDecimal depositAmount;
    private LocalDate depositDate;
    private LocalDate signedDate;
    private String paymentMethod;
    private PaymentStatus paymentStatus;
    private DealStatus dealStatus;
    private ApprovalStatus approvalStatus;
    private String note;

    private UUID leadId;
    private UUID customerId;
    private UUID salesId;

    private CompanyLegalInfo company;
    private ProductPrintDto product;
    private ProjectPrintDto project;
    private List<PaymentPrintDto> payments;
    private List<SchedulePrintDto> schedules;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CompanyLegalInfo {
        private String companyName;
        private String brandName;
        private String taxCode;
        private String headquarterAddress;
        private String hotline;
        private String website;
        private String representativeName;
        private String representativePosition;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProductPrintDto {
        private UUID id;
        private String code;
        private String block;
        private String type;
        private BigDecimal price;
        private BigDecimal area;
        private Integer bedroom;
        private String direction;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProjectPrintDto {
        private UUID id;
        private String name;
        private String location;
        private String investor;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentPrintDto {
        private UUID id;
        private BigDecimal amount;
        private LocalDateTime paidAt;
        private String method;
        private String receiptUrl;
        private String note;
        private UUID createdBy;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SchedulePrintDto {
        private UUID id;
        private Integer installmentNo;
        private String name;
        private BigDecimal percentage;
        private BigDecimal amountDue;
        private LocalDate dueDate;
        private String notes;
    }
}
