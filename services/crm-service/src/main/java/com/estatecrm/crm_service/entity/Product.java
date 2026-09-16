package com.estatecrm.crm_service.entity;

import com.estatecrm.crm_service.enums.ProductStatus;
import com.estatecrm.crm_service.enums.ProductType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * San pham bat dong san thuoc mot du an (can ho, dat, nha pho).
 * project_id la UUID tho thay vi @ManyToOne: giu entity phang nhu cac bang khac
 * cua he thong, tranh lazy-loading ngoai y muon.
 * Ma can (code) chi duy nhat trong pham vi mot du an.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "products", uniqueConstraints = @UniqueConstraint(
        name = "uq_products_project_code", columnNames = {"project_id", "code"}))
public class Product extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(nullable = false, length = 50)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProductType type;

    /** Dien tich, m2. numeric(10,2) nen dung BigDecimal chu khong phai double. */
    @Column(precision = 10, scale = 2)
    private BigDecimal area;

    @Column(length = 20)
    private String block;

    /** Gia niem yet. numeric(15,2). */
    @Column(precision = 15, scale = 2)
    private BigDecimal price;

    private Integer bedroom;

    @Column(length = 20)
    private String direction;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProductStatus status = ProductStatus.AVAILABLE;
}
