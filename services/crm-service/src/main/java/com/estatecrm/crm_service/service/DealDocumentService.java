package com.estatecrm.crm_service.service;

import com.estatecrm.crm_service.dto.DealPrintDataResponse;
import com.estatecrm.crm_service.dto.DealPrintDataResponse.CompanyLegalInfo;
import com.estatecrm.crm_service.dto.DealPrintDataResponse.PaymentPrintDto;
import com.estatecrm.crm_service.dto.DealPrintDataResponse.ProductPrintDto;
import com.estatecrm.crm_service.dto.DealPrintDataResponse.ProjectPrintDto;
import com.estatecrm.crm_service.dto.DealPrintDataResponse.SchedulePrintDto;
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
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DealDocumentService {

    private final DealRepository dealRepository;
    private final LeadRepository leadRepository;
    private final ProductRepository productRepository;
    private final ProjectRepository projectRepository;
    private final DealPaymentRepository dealPaymentRepository;
    private final DealPaymentScheduleRepository dealPaymentScheduleRepository;

    public DealDocumentService(
            DealRepository dealRepository,
            LeadRepository leadRepository,
            ProductRepository productRepository,
            ProjectRepository projectRepository,
            DealPaymentRepository dealPaymentRepository,
            DealPaymentScheduleRepository dealPaymentScheduleRepository) {
        this.dealRepository = dealRepository;
        this.leadRepository = leadRepository;
        this.productRepository = productRepository;
        this.projectRepository = projectRepository;
        this.dealPaymentRepository = dealPaymentRepository;
        this.dealPaymentScheduleRepository = dealPaymentScheduleRepository;
    }

    @Transactional(readOnly = true)
    public DealPrintDataResponse getDealPrintData(UUID dealId) {
        Deal deal = dealRepository.findById(dealId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy hợp đồng"));

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

        List<DealPayment> payments = dealPaymentRepository.findByDealIdOrderByPaidAtAsc(dealId);
        List<DealPaymentSchedule> schedules = dealPaymentScheduleRepository.findByDealIdOrderByInstallmentNoAsc(dealId);

        CompanyLegalInfo company = CompanyLegalInfo.builder()
                .companyName("TỔNG CÔNG TY CỔ PHẦN ĐẤT XANH MIỀN TRUNG")
                .brandName("REGAL GROUP")
                .taxCode("0401412898")
                .headquarterAddress("Số 52-54 Võ Văn Kiệt, Phường An Hải Đông, Quận Sơn Trà, TP. Đà Nẵng")
                .hotline("1900 234 567 - (0236) 6266 266")
                .website("https://regalgroup.vn")
                .representativeName("Trần Ngọc Thái")
                .representativePosition("Phó Tổng Giám Đốc Khối Phát Triển Dự Án")
                .build();

        ProductPrintDto productDto = null;
        if (product != null) {
            productDto = ProductPrintDto.builder()
                    .id(product.getId())
                    .code(product.getCode())
                    .block(product.getBlock())
                    .type(product.getType() != null ? product.getType().name() : null)
                    .price(product.getPrice())
                    .area(product.getArea())
                    .bedroom(product.getBedroom())
                    .direction(product.getDirection())
                    .build();
        }

        ProjectPrintDto projectDto = null;
        if (project != null) {
            projectDto = ProjectPrintDto.builder()
                    .id(project.getId())
                    .name(project.getName())
                    .location(project.getLocation())
                    .investor(project.getInvestor() != null ? project.getInvestor() : "Tập đoàn Regal Group")
                    .build();
        }

        List<PaymentPrintDto> paymentDtos = payments.stream()
                .map(p -> PaymentPrintDto.builder()
                        .id(p.getId())
                        .amount(p.getAmount())
                        .paidAt(p.getPaidAt())
                        .method(p.getMethod() != null ? p.getMethod().name() : null)
                        .receiptUrl(p.getReceiptUrl())
                        .note(p.getNote())
                        .createdBy(p.getCreatedBy())
                        .build())
                .collect(Collectors.toList());

        List<SchedulePrintDto> scheduleDtos = schedules.stream()
                .map(s -> SchedulePrintDto.builder()
                        .id(s.getId())
                        .installmentNo(s.getInstallmentNo())
                        .name(s.getName())
                        .percentage(s.getPercentage())
                        .amountDue(s.getAmountDue())
                        .dueDate(s.getDueDate())
                        .notes(s.getNotes())
                        .build())
                .collect(Collectors.toList());

        return DealPrintDataResponse.builder()
                .dealId(deal.getId())
                .contractCode(deal.getContractCode())
                .contractValue(deal.getContractValue())
                .depositAmount(deal.getDepositAmount())
                .depositDate(deal.getDepositDate())
                .signedDate(deal.getSignedDate())
                .paymentMethod(deal.getPaymentMethod())
                .paymentStatus(deal.getPaymentStatus())
                .dealStatus(deal.getStatus())
                .approvalStatus(deal.getApprovalStatus())
                .note(deal.getNote())
                .leadId(deal.getLeadId())
                .customerId(lead != null ? lead.getCustomerId() : null)
                .salesId(deal.getSalesId())
                .company(company)
                .product(productDto)
                .project(projectDto)
                .payments(paymentDtos)
                .schedules(scheduleDtos)
                .build();
    }
}
