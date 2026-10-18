package com.estatecrm.crm_service.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.estatecrm.crm_service.dto.DealPrintDataResponse;
import com.estatecrm.crm_service.entity.Deal;
import com.estatecrm.crm_service.entity.DealPayment;
import com.estatecrm.crm_service.entity.DealPaymentSchedule;
import com.estatecrm.crm_service.entity.Lead;
import com.estatecrm.crm_service.entity.Product;
import com.estatecrm.crm_service.entity.Project;
import com.estatecrm.crm_service.enums.ApprovalStatus;
import com.estatecrm.crm_service.enums.DealStatus;
import com.estatecrm.crm_service.enums.PaymentStatus;
import com.estatecrm.crm_service.enums.ProductType;
import com.estatecrm.crm_service.repository.DealPaymentRepository;
import com.estatecrm.crm_service.repository.DealPaymentScheduleRepository;
import com.estatecrm.crm_service.repository.DealRepository;
import com.estatecrm.crm_service.repository.LeadRepository;
import com.estatecrm.crm_service.repository.ProductRepository;
import com.estatecrm.crm_service.repository.ProjectRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class DealDocumentServiceTests {

    @Mock
    private DealRepository dealRepository;

    @Mock
    private LeadRepository leadRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private DealPaymentRepository dealPaymentRepository;

    @Mock
    private DealPaymentScheduleRepository dealPaymentScheduleRepository;

    private DealDocumentService service;

    @BeforeEach
    void setUp() {
        service = new DealDocumentService(
                dealRepository,
                leadRepository,
                productRepository,
                projectRepository,
                dealPaymentRepository,
                dealPaymentScheduleRepository
        );
    }

    @Test
    void testGetDealPrintData_success() {
        UUID dealId = UUID.randomUUID();
        UUID leadId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID salesId = UUID.randomUUID();

        Deal deal = new Deal();
        deal.setId(dealId);
        deal.setContractCode("HD-20261018-001");
        deal.setContractValue(new BigDecimal("3500000000"));
        deal.setDepositAmount(new BigDecimal("100000000"));
        deal.setDepositDate(LocalDate.of(2026, 10, 18));
        deal.setSignedDate(LocalDate.of(2026, 10, 18));
        deal.setPaymentStatus(PaymentStatus.PARTIAL);
        deal.setStatus(DealStatus.ACTIVE);
        deal.setApprovalStatus(ApprovalStatus.APPROVED);
        deal.setLeadId(leadId);
        deal.setSalesId(salesId);

        when(dealRepository.findById(dealId)).thenReturn(Optional.of(deal));

        Lead lead = new Lead();
        lead.setId(leadId);
        lead.setCustomerId(customerId);
        lead.setProductId(productId);
        lead.setProjectId(projectId);
        when(leadRepository.findById(leadId)).thenReturn(Optional.of(lead));

        Product product = new Product();
        product.setId(productId);
        product.setCode("RL-A201");
        product.setBlock("A");
        product.setType(ProductType.APARTMENT);
        product.setPrice(new BigDecimal("3500000000"));
        product.setArea(new BigDecimal("75.50"));
        product.setDirection("Đông Nam");
        product.setBedroom(2);
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));

        Project project = new Project();
        project.setId(projectId);
        project.setName("Regal Legend");
        project.setLocation("Quảng Bình");
        project.setInvestor("Regal Group");
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));

        DealPayment payment = new DealPayment();
        payment.setId(UUID.randomUUID());
        payment.setDealId(dealId);
        payment.setAmount(new BigDecimal("100000000"));
        payment.setPaidAt(LocalDateTime.now());
        when(dealPaymentRepository.findByDealIdOrderByPaidAtAsc(dealId)).thenReturn(List.of(payment));

        DealPaymentSchedule schedule = new DealPaymentSchedule();
        schedule.setId(UUID.randomUUID());
        schedule.setDealId(dealId);
        schedule.setInstallmentNo(1);
        schedule.setName("Đợt 1");
        schedule.setPercentage(new BigDecimal("15.00"));
        schedule.setAmountDue(new BigDecimal("525000000"));
        schedule.setDueDate(LocalDate.of(2026, 11, 1));
        when(dealPaymentScheduleRepository.findByDealIdOrderByInstallmentNoAsc(dealId)).thenReturn(List.of(schedule));

        DealPrintDataResponse res = service.getDealPrintData(dealId);

        assertNotNull(res);
        assertEquals(dealId, res.getDealId());
        assertEquals("HD-20261018-001", res.getContractCode());
        assertEquals(customerId, res.getCustomerId());
        assertNotNull(res.getProduct());
        assertEquals("RL-A201", res.getProduct().getCode());
        assertNotNull(res.getProject());
        assertEquals("Regal Legend", res.getProject().getName());
        assertNotNull(res.getCompany());
        assertTrue(res.getCompany().getCompanyName().contains("ĐẤT XANH MIỀN TRUNG"));
        assertEquals(1, res.getPayments().size());
        assertEquals(1, res.getSchedules().size());
    }

    @Test
    void testGetDealPrintData_notFound_throwsException() {
        UUID dealId = UUID.randomUUID();
        when(dealRepository.findById(dealId)).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class, () -> service.getDealPrintData(dealId));
    }
}
