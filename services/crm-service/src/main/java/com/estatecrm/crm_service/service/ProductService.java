package com.estatecrm.crm_service.service;

import com.estatecrm.crm_service.dto.CreateProductRequest;
import com.estatecrm.crm_service.dto.LockProductRequest;
import com.estatecrm.crm_service.dto.ProductResponse;
import com.estatecrm.crm_service.dto.UpdateProductRequest;
import com.estatecrm.crm_service.entity.Product;
import com.estatecrm.crm_service.entity.ProductReservation;
import com.estatecrm.crm_service.enums.ProductStatus;
import com.estatecrm.crm_service.enums.ProductType;
import com.estatecrm.crm_service.enums.ReservationStatus;
import com.estatecrm.crm_service.exception.ConflictException;
import com.estatecrm.crm_service.repository.ContactDetailRepository;
import com.estatecrm.crm_service.repository.DealRepository;
import com.estatecrm.crm_service.repository.LeadRepository;
import com.estatecrm.crm_service.repository.ProductRepository;
import com.estatecrm.crm_service.repository.ProductReservationRepository;
import com.estatecrm.crm_service.repository.ProjectRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class ProductService {

    private static final String ADMIN = "ADMIN";
    private static final String MANAGER = "MANAGER";
    private static final String SALES = "SALES";

    private final ProductRepository productRepository;
    private final ProjectRepository projectRepository;
    private final LeadRepository leadRepository;
    private final ContactDetailRepository contactDetailRepository;
    private final ImageStorageService imageStorageService;
    private final ProjectAssignmentService projectAssignmentService;
    private final ProductReservationRepository productReservationRepository;
    private final DealRepository dealRepository;

    public ProductService(
            ProductRepository productRepository,
            ProjectRepository projectRepository,
            LeadRepository leadRepository,
            ContactDetailRepository contactDetailRepository,
            ImageStorageService imageStorageService,
            ProjectAssignmentService projectAssignmentService,
            ProductReservationRepository productReservationRepository,
            DealRepository dealRepository) {
        this.productRepository = productRepository;
        this.projectRepository = projectRepository;
        this.leadRepository = leadRepository;
        this.contactDetailRepository = contactDetailRepository;
        this.imageStorageService = imageStorageService;
        this.projectAssignmentService = projectAssignmentService;
        this.productReservationRepository = productReservationRepository;
        this.dealRepository = dealRepository;
    }

    @Transactional
    public ProductResponse create(CreateProductRequest request, UUID actorId, String role) {
        requirePrivileged(role);
        if (MANAGER.equals(role) && actorId != null && !projectAssignmentService.isAssignedToProject(actorId, request.projectId())) {
            throw new ResponseStatusException(FORBIDDEN, "Project is not assigned to you");
        }
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
        product.setImageUrl(request.imageUrl());
        if (request.status() == ProductStatus.RESERVED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Use the reservation action to reserve a product");
        }
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
        return list(projectId, type, status, block, minPrice, maxPrice, keyword, null, null, pageable);
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
            UUID actorId,
            String role,
            Pageable pageable) {
        java.util.List<UUID> allowedProjectIds = null;
        if ((MANAGER.equals(role) || SALES.equals(role)) && actorId != null) {
            java.util.List<UUID> assigned = projectAssignmentService.getAssignedProjectIds(actorId);
            if (assigned.isEmpty()) {
                return Page.empty(pageable);
            }
            if (projectId != null && !assigned.contains(projectId)) {
                return Page.empty(pageable);
            }
            allowedProjectIds = assigned;
        }
        return productRepository
                .findAll(ProductRepository.filter(
                        projectId, type, status, block, minPrice, maxPrice, keyword, allowedProjectIds),
                        pageable)
                .map(ProductResponse::from);
    }

    @Transactional(readOnly = true)
    public ProductResponse get(UUID productId, UUID actorId, String role) {
        Product product = find(productId);
        verifyProjectAccess(product, actorId, role);
        return ProductResponse.from(product);
    }

    @Transactional
    public ProductResponse update(
            UUID productId, UpdateProductRequest request, UUID actorId, String role) {
        requirePrivileged(role);
        Product product = findAndVerifyAccess(productId, actorId, role);
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
        if (request.imageUrl() != null) {
            product.setImageUrl(request.imageUrl());
        }
        if (request.status() != null) {
            if (request.status() != product.getStatus()) {
                if (product.getStatus() == ProductStatus.RESERVED || request.status() == ProductStatus.RESERVED) {
                    throw new ConflictException("Use the reservation/unlock actions to change a reservation");
                }
                if (dealRepository.hasOtherApprovedDeal(productId, null)) {
                    throw new ConflictException("Product belongs to an approved contract");
                }
            }
            product.setStatus(request.status());
        }
        product.setUpdatedBy(actorId);
        return ProductResponse.from(productRepository.save(product));
    }

    @Transactional
    public void delete(UUID productId, UUID actorId, String role) {
        requirePrivileged(role);
        Product product = findAndVerifyAccess(productId, actorId, role);
        if (leadRepository.existsByProductId(productId)) {
            throw new ConflictException("Product still has leads; delete them first");
        }
        if (contactDetailRepository.existsByProductId(productId)) {
            throw new ConflictException("Product is used in a contract; delete it first");
        }
        productRepository.delete(product);
    }

    @Transactional
    public void delete(UUID productId, String role) {
        delete(productId, null, role);
    }

    @Transactional
    public ProductResponse uploadImage(UUID productId, MultipartFile image, UUID actorId, String role) {
        requirePrivileged(role);
        Product product = findAndVerifyAccess(productId, actorId, role);
        product.setImageUrl(imageStorageService.upload("products", productId.toString(), image));
        product.setUpdatedBy(actorId);
        return ProductResponse.from(productRepository.save(product));
    }

    private Product findAndVerifyAccess(UUID productId, UUID actorId, String role) {
        Product product = productRepository.findWithLockById(productId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Product not found"));
        verifyProjectAccess(product, actorId, role);
        return product;
    }

    private void verifyProjectAccess(Product product, UUID actorId, String role) {
        if (!ADMIN.equals(role) && !MANAGER.equals(role) && !SALES.equals(role)) {
            throw new ResponseStatusException(FORBIDDEN, "Unsupported role");
        }
        if (MANAGER.equals(role) || SALES.equals(role)) {
            if (actorId == null) {
                throw new ResponseStatusException(FORBIDDEN, "Actor is required");
            }
            if (!projectAssignmentService.isAssignedToProject(actorId, product.getProjectId())) {
                throw new ResponseStatusException(FORBIDDEN, "Product belongs to a project you are not assigned to");
            }
        }
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

    @Transactional
    public ProductResponse lock(UUID productId, LockProductRequest request, UUID actorId, String role) {
        Product product = findAndVerifyAccess(productId, actorId, role);

        if (product.getStatus() == ProductStatus.SOLD) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot lock a sold product");
        }
        if (dealRepository.hasOtherApprovedDeal(productId, null)) {
            throw new ConflictException("Product belongs to an approved contract");
        }

        LocalDate today = LocalDate.now();
        if (product.getStatus() == ProductStatus.RESERVED) {
            if (product.getLockExpiresAt() != null && product.getLockExpiresAt().isBefore(today)) {
                productReservationRepository.findFirstByProductIdAndStatusOrderByPriorityAsc(
                        productId, ReservationStatus.ACTIVE).ifPresent(r -> {
                    r.setStatus(ReservationStatus.EXPIRED);
                    productReservationRepository.save(r);
                    productReservationRepository.flush();
                });
            } else {
                throw new ConflictException("Product is already reserved until " + product.getLockExpiresAt());
            }
        }

        LocalDate expiry = request.expiryDate();
        if (expiry == null) {
            int days = (request.days() != null && request.days() > 0) ? request.days() : 2;
            expiry = today.plusDays(days);
        }
        if (expiry.isBefore(today)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Expiry date cannot be in the past");
        }

        BigDecimal deposit = request.depositAmount() != null ? request.depositAmount() : BigDecimal.ZERO;

        product.setStatus(ProductStatus.RESERVED);
        product.setLockedBy(actorId);
        product.setLockedCustomerId(request.customerId());
        product.setLockExpiresAt(expiry);
        product.setLockDepositAmount(deposit);
        product.setUpdatedBy(actorId);
        Product saved = productRepository.save(product);

        ProductReservation reservation = new ProductReservation();
        reservation.setProductId(productId);
        reservation.setCustomerId(request.customerId());
        reservation.setSalesId(actorId);
        reservation.setPriority(1);
        reservation.setStatus(ReservationStatus.ACTIVE);
        reservation.setDepositAmount(deposit);
        reservation.setExpiryDate(expiry);
        reservation.setNotes(request.notes());
        productReservationRepository.save(reservation);

        return ProductResponse.from(saved);
    }

    @Transactional
    public ProductResponse unlock(UUID productId, UUID actorId, String role) {
        Product product = findAndVerifyAccess(productId, actorId, role);

        if (product.getStatus() != ProductStatus.RESERVED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Product is not reserved");
        }

        boolean isOwner = product.getLockedBy() != null && product.getLockedBy().equals(actorId);
        boolean isPrivileged = ADMIN.equals(role) || MANAGER.equals(role);
        if (!isOwner && !isPrivileged) {
            throw new ResponseStatusException(FORBIDDEN, "You do not have permission to unlock this product");
        }

        product.setStatus(ProductStatus.AVAILABLE);
        product.setLockedBy(null);
        product.setLockedCustomerId(null);
        product.setLockExpiresAt(null);
        product.setLockDepositAmount(null);
        product.setUpdatedBy(actorId);
        Product saved = productRepository.save(product);

        productReservationRepository.findFirstByProductIdAndStatusOrderByPriorityAsc(
                productId, ReservationStatus.ACTIVE).ifPresent(r -> {
            r.setStatus(ReservationStatus.CANCELLED);
            productReservationRepository.save(r);
        });

        return ProductResponse.from(saved);
    }

    @Transactional
    public int releaseExpiredReservations() {
        LocalDate today = LocalDate.now();
        List<ProductReservation> expired = productReservationRepository
                .findByStatusAndExpiryDateBefore(ReservationStatus.ACTIVE, today);
        int released = 0;
        expired.sort(java.util.Comparator.comparing(ProductReservation::getProductId));
        for (ProductReservation res : expired) {
            Product prod = productRepository.findWithLockById(res.getProductId()).orElse(null);
            if (prod != null) {
                if (prod.getStatus() == ProductStatus.RESERVED &&
                    prod.getLockExpiresAt() != null && prod.getLockExpiresAt().isBefore(today) &&
                    res.getExpiryDate().equals(prod.getLockExpiresAt()) &&
                    java.util.Objects.equals(res.getCustomerId(), prod.getLockedCustomerId())) {
                    res.setStatus(ReservationStatus.EXPIRED);
                    productReservationRepository.save(res);
                    prod.setStatus(ProductStatus.AVAILABLE);
                    prod.setLockedBy(null);
                    prod.setLockedCustomerId(null);
                    prod.setLockExpiresAt(null);
                    prod.setLockDepositAmount(null);
                    productRepository.save(prod);
                    released++;
                }
            }
        }
        return released;
    }
}
