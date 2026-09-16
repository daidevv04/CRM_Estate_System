package com.estatecrm.crm_service.entity;

import com.estatecrm.crm_service.enums.LeadStage;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Khach hang tiem nang quan tam mot san pham.
 * customer_id va assigned_to la UUID tho tro toi customer-db.customers.id va
 * user-db.users.id (project Supabase khac), khong the dat FK vat ly.
 * product_id nam cung DB nay nen co FK that o tang DB.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "leads")
public class Lead extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LeadStage stage = LeadStage.NEW;

    /** Gia tri du kien cua thuong vu, numeric(15,2). */
    @Column(name = "expected_value", precision = 15, scale = 2)
    private BigDecimal expectedValue;

    /** Ngay du kien chot. Khong chan qua khu o DB (now() la ham volatile). */
    @Column(name = "close_date")
    private LocalDate closeDate;

    @Column(name = "assigned_to", nullable = false)
    private UUID assignedTo;
}
