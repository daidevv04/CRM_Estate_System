package com.estatecrm.crm_service.entity;

import com.estatecrm.crm_service.enums.ApprovalStatus;
import com.estatecrm.crm_service.enums.DealStatus;
import com.estatecrm.crm_service.enums.PaymentStatus;
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
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Hop dong chinh thuc khi mot lead chot thanh cong.
 * lead_id la UNIQUE o DB: mot lead chi ra mot hop dong. Nhieu san pham trong
 * cung hop dong nam o contact_detail.
 * sales_id va approved_by la UUID tho tro toi user-db.users.id.
 *
 * Rang buoc dang luu y o DB (xem V1__init.sql):
 *  - APPROVED bat buoc co approved_by va approved_at
 *  - deposit_amount khong duoc lon hon contract_value
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "deals")
public class Deal extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "lead_id", nullable = false, unique = true)
    private UUID leadId;

    @Column(name = "sales_id", nullable = false)
    private UUID salesId;

    @Column(name = "contract_code", nullable = false, unique = true, length = 50)
    private String contractCode;

    @Column(name = "contract_value", precision = 15, scale = 2)
    private BigDecimal contractValue;

    @Column(name = "deposit_amount", precision = 15, scale = 2)
    private BigDecimal depositAmount;

    @Column(name = "deposit_date")
    private LocalDate depositDate;

    @Column(name = "signed_date")
    private LocalDate signedDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 20)
    private PaymentStatus paymentStatus = PaymentStatus.UNPAID;

    /** Tien mat / Chuyen khoan / Vay ngan hang. De tu do, khong gioi han enum. */
    @Column(name = "payment_method", length = 30)
    private String paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "approval_status", nullable = false, length = 20)
    private ApprovalStatus approvalStatus = ApprovalStatus.PENDING;

    @Column(name = "approved_by")
    private UUID approvedBy;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    /** Link file scan hop dong (S3/Drive). */
    @Column(name = "file_url", length = 500)
    private String fileUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DealStatus status = DealStatus.IN_PROGRESS;

    @Column(columnDefinition = "text")
    private String note;
}
