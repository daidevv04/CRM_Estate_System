package com.estatecrm.crm_service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.estatecrm.crm_service.dto.CreateDealPaymentRequest;
import com.estatecrm.crm_service.dto.LockProductRequest;
import com.estatecrm.crm_service.dto.UpdateDealRequest;
import com.estatecrm.crm_service.entity.Deal;
import com.estatecrm.crm_service.entity.Lead;
import com.estatecrm.crm_service.entity.Product;
import com.estatecrm.crm_service.entity.Project;
import com.estatecrm.crm_service.enums.*;
import com.estatecrm.crm_service.exception.ConflictException;
import com.estatecrm.crm_service.repository.*;
import com.estatecrm.crm_service.service.DealPaymentService;
import com.estatecrm.crm_service.service.DealService;
import com.estatecrm.crm_service.service.ProductService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest
@ContextConfiguration(initializers = IsolatedCrmDatabase.class)
@EnabledIfEnvironmentVariable(named = "IT_INFRA", matches = "up")
class SalesIntegrityIntegrationTests {
    private static final UUID ACTOR = UUID.randomUUID();
    private static final UUID CUSTOMER = UUID.randomUUID();
    @Autowired DataSource dataSource;
    @Autowired ProjectRepository projects;
    @Autowired ProductRepository products;
    @Autowired LeadRepository leads;
    @Autowired DealRepository deals;
    @Autowired ProductService productService;
    @Autowired DealService dealService;
    @Autowired DealPaymentService payments;
    private JdbcTemplate jdbc;

    @BeforeEach
    void resetOnlyIsolatedSchema() {
        jdbc = new JdbcTemplate(dataSource);
        assertThat(jdbc.queryForObject("SELECT current_schema()", String.class)).startsWith("crm_test_");
        jdbc.execute("TRUNCATE projects CASCADE");
    }

    @Test
    void simultaneousReservationsHaveExactlyOneWinner() throws Exception {
        Product product = product();
        List<Integer> outcomes = race(
                () -> reserve(product.getId(), CUSTOMER),
                () -> reserve(product.getId(), UUID.randomUUID()));
        assertThat(outcomes).containsExactlyInAnyOrder(200, 409);
        assertThat(activeReservations(product.getId())).isEqualTo(1);
        assertThat(products.findById(product.getId()).orElseThrow().getStatus()).isEqualTo(ProductStatus.RESERVED);
    }

    @Test
    void simultaneousApprovalsCannotSellOnePropertyTwice() throws Exception {
        Product product = product();
        Deal first = deal(product, CUSTOMER);
        Deal second = deal(product, UUID.randomUUID());
        assertThat(race(() -> approve(first), () -> approve(second))).containsExactlyInAnyOrder(200, 409);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM deals WHERE approval_status='APPROVED'", Integer.class))
                .isEqualTo(1);
        assertThat(products.findById(product.getId()).orElseThrow().getStatus()).isEqualTo(ProductStatus.SOLD);
    }

    @Test
    void expiryRacingApprovalPreservesInventoryOwnership() throws Exception {
        Product product = product();
        Deal deal = deal(product, CUSTOMER);
        reserve(product.getId(), CUSTOMER);
        jdbc.update("UPDATE products SET lock_expires_at=? WHERE id=?", LocalDate.now().minusDays(1), product.getId());
        jdbc.update("UPDATE product_reservations SET expiry_date=? WHERE product_id=?",
                LocalDate.now().minusDays(1), product.getId());
        List<Integer> outcomes = race(() -> approve(deal), () -> { productService.releaseExpiredReservations(); return 200; });
        assertThat(outcomes.get(0)).isIn(200, 409);
        assertThat(outcomes.get(1)).isEqualTo(200);
        boolean approved = deals.findById(deal.getId()).orElseThrow().getApprovalStatus() == ApprovalStatus.APPROVED;
        assertThat(products.findById(product.getId()).orElseThrow().getStatus())
                .isEqualTo(approved ? ProductStatus.SOLD : ProductStatus.AVAILABLE);
        assertThat(activeReservations(product.getId())).isZero();
    }

    @Test
    void schedulerNeverReleasesSoldInventory() {
        Product product = product();
        Deal deal = deal(product, CUSTOMER);
        reserve(product.getId(), CUSTOMER);
        assertThat(approve(deal)).isEqualTo(200);
        jdbc.update("UPDATE product_reservations SET status='ACTIVE', expiry_date=? WHERE product_id=?",
                LocalDate.now().minusDays(1), product.getId());
        assertThat(productService.releaseExpiredReservations()).isZero();
        assertThat(products.findById(product.getId()).orElseThrow().getStatus()).isEqualTo(ProductStatus.SOLD);
    }

