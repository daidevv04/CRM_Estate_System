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

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "product_id")
    private UUID productId;

    @Column(name = "demand_notes", columnDefinition = "TEXT")
    private String demandNotes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LeadStage stage = LeadStage.NEW;

    @Column(name = "expected_value", precision = 15, scale = 2)
    private BigDecimal expectedValue;

    @Column(name = "close_date")
    private LocalDate closeDate;

    @Column(name = "assigned_to", nullable = false)
    private UUID assignedTo;
}
