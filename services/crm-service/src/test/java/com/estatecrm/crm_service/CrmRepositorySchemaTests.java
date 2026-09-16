package com.estatecrm.crm_service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.estatecrm.crm_service.entity.ContactDetail;
import com.estatecrm.crm_service.entity.Deal;
import com.estatecrm.crm_service.entity.Lead;
import com.estatecrm.crm_service.entity.Product;
import com.estatecrm.crm_service.entity.Project;
import com.estatecrm.crm_service.enums.ApprovalStatus;
import com.estatecrm.crm_service.enums.DealStatus;
import com.estatecrm.crm_service.enums.LeadStage;
import com.estatecrm.crm_service.enums.PaymentStatus;
import com.estatecrm.crm_service.enums.ProductStatus;
import com.estatecrm.crm_service.enums.ProductType;
import com.estatecrm.crm_service.enums.ProjectStatus;
import com.estatecrm.crm_service.repository.ContactDetailRepository;
import com.estatecrm.crm_service.repository.DealRepository;
import com.estatecrm.crm_service.repository.LeadRepository;
import com.estatecrm.crm_service.repository.ProductRepository;
import com.estatecrm.crm_service.repository.ProjectRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

/**
 * Chay tren DB that (Supabase) de kiem chung entity <-> schema va hanh vi rang
 * buoc. Chi bat khi IT_INFRA=up, cung quy uoc voi 2 service kia.
 *
 * Dat truoc khi chay:
 *   set -a && . ./.env && set +a && IT_INFRA=up mvn test
 *
 * Du lieu test tu xoa o @AfterAll theo dung thu tu FK.
 */
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIfEnvironmentVariable(named = "IT_INFRA", matches = "up")
class CrmRepositorySchemaTests {

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private LeadRepository leadRepository;

    @Autowired
    private DealRepository dealRepository;

    @Autowired
    private ContactDetailRepository contactDetailRepository;

    @Autowired
    private JwtDecoder jwtDecoder;

    /** UUID nguoi dung nam o user-db, khong co FK vat ly nen chi can mot gia tri. */
    private static final UUID ACTOR = UUID.fromString("11111111-1111-1111-1111-111111111111");

    /** Khach nam o customer-db, cung khong co FK vat ly. */
    private static final UUID CUSTOMER = UUID.fromString("22222222-2222-2222-2222-222222222222");

    /** Xoa sach du lieu test con sot lai tu lan chay truoc. */
    @BeforeEach
    void cleanLeftovers() {
        contactDetailRepository.deleteAll();
        dealRepository.deleteAll();
        leadRepository.deleteAll();
        productRepository.deleteAll();
        projectRepository.deleteAll();
    }

    @AfterAll
    void cleanUp() {
        cleanLeftovers();
    }

    /** Tao du an toi thieu, tat ca truong khac de DB/entity tu dien. */
    private Project newProject(String name) {
        Project project = new Project();
        project.setName(name);
        project.setLocation("Quan 2, TP.HCM");
        project.setInvestor("Chu dau tu Test");
        return projectRepository.save(project);
    }

    private Product newProduct(UUID projectId, String code) {
        Product product = new Product();
        product.setProjectId(projectId);
        product.setCode(code);
        product.setType(ProductType.APARTMENT);
        product.setArea(new BigDecimal("75.50"));
        product.setPrice(new BigDecimal("3200000000.00"));
        product.setBedroom(2);
        return productRepository.save(product);
    }

    private Lead newLead(UUID productId) {
        Lead lead = new Lead();
        lead.setCustomerId(CUSTOMER);
        lead.setProductId(productId);
        lead.setAssignedTo(ACTOR);
        lead.setExpectedValue(new BigDecimal("3000000000.00"));
        lead.setCloseDate(LocalDate.now().plusDays(30));
        return leadRepository.save(lead);
    }

    private Deal newDeal(UUID leadId, String contractCode) {
        Deal deal = new Deal();
        deal.setLeadId(leadId);
        deal.setSalesId(ACTOR);
        deal.setContractCode(contractCode);
        deal.setContractValue(new BigDecimal("3200000000.00"));
        deal.setDepositAmount(new BigDecimal("100000000.00"));
        return dealRepository.save(deal);
    }

    // ---------------------------------------------------------------- project

