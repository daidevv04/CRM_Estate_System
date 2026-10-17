package com.estatecrm.crm_service.repository;

import com.estatecrm.crm_service.entity.ProductReservation;
import com.estatecrm.crm_service.enums.ReservationStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductReservationRepository extends JpaRepository<ProductReservation, UUID> {

    Optional<ProductReservation> findFirstByProductIdAndStatusOrderByPriorityAsc(
            UUID productId, ReservationStatus status);

    List<ProductReservation> findByProductIdAndStatus(UUID productId, ReservationStatus status);

    List<ProductReservation> findByStatusAndExpiryDateBefore(ReservationStatus status, LocalDate date);

    List<ProductReservation> findByProductIdOrderByCreatedAtDesc(UUID productId);
}
