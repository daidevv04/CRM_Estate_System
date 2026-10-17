package com.estatecrm.crm_service.service;

import com.estatecrm.crm_service.dto.CreateProductRequest;
import com.estatecrm.crm_service.dto.ProductResponse;
import com.estatecrm.crm_service.dto.UpdateProductRequest;
import com.estatecrm.crm_service.entity.Product;
import com.estatecrm.crm_service.enums.ProductStatus;
import com.estatecrm.crm_service.enums.ProductType;
import com.estatecrm.crm_service.repository.ContactDetailRepository;
import com.estatecrm.crm_service.repository.LeadRepository;
import com.estatecrm.crm_service.repository.ProductRepository;
import com.estatecrm.crm_service.repository.ProjectRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTests {

    @Mock
    private ProductRepository productRepository;
    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private LeadRepository leadRepository;
    @Mock
    private ContactDetailRepository contactDetailRepository;
    @Mock
    private ImageStorageService imageStorageService;
    @Mock
    private ProjectAssignmentService projectAssignmentService;
    @Mock
    private com.estatecrm.crm_service.repository.ProductReservationRepository productReservationRepository;

    private ProductService productService;

    @BeforeEach
    void setUp() {
        productService = new ProductService(
                productRepository,
                projectRepository,
                leadRepository,
                contactDetailRepository,
                imageStorageService,
                projectAssignmentService,
                productReservationRepository);
    }

    @Test
    @DisplayName("create product succeeds when role is ADMIN")
    void create_whenAdmin_succeeds() {
        UUID projectId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        CreateProductRequest req = new CreateProductRequest(
                projectId, "P-101", ProductType.APARTMENT, new BigDecimal("75.5"), "A",
                new BigDecimal("2500000000"), 2, null, null, ProductStatus.AVAILABLE);

        when(projectRepository.existsById(projectId)).thenReturn(true);
        when(productRepository.existsByProjectIdAndCode(projectId, "P-101")).thenReturn(false);
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> {
            Product p = inv.getArgument(0);
            p.setId(UUID.randomUUID());
            return p;
        });

        ProductResponse res = productService.create(req, adminId, "ADMIN");
        assertThat(res).isNotNull();
        assertThat(res.code()).isEqualTo("P-101");
    }

    @Test
    @DisplayName("create product fails when manager is not assigned to project")
    void create_whenManagerNotAssigned_throwsForbidden() {
        UUID projectId = UUID.randomUUID();
        UUID managerId = UUID.randomUUID();
        CreateProductRequest req = new CreateProductRequest(
                projectId, "P-101", ProductType.APARTMENT, new BigDecimal("75.5"), "A",
                new BigDecimal("2500000000"), 2, null, null, ProductStatus.AVAILABLE);

        when(projectAssignmentService.isAssignedToProject(managerId, projectId)).thenReturn(false);

        assertThatThrownBy(() -> productService.create(req, managerId, "MANAGER"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Project is not assigned to you");
    }

    @Test
    @DisplayName("create product succeeds when manager is assigned to project")
    void create_whenManagerAssigned_succeeds() {
        UUID projectId = UUID.randomUUID();
        UUID managerId = UUID.randomUUID();
        CreateProductRequest req = new CreateProductRequest(
                projectId, "P-101", ProductType.APARTMENT, new BigDecimal("75.5"), "A",
                new BigDecimal("2500000000"), 2, null, null, ProductStatus.AVAILABLE);

        when(projectAssignmentService.isAssignedToProject(managerId, projectId)).thenReturn(true);
        when(projectRepository.existsById(projectId)).thenReturn(true);
        when(productRepository.existsByProjectIdAndCode(projectId, "P-101")).thenReturn(false);
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> {
            Product p = inv.getArgument(0);
            p.setId(UUID.randomUUID());
            return p;
        });

        ProductResponse res = productService.create(req, managerId, "MANAGER");
        assertThat(res).isNotNull();
        assertThat(res.code()).isEqualTo("P-101");
    }

    @Test
    @DisplayName("list products when manager has no assigned projects returns empty page")
    void list_whenManagerHasNoAssignedProjects_returnsEmpty() {
        UUID managerId = UUID.randomUUID();
        when(projectAssignmentService.getAssignedProjectIds(managerId)).thenReturn(List.of());

        Page<ProductResponse> res = productService.list(
                null, null, null, null, null, null, null,
                managerId, "MANAGER", PageRequest.of(0, 10));

        assertThat(res.getContent()).isEmpty();
    }

    @Test
    @DisplayName("update product fails when manager not assigned to project")
    void update_whenManagerNotAssigned_throwsForbidden() {
        UUID productId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID managerId = UUID.randomUUID();

        Product product = new Product();
        product.setId(productId);
        product.setProjectId(projectId);

        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(projectAssignmentService.isAssignedToProject(managerId, projectId)).thenReturn(false);

        UpdateProductRequest req = new UpdateProductRequest(
                "P-102", null, null, null, null, null, null, null, null);

        assertThatThrownBy(() -> productService.update(productId, req, managerId, "MANAGER"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Product belongs to a project you are not assigned to");
    }

    @Test
    @DisplayName("delete product fails when sales tries to delete")
    void delete_whenSales_throwsForbidden() {
        UUID productId = UUID.randomUUID();
        assertThatThrownBy(() -> productService.delete(productId, UUID.randomUUID(), "SALES"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Only ADMIN/MANAGER can manage products");
    }

    @Test
    @DisplayName("lock product succeeds and sets status to RESERVED")
    void lock_whenAvailable_succeeds() {
        UUID productId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID salesId = UUID.randomUUID();

        Product product = new Product();
        product.setId(productId);
        product.setProjectId(UUID.randomUUID());
        product.setCode("A101");
        product.setType(ProductType.APARTMENT);
        product.setStatus(ProductStatus.AVAILABLE);

        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        com.estatecrm.crm_service.dto.LockProductRequest req = new com.estatecrm.crm_service.dto.LockProductRequest(
                customerId, 3, null, new BigDecimal("50000000"), "Dat coc giu cho");

        ProductResponse res = productService.lock(productId, req, salesId, "SALES");

        assertThat(res.status()).isEqualTo(ProductStatus.RESERVED);
        assertThat(res.lockedBy()).isEqualTo(salesId);
        assertThat(res.lockedCustomerId()).isEqualTo(customerId);
        assertThat(res.lockExpiresAt()).isEqualTo(java.time.LocalDate.now().plusDays(3));
        verify(productReservationRepository).save(any());
    }

    @Test
    @DisplayName("lock fails when product is already SOLD")
    void lock_whenSold_throwsBadRequest() {
        UUID productId = UUID.randomUUID();
        Product product = new Product();
        product.setId(productId);
        product.setStatus(ProductStatus.SOLD);

        when(productRepository.findById(productId)).thenReturn(Optional.of(product));

        com.estatecrm.crm_service.dto.LockProductRequest req = new com.estatecrm.crm_service.dto.LockProductRequest(
                UUID.randomUUID(), 2, null, BigDecimal.ZERO, null);

        assertThatThrownBy(() -> productService.lock(productId, req, UUID.randomUUID(), "SALES"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Cannot lock a sold product");
    }

    @Test
    @DisplayName("unlock succeeds when actor is the sales who locked it")
    void unlock_whenOwnerSales_succeeds() {
        UUID productId = UUID.randomUUID();
        UUID salesId = UUID.randomUUID();

        Product product = new Product();
        product.setId(productId);
        product.setProjectId(UUID.randomUUID());
        product.setCode("A101");
        product.setType(ProductType.APARTMENT);
        product.setStatus(ProductStatus.RESERVED);
        product.setLockedBy(salesId);
        product.setLockExpiresAt(java.time.LocalDate.now().plusDays(2));

        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProductResponse res = productService.unlock(productId, salesId, "SALES");

        assertThat(res.status()).isEqualTo(ProductStatus.AVAILABLE);
        assertThat(res.lockedBy()).isNull();
        assertThat(res.lockExpiresAt()).isNull();
    }

    @Test
    @DisplayName("unlock fails when actor is another sales person")
    void unlock_whenAnotherSales_throwsForbidden() {
        UUID productId = UUID.randomUUID();
        UUID salesId1 = UUID.randomUUID();
        UUID salesId2 = UUID.randomUUID();

        Product product = new Product();
        product.setId(productId);
        product.setStatus(ProductStatus.RESERVED);
        product.setLockedBy(salesId1);

        when(productRepository.findById(productId)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> productService.unlock(productId, salesId2, "SALES"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("You do not have permission to unlock this product");
    }
}
