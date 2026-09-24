package com.estatecrm.crm_service.service;

import com.estatecrm.crm_service.dto.CreateProductRequest;
import com.estatecrm.crm_service.dto.ProductResponse;
import com.estatecrm.crm_service.dto.UpdateProductRequest;
import com.estatecrm.crm_service.entity.Product;
import com.estatecrm.crm_service.enums.ProductStatus;
import com.estatecrm.crm_service.enums.ProductType;
import com.estatecrm.crm_service.exception.ConflictException;
import com.estatecrm.crm_service.repository.ContactDetailRepository;
import com.estatecrm.crm_service.repository.LeadRepository;
import com.estatecrm.crm_service.repository.ProductRepository;
import com.estatecrm.crm_service.repository.ProjectRepository;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;

/**
 * San pham bat dong san. Doc: moi vai tro. Tao/sua/xoa: ADMIN/MANAGER.
 * Ma can chi duy nhat trong pham vi mot du an (uq_products_project_code).
 */
@Service
public class ProductService {

    private static final String ADMIN = "ADMIN";
    private static final String MANAGER = "MANAGER";

    private final ProductRepository productRepository;
    private final ProjectRepository projectRepository;
    private final LeadRepository leadRepository;
    private final ContactDetailRepository contactDetailRepository;

    public ProductService(
            ProductRepository productRepository,
            ProjectRepository projectRepository,
            LeadRepository leadRepository,
            ContactDetailRepository contactDetailRepository) {
        this.productRepository = productRepository;
        this.projectRepository = projectRepository;
        this.leadRepository = leadRepository;
        this.contactDetailRepository = contactDetailRepository;
    }

    @Transactional
    public ProductResponse create(CreateProductRequest request, UUID actorId, String role) {
        requirePrivileged(role);
        if (!projectRepository.existsById(request.projectId())) {
            throw new ResponseStatusException(NOT_FOUND, "Project not found");
        }
        if (productRepository.existsByProjectIdAndCode(request.projectId(), request.code())) {
            throw new ConflictException("Product code already exists in this project");
        }
        Product product = new Product();
        product.setProjectId(request.projectId());
        product.setCode(request.code());
        product.setType(request.type());
        product.setArea(request.area());
        product.setBlock(request.block());
        product.setPrice(request.price());
        product.setBedroom(request.bedroom());
        product.setDirection(request.direction() == null ? null : request.direction().name());
        if (request.status() != null) {
            product.setStatus(request.status());
        }
        product.setCreatedBy(actorId);
        return ProductResponse.from(productRepository.save(product));
    }

    @Transactional(readOnly = true)
    public Page<ProductResponse> list(
            UUID projectId,
            ProductType type,
            ProductStatus status,
            String block,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            String keyword,
            Pageable pageable) {
        return productRepository
                .findAll(ProductRepository.filter(
                        projectId, type, status, block, minPrice, maxPrice, keyword),
                        pageable)
                .map(ProductResponse::from);
    }

    @Transactional(readOnly = true)
    public ProductResponse get(UUID productId) {
        return ProductResponse.from(find(productId));
    }

    @Transactional
    public ProductResponse update(
            UUID productId, UpdateProductRequest request, UUID actorId, String role) {
        requirePrivileged(role);
        Product product = find(productId);
        if (request.code() != null
                && !request.code().equals(product.getCode())
                && productRepository.existsByProjectIdAndCode(product.getProjectId(), request.code())) {
            throw new ConflictException("Product code already exists in this project");
        }
        if (request.code() != null) {
            product.setCode(request.code());
        }
        if (request.type() != null) {
            product.setType(request.type());
        }
        if (request.area() != null) {
            product.setArea(request.area());
        }
        if (request.block() != null) {
            product.setBlock(request.block());
        }
        if (request.price() != null) {
            product.setPrice(request.price());
        }
        if (request.bedroom() != null) {
            product.setBedroom(request.bedroom());
        }
        if (request.direction() != null) {
            product.setDirection(request.direction().name());
        }
        if (request.status() != null) {
            product.setStatus(request.status());
        }
        product.setUpdatedBy(actorId);
        return ProductResponse.from(productRepository.save(product));
    }

    /**
     * Xoa san pham. Chan truoc khi con lead hoac dong hop dong tro toi
     * (FK khong cascade), de tra message dung ngu canh thay vi loi DB.
     */
    @Transactional
    public void delete(UUID productId, String role) {
        requirePrivileged(role);
        Product product = find(productId);
        if (leadRepository.existsByProductId(productId)) {
            throw new ConflictException("Product still has leads; delete them first");
        }
        if (contactDetailRepository.existsByProductId(productId)) {
            throw new ConflictException("Product is used in a contract; delete it first");
        }
        productRepository.delete(product);
    }

    private Product find(UUID productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Product not found"));
    }

    private void requirePrivileged(String role) {
        if (!ADMIN.equals(role) && !MANAGER.equals(role)) {
            throw new ResponseStatusException(FORBIDDEN, "Only ADMIN/MANAGER can manage products");
        }
    }
}
