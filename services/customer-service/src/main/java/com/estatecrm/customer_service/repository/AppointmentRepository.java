package com.estatecrm.customer_service.repository;

import com.estatecrm.customer_service.entity.Appointment;
import com.estatecrm.customer_service.enums.AppointmentStatus;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AppointmentRepository
        extends JpaRepository<Appointment, UUID>, JpaSpecificationExecutor<Appointment> {

    /** UUID khong the trung voi ban ghi nao, dung khi tao moi lich hen. */
    UUID NO_APPOINTMENT = new UUID(0L, 0L);

    /** Dem lich hen cua khach, de chan xoa khach khi con du lieu con. */
    long countByCustomerId(UUID customerId);

    /**
     * Loc dong cho GET /appointments. salesId duoc truyen tu service: SALES bi ep
     * bang chinh ho, ADMIN/MANAGER truyen null de xem tat ca. from/to loc theo
     * khoang giao nhau (lich nao cham vao khoang duoc chon).
     */
    static Specification<Appointment> filter(
            UUID salesId,
            UUID customerId,
            AppointmentStatus status,
            LocalDateTime from,
            LocalDateTime to) {
        return (root, query, cb) -> {
            List<Predicate> conditions = new ArrayList<>();
            if (salesId != null) {
                conditions.add(cb.equal(root.get("salesId"), salesId));
            }
            if (customerId != null) {
                conditions.add(cb.equal(root.get("customer").get("id"), customerId));
            }
            if (status != null) {
                conditions.add(cb.equal(root.get("status"), status));
            }
            if (from != null) {
                conditions.add(cb.greaterThan(root.get("endTime"), from));
            }
            if (to != null) {
                conditions.add(cb.lessThan(root.get("startTime"), to));
            }
            return cb.and(conditions.toArray(new Predicate[0]));
        };
    }

    /**
     * Dem so lich bi giao nhau cua mot sales: lich cu bat dau truoc khi lich
     * moi ket thuc VA ket thuc sau khi lich moi bat dau. Bo qua lich da huy.
     * Truyen NO_APPOINTMENT khi tao moi.
     */
    @Query("""
            select count(a) from Appointment a
            where a.salesId = :salesId
              and a.status <> com.estatecrm.customer_service.enums.AppointmentStatus.CANCELLED
              and a.startTime < :endTime
              and a.endTime > :startTime
              and a.id <> :excludeId
            """)
    long countOverlapping(
            @Param("salesId") UUID salesId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            @Param("excludeId") UUID excludeId);
}
