package com.estatecrm.crm_service.service;

import com.estatecrm.crm_service.dto.ContactDetailResponse;
import com.estatecrm.crm_service.dto.CreateContactDetailRequest;
import com.estatecrm.crm_service.dto.UpdateContactDetailRequest;
import com.estatecrm.crm_service.entity.ContactDetail;
import com.estatecrm.crm_service.entity.Deal;
import com.estatecrm.crm_service.entity.Product;
import com.estatecrm.crm_service.enums.ProductStatus;
import com.estatecrm.crm_service.exception.ConflictException;
import com.estatecrm.crm_service.repository.ContactDetailRepository;
import com.estatecrm.crm_service.repository.ProductRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.NOT_FOUND;

/**
 * Dong san pham trong hop dong. Quyen rieng ke thua tu hop dong qua
 * DealService.requireAccess, giong cach EmailCareService ke tu khach hang.
 * San pham phai AVAILABLE/RESERVED khi them vao hop dong (rang buoc tuy
 * trang thai khong dat duoc o DB, xem V1__init.sql muc contact_detail).
 */
@Service
public class ContactDetailService {

    private final ContactDetailRepository contactDetailRepository;
    private final ProductRepository productRepository;
    private final DealService dealService;

    public ContactDetailService(
            ContactDetailRepository contactDetailRepository,
            ProductRepository productRepository,
            DealService dealService) {
        this.contactDetailRepository = contactDetailRepository;
        this.productRepository = productRepository;
        this.dealService = dealService;
    }

    @Transactional
    public ContactDetailResponse create(
            UUID dealId, CreateContactDetailRequest request, UUID actorId, String role) {
        Deal deal = dealService.requireAccess(dealId, actorId, role);
        Product product = productRepository.findById(request.productId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Product not found"));
        if (product.getStatus() == ProductStatus.SOLD) {
            throw new ConflictException("Product is already sold and cannot be added to a contract");
        }
        if (contactDetailRepository.existsByDealIdAndProductId(dealId, request.productId())) {
            throw new ConflictException("Product is already in this contract");
        }
        ContactDetail detail = new ContactDetail();
        detail.setDealId(deal.getId());
        detail.setProductId(request.productId());
        detail.setUnitPrice(request.unitPrice());
        if (request.quantity() != null) {
            detail.setQuantity(request.quantity());
        }
        detail.setNote(request.note());
        return ContactDetailResponse.from(contactDetailRepository.save(detail));
    }

    @Transactional(readOnly = true)
    public List<ContactDetailResponse> list(UUID dealId, UUID actorId, String role) {
        dealService.requireAccess(dealId, actorId, role);
        return contactDetailRepository.findByDealId(dealId).stream()
                .map(ContactDetailResponse::from)
                .toList();
    }

    @Transactional
    public ContactDetailResponse update(
            UUID dealId,
            UUID itemId,
            UpdateContactDetailRequest request,
            UUID actorId,
            String role) {
        ContactDetail detail = requireItem(dealId, itemId, actorId, role);
        if (request.unitPrice() != null) {
            detail.setUnitPrice(request.unitPrice());
        }
        if (request.quantity() != null) {
            detail.setQuantity(request.quantity());
        }
        if (request.note() != null) {
            detail.setNote(request.note());
        }
        detail.setUpdatedBy(actorId);
        return ContactDetailResponse.from(contactDetailRepository.save(detail));
    }

    @Transactional
    public void delete(UUID dealId, UUID itemId, UUID actorId, String role) {
        contactDetailRepository.delete(requireItem(dealId, itemId, actorId, role));
    }

    /** Nap dong theo id va kiem tra no thuoc dung hop dong dang xet. */
    private ContactDetail requireItem(
            UUID dealId, UUID itemId, UUID actorId, String role) {
        dealService.requireAccess(dealId, actorId, role);
        return contactDetailRepository.findById(itemId)
                .filter(item -> item.getDealId().equals(dealId))
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Contract item not found"));
    }
}