    @Test
    void projectMacDinhDungVaTuDienTimestamp() {
        Project saved = newProject("Du an mac dinh");

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getStatus()).isEqualTo(ProjectStatus.PLANNING);
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        // Hai cot phai bang nhau luc moi tao, lech nhau nghia la set sai cho.
        assertThat(saved.getUpdatedAt()).isEqualTo(saved.getCreatedAt());
    }

    @Test
    void projectSuaThiUpdatedAtDoiConCreatedAtGiuNguyen() {
        Project saved = newProject("Du an de sua");
        LocalDateTime created = saved.getCreatedAt();

        saved.setStatus(ProjectStatus.SELLING);
        Project updated = projectRepository.saveAndFlush(saved);
        projectRepository.flush();

        Project reread = projectRepository.findById(saved.getId()).orElseThrow();
        assertThat(reread.getStatus()).isEqualTo(ProjectStatus.SELLING);
        // Cot trong DB la timestamp (do chinh xac micro giay) con LocalDateTime
        // cua Java giu nano giay, nen phai cat bot truoc khi so sanh.
        assertThat(reread.getCreatedAt()).isEqualTo(created.truncatedTo(ChronoUnit.MICROS));
        assertThat(reread.getUpdatedAt()).isAfterOrEqualTo(reread.getCreatedAt());
        assertThat(updated.getId()).isEqualTo(saved.getId());
    }

    @Test
    void projectTenRongBiDBChan() {
        Project bad = new Project();
        bad.setName(null);

        assertThatThrownBy(() -> projectRepository.saveAndFlush(bad))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ---------------------------------------------------------------- product

    @Test
    void productMaTrungTrongCungDuAnBiChan() {
        Project project = newProject("Du an trung ma");
        newProduct(project.getId(), "A-1205");

        assertThatThrownBy(() -> productRepository.saveAndFlush(productOf(project.getId(), "A-1205")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void productMaTrungNhungKhacDuAnDuocPhep() {
        Project first = newProject("Du an 1");
        Project second = newProject("Du an 2");
        newProduct(first.getId(), "A-1205");

        Product saved = productRepository.saveAndFlush(productOf(second.getId(), "A-1205"));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getStatus()).isEqualTo(ProductStatus.AVAILABLE);
    }

    @Test
    void productTroToiDuAnKhongTonTaiBiChan() {
        assertThatThrownBy(() -> productRepository.saveAndFlush(productOf(UUID.randomUUID(), "B-01")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void productGiaAmBiCheckChan() {
        Project project = newProject("Du an gia am");
        Product bad = productOf(project.getId(), "C-01");
        bad.setPrice(new BigDecimal("-1.00"));

        assertThatThrownBy(() -> productRepository.saveAndFlush(bad))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void productDienTichBangKhongBiCheckChan() {
        Project project = newProject("Du an dien tich 0");
        Product bad = productOf(project.getId(), "D-01");
        bad.setArea(BigDecimal.ZERO);

        assertThatThrownBy(() -> productRepository.saveAndFlush(bad))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void productExistsByProjectIdAndCodeDung() {
        Project project = newProject("Du an tra cuu");
        newProduct(project.getId(), "E-09");

        assertThat(productRepository.existsByProjectIdAndCode(project.getId(), "E-09")).isTrue();
        assertThat(productRepository.existsByProjectIdAndCode(project.getId(), "E-99")).isFalse();
        assertThat(productRepository.existsByProjectIdAndCode(UUID.randomUUID(), "E-09")).isFalse();
    }

    @Test
    void productLocTheoDieuKienKetHop() {
        Project project = newProject("Du an loc");
        Product cheap = newProduct(project.getId(), "F-01");
        cheap.setPrice(new BigDecimal("1000000000.00"));
        Product dear = newProduct(project.getId(), "F-02");
        dear.setPrice(new BigDecimal("9000000000.00"));
        productRepository.saveAllAndFlush(List.of(cheap, dear));

        // Chi "F-01" nam duoi tran 2 ty, va ca hai deu la APARTMENT.
        var onlyCheap = productRepository.findAll(
                ProductRepository.filter(project.getId(), ProductType.APARTMENT, null, null,
                        null, new BigDecimal("2000000000.00"), null),
                Sort.by("code"));
        assertThat(onlyCheap).extracting(Product::getCode).containsExactly("F-01");

        // Loc theo type khac phai tra ve rong.
        assertThat(productRepository.findAll(
                ProductRepository.filter(null, ProductType.TOWNHOUSE, null, null, null, null, null)))
                .isEmpty();

        // Loc rong phai tra het, khong duoc tra ve 0 dong.
        assertThat(productRepository.findAll(
                ProductRepository.filter(null, null, null, null, null, null, null))).hasSize(2);
    }

    // ------------------------------------------------------------------- lead

    private Product productOf(UUID projectId, String code) {
        Product product = new Product();
        product.setProjectId(projectId);
        product.setCode(code);
        product.setType(ProductType.LAND);
        return product;
    }

    @Test
    void leadMacDinhStageNew() {
        Project project = newProject("Du an lead");
        Product product = newProduct(project.getId(), "G-01");

        Lead saved = newLead(product.getId());

        assertThat(saved.getStage()).isEqualTo(LeadStage.NEW);
        assertThat(saved.getId()).isNotNull();
    }

    @Test
    void leadTroToiSanPhamKhongTonTaiBiChan() {
        Lead lead = new Lead();
        lead.setCustomerId(CUSTOMER);
        lead.setProductId(UUID.randomUUID());
        lead.setAssignedTo(ACTOR);

        assertThatThrownBy(() -> leadRepository.saveAndFlush(lead))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void leadLocTheoStageVaAssignedTo() {
        Project project = newProject("Du an loc lead");
        Product product = newProduct(project.getId(), "H-01");
        Lead won = newLead(product.getId());
        won.setStage(LeadStage.WON);
        leadRepository.saveAndFlush(won);

        assertThat(leadRepository.findAll(LeadRepository.filter(null, null, ACTOR, LeadStage.WON, null)))
                .hasSize(1);
        assertThat(leadRepository.findAll(LeadRepository.filter(null, null, ACTOR, LeadStage.LOST, null)))
                .isEmpty();
        // Filter closeDate nghia la "den han vao ngay do hoac truoc", va bao gom
        // ca lead khong dat han. Lead nay han con 30 ngay nen hom nay chua toi.
        assertThat(leadRepository.findAll(LeadRepository.filter(null, null, null, null, LocalDate.now())))
                .isEmpty();
        assertThat(leadRepository.findAll(
                LeadRepository.filter(null, null, null, null, LocalDate.now().plusDays(60))))
                .hasSize(1);
    }

    // ------------------------------------------------------------------- deal

    @Test
    void dealPendingKhongCanApprovedBy() {
        Project project = newProject("Du an deal pending");
        Product product = newProduct(project.getId(), "I-01");
        Lead lead = newLead(product.getId());

        Deal saved = newDeal(lead.getId(), "HD-2026-0001");

        assertThat(saved.getApprovalStatus()).isEqualTo(ApprovalStatus.PENDING);
        assertThat(saved.getPaymentStatus()).isEqualTo(PaymentStatus.UNPAID);
        assertThat(saved.getStatus()).isEqualTo(DealStatus.IN_PROGRESS);
    }

    @Test
    void dealApprovedThieuApprovedByBiCheckChan() {
        Project project = newProject("Du an deal approved thieu");
        Product product = newProduct(project.getId(), "J-01");
        Lead lead = newLead(product.getId());
        Deal deal = newDeal(lead.getId(), "HD-2026-0002");
        deal.setApprovalStatus(ApprovalStatus.APPROVED);

        assertThatThrownBy(() -> dealRepository.saveAndFlush(deal))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void dealApprovedDuTruongThiQua() {
        Project project = newProject("Du an deal approved du");
        Product product = newProduct(project.getId(), "K-01");
        Lead lead = newLead(product.getId());
        Deal deal = newDeal(lead.getId(), "HD-2026-0003");
        deal.setApprovalStatus(ApprovalStatus.APPROVED);
        deal.setApprovedBy(ACTOR);
        deal.setApprovedAt(LocalDateTime.now());

        assertThat(dealRepository.saveAndFlush(deal).getId()).isNotNull();
    }

    @Test
    void dealTrungMaHopDongBiChan() {
        Project project = newProject("Du an trung ma hd");
        Product product = newProduct(project.getId(), "L-01");
        Lead firstLead = newLead(product.getId());
        newDeal(firstLead.getId(), "HD-2026-0004");

        Lead secondLead = newLead(product.getId());
        assertThatThrownBy(() -> dealRepository.saveAndFlush(newDealQuiet(secondLead.getId(), "HD-2026-0004")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void dealMotLeadChiMotHopDong() {
        Project project = newProject("Du an lead 1 hd");
        Product product = newProduct(project.getId(), "M-01");
        Lead lead = newLead(product.getId());
        newDeal(lead.getId(), "HD-2026-0005");

        assertThatThrownBy(() -> dealRepository.saveAndFlush(newDealQuiet(lead.getId(), "HD-2026-0006")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void dealDatCocLonHonGiaTriHopDongBiChan() {
        Project project = newProject("Du an coc qua lon");
        Product product = newProduct(project.getId(), "N-01");
        Lead lead = newLead(product.getId());
        Deal deal = newDealQuiet(lead.getId(), "HD-2026-0007");
        deal.setContractValue(new BigDecimal("1000000000.00"));
        deal.setDepositAmount(new BigDecimal("2000000000.00"));

        assertThatThrownBy(() -> dealRepository.saveAndFlush(deal))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void dealExistsByContractCodeVaLedIdDung() {
        Project project = newProject("Du an tra cuu deal");
        Product product = newProduct(project.getId(), "O-01");
        Lead lead = newLead(product.getId());
        newDeal(lead.getId(), "HD-2026-0008");

        assertThat(dealRepository.existsByContractCode("HD-2026-0008")).isTrue();
        assertThat(dealRepository.existsByContractCode("HD-9999-9999")).isFalse();
        assertThat(dealRepository.existsByLeadId(lead.getId())).isTrue();
        assertThat(dealRepository.existsByLeadId(UUID.randomUUID())).isFalse();
    }

    private Deal newDealQuiet(UUID leadId, String code) {
        Deal deal = new Deal();
        deal.setLeadId(leadId);
        deal.setSalesId(ACTOR);
        deal.setContractCode(code);
        return deal;
    }

    // ---------------------------------------------------------- contact detail

    @Test
    void contactDetailMacDinhQuantityMotVaXoaTheoDeal() {
        Project project = newProject("Du an contact detail");
        Product product = newProduct(project.getId(), "P-01");
        Lead lead = newLead(product.getId());
        Deal deal = newDeal(lead.getId(), "HD-2026-0010");

        ContactDetail detail = new ContactDetail();
        detail.setDealId(deal.getId());
        detail.setProductId(product.getId());
        detail.setUnitPrice(new BigDecimal("3100000000.00"));
        detail.setQuantity(null); // de entity/DB tu dien 1
        ContactDetail saved = contactDetailRepository.saveAndFlush(detail);

        assertThat(saved.getQuantity()).isEqualTo(1);
        assertThat(contactDetailRepository.findByDealId(deal.getId())).hasSize(1);

        dealRepository.delete(deal);
        dealRepository.flush();
        assertThat(contactDetailRepository.findByDealId(deal.getId())).isEmpty();
    }

    @Test
    void contactDetailQuantityKhongDuongBiChan() {
        Project project = newProject("Du an quantity 0");
        Product product = newProduct(project.getId(), "Q-01");
        Lead lead = newLead(product.getId());
        Deal deal = newDeal(lead.getId(), "HD-2026-0011");
        ContactDetail detail = new ContactDetail();
        detail.setDealId(deal.getId());
        detail.setProductId(product.getId());
        detail.setQuantity(0);

        assertThatThrownBy(() -> contactDetailRepository.saveAndFlush(detail))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void contactDetailCungSanPhamTrongCungHopDongBiChan() {
        Project project = newProject("Du an contact trung");
        Product product = newProduct(project.getId(), "R-01");
        Lead lead = newLead(product.getId());
        Deal deal = newDeal(lead.getId(), "HD-2026-0012");

        ContactDetail first = new ContactDetail();
        first.setDealId(deal.getId());
        first.setProductId(product.getId());
        contactDetailRepository.saveAndFlush(first);

        ContactDetail second = new ContactDetail();
        second.setDealId(deal.getId());
        second.setProductId(product.getId());

        assertThatThrownBy(() -> contactDetailRepository.saveAndFlush(second))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // -------------------------------------------------------------- jwt verify

    @Test
    void jwtKyBangSecretKhacBiTuChoi() {
        // Ba doan nhung chu ky gia, phai bi coi la token khong hop le.
        String forged = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ4Iiwicm9sZSI6IkFETUlOIn0."
                + "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";

        assertThatThrownBy(() -> jwtDecoder.decode(forged)).isInstanceOf(JwtException.class);
    }
}
