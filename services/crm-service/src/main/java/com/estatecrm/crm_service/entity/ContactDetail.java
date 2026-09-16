package com.estatecrm.crm_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Chi tiet san pham trong mot hop dong (thiet ke muc 1.12).
 * Ten bang giu nguyen theo thiet ke; y nghia la "dong san pham cua deal".
 * FK deal_id ON DELETE CASCADE: xoa hop dong thi cac dong di theo.
 * Cung mot san pham khong duoc xuat hien 2 lan trong cung hop dong.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "contact_detail", uniqueConstraints = @UniqueConstraint(
        name = "uq_contact_detail_deal_product", columnNames = {"deal_id", "product_id"}))
public class ContactDetail extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "deal_id", nullable = false)
    private UUID dealId;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    /** Gia tho thuan cho san pham nay, co the khac gia niem yet cua Product. */
    @Column(name = "unit_price", precision = 15, scale = 2)
    private BigDecimal unitPrice;

    /** Mac dinh 1, DB cung co DEFAULT 1 cho truong hop ghi bang SQL tho. */
    @Column(nullable = false)
    private Integer quantity = 1;

    /**
     * Khai bao DEFAULT o cot khong lam Hibernate bo cot khoi cau INSERT: no van gui
     * NULL tuong minh. Nen default phai ap o day moi chac.
     */
    @PrePersist
    void applyDefaults() {
        if (quantity == null) {
            quantity = 1;
        }
    }

    @Column(columnDefinition = "text")
    private String note;
}