    @Test
    void simultaneousReceiptsCannotExceedContractValue() throws Exception {
        Deal deal = deal(product(), CUSTOMER);
        var request = new CreateDealPaymentRequest(new BigDecimal("700"), LocalDateTime.now(),
                PaymentMethod.BANK_TRANSFER, null, null);
        Callable<Integer> pay = () -> outcome(() -> payments.create(deal.getId(), request, ACTOR, "ADMIN"));
        assertThat(race(pay, pay)).containsExactlyInAnyOrder(200, 400);
        assertThat(jdbc.queryForObject("SELECT sum(amount) FROM deal_payments WHERE deal_id=?", BigDecimal.class, deal.getId()))
                .isEqualByComparingTo("700");
        assertThat(deals.findById(deal.getId()).orElseThrow().getPaymentStatus()).isEqualTo(PaymentStatus.PARTIAL);
    }

    @Test
    void approvalRejectsAnotherCustomersReservation() {
        Product product = product();
        Deal deal = deal(product, CUSTOMER);
        reserve(product.getId(), UUID.randomUUID());
        assertThat(approve(deal)).isEqualTo(409);
        assertThat(products.findById(product.getId()).orElseThrow().getStatus()).isEqualTo(ProductStatus.RESERVED);
        assertThat(deals.findById(deal.getId()).orElseThrow().getApprovalStatus()).isEqualTo(ApprovalStatus.PENDING);
    }

    @Test
    void databaseRejectsDuplicateActiveReservationsEvenWhenServiceIsBypassed() {
        Product product = product();
        reserve(product.getId(), CUSTOMER);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO product_reservations "
                + "(product_id,customer_id,sales_id,expiry_date) VALUES (?,?,?,?)",
                product.getId(), UUID.randomUUID(), ACTOR, LocalDate.now().plusDays(1)))
                .isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
    }

    private Product product() {
        Project project = new Project();
        project.setName("Isolated sales test");
        project = projects.saveAndFlush(project);
        Product product = new Product();
        product.setProjectId(project.getId());
        product.setCode("TEST-" + UUID.randomUUID());
        product.setType(ProductType.APARTMENT);
        return products.saveAndFlush(product);
    }

    private Deal deal(Product product, UUID customer) {
        Lead lead = new Lead();
        lead.setProjectId(product.getProjectId());
        lead.setProductId(product.getId());
        lead.setCustomerId(customer);
        lead.setAssignedTo(ACTOR);
        lead.setStage(LeadStage.WON);
        lead = leads.saveAndFlush(lead);
        Deal deal = new Deal();
        deal.setLeadId(lead.getId());
        deal.setSalesId(ACTOR);
        deal.setContractCode("TEST-" + UUID.randomUUID());
        deal.setContractValue(new BigDecimal("1000"));
        deal.setDepositAmount(BigDecimal.ZERO);
        return deals.saveAndFlush(deal);
    }

    private int reserve(UUID productId, UUID customerId) {
        return outcome(() -> productService.lock(productId,
                new LockProductRequest(customerId, 2, null, BigDecimal.ZERO, null), ACTOR, "ADMIN"));
    }

    private int approve(Deal deal) {
        return outcome(() -> dealService.update(deal.getId(), new UpdateDealRequest(
                null, null, null, null, null, null, null, ApprovalStatus.APPROVED,
                null, null, null, null), ACTOR, "ADMIN"));
    }

    private int activeReservations(UUID productId) {
        return jdbc.queryForObject("SELECT count(*) FROM product_reservations WHERE product_id=? AND status='ACTIVE'",
                Integer.class, productId);
    }

    private static int outcome(Runnable operation) {
        try { operation.run(); return 200; }
        catch (ConflictException e) { return 409; }
        catch (ResponseStatusException e) { return e.getStatusCode().value(); }
    }

    private static List<Integer> race(Callable<Integer> first, Callable<Integer> second) throws Exception {
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var tasks = List.of(first, second).stream().map(task -> executor.submit(() -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Race start timed out");
                return task.call();
            })).toList();
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return List.of(tasks.get(0).get(45, TimeUnit.SECONDS), tasks.get(1).get(45, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }
}
