package com.estatecrm.crm_service.repository;

import com.estatecrm.crm_service.entity.ContactDetail;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContactDetailRepository extends JpaRepository<ContactDetail, UUID> {

    List<ContactDetail> findByDealId(UUID dealId);

    boolean existsByDealIdAndProductId(UUID dealId, UUID productId);

    /** Xoa product con bi dong hop dong tro toi thi chan truoc (FK khong cascade). */
    boolean existsByProductId(UUID productId);
}
