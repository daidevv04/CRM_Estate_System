package com.estatecrm.crm_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.estatecrm.crm_service.dto.CreateDealRequest;
import com.estatecrm.crm_service.dto.DealResponse;
import com.estatecrm.crm_service.dto.UpdateDealRequest;
import com.estatecrm.crm_service.entity.Deal;
import com.estatecrm.crm_service.entity.Lead;
import com.estatecrm.crm_service.entity.Product;
import com.estatecrm.crm_service.enums.ApprovalStatus;
import com.estatecrm.crm_service.enums.DealStatus;
import com.estatecrm.crm_service.enums.LeadStage;
import com.estatecrm.crm_service.enums.ProductStatus;
import com.estatecrm.crm_service.exception.ConflictException;
import com.estatecrm.crm_service.repository.DealRepository;
import com.estatecrm.crm_service.repository.LeadRepository;
import com.estatecrm.crm_service.repository.ProductRepository;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class DealServiceTests {

    private static final UUID CUSTOMER_ID = UUID.randomUUID();
    private static final UUID ACTOR = UUID.randomUUID();
    private static final UUID LEAD_ID = UUID.randomUUID();
    private static final UUID PRODUCT_ID = UUID.randomUUID();
    private static final UUID PROJECT_ID = UUID.randomUUID();

    @Mock
    private DealRepository dealRepository;

    @Mock
    private LeadRepository leadRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProjectAssignmentService projectAssignmentService;

    @Mock
    private com.estatecrm.crm_service.repository.ProductReservationRepository productReservationRepository;

    @Mock
    private DealCommissionService commissionService;

    @InjectMocks
    private DealService dealService;

    @Mock
    private com.estatecrm.crm_service.repository.DealPaymentRepository paymentRepository;

    @Test
    void salesCannotCreateContractFromAnotherSalesLead() {
        Lead lead = wonLead();
        lead.setAssignedTo(UUID.randomUUID());
        when(leadRepository.findWithLockById(LEAD_ID)).thenReturn(Optional.of(lead));
        assertThatThrownBy(() -> dealService.create(request("FOREIGN", null, null), ACTOR, "SALES"))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("Lead is not assigned to you");
        verify(dealRepository, never()).save(any());
    }

    @Test
    void cannotMarkUnpaidContractAsPaidThroughUpdate() {
        Deal deal = new Deal();
        deal.setId(UUID.randomUUID());
        deal.setSalesId(ACTOR);
        when(dealRepository.findWithLockById(deal.getId())).thenReturn(Optional.of(deal));
        when(dealRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        dealService.update(deal.getId(), new UpdateDealRequest(null, null, null, null, null,
                com.estatecrm.crm_service.enums.PaymentStatus.PAID, null, null, null, null, null, null), ACTOR, "SALES");
        assertThat(deal.getPaymentStatus()).isEqualTo(com.estatecrm.crm_service.enums.PaymentStatus.UNPAID);
    }

    private Lead wonLead() {
        Lead lead = new Lead();
        lead.setId(LEAD_ID);
        lead.setProjectId(PROJECT_ID);
        lead.setProductId(PRODUCT_ID);
        lead.setStage(LeadStage.WON);
        lead.setAssignedTo(ACTOR);
        lead.setCustomerId(CUSTOMER_ID);
        return lead;
    }

    private Product availableProduct() {
        Product product = new Product();
        product.setId(PRODUCT_ID);
        product.setProjectId(PROJECT_ID);
        product.setStatus(ProductStatus.AVAILABLE);
        return product;
    }

    @Test
    void rejectsDealFromLeadThatIsNotWon() {
        Lead lead = wonLead();
        lead.setStage(LeadStage.NEGOTIATION);
        when(leadRepository.findWithLockById(LEAD_ID)).thenReturn(Optional.of(lead));

        assertThatThrownBy(() -> dealService.create(request("HD-00", null, null), ACTOR, "SALES"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("must be WON");
        verify(dealRepository, never()).save(any());
    }

    @Test
    void rejectsDealFromLeadWithoutAssignedProduct() {
        Lead lead = wonLead();
        lead.setProductId(null);
        when(leadRepository.findWithLockById(LEAD_ID)).thenReturn(Optional.of(lead));

        assertThatThrownBy(() -> dealService.create(request("HD-00B", null, null), ACTOR, "SALES"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("must have an assigned product");
        verify(dealRepository, never()).save(any());
    }

    @Test
    void rejectsDealWhenProductIsAlreadySold() {
        Product soldProduct = availableProduct();
        soldProduct.setStatus(ProductStatus.SOLD);

        when(leadRepository.findWithLockById(LEAD_ID)).thenReturn(Optional.of(wonLead()));
        when(productRepository.findWithLockById(PRODUCT_ID)).thenReturn(Optional.of(soldProduct));

        assertThatThrownBy(() -> dealService.create(request("HD-00C", null, null), ACTOR, "SALES"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Product has already been sold");
        verify(dealRepository, never()).save(any());
    }

    @Test
    void rejectsSecondContractForSameLead() {
        when(leadRepository.findWithLockById(LEAD_ID)).thenReturn(Optional.of(wonLead()));
        when(productRepository.findWithLockById(PRODUCT_ID)).thenReturn(Optional.of(availableProduct()));
        when(dealRepository.existsByLeadId(LEAD_ID)).thenReturn(true);

        assertThatThrownBy(() -> dealService.create(request("HD-01", null, null), ACTOR, "SALES"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already has a contract");
        verify(dealRepository, never()).save(any());
    }

    @Test
    void salesCannotAssignContractToSomeoneElse() {
        UUID otherSales = UUID.randomUUID();
        when(leadRepository.findWithLockById(LEAD_ID)).thenReturn(Optional.of(wonLead()));
        when(productRepository.findWithLockById(PRODUCT_ID)).thenReturn(Optional.of(availableProduct()));
        when(dealRepository.existsByLeadId(LEAD_ID)).thenReturn(false);
        when(dealRepository.existsByContractCode("HD-02")).thenReturn(false);
        when(dealRepository.save(any(Deal.class))).thenAnswer(invocation -> invocation.getArgument(0));

        dealService.create(request("HD-02", null, otherSales), ACTOR, "SALES");

        ArgumentCaptor<Deal> captor = ArgumentCaptor.forClass(Deal.class);
        verify(dealRepository).save(captor.capture());
        assertThat(captor.getValue().getSalesId()).isEqualTo(ACTOR);
    }

    @Test
    void rejectsDepositLargerThanContractValue() {
        when(leadRepository.findWithLockById(LEAD_ID)).thenReturn(Optional.of(wonLead()));
        when(productRepository.findWithLockById(PRODUCT_ID)).thenReturn(Optional.of(availableProduct()));
        when(dealRepository.existsByLeadId(LEAD_ID)).thenReturn(false);
        when(dealRepository.existsByContractCode("HD-03")).thenReturn(false);

        assertThatThrownBy(() -> dealService.create(
                        request("HD-03", new BigDecimal("120"), null), ACTOR, "SALES"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Deposit amount must not exceed contract value");
        verify(dealRepository, never()).save(any());
    }

    @Test
    void salesCannotApproveContract() {
        Deal deal = new Deal();
        deal.setId(UUID.randomUUID());
        deal.setLeadId(LEAD_ID);
        deal.setSalesId(ACTOR);
        deal.setContractCode("HD-04");
        when(dealRepository.findWithLockById(deal.getId())).thenReturn(Optional.of(deal));

        assertThatThrownBy(() -> dealService.update(
                        deal.getId(),
                        new UpdateDealRequest(
                                null, null, null, null, null, null, null,
                                ApprovalStatus.APPROVED, null, null, null, null),
                        ACTOR, "SALES"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Only ADMIN/MANAGER");
        assertThat(deal.getApprovalStatus()).isEqualTo(ApprovalStatus.PENDING);
    }

    @Test
    void managerApprovalStampsApprovedByAndSetsProductToSold() {
        Deal deal = new Deal();
        deal.setId(UUID.randomUUID());
        deal.setLeadId(LEAD_ID);
        deal.setSalesId(UUID.randomUUID());
        deal.setContractCode("HD-05");

        Lead lead = wonLead();
        Product product = availableProduct();
        product.setStatus(ProductStatus.RESERVED);
        product.setLockedBy(ACTOR);

        com.estatecrm.crm_service.entity.ProductReservation reservation = new com.estatecrm.crm_service.entity.ProductReservation();
        reservation.setStatus(com.estatecrm.crm_service.enums.ReservationStatus.ACTIVE);
        reservation.setCustomerId(CUSTOMER_ID);
        reservation.setExpiryDate(java.time.LocalDate.now().plusDays(1));

        when(dealRepository.findWithLockById(deal.getId())).thenReturn(Optional.of(deal));
        when(leadRepository.findById(LEAD_ID)).thenReturn(Optional.of(lead));
        when(productRepository.findWithLockById(PRODUCT_ID)).thenReturn(Optional.of(product));
        when(projectAssignmentService.isAssignedToProject(ACTOR, PROJECT_ID)).thenReturn(true);
        when(productReservationRepository.findFirstByProductIdAndStatusOrderByPriorityAsc(
                PRODUCT_ID, com.estatecrm.crm_service.enums.ReservationStatus.ACTIVE))
                .thenReturn(Optional.of(reservation));
        when(dealRepository.save(any(Deal.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DealResponse response = dealService.update(
                deal.getId(),
                new UpdateDealRequest(
                                null, null, null, null, null, null, null,
                                ApprovalStatus.APPROVED, null, null, null, null),
                ACTOR, "MANAGER");

        assertThat(response.approvalStatus()).isEqualTo(ApprovalStatus.APPROVED);
        assertThat(response.approvedBy()).isEqualTo(ACTOR);
        assertThat(response.approvedAt()).isNotNull();

        // Verify product was synced to SOLD and lock fields cleared
        verify(productRepository).save(product);
        assertThat(product.getStatus()).isEqualTo(ProductStatus.SOLD);
        assertThat(product.getLockedBy()).isNull();

        // Verify reservation was converted to DEAL
        assertThat(reservation.getStatus()).isEqualTo(com.estatecrm.crm_service.enums.ReservationStatus.CONVERTED_TO_DEAL);
        verify(productReservationRepository).save(reservation);
    }

    @Test
    void rejectingApprovedContractRevertsProductToAvailable() {
        Deal deal = new Deal();
        deal.setId(UUID.randomUUID());
        deal.setLeadId(LEAD_ID);
        deal.setSalesId(UUID.randomUUID());
        deal.setContractCode("HD-06");
        deal.setApprovalStatus(ApprovalStatus.APPROVED);

        Lead lead = wonLead();
        Product product = availableProduct();
        product.setStatus(ProductStatus.SOLD);

        when(dealRepository.findWithLockById(deal.getId())).thenReturn(Optional.of(deal));
        when(leadRepository.findById(LEAD_ID)).thenReturn(Optional.of(lead));
        when(productRepository.findWithLockById(PRODUCT_ID)).thenReturn(Optional.of(product));
        when(projectAssignmentService.isAssignedToProject(ACTOR, PROJECT_ID)).thenReturn(true);
        when(dealRepository.save(any(Deal.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DealResponse response = dealService.update(
                deal.getId(),
                new UpdateDealRequest(
                                null, null, null, null, null, null, null,
                                ApprovalStatus.REJECTED, null, null, null, null),
                ACTOR, "MANAGER");

        assertThat(response.approvalStatus()).isEqualTo(ApprovalStatus.REJECTED);
        verify(productRepository).save(product);
        assertThat(product.getStatus()).isEqualTo(ProductStatus.AVAILABLE);
    }

    private CreateDealRequest request(String code, BigDecimal deposit, UUID salesId) {
        return new CreateDealRequest(
                LEAD_ID, code, new BigDecimal("100"), deposit,
                null, null, null, null, null, null, null, salesId);
    }

    @Test
    void approvalCannotConsumeAnotherCustomersReservation() {
        Deal deal = approvalDeal();
        Product product = availableProduct();
        product.setStatus(ProductStatus.RESERVED);
        var reservation = new com.estatecrm.crm_service.entity.ProductReservation();
        reservation.setCustomerId(UUID.randomUUID());
        reservation.setStatus(com.estatecrm.crm_service.enums.ReservationStatus.ACTIVE);
        reservation.setExpiryDate(java.time.LocalDate.now().plusDays(1));
        when(leadRepository.findById(LEAD_ID)).thenReturn(Optional.of(wonLead()));
        when(productRepository.findWithLockById(PRODUCT_ID)).thenReturn(Optional.of(product));
        when(productReservationRepository.findFirstByProductIdAndStatusOrderByPriorityAsc(
                PRODUCT_ID, com.estatecrm.crm_service.enums.ReservationStatus.ACTIVE)).thenReturn(Optional.of(reservation));
        assertThatThrownBy(() -> dealService.update(deal.getId(), approvalRequest(), ACTOR, "ADMIN"))
                .isInstanceOf(ConflictException.class).hasMessageContaining("another customer");
        assertThat(reservation.getStatus()).isEqualTo(com.estatecrm.crm_service.enums.ReservationStatus.ACTIVE);
        assertThat(product.getStatus()).isEqualTo(ProductStatus.RESERVED);
        verify(productRepository, never()).save(any());
    }

    @Test
    void approvalRejectsProductWithAnotherApprovedContract() {
        Deal deal = approvalDeal();
        when(leadRepository.findById(LEAD_ID)).thenReturn(Optional.of(wonLead()));
        when(productRepository.findWithLockById(PRODUCT_ID)).thenReturn(Optional.of(availableProduct()));
        when(dealRepository.hasOtherApprovedDeal(PRODUCT_ID, deal.getId())).thenReturn(true);
        assertThatThrownBy(() -> dealService.update(deal.getId(), approvalRequest(), ACTOR, "ADMIN"))
                .isInstanceOf(ConflictException.class).hasMessageContaining("another approved contract");
        verify(productRepository, never()).save(any());
    }

    @Test
    void cannotReduceContractBelowReceipts() {
        Deal deal = approvalDeal();
        when(paymentRepository.totalPaidByDealId(deal.getId())).thenReturn(new BigDecimal("100"));
        assertThatThrownBy(() -> dealService.update(deal.getId(), new UpdateDealRequest(
                null, new BigDecimal("90"), null, null, null, null, null, null, null, null, null, null), ACTOR, "ADMIN"))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("below payments");
        verify(dealRepository, never()).save(any());
    }

    @Test
    void reducingValueToReceiptsMarksPaid() {
        Deal deal = approvalDeal();
        when(paymentRepository.totalPaidByDealId(deal.getId())).thenReturn(new BigDecimal("100"));
        when(dealRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        dealService.update(deal.getId(), new UpdateDealRequest(null, new BigDecimal("100"), null,
                null, null, null, null, null, null, null, null, null), ACTOR, "ADMIN");
        assertThat(deal.getPaymentStatus()).isEqualTo(com.estatecrm.crm_service.enums.PaymentStatus.PAID);
    }

    @Test
    void deletionPreservesReservationMadeAfterContractWasCancelled() {
        Deal deal = approvalDeal();
        deal.setStatus(DealStatus.CANCELLED);
        deal.setApprovalStatus(ApprovalStatus.APPROVED);
        Product product = availableProduct();
        product.setStatus(ProductStatus.RESERVED);
        when(leadRepository.findById(LEAD_ID)).thenReturn(Optional.of(wonLead()));
        when(productRepository.findWithLockById(PRODUCT_ID)).thenReturn(Optional.of(product));
        dealService.delete(deal.getId(), ACTOR, "ADMIN");
        assertThat(product.getStatus()).isEqualTo(ProductStatus.RESERVED);
        verify(productRepository, never()).save(any());
    }

    @Test
    void cannotDeleteContractWithReceipts() {
        Deal deal = approvalDeal();
        when(paymentRepository.totalPaidByDealId(deal.getId())).thenReturn(BigDecimal.ONE);
        assertThatThrownBy(() -> dealService.delete(deal.getId(), ACTOR, "ADMIN"))
                .isInstanceOf(ConflictException.class).hasMessageContaining("recorded payments");
        verify(dealRepository, never()).delete(any(Deal.class));
    }

    private Deal approvalDeal() {
        Deal deal = new Deal();
        deal.setId(UUID.randomUUID());
        deal.setLeadId(LEAD_ID);
        deal.setSalesId(ACTOR);
        when(dealRepository.findWithLockById(deal.getId())).thenReturn(Optional.of(deal));
        return deal;
    }

    private UpdateDealRequest approvalRequest() {
        return new UpdateDealRequest(null, null, null, null, null, null, null,
                ApprovalStatus.APPROVED, null, null, null, null);
    }
}
